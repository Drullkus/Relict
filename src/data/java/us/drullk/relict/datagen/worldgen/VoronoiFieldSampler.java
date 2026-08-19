package us.drullk.relict.datagen.worldgen;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import us.drullk.relict.Relict;
import us.drullk.relict.init.custom.RelictCustomRegistries;
import us.drullk.relict.init.custom.RelictVoronoiSources;
import us.drullk.relict.init.worldgen.RelictDimension;
import us.drullk.relict.worldgen.ElevationClass;
import us.drullk.relict.worldgen.Province;
import us.drullk.relict.worldgen.VoronoiSource;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Evaluates the real voronoi graph during datagen and reports whether the blending law holds, and whether
 * the underground biome pick is epoch-biased.
 *
 * <h2>Why this exists, and why it is a data provider</h2>
 * Block-scan verification cannot answer these questions: carvers and features confound the shape, and a
 * one-block step at a cell vertex is invisible in a screenshot but fatal in play. It cannot be a standalone
 * {@code main} either — see {@link RidgeFieldSampler} for that story. Running inside datagen gets the graph
 * datagen emits, with the seed injected through {@link VoronoiSource#bindSeed(net.minecraft.resources.Identifier, long)}
 * rather than borrowed from a server that does not exist yet. Emits no datapack files — writes plain-text
 * reports and PGM images under {@code relict.terrainReportDir} (set by {@code runServerData}; see
 * build.gradle), and always prints to stdout regardless of whether that property is set.
 *
 * <h2>What each surface check answers</h2>
 * <ul>
 *   <li><b>(a) continuity</b> — the largest surface step between two adjacent columns, swept along dense lines
 *       through numerically located cell vertices, where an argmin-based blend steps worst.</li>
 *   <li><b>(b) plateaus</b> — that a cell's interior reads {@code seaLevel + ELEVATION_SCALE * cellElevation}
 *       exactly, which is the whole of the elevation semantics.</li>
 *   <li><b>(c) borders</b> — that a two-province border is monotonic across and carries the plain average of
 *       the two elevations on the border line itself.</li>
 *   <li><b>(d) max contrast</b> — the same across the largest legal contrast, {@code -1} against {@code +1},
 *       looking for oscillation or overshoot rather than a clean scarp.</li>
 *   <li><b>(e) ridges</b> — that the wrinkle field survived, by the curvature distribution the handoff
 *       recorded, and how much relief the plain itself carries between the ridges.</li>
 *   <li><b>(f) maps</b> — a hillshade and an epoch map, for eyeballing.</li>
 *   <li><b>(g) elevation ladder</b> — the band each province reaches once the epoch ramp carries the relief,
 *       and how far a plateau steps between two adjacent cells of the same class and of different classes.</li>
 *   <li><b>(h) detached solids</b> — real blocks, counted above the analytic surface, where the terrain
 *       density cannot put any. Aquifers are permanently disabled in {@code RelictDimensionGenerator}, so
 *       this scans the current density function once rather than comparing an aquifer toggle; it exists as
 *       a regression tripwire and should always read zero.</li>
 * </ul>
 * The underground report below is separate: it is a headless proof that the underground biome pick is
 * epoch-biased, plus a teleport listing for eyeballing each province in the world.
 */
public final class VoronoiFieldSampler implements DataProvider {

    /** Arbitrary and fixed, so two runs are comparable. Not the seed of any world. */
    private static final long SEED = 0x5EEDL;

    private static final int SEA_LEVEL = 128;

    private static final int MAP_STEP = 8;
    private static final int MAP_GRID = 512;

    private static final int EPOCH_MAP_STEP = 64;
    private static final int EPOCH_MAP_GRID = 512;

    private static final int VERTEX_SCAN_STEP = 16;
    private static final int VERTEX_SCAN_RADIUS = 3072;
    private static final int VERTICES_SWEPT = 8;
    private static final int SWEEP_HALF_LENGTH = 192;
    private static final int PROFILE_HALF_LENGTH = 128;

    /** Above this the step is a ridge, not a blend seam; the blend's own bound is the 2 blocks below it. */
    private static final double PLATEAU_STEP_LIMIT = 2.0;

    /** Ridge shape is 0..1, so anything above this means the column stands on a ridge rather than a plain. */
    private static final double RIDGE_PRESENT = 0.02;

    private static final int RELIEF_WINDOW = 128;

    /** Below this a border is two spellings of the same height and profiling it proves nothing. */
    private static final double BORDER_MIN_CONTRAST = 0.05;

    /** Slack for comparing two doubles that reached the same quantity by different arithmetic. */
    private static final double ROUNDING = 1.0e-9;

    private static final int PROBE_CELLS = 3;
    private static final int PROBE_PATCH = 5;
    private static final int PROBE_PATCH_STEP = 16;
    private static final int PROBE_PROFILE_HEIGHT = 48;

    /** Underground report: distinct fixed seed so its output stays comparable across runs on its own. */
    private static final long UNDERGROUND_SEED = 20260818L;
    private static final int UNDERGROUND_CELL_RADIUS = 60;
    private static final int UNDERGROUND_TELEPORT_Y = 40;

    private final CompletableFuture<HolderLookup.Provider> registries;

    public VoronoiFieldSampler(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        this.registries = registries;
    }

    @Override
    public String getName() {
        return "Relict Voronoi Field Sampler";
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        return this.registries.thenAccept(registries -> {
            Path directory = reportDirectory();

            StringBuilder surfaceReport = surfaceReport(registries);
            writeReport(directory, "voronoi_report.txt", surfaceReport.toString());
            System.out.print(surfaceReport);

            StringBuilder undergroundReport = undergroundReport(registries);
            writeReport(directory, "voronoi_underground_report.txt", undergroundReport.toString());
            System.out.print(undergroundReport);
        });
    }

    private static Path reportDirectory() {
        String dir = System.getProperty("relict.terrainReportDir");
        return dir == null ? null : Path.of(dir);
    }

    private static void writeReport(Path directory, String fileName, String text) {
        if (directory == null) {
            return;
        }

        try {
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(fileName), text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ------------------------------------------------------------------------------------- surface report

    private static StringBuilder surfaceReport(HolderLookup.Provider registries) {
        PositionalRandomFactory random = new XoroshiroRandomSource(SEED).forkPositional();

        VoronoiSource mars = registries.lookupOrThrow(RelictCustomRegistries.VORONOI_SOURCE_REGISTRY)
                .getOrThrow(RelictVoronoiSources.MARS).value();
        mars.bindSeed(RelictVoronoiSources.MARS.identifier(), SEED);

        HolderLookup.RegistryLookup<DensityFunction> functions = registries.lookupOrThrow(Registries.DENSITY_FUNCTION);
        DensityFunction surfaceHeight = seed(holder(functions, RelictDensityFunctionGenerator.VORONOI_SURFACE_HEIGHT), random);
        DensityFunction relief = seed(holder(functions, RelictDensityFunctionGenerator.RELIEF), random);
        DensityFunction ridgeShape = seed(holder(functions, RelictDensityFunctionGenerator.RIDGE_SHAPE), random);
        DensityFunction surfaceY = RelictNoiseRouter.surfaceY(surfaceHeight, relief, SEA_LEVEL);

        StringBuilder report = new StringBuilder("\n=== voronoi province field ===\n");
        report.append(String.format("%nseed %d   cell_size %d   jitter %.2f   blend_width %.1f (max %.1f)   epoch_spacing %d   epoch_relief %.2f%n",
                SEED, mars.cellSize(), mars.jitter(), mars.blendWidth(),
                VoronoiSource.maxBlendWidth(mars.cellSize(), mars.jitter()), mars.epochSpacing(), mars.epochRelief()));
        report.append(String.format("sea level %d   elevation scale %.0f   plateau band y %.0f..%.0f%n",
                SEA_LEVEL, RelictNoiseRouter.ELEVATION_SCALE,
                SEA_LEVEL - RelictNoiseRouter.ELEVATION_SCALE, SEA_LEVEL + RelictNoiseRouter.ELEVATION_SCALE));

        List<long[]> vertices = findVertices(mars);
        continuitySweep(report, mars, surfaceHeight, surfaceY, vertices);
        plateauTable(report, mars, surfaceHeight);
        borderProfiles(report, mars, surfaceHeight);
        maxContrast(report, registries);
        ridgeCurvature(report, mars, surfaceY, relief, ridgeShape);
        identitySalt(report, registries);
        epochBias(report, mars);
        maps(report, reportDirectory(), mars, surfaceY);
        elevationLadder(report, mars);
        detachedSolids(report, registries, mars, surfaceY);

        return report;
    }

    // ------------------------------------------------------------------------------------ (a) continuity

    /**
     * Cell vertices, found numerically: a coarse grid of nearest-cell IDs, and every 2x2 block of samples
     * holding three or more distinct cells brackets one. No polygon clipping — this only has to find the
     * neighborhood, and the sweep below covers the rest.
     */
    private static List<long[]> findVertices(VoronoiSource source) {
        int span = 2 * VERTEX_SCAN_RADIUS / VERTEX_SCAN_STEP;
        long[][] cells = new long[span][span];
        for (int iz = 0; iz < span; iz++) {
            for (int ix = 0; ix < span; ix++) {
                VoronoiSource.Cell cell = source.nearest(ix * VERTEX_SCAN_STEP - VERTEX_SCAN_RADIUS,
                        iz * VERTEX_SCAN_STEP - VERTEX_SCAN_RADIUS);
                cells[iz][ix] = (long) cell.cellX() << 32 | cell.cellZ() & 0xFFFFFFFFL;
            }
        }

        List<long[]> found = new ArrayList<>();
        for (int iz = 0; iz + 1 < span; iz++) {
            for (int ix = 0; ix + 1 < span; ix++) {
                long a = cells[iz][ix];
                long b = cells[iz][ix + 1];
                long c = cells[iz + 1][ix];
                long d = cells[iz + 1][ix + 1];
                int distinct = (int) Arrays.stream(new long[]{a, b, c, d}).distinct().count();
                if (distinct >= 3) {
                    found.add(new long[]{ix * VERTEX_SCAN_STEP - VERTEX_SCAN_RADIUS, iz * VERTEX_SCAN_STEP - VERTEX_SCAN_RADIUS});
                }
            }
        }

        return found;
    }

    private static void continuitySweep(StringBuilder report, VoronoiSource source, DensityFunction surfaceHeight,
                                        DensityFunction surfaceY, List<long[]> vertices) {
        report.append(String.format("%n(a) continuity%n"));
        report.append(String.format("    %d vertex regions located within %d blocks of the origin%n",
                vertices.size(), VERTEX_SCAN_RADIUS));

        double worstPlateau = 0.0;
        long[] worstAt = {0, 0};
        double worstSurface = 0.0;
        int swept = 0;

        for (int v = 0; v < vertices.size() && swept < VERTICES_SWEPT; v += Math.max(1, vertices.size() / VERTICES_SWEPT)) {
            long[] vertex = vertices.get(v);
            swept++;

            for (boolean alongX : new boolean[]{true, false}) {
                double previousPlateau = Double.NaN;
                double previousSurface = Double.NaN;

                for (int d = -SWEEP_HALF_LENGTH; d <= SWEEP_HALF_LENGTH; d++) {
                    int x = (int) vertex[0] + (alongX ? d : 0);
                    int z = (int) vertex[1] + (alongX ? 0 : d);
                    double plateau = SEA_LEVEL + RelictNoiseRouter.ELEVATION_SCALE * sample(surfaceHeight, x, z);
                    double surface = sample(surfaceY, x, z);

                    if (!Double.isNaN(previousPlateau)) {
                        double step = Math.abs(plateau - previousPlateau);
                        if (step > worstPlateau) {
                            worstPlateau = step;
                            worstAt = new long[]{x, z};
                        }
                        worstSurface = Math.max(worstSurface, Math.abs(surface - previousSurface));
                    }

                    previousPlateau = plateau;
                    previousSurface = surface;
                }
            }
        }

        report.append(String.format("    swept %d vertices, two 1-block lines of %d blocks each%n", swept, 2 * SWEEP_HALF_LENGTH + 1));
        report.append(String.format("    max |d plateau| between adjacent columns  %.4f blocks at x=%d z=%d  (limit %.1f)  %s%n",
                worstPlateau, worstAt[0], worstAt[1], PLATEAU_STEP_LIMIT, worstPlateau <= PLATEAU_STEP_LIMIT ? "PASS" : "FAIL"));
        report.append(String.format("    max |d surfaceY| between adjacent columns %.4f blocks  (ridge relief included, informational)%n",
                worstSurface));

        // The other place a selected-neighbor blend steps: the grid line where the candidate set changes.
        double worstGridLine = 0.0;
        for (int gz = -3; gz <= 3; gz++) {
            for (int line = -3; line <= 3; line++) {
                int x = line * source.cellSize();
                for (int z = gz * source.cellSize(); z < (gz + 1) * source.cellSize(); z += 7) {
                    double before = sample(surfaceHeight, x - 1, z);
                    double after = sample(surfaceHeight, x + 1, z);
                    worstGridLine = Math.max(worstGridLine, Math.abs(after - before) * RelictNoiseRouter.ELEVATION_SCALE / 2.0);
                }
            }
        }
        report.append(String.format("    max |d plateau| across a scan-window grid line %.4f blocks%n", worstGridLine));
    }

    // -------------------------------------------------------------------------------------- (b) plateaus

    /** Deep-interior samples, one per province, against {@code seaLevel + ELEVATION_SCALE * cellElevation}. */
    private static void plateauTable(StringBuilder report, VoronoiSource source, DensityFunction surfaceHeight) {
        report.append(String.format("%n(b) plateaus, at the cell each province sits deepest inside%n"));
        report.append(String.format("    %-28s %8s %10s %10s %8s%n", "province", "elev", "expected", "measured", "delta"));

        Map<String, double[]> deepest = new LinkedHashMap<>();
        int cells = VERTEX_SCAN_RADIUS / source.cellSize();

        for (int cz = -cells; cz <= cells; cz++) {
            for (int cx = -cells; cx <= cells; cx++) {
                double centerX = source.centerX(cx, cz);
                double centerZ = source.centerZ(cx, cz);
                VoronoiSource.Cell cell = source.nearest((int) centerX, (int) centerZ);
                if (cell.cellX() != cx || cell.cellZ() != cz) {
                    continue;
                }

                Holder<Province> province = source.provinceAt(cx, cz);
                String name = province.unwrapKey().orElseThrow().identifier().toString();
                double margin = cell.distanceToSecondCenter() - cell.distanceToCenter();
                double[] best = deepest.get(name);
                if (best == null || margin > best[0]) {
                    deepest.put(name, new double[]{margin, centerX, centerZ, source.cellElevation(province.value(), cx, cz)});
                }
            }
        }

        deepest.forEach((name, entry) -> {
            double expected = SEA_LEVEL + RelictNoiseRouter.ELEVATION_SCALE * entry[3];
            double measured = SEA_LEVEL + RelictNoiseRouter.ELEVATION_SCALE * sample(surfaceHeight, (int) entry[1], (int) entry[2]);
            report.append(String.format("    %-28s %8.2f %10.2f %10.2f %8.4f %s%n", name, entry[3], expected, measured,
                    measured - expected, Math.abs(measured - expected) < 0.001 ? "PASS" : "FAIL"));
        });
    }

    // --------------------------------------------------------------------------------------- (c) borders

    /**
     * Walks the line joining two neighboring cell centers. The border is the midpoint of that line, so a
     * symmetric law must read the plain average of the two elevations there.
     */
    private static void borderProfiles(StringBuilder report, VoronoiSource source, DensityFunction surfaceHeight) {
        report.append(String.format("%n(c) two-province borders, profiled along the line joining the centers%n"));

        int cells = VERTEX_SCAN_RADIUS / source.cellSize();
        List<int[]> pairs = new ArrayList<>();

        for (int cz = -cells; cz <= cells; cz++) {
            for (int cx = -cells; cx <= cells; cx++) {
                for (int[] step : new int[][]{{1, 0}, {0, 1}}) {
                    int farX = cx + step[0];
                    int farZ = cz + step[1];

                    // A contact worth profiling: two different provinces, far enough apart in elevation that
                    // the transition has a direction for the monotonicity test to follow.
                    if (source.provinceAt(cx, cz) != source.provinceAt(farX, farZ)
                            && Math.abs(cellElevation(source, cx, cz) - cellElevation(source, farX, farZ)) > BORDER_MIN_CONTRAST) {
                        pairs.add(new int[]{cx, cz, farX, farZ});
                    }
                }
            }
        }

        // Deepest into its own border segment first: the 50/50 claim is about two cells alone, and a third
        // inside the kernel would rightly move the value.
        pairs.sort((a, b) -> Double.compare(vertexClearance(source, b), vertexClearance(source, a)));

        int profiled = 0;
        for (int[] pair : pairs) {
            if (profiled >= 3) {
                break;
            }

            if (profileBorder(report, source, surfaceHeight, pair[0], pair[1], pair[2], pair[3],
                    source.provinceAt(pair[0], pair[1]), source.provinceAt(pair[2], pair[3]))) {
                profiled++;
            }
        }
    }

    private static double vertexClearance(VoronoiSource source, int[] pair) {
        int midX = (int) Math.round((source.centerX(pair[0], pair[1]) + source.centerX(pair[2], pair[3])) / 2.0);
        int midZ = (int) Math.round((source.centerZ(pair[0], pair[1]) + source.centerZ(pair[2], pair[3])) / 2.0);
        return source.nearest(midX, midZ).edgeDistance() > 1.0 ? -1.0 : thirdSurplus(source, midX, midZ);
    }

    private static boolean profileBorder(StringBuilder report, VoronoiSource source, DensityFunction surfaceHeight,
                                         int nearX, int nearZ, int farX, int farZ,
                                         Holder<Province> near, Holder<Province> far) {
        double ax = source.centerX(nearX, nearZ);
        double az = source.centerZ(nearX, nearZ);
        double bx = source.centerX(farX, farZ);
        double bz = source.centerZ(farX, farZ);
        int midX = (int) Math.round((ax + bx) / 2.0);
        int midZ = (int) Math.round((az + bz) / 2.0);

        // Only a border away from a vertex proves the 50/50 claim; near one a third cell rightly joins in, so
        // the third-nearest site must be outside the kernel's support along the whole profile.
        VoronoiSource.Cell mid = source.nearest(midX, midZ);
        if (mid.edgeDistance() > 1.0 || thirdSurplus(source, midX, midZ) < 2.0 * source.blendWidth() + PROFILE_HALF_LENGTH) {
            return false;
        }

        double nearElevation = cellElevation(source, nearX, nearZ);
        double farElevation = cellElevation(source, farX, farZ);
        double expected = SEA_LEVEL + RelictNoiseRouter.ELEVATION_SCALE * (nearElevation + farElevation) / 2.0;
        double measured = SEA_LEVEL + RelictNoiseRouter.ELEVATION_SCALE * sample(surfaceHeight, midX, midZ);

        // The tolerance is derived, not chosen. Two things move a real sample off the exact average, and both
        // are properties of the sample point rather than of the law: the block grid puts the nearest integer
        // column a fraction of a block off the bisector, and no border in a real tessellation is far enough
        // from every other site for their weights to be exactly zero.
        // The profile guard puts every other candidate outside the kernel, so the second term is normally zero
        // and the bound is exact. ROUNDING is the slack that costs a comparison of two doubles reached by
        // different arithmetic; it is a nanoblock and cannot hide a real seam.
        double[] surpluses = surpluses(source, midX, midZ);
        double owner = kernel(source, surpluses[1]);
        double others = otherWeight(source, midX, midZ);
        double tolerance = ROUNDING + RelictNoiseRouter.ELEVATION_SCALE
                * (Math.abs(1.0 - owner) * Math.abs(nearElevation - farElevation) / (2.0 * (1.0 + owner))
                + others * 2.0 / (2.0 + others));

        report.append(String.format("%n    %s (%.2f) -> %s (%.2f) at x=%d z=%d%n",
                near.unwrapKey().orElseThrow().identifier().getPath(), nearElevation,
                far.unwrapKey().orElseThrow().identifier().getPath(), farElevation, midX, midZ));
        report.append(String.format("    the sampled column sits %.3f blocks off the bisector, %.0f from the nearest vertex%n",
                surpluses[1] / 2.0, surpluses[2] / 2.0));
        report.append(String.format("    border reads %.3f, average is %.3f, delta %.4f, bound %.4f  %s%n",
                measured, expected, measured - expected, tolerance,
                Math.abs(measured - expected) <= tolerance ? "PASS" : "FAIL"));

        double unitX = (bx - ax) / Math.hypot(bx - ax, bz - az);
        double unitZ = (bz - az) / Math.hypot(bx - ax, bz - az);
        double previous = Double.NaN;
        boolean monotonic = true;
        double sign = Math.signum(farElevation - nearElevation);
        StringBuilder profile = new StringBuilder("    ");

        for (int d = -PROFILE_HALF_LENGTH; d <= PROFILE_HALF_LENGTH; d += 8) {
            double value = SEA_LEVEL + RelictNoiseRouter.ELEVATION_SCALE
                    * sample(surfaceHeight, (int) Math.round(midX + unitX * d), (int) Math.round(midZ + unitZ * d));
            if (!Double.isNaN(previous) && (value - previous) * sign < -0.001) {
                monotonic = false;
            }
            previous = value;
            profile.append(String.format("%.1f ", value));
        }

        report.append(String.format("    profile -%d..+%d by 8:%n%s%n", PROFILE_HALF_LENGTH, PROFILE_HALF_LENGTH, profile));
        report.append(String.format("    monotonic across the border  %s%n", monotonic ? "PASS" : "FAIL"));
        return true;
    }

    private static double cellElevation(VoronoiSource source, int cellX, int cellZ) {
        return source.cellElevation(source.provinceAt(cellX, cellZ).value(), cellX, cellZ);
    }

    /** Surplus of the third-nearest site over the nearest — how far the point is from a cell vertex. */
    private static double thirdSurplus(VoronoiSource source, int blockX, int blockZ) {
        double[] surpluses = surpluses(source, blockX, blockZ);
        return surpluses[2];
    }

    /** Total blend weight held by every candidate but the two owning the border. */
    private static double otherWeight(VoronoiSource source, int blockX, int blockZ) {
        double[] surpluses = surpluses(source, blockX, blockZ);
        double total = 0.0;

        for (int i = 2; i < surpluses.length; i++) {
            total += kernel(source, surpluses[i]);
        }

        return total;
    }

    /** The blending law's own kernel, restated here so a bound derived from it is not derived from itself. */
    private static double kernel(VoronoiSource source, double surplus) {
        double falloff = 1.0 - surplus / (2.0 * source.blendWidth());
        return falloff <= 0.0 ? 0.0 : falloff * falloff;
    }

    /** Every candidate's distance surplus over the nearest, ascending. */
    private static double[] surpluses(VoronoiSource source, int blockX, int blockZ) {
        int gridX = Math.floorDiv(blockX, source.cellSize());
        int gridZ = Math.floorDiv(blockZ, source.cellSize());
        List<Double> distances = new ArrayList<>();

        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                distances.add(Math.hypot(source.centerX(gridX + dx, gridZ + dz) - blockX,
                        source.centerZ(gridX + dx, gridZ + dz) - blockZ));
            }
        }

        distances.sort(Double::compare);
        return distances.stream().mapToDouble(distance -> distance - distances.getFirst()).toArray();
    }

    // ---------------------------------------------------------------------------------- (d) max contrast

    /**
     * A source of nothing but the two extremes, so the largest legal contrast is guaranteed to occur and can
     * be profiled. A clean scarp is monotonic and never leaves the two endpoint elevations. The epoch ramp is
     * switched off here so that {@code -1} and {@code +1} are what the cells actually carry.
     */
    private static void maxContrast(StringBuilder report, HolderLookup.Provider registries) {
        report.append(String.format("%n(d) max contrast, a test source of elevation_offset -1 against +1, epoch_relief 0%n"));

        HolderLookup.RegistryLookup<Biome> biomes = registries.lookupOrThrow(Registries.BIOME);
        Holder<Province> low = Holder.direct(new Province(biomes.getOrThrow(Biomes.DESERT), ElevationClass.LOW, -1.0F, 0.0F, 0.0F));
        Holder<Province> high = Holder.direct(new Province(biomes.getOrThrow(Biomes.BADLANDS), ElevationClass.HIGH, 1.0F, 0.0F, 0.0F));

        VoronoiSource source = new VoronoiSource(RelictProvinceGenerator.CELL_SIZE, RelictProvinceGenerator.JITTER,
                RelictProvinceGenerator.BLEND_WIDTH, RelictProvinceGenerator.EPOCH_SPACING, 0.0F,
                WeightedList.<Holder<Province>>builder().add(low, 1).add(high, 1).build());
        source.bindSeed(Relict.id("max_contrast_test"), SEED);

        int cells = VERTEX_SCAN_RADIUS / source.cellSize();
        for (int cz = -cells; cz <= cells; cz++) {
            for (int cx = -cells; cx <= cells; cx++) {
                if (source.provinceAt(cx, cz).value().elevationOffset() == source.provinceAt(cx + 1, cz).value().elevationOffset()) {
                    continue;
                }

                double ax = source.centerX(cx, cz);
                double az = source.centerZ(cx, cz);
                double bx = source.centerX(cx + 1, cz);
                double bz = source.centerZ(cx + 1, cz);
                int midX = (int) Math.round((ax + bx) / 2.0);
                int midZ = (int) Math.round((az + bz) / 2.0);
                if (source.nearest(midX, midZ).edgeDistance() > 1.0) {
                    continue;
                }

                double unitX = (bx - ax) / Math.hypot(bx - ax, bz - az);
                double unitZ = (bz - az) / Math.hypot(bx - ax, bz - az);
                double low64 = SEA_LEVEL - RelictNoiseRouter.ELEVATION_SCALE;
                double high64 = SEA_LEVEL + RelictNoiseRouter.ELEVATION_SCALE;
                double sign = Math.signum(source.provinceAt(cx + 1, cz).value().elevationOffset()
                        - source.provinceAt(cx, cz).value().elevationOffset());

                double previous = Double.NaN;
                double worstReversal = 0.0;
                double overshoot = 0.0;
                double biggestStep = 0.0;
                StringBuilder profile = new StringBuilder("    ");

                for (int d = -160; d <= 160; d++) {
                    double value = SEA_LEVEL + RelictNoiseRouter.ELEVATION_SCALE * source.blend(
                            (int) Math.round(midX + unitX * d), (int) Math.round(midZ + unitZ * d), source::cellElevation);
                    overshoot = Math.max(overshoot, Math.max(low64 - value, value - high64));
                    if (!Double.isNaN(previous)) {
                        worstReversal = Math.max(worstReversal, -(value - previous) * sign);
                        biggestStep = Math.max(biggestStep, Math.abs(value - previous));
                    }
                    previous = value;
                    if (d % 16 == 0) {
                        profile.append(String.format("%.1f ", value));
                    }
                }

                report.append(String.format("    scarp at x=%d z=%d, profile -160..+160 by 16:%n%s%n", midX, midZ, profile));
                report.append(String.format("    worst reversal %.4f  overshoot %.4f  biggest 1-block step %.4f  %s%n",
                        worstReversal, overshoot, biggestStep,
                        worstReversal < 0.001 && overshoot < 0.001 && biggestStep <= PLATEAU_STEP_LIMIT ? "PASS" : "FAIL"));
                return;
            }
        }

        report.append("    no adjacent extreme pair found, which should be impossible with two provinces\n");
    }

    // ---------------------------------------------------------------------------------------- (e) ridges

    /**
     * The curvature distribution over 8 blocks inside wrinkle_plains cells. The handoff records p50 0.5,
     * p90 1.0, p99 3.0, p99.9 26.5 from the built world; p99.9 landing on the ridge amplitude is the check
     * that the ridges are really there rather than a failed seeding pass reading flat zero.
     */
    private static void ridgeCurvature(StringBuilder report, VoronoiSource source, DensityFunction surfaceY,
                                       DensityFunction relief, DensityFunction ridgeShape) {
        List<Double> curvatures = new ArrayList<>();
        List<Double> midCellWindows = new ArrayList<>();
        List<Double> borderWindows = new ArrayList<>();
        List<Double> midCellAmplitude = new ArrayList<>();
        List<Double> borderAmplitude = new ArrayList<>();
        double lowestRelief = Double.MAX_VALUE;
        double highestRelief = -Double.MAX_VALUE;
        double steepest = 0.0;
        int ridged = 0;
        int carryingRidge = 0;
        int span = 384;

        for (int iz = 0; iz < span; iz++) {
            for (int ix = 1; ix + 6 < span; ix++) {
                int x = ix * MAP_STEP - span * MAP_STEP / 2;
                int z = iz * MAP_STEP - span * MAP_STEP / 2;
                VoronoiSource.Cell cell = source.nearest(x, z);
                if (source.provinceAt(cell.cellX(), cell.cellZ()).value().ridgeAmplitude() <= 0.0F) {
                    continue;
                }

                double left = sample(surfaceY, x - MAP_STEP, z);
                double here = sample(surfaceY, x, z);
                double right = sample(surfaceY, x + MAP_STEP, z);
                curvatures.add(Math.abs(left - 2.0 * here + right));
                steepest = Math.max(steepest, Math.abs(sample(surfaceY, x + 40, z) - here));
                steepest = Math.max(steepest, Math.abs(sample(surfaceY, x, z + 40) - here));

                ridged++;
                boolean midCell = cell.edgeDistance() > 2.0 * source.blendWidth();
                boolean border = cell.edgeDistance() < source.blendWidth();

                double amplitude = source.blend(x, z, (province, cellX, cellZ) -> province.ridgeAmplitude());
                if (midCell) {
                    midCellAmplitude.add(amplitude);
                } else if (border) {
                    borderAmplitude.add(amplitude);
                }

                if (sample(ridgeShape, x, z) > RIDGE_PRESENT) {
                    carryingRidge++;
                    continue;
                }

                // Off-ridge only, so this measures the plain the producer walks between ridges rather than a
                // ridge flank. Split by distance to the border, which is where the blend law dilutes relief.
                if (midCell) {
                    lowestRelief = Math.min(lowestRelief, sample(relief, x, z));
                    highestRelief = Math.max(highestRelief, sample(relief, x, z));
                }

                double window = reliefWindow(relief, ridgeShape, x, z);
                if (Double.isNaN(window)) {
                    continue;
                }

                if (midCell) {
                    midCellWindows.add(window);
                } else if (border) {
                    borderWindows.add(window);
                }
            }
        }

        report.append(String.format("%n(e) ridge relief, inside ridged provinces (%d samples)%n", curvatures.size()));

        if (curvatures.isEmpty()) {
            report.append("    no ridged cells in range\n");
            return;
        }

        double[] sorted = curvatures.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        report.append(String.format("    rise over 40 blocks, at the steepest scarp  %.1f blocks%n", steepest));
        report.append("    ridge_report.txt, from the same field without the province term: 25.4\n");
        report.append(String.format("    curvature over 8 blocks   p50 %.2f   p90 %.2f   p99 %.2f   p99.9 %.2f   max %.2f%n",
                percentile(sorted, 0.50), percentile(sorted, 0.90), percentile(sorted, 0.99),
                percentile(sorted, 0.999), sorted[sorted.length - 1]));
        report.append("    handoff, from the built world: p50 0.5  p90 1.0  p99 3.0  p99.9 26.5\n");
        report.append(String.format("    ridge coverage  %.1f%% of ridged columns carry any ridge%n",
                100.0 * carryingRidge / ridged));
        report.append(String.format("    plain relief off-ridge, mid-cell, whole channel  %.1f blocks peak to trough%n",
                highestRelief - lowestRelief));

        report.append(String.format("    plain relief over a %d-block window, off-ridge, in blocks:%n", RELIEF_WINDOW));
        reliefRow(report, "mid-cell", midCellWindows);
        reliefRow(report, "border", borderWindows);

        // Whether the ridges are diluted everywhere or only where the kernel reaches a neighbor without them.
        report.append("    blended ridge_amplitude, in blocks:\n");
        reliefRow(report, "mid-cell", midCellAmplitude);
        reliefRow(report, "border", borderAmplitude);
    }

    /**
     * Peak-to-trough of the relief channel over one horizontal window, or NaN if a ridge intrudes anywhere in
     * it — a window straddling a ridge foot would report the ridge and not the plain.
     */
    private static double reliefWindow(DensityFunction relief, DensityFunction ridgeShape, int x, int z) {
        double lowest = Double.MAX_VALUE;
        double highest = -Double.MAX_VALUE;

        for (int d = 0; d <= RELIEF_WINDOW; d += 4) {
            if (sample(ridgeShape, x + d, z) > RIDGE_PRESENT) {
                return Double.NaN;
            }

            double value = sample(relief, x + d, z);
            lowest = Math.min(lowest, value);
            highest = Math.max(highest, value);
        }

        return highest - lowest;
    }

    private static void reliefRow(StringBuilder report, String label, List<Double> windows) {
        if (windows.isEmpty()) {
            report.append(String.format("        %-10s no samples%n", label));
            return;
        }

        double[] sorted = windows.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        report.append(String.format("        %-10s %6d samples   p10 %5.1f   p50 %5.1f   p90 %5.1f   max %5.1f%n",
                label, sorted.length, percentile(sorted, 0.10), percentile(sorted, 0.50),
                percentile(sorted, 0.90), sorted[sorted.length - 1]));
    }

    /** Two sources configured alike must not tessellate alike, which is what the registry-ID salt buys. */
    private static void identitySalt(StringBuilder report, HolderLookup.Provider registries) {
        HolderLookup.RegistryLookup<Biome> biomes = registries.lookupOrThrow(Registries.BIOME);
        Holder<Province> only = Holder.direct(new Province(biomes.getOrThrow(Biomes.DESERT), ElevationClass.MID, 0.0F, 0.0F, 0.0F));
        WeightedList<Holder<Province>> provinces = WeightedList.<Holder<Province>>builder().add(only, 1).build();

        VoronoiSource first = new VoronoiSource(RelictProvinceGenerator.CELL_SIZE, RelictProvinceGenerator.JITTER,
                RelictProvinceGenerator.BLEND_WIDTH, RelictProvinceGenerator.EPOCH_SPACING,
                RelictProvinceGenerator.EPOCH_RELIEF, provinces);
        VoronoiSource second = new VoronoiSource(RelictProvinceGenerator.CELL_SIZE, RelictProvinceGenerator.JITTER,
                RelictProvinceGenerator.BLEND_WIDTH, RelictProvinceGenerator.EPOCH_SPACING,
                RelictProvinceGenerator.EPOCH_RELIEF, provinces);
        first.bindSeed(Relict.id("salt_test_a"), SEED);
        second.bindSeed(Relict.id("salt_test_b"), SEED);

        double closest = Double.MAX_VALUE;
        for (int cz = -32; cz <= 32; cz++) {
            for (int cx = -32; cx <= 32; cx++) {
                closest = Math.min(closest, Math.hypot(first.centerX(cx, cz) - second.centerX(cx, cz),
                        first.centerZ(cx, cz) - second.centerZ(cx, cz)));
            }
        }

        report.append(String.format("%nregistry-ID salt, two identically configured sources over 4225 cells%n"));
        report.append(String.format("    closest their centers ever come  %.2f blocks  %s%n", closest, closest > 0.0 ? "PASS" : "FAIL"));
    }

    /** How the epoch field biases the pick — the evidence that the field conditions anything at all. */
    private static void epochBias(StringBuilder report, VoronoiSource source) {
        int cells = 4 * source.epochSpacing() / source.cellSize();
        Map<String, int[]> shares = new LinkedHashMap<>();
        double lowest = Double.MAX_VALUE;
        double highest = -Double.MAX_VALUE;

        for (int cz = -cells; cz <= cells; cz++) {
            for (int cx = -cells; cx <= cells; cx++) {
                double epoch = source.cellEpoch(cx, cz);
                lowest = Math.min(lowest, epoch);
                highest = Math.max(highest, epoch);
                int bucket = epoch < -0.25 ? 0 : epoch < 0.25 ? 1 : 2;
                shares.computeIfAbsent(source.provinceAt(cx, cz).value().elevationClass().getSerializedName(),
                        key -> new int[3])[bucket]++;
            }
        }

        report.append(String.format("%nepoch bias over %d cells, epoch %.3f..%.3f%n",
                (2 * cells + 1) * (2 * cells + 1), lowest, highest));
        report.append(String.format("    %-8s %10s %10s %10s%n", "class", "epoch<-.25", "-.25..+.25", "epoch>+.25"));
        shares.forEach((name, counts) -> report.append(String.format("    %-8s %10d %10d %10d%n",
                name, counts[0], counts[1], counts[2])));
    }

    private static double percentile(double[] sorted, double fraction) {
        return sorted[Math.min(sorted.length - 1, (int) (fraction * sorted.length))];
    }

    // ------------------------------------------------------------------------------- (g) elevation ladder

    /**
     * What the epoch-derived plateau bought. A province is no longer one height: it is a band, set by how far
     * the epoch ramp carries and offset by its own class. The two step distributions underneath are the point
     * of the change — a same-class border must be a seam and a cross-class border a scarp.
     */
    private static void elevationLadder(StringBuilder report, VoronoiSource source) {
        int cells = 4 * source.epochSpacing() / source.cellSize();
        Map<String, double[]> bands = new LinkedHashMap<>();
        List<Double> sameClass = new ArrayList<>();
        List<Double> crossClass = new ArrayList<>();

        for (int cz = -cells; cz <= cells; cz++) {
            for (int cx = -cells; cx <= cells; cx++) {
                Holder<Province> holder = source.provinceAt(cx, cz);
                Province province = holder.value();
                double elevation = source.cellElevation(province, cx, cz);

                double[] band = bands.computeIfAbsent(holder.unwrapKey().orElseThrow().identifier().getPath(),
                        key -> new double[]{Double.MAX_VALUE, -Double.MAX_VALUE, 0.0, 0.0});
                band[0] = Math.min(band[0], elevation);
                band[1] = Math.max(band[1], elevation);
                band[2] += elevation;
                band[3]++;

                for (int[] step : new int[][]{{1, 0}, {0, 1}}) {
                    Province other = source.provinceAt(cx + step[0], cz + step[1]).value();
                    double difference = RelictNoiseRouter.ELEVATION_SCALE * Math.abs(elevation
                            - source.cellElevation(other, cx + step[0], cz + step[1]));
                    (other.elevationClass() == province.elevationClass() ? sameClass : crossClass).add(difference);
                }
            }
        }

        report.append(String.format("%n(g) elevation ladder over %d cells, epoch_relief %.2f%n",
                (2 * cells + 1) * (2 * cells + 1), source.epochRelief()));
        report.append(String.format("    %-18s %6s %8s %8s %8s %8s%n",
                "province", "class", "offset", "min y", "mean y", "max y"));

        bands.forEach((name, band) -> {
            Province province = province(source, name);
            report.append(String.format("    %-18s %6s %8.2f %8.1f %8.1f %8.1f%n", name,
                    province.elevationClass().getSerializedName(), province.elevationOffset(),
                    SEA_LEVEL + RelictNoiseRouter.ELEVATION_SCALE * band[0],
                    SEA_LEVEL + RelictNoiseRouter.ELEVATION_SCALE * band[2] / band[3],
                    SEA_LEVEL + RelictNoiseRouter.ELEVATION_SCALE * band[1]));
        });

        report.append("    plateau step between adjacent cell centers, in blocks:\n");
        stepRow(report, "same class", sameClass);
        stepRow(report, "cross class", crossClass);
    }

    private static Province province(VoronoiSource source, String path) {
        return source.provinces().unwrap().stream()
                .map(Weighted::value)
                .filter(holder -> holder.unwrapKey().orElseThrow().identifier().getPath().equals(path))
                .findFirst().orElseThrow().value();
    }

    private static void stepRow(StringBuilder report, String label, List<Double> steps) {
        if (steps.isEmpty()) {
            report.append(String.format("        %-12s no samples%n", label));
            return;
        }

        double[] sorted = steps.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        report.append(String.format("        %-12s %6d pairs   p50 %5.1f   p90 %5.1f   max %5.1f%n",
                label, sorted.length, percentile(sorted, 0.50), percentile(sorted, 0.90),
                sorted[sorted.length - 1]));
    }

    // -------------------------------------------------------------------------------- (h) detached solids

    /**
     * The only check here that builds real blocks, because the bug it answers is not in the height field.
     * <p>
     * {@code terrain} is {@code 0.1 * (surfaceY + 0.5 - y)}, so the density is positive only below the
     * surface and nothing downstream of it can add rock above the surface. Any solid block found above
     * {@code surfaceY} therefore came from the block-state stage, not from the terrain, and counting them is
     * the whole test.
     * <p>
     * This used to run twice, once as datagen emits it and once with aquifers forced back on, so the count
     * separated the two. Aquifers are now permanently disabled in {@code RelictDimensionGenerator}, so
     * there is nothing left to toggle — this scans the actual registered density function once and expects
     * zero. It stays as a regression tripwire against the block-state stage reintroducing a barrier.
     */
    private static void detachedSolids(StringBuilder report, HolderLookup.Provider registries, VoronoiSource source,
                                       DensityFunction surfaceY) {
        NoiseGeneratorSettings settings = registries.lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(RelictDimension.MARS_NOISE_SETTINGS).value();
        LevelStem levelStem = registries.lookupOrThrow(Registries.LEVEL_STEM)
                .getOrThrow(RelictDimension.MARS_LEVELSTEM).value();

        if (!(levelStem.generator() instanceof NoiseBasedChunkGenerator generator)) {
            report.append("\n(h) detached solids, skipped: the Mars level stem generator is not noise-based\n");
            return;
        }

        RandomState state = RandomState.create(settings, registries.lookupOrThrow(Registries.NOISE), SEED);
        LevelHeightAccessor height = LevelHeightAccessor.create(settings.noiseSettings().minY(), settings.noiseSettings().height());

        List<int[]> columns = probeColumns(source);

        report.append(String.format("%n(h) detached solids, over %d columns around the lowest provinces (aquifers permanently off)%n",
                columns.size()));

        int affected = 0;
        int total = 0;
        int highest = Integer.MIN_VALUE;
        int worstCount = 0;
        int[] worst = null;

        for (int[] column : columns) {
            NoiseColumn blocks = generator.getBaseColumn(column[0], column[1], height, state);
            int floor = Mth.ceil(sample(surfaceY, column[0], column[1])) + 1;
            int found = 0;

            for (int y = floor; y <= height.getMaxY(); y++) {
                if (!blocks.getBlock(y).isAir()) {
                    found++;
                    highest = Math.max(highest, y);
                }
            }

            if (found > 0) {
                affected++;
                total += found;
                if (found > worstCount) {
                    worstCount = found;
                    worst = column;
                }
            }
        }

        report.append(String.format("    %-16s %10s %14s   %s%n", "columns", "blocks", "highest y", "result"));
        report.append(String.format("    %-16d %10d %14s   %s%n", affected, total,
                highest == Integer.MIN_VALUE ? "none" : String.valueOf(highest), affected == 0 ? "PASS" : "FAIL"));

        if (worst != null) {
            probeProfile(report, generator, state, worst, surfaceY, height);
        }
    }

    /** One column printed in full, so the density and the block it became sit side by side. */
    private static void probeProfile(StringBuilder report, NoiseBasedChunkGenerator generator, RandomState state,
                                     int[] column, DensityFunction surfaceY, LevelHeightAccessor height) {
        NoiseColumn blocks = generator.getBaseColumn(column[0], column[1], height, state);
        double surface = sample(surfaceY, column[0], column[1]);

        int top = Mth.ceil(surface) + PROBE_PROFILE_HEIGHT;
        report.append(String.format("%n    column x=%d z=%d, surface y %.1f%n", column[0], column[1], surface));
        report.append(String.format("    %5s %12s %12s%n", "y", "density", "block"));

        for (int y = top; y >= Mth.floor(surface) - 2; y--) {
            double density = generator.getInterpolatedNoiseValue(state, new DensityFunction.SinglePointContext(column[0], y, column[1]));
            report.append(String.format("    %5d %12.5f %12s%n", y, density, blocks.getBlock(y).isAir() ? "air" : "solid"));
        }
    }

    /**
     * A patch of columns over each of the lowest cells anywhere in the epoch scan. The lowest cells are the
     * whole point: a barrier only ever reaches over terrain where the surface sits far enough under sea
     * level, so a probe over average ground would report a clean world and prove nothing.
     */
    private static List<int[]> probeColumns(VoronoiSource source) {
        int cells = 4 * source.epochSpacing() / source.cellSize();
        List<int[]> lowest = new ArrayList<>();

        for (int cz = -cells; cz <= cells; cz++) {
            for (int cx = -cells; cx <= cells; cx++) {
                lowest.add(new int[]{cx, cz});
            }
        }

        lowest.sort(Comparator.comparingDouble(cell -> cellElevation(source, cell[0], cell[1])));

        List<int[]> columns = new ArrayList<>();
        for (int[] cell : lowest.subList(0, Math.min(PROBE_CELLS, lowest.size()))) {
            int centerX = (int) source.centerX(cell[0], cell[1]);
            int centerZ = (int) source.centerZ(cell[0], cell[1]);

            for (int iz = -PROBE_PATCH; iz <= PROBE_PATCH; iz++) {
                for (int ix = -PROBE_PATCH; ix <= PROBE_PATCH; ix++) {
                    columns.add(new int[]{centerX + ix * PROBE_PATCH_STEP, centerZ + iz * PROBE_PATCH_STEP});
                }
            }
        }

        return columns;
    }

    // ------------------------------------------------------------------------------------------ (f) maps

    private static void maps(StringBuilder report, Path directory, VoronoiSource source, DensityFunction surfaceY) {
        report.append(String.format("%n(f) maps%n"));

        if (directory == null) {
            report.append("    relict.terrainReportDir unset, PGMs skipped\n");
            return;
        }

        double[][] heights = new double[MAP_GRID][MAP_GRID];
        for (int iz = 0; iz < MAP_GRID; iz++) {
            for (int ix = 0; ix < MAP_GRID; ix++) {
                heights[iz][ix] = sample(surfaceY, ix * MAP_STEP - MAP_GRID * MAP_STEP / 2,
                        iz * MAP_STEP - MAP_GRID * MAP_STEP / 2);
            }
        }

        int[][] epoch = new int[EPOCH_MAP_GRID][EPOCH_MAP_GRID];
        for (int iz = 0; iz < EPOCH_MAP_GRID; iz++) {
            for (int ix = 0; ix < EPOCH_MAP_GRID; ix++) {
                VoronoiSource.Cell cell = source.nearest(ix * EPOCH_MAP_STEP - EPOCH_MAP_GRID * EPOCH_MAP_STEP / 2,
                        iz * EPOCH_MAP_STEP - EPOCH_MAP_GRID * EPOCH_MAP_STEP / 2);
                epoch[iz][ix] = (int) Math.round(127.5 * (1.0 + source.cellEpoch(cell.cellX(), cell.cellZ())));
            }
        }

        // Hillshade for the ridges, which are only ever legible by their shadows, and a flat elevation ramp for
        // the province plateaus, which the hillshade's own contrast stretch flattens out.
        int[][] elevation = new int[MAP_GRID][MAP_GRID];
        for (int iz = 0; iz < MAP_GRID; iz++) {
            for (int ix = 0; ix < MAP_GRID; ix++) {
                elevation[iz][ix] = (int) Math.round(255.0 * (heights[iz][ix] - (SEA_LEVEL - RelictNoiseRouter.ELEVATION_SCALE))
                        / (2.0 * RelictNoiseRouter.ELEVATION_SCALE));
            }
        }

        try {
            Files.createDirectories(directory);
            writeHillshade(directory.resolve("voronoi_hillshade.pgm"), heights);
            writeGray(directory.resolve("voronoi_elevation.pgm"), elevation);
            writeGray(directory.resolve("voronoi_epoch.pgm"), epoch);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        report.append(String.format("    %s  (%d blocks across, %d per pixel)%n",
                directory.resolve("voronoi_hillshade.pgm").toAbsolutePath(), MAP_GRID * MAP_STEP, MAP_STEP));
        report.append(String.format("    %s  (same window, black at y %.0f, white at y %.0f)%n",
                directory.resolve("voronoi_elevation.pgm").toAbsolutePath(),
                SEA_LEVEL - RelictNoiseRouter.ELEVATION_SCALE, SEA_LEVEL + RelictNoiseRouter.ELEVATION_SCALE));
        report.append(String.format("    %s  (%d blocks across, %d per pixel, cell-constant)%n",
                directory.resolve("voronoi_epoch.pgm").toAbsolutePath(), EPOCH_MAP_GRID * EPOCH_MAP_STEP, EPOCH_MAP_STEP));
    }

    /** Lambert relief from a fixed low sun, as a binary PGM — no image library, and it opens in anything. */
    private static void writeHillshade(Path path, double[][] heights) throws IOException {
        int[][] gray = new int[MAP_GRID][MAP_GRID];
        for (int z = 0; z < MAP_GRID; z++) {
            for (int x = 0; x < MAP_GRID; x++) {
                double dx = heights[z][Math.min(x + 1, MAP_GRID - 1)] - heights[z][Math.max(x - 1, 0)];
                double dz = heights[Math.min(z + 1, MAP_GRID - 1)][x] - heights[Math.max(z - 1, 0)][x];
                double nx = -dx / (2.0 * MAP_STEP);
                double nz = -dz / (2.0 * MAP_STEP);
                double length = Math.sqrt(nx * nx + 1.0 + nz * nz);
                double lambert = (nx * -0.612 + 0.5 + nz * -0.612) / length;
                gray[z][x] = (int) Math.round(255.0 * Math.clamp(lambert * 1.9, 0.0, 1.0));
            }
        }

        writeGray(path, gray);
    }

    private static void writeGray(Path path, int[][] gray) throws IOException {
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(path))) {
            out.write(("P5\n" + gray[0].length + " " + gray.length + "\n255\n").getBytes(StandardCharsets.US_ASCII));
            for (int[] row : gray) {
                for (int value : row) {
                    out.write(Math.clamp(value, 0, 255));
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------- underground report

    /**
     * Headless proof that the underground biome pick is epoch-biased, plus a teleport listing (cell
     * centers, this seed) for eyeballing each province in the world.
     */
    private static StringBuilder undergroundReport(HolderLookup.Provider registries) {
        HolderLookup.RegistryLookup<VoronoiSource> sources = registries.lookupOrThrow(RelictCustomRegistries.VORONOI_SOURCE_REGISTRY);
        Holder<VoronoiSource> holder = sources.getOrThrow(RelictVoronoiSources.MARS_UNDERGROUND);
        VoronoiSource source = holder.value();
        source.bindSeed(holder.unwrapKey().orElseThrow().identifier(), UNDERGROUND_SEED);

        // bucket: 0 = young epoch (<-0.25), 1 = mid, 2 = old (>0.25)
        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, int[]> nearestCell = new LinkedHashMap<>();
        Map<String, Integer> nearestDistance = new LinkedHashMap<>();

        for (int cellX = -UNDERGROUND_CELL_RADIUS; cellX <= UNDERGROUND_CELL_RADIUS; cellX++) {
            for (int cellZ = -UNDERGROUND_CELL_RADIUS; cellZ <= UNDERGROUND_CELL_RADIUS; cellZ++) {
                double epoch = source.cellEpoch(cellX, cellZ);
                int bucket = epoch < -0.25 ? 0 : epoch > 0.25 ? 2 : 1;
                Holder<Province> province = source.provinceAt(cellX, cellZ);
                String id = province.unwrapKey().map(key -> key.identifier().toString()).orElse("(inline)");
                counts.computeIfAbsent(id, key -> new int[3])[bucket]++;

                // Teleport spots stay near the world origin (Manhattan cell distance) so they are actually convenient.
                int distance = Math.abs(cellX) + Math.abs(cellZ);
                if (distance < nearestDistance.getOrDefault(id, Integer.MAX_VALUE)) {
                    nearestDistance.put(id, distance);
                    nearestCell.put(id, new int[] {cellX, cellZ});
                }
            }
        }

        StringBuilder report = new StringBuilder();
        report.append("=== underground voronoi field: epoch bias (0.8 T1) ===\n\n");
        report.append("seed ").append(UNDERGROUND_SEED)
                .append("   cell_size ").append(source.cellSize())
                .append("   epoch_spacing ").append(source.epochSpacing())
                .append("   grid [").append(-UNDERGROUND_CELL_RADIUS).append(", ").append(UNDERGROUND_CELL_RADIUS).append("]\n\n");
        report.append(String.format("%-28s %12s %12s %12s%n", "province", "young(<-.25)", "mid", "old(>.25)"));
        for (Map.Entry<String, int[]> entry : counts.entrySet()) {
            int[] c = entry.getValue();
            report.append(String.format("%-28s %12d %12d %12d%n", entry.getKey(), c[0], c[1], c[2]));
        }

        report.append("\nteleport spots (cell centers, this seed, y=").append(UNDERGROUND_TELEPORT_Y).append("):\n");
        for (Map.Entry<String, int[]> entry : nearestCell.entrySet()) {
            int[] cell = entry.getValue();
            double worldX = source.centerX(cell[0], cell[1]);
            double worldZ = source.centerZ(cell[0], cell[1]);
            report.append(String.format("  %-28s cell [%d, %d]  /execute in relict:mars run tp @s %.1f %d %.1f%n",
                    entry.getKey(), cell[0], cell[1], worldX, UNDERGROUND_TELEPORT_Y, worldZ));
        }

        return report;
    }

    // ---------------------------------------------------------------------------------------- plumbing

    private static DensityFunction holder(HolderLookup.RegistryLookup<DensityFunction> functions, ResourceKey<DensityFunction> key) {
        return new DensityFunctions.HolderHolder(functions.getOrThrow(key));
    }

    private static double sample(DensityFunction function, int x, int z) {
        return function.compute(new DensityFunction.SinglePointContext(x, 0, z));
    }

    /** Instantiates every noise in the graph against {@link #SEED}, the way {@code RandomState} does. */
    private static DensityFunction seed(DensityFunction graph, PositionalRandomFactory random) {
        return graph.mapAll(new DensityFunction.Visitor() {
            @Override
            public DensityFunction apply(DensityFunction input) {
                return input;
            }

            @Override
            public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder noise) {
                NormalNoise.NoiseParameters parameters = noise.noiseData().value();
                RandomSource source = random.fromHashOf(noise.noiseData().unwrapKey().orElseThrow().identifier());
                return new DensityFunction.NoiseHolder(noise.noiseData(), NormalNoise.create(source, parameters));
            }
        });
    }

}
