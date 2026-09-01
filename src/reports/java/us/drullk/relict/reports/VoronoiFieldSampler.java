package us.drullk.relict.reports;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import us.drullk.relict.Relict;
import us.drullk.relict.datagen.worldgen.RelictDensityFunctionGenerator;
import us.drullk.relict.datagen.worldgen.RelictProvinceGenerator;
import us.drullk.relict.datagen.worldgen.RelictSurfaceRules;
import us.drullk.relict.init.custom.RelictCustomRegistries;
import us.drullk.relict.init.custom.RelictVoronoiSources;
import us.drullk.relict.init.worldgen.RelictDimension;
import us.drullk.relict.init.worldgen.RelictNoises;
import us.drullk.relict.worldgen.CraterFieldFunction;
import us.drullk.relict.worldgen.DuneCrest;
import us.drullk.relict.worldgen.DuneWaveFunction;
import us.drullk.relict.worldgen.LatticeHash;
import us.drullk.relict.worldgen.ElevationClass;
import us.drullk.relict.worldgen.NoiseSpread;
import us.drullk.relict.worldgen.Province;
import us.drullk.relict.datagen.worldgen.RelictNoiseRouter;
import us.drullk.relict.worldgen.VariantSelector;
import us.drullk.relict.worldgen.VoronoiSource;
import us.drullk.relict.datagen.worldgen.densityfields.RelictRidgeField;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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
 * reports and PGM images under {@code relict.terrainReportDir} (set by {@code reportsData}; see
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

    private static final int CAVE_CENSUS_GRID = 24;
    private static final int CAVE_CENSUS_STEP = 16;

    private static final int CAVE_BAND_DEPTH = 134;

    private static final int FULL_COLUMN_SPAN = 384;

    /** (i) surface-biome-at-surface / underground-biome-below-cut scan window. */
    private static final int INVARIANT_SCAN_RADIUS = 2048;
    private static final int INVARIANT_SCAN_STEP = 37;

    /** (i) how far below the cut a column is probed to prove it resolves to the underground field. */
    private static final int UNDERGROUND_PROBE_DEPTH = 60;

    /** (l) RELIEF composition-invariant scan window. */
    private static final int COMPOSITION_CHECK_RADIUS = 1024;
    private static final int COMPOSITION_CHECK_STEP = 97;

    /** (m) F3-readout-agreement sample count, drawn from the same vertex list as (a) and (k). */
    private static final int DEBUG_READOUT_SAMPLES = 6;

    /** (n) the grid the prototype's spread was measured on, so the two numbers mean the same thing. */
    private static final double SPREAD_STEP = 0.0917;
    private static final int SPREAD_SAMPLES = 400;

    /**
     * (o)/(p) morphology windows. Far enough apart that the variant selector lands in different places, so the
     * bars are checked against a blended field rather than one point of the variant axis.
     */
    private static final int[][] MORPHOLOGY_WINDOWS = {{0, 0}, {40000, -25000}, {-31000, 47000}};

    /** (o) the prototype's dune plate: 2400 blocks at 2 blocks per sample. */
    private static final int DUNE_WINDOW = 2400;
    private static final int DUNE_WINDOW_STEP = 2;

    /** Slopes gentler than this are the isotropic plain, not a dune face; 0.06 is about 3.4 degrees. */
    private static final double DUNE_SLOPE_BAND = 0.06;

    private static final int CREST_TRANSECT_STRIDE = 40;
    private static final double CREST_PROMINENCE = 2.0;

    /** (p) the prototype's mesa slope plate: 1200 blocks at 1 block per sample. */
    private static final int MESA_WINDOW = 1200;
    private static final int MESA_WINDOW_STEP = 1;

    /**
     * The M2 cliff-coverage floor is the checklist's 1.5% relaxed to 1.0%: 0.14c recorded 1.4% for the butte
     * end state and judged it a pass with note, because isolated buttes carry little cliff perimeter per area.
     * The blended field spends real time near that end.
     */
    private static final double MESA_CLIFF_FLOOR = 0.010;

    private static final int MODE_BINS = 140;
    private static final double MODE_MIN_SEPARATION = 10.0;
    private static final double MODE_BAND = 4.0;

    /** (q) relief patch, well inside one cell so the blend law is not diluting the numbers. */
    private static final int RELIEF_PATCH = 384;
    private static final int RELIEF_PATCH_STEP = 2;

    /** dune crest palette-map plate colours, schematic rather than texture-accurate. */
    private static final double[] BASALT_RGB = {58.0, 58.0, 64.0};
    private static final double[] RED_SAND_RGB = {196.0, 94.0, 48.0};
    private static final double[] GRAVEL_RGB = {132.0, 132.0, 132.0};

    /** wrinkle_plains gravel-patch sanity plate window. */
    private static final int GRAVEL_WINDOW = 1000;
    private static final int GRAVEL_WINDOW_STEP = 2;

    /** (s) contiguous census window, so the per-layer counts are areal densities and not just tallies. */
    private static final int CRATER_CENSUS_SPAN = 16384;

    /** (s) half-octave diameter thresholds for the cumulative size-frequency fit, in blocks. */
    private static final double[] CRATER_SIZE_THRESHOLDS = {16.0, 22.6, 32.0, 45.3, 64.0, 90.5, 128.0, 181.0};

    private static final double CRATER_SLOPE_LOWEST = -2.4;
    private static final double CRATER_SLOPE_HIGHEST = -1.6;

    /**
     * (t) cells drawn per layer, scattered across {@link #CRATER_EPOCH_SPAN}. Large because the check is a
     * strict monotonicity over eleven bins and the youngest two bins are the flattest part of a convex curve:
     * at this count the smallest legitimate step is several times the counting noise.
     */
    private static final int CRATER_EPOCH_CELLS = 400000;
    private static final int CRATER_EPOCH_SPAN = 200000;
    private static final int CRATER_EPOCH_BINS = 11;
    private static final int CRATER_EPOCH_MIN_CELLS = 500;

    private static final double BLOCKS_PER_SQUARE_KILOMETRE = 1.0e6;

    /** (u) the three size classes the depth law was calibrated at. */
    private static final double[] CRATER_PROFILE_DIAMETERS = {24.0, 96.0, 224.0};
    private static final double CRATER_PROFILE_RADIUS_STEP = 0.002;
    private static final double CRATER_PROFILE_FRESH = 0.05;
    private static final double CRATER_PROFILE_TOLERANCE = 0.05;
    private static final int CRATER_PROFILE_SEARCH_CELLS = 400000;
    private static final int CRATER_PROFILE_RING_SAMPLES = 64;

    /** (v) overlap window. Stride is coprime with every cell edge, so no layer is sampled in phase. */
    private static final int CRATER_OVERLAP_SPAN = 2048;
    private static final int CRATER_OVERLAP_STEP = 13;

    /** (j) how many of the deepest fresh bowls are swept, and the ring the transects run out to. */
    private static final int CRATER_FLOOR_CASES = 6;
    private static final int CRATER_FLOOR_SEARCH_CELLS = 200000;

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
            List<String> failures = new ArrayList<>();

            StringBuilder surfaceReport = surfaceReport(registries, failures);
            writeReport(directory, "voronoi_report.txt", surfaceReport.toString());
            System.out.print(surfaceReport);

            StringBuilder undergroundReport = undergroundReport(registries);
            writeReport(directory, "voronoi_underground_report.txt", undergroundReport.toString());
            System.out.print(undergroundReport);

            // The morphology bars are the 0.14c checklists, and a port that drifts off them is a landform that
            // stopped being the landform it was designed as. Nothing about that shows in the emitted JSON, so
            // the build is the only place it can be caught.
            if (!failures.isEmpty()) {
                throw new IllegalStateException("Surface morphology checks failed:\n  " + String.join("\n  ", failures));
            }
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

    private static StringBuilder surfaceReport(HolderLookup.Provider registries, List<String> failures) {
        PositionalRandomFactory random = new XoroshiroRandomSource(SEED).forkPositional();

        Holder<VoronoiSource> marsHolder = registries.lookupOrThrow(RelictCustomRegistries.VORONOI_SOURCE_REGISTRY)
                .getOrThrow(RelictVoronoiSources.MARS);
        VoronoiSource mars = marsHolder.value();
        mars.bindSeed(RelictVoronoiSources.MARS.identifier(), SEED);
        CraterFieldFunction craters = new CraterFieldFunction(marsHolder, CraterFieldFunction.Mode.DELTA);

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
        surfaceBiomeInvariant(report, registries, mars, surfaceY);
        craterFloorInvariant(report, registries, mars, craters, surfaceY);
        marginVsWorstDrop(report, surfaceY, vertices);
        compositionInvariant(report, registries, surfaceY, random);
        debugReadoutAgreement(report, registries, vertices);
        noiseSpread(report, registries, random, failures);
        duneMorphology(report, registries, random, failures);
        mesaMorphology(report, registries, random, failures);
        provinceRelief(report, registries, mars, random);
        dunePalette(report, registries, reportDirectory(), random);
        wrinklePlainsGravelPalette(report, registries, reportDirectory(), mars, surfaceY, random);
        craterCensus(report, mars, failures);
        craterEpochDensity(report, mars, failures);
        craterProfile(report, registries, mars, craters, random, failures);
        craterOverlapBound(report, mars, craters, failures);
        surfaceTeleports(report, mars, surfaceY);
        craterTeleports(report, mars, craters, surfaceY);

        return report;
    }

    // -------------------------------------------------------------------------- morphology check plumbing

    private static void bar(StringBuilder report, List<String> failures, String label, double value,
                            double lowest, double highest, String units) {
        boolean passed = value >= lowest && value <= highest;
        report.append(String.format("    %-34s %9.3f %-8s [%.3f .. %.3f]  %s%n",
                label, value, units, lowest, highest, passed ? "PASS" : "FAIL"));

        if (!passed) {
            failures.add(String.format("%s = %.3f %s, outside [%.3f, %.3f]", label, value, units, lowest, highest));
        }
    }

    /**
     * The one measurement the ported primitives' amplitudes depend on, re-taken every run rather than trusted.
     * The multi-octave figure is not derivable from the single-octave one: {@code NormalNoise} renormalizes
     * the whole octave stack, so a three-octave field is nowhere near the single-octave value times the
     * amplitude sum, and reading it backwards silently produces crest wander of the wrong size.
     */
    private static void noiseSpread(StringBuilder report, HolderLookup.Provider registries,
                                    PositionalRandomFactory random, List<String> failures) {
        report.append(String.format("%n(n) noise spread, against the constants NoiseSpread records%n"));

        HolderLookup.RegistryLookup<NormalNoise.NoiseParameters> parameters = registries.lookupOrThrow(Registries.NOISE);
        double single = spreadOf(parameters, random, RelictNoises.DUNE_CRENULATION);
        double triple = spreadOf(parameters, random, RelictNoises.DUNE_WARP);

        report.append(String.format("    single-octave measured %.4f, NoiseSpread.MINECRAFT      %.4f%n", single, NoiseSpread.MINECRAFT));
        report.append(String.format("    three-octave measured  %.4f, NoiseSpread.MINECRAFT_FBM_3 %.4f%n", triple, NoiseSpread.MINECRAFT_FBM_3));
        bar(report, failures, "single-octave sd / recorded", single / NoiseSpread.MINECRAFT, 0.95, 1.05, "");
        bar(report, failures, "three-octave sd / recorded", triple / NoiseSpread.MINECRAFT_FBM_3, 0.95, 1.05, "");
    }

    /** Off-lattice, on the grid the prototype's own spread was measured on, since Perlin noise is zero on it. */
    private static double spreadOf(HolderLookup.RegistryLookup<NormalNoise.NoiseParameters> parameters,
                                   PositionalRandomFactory random, ResourceKey<NormalNoise.NoiseParameters> key) {
        NormalNoise noise = NormalNoise.create(random.fromHashOf(key.identifier()), parameters.getOrThrow(key).value());

        double total = 0.0;
        double totalSquares = 0.0;
        for (int i = 0; i < SPREAD_SAMPLES; i++) {
            for (int j = 0; j < SPREAD_SAMPLES; j++) {
                double value = noise.getValue(i * SPREAD_STEP + 0.031, 0.0, j * SPREAD_STEP + 0.017);
                total += value;
                totalSquares += value * value;
            }
        }

        double count = (double) SPREAD_SAMPLES * SPREAD_SAMPLES;
        return Math.sqrt(totalSquares / count - (total / count) * (total / count));
    }

    /**
     * Both morphology checks measure the primitive on its own — its own province amplitude over the shared
     * plain, with no voronoi gating — because that is what the 0.14c plates measured and the bars are quoted
     * against. What a province actually builds once the blend law dilutes it is (p) below.
     */
    private static double[][] shapeGrid(DensityFunction shape, double amplitude, DensityFunction plain,
                                        double plainRoughness, int originX, int originZ, int span, int step) {
        int side = span / step;
        double[][] grid = new double[side][side];

        for (int iz = 0; iz < side; iz++) {
            for (int ix = 0; ix < side; ix++) {
                int x = originX + ix * step;
                int z = originZ + iz * step;
                grid[iz][ix] = SEA_LEVEL + amplitude * sample(shape, x, z) + plainRoughness * sample(plain, x, z);
            }
        }

        return grid;
    }

    private static double[] sortedHeights(double[][] grid) {
        double[] all = new double[grid.length * grid.length];
        int at = 0;
        for (double[] row : grid) {
            for (double value : row) {
                all[at++] = value;
            }
        }

        Arrays.sort(all);
        return all;
    }

    // ------------------------------------------------------------------------- (o) rusted dunes morphology

    /**
     * The 0.14c dune checklist D1-D5, as measured by {@code prototypes/tools/metrics_14c.py}, restated over
     * the ported field. D6-D10 are visual checks and stay a manual in-game pass, not measured here.
     */
    private static void duneMorphology(StringBuilder report, HolderLookup.Provider registries,
                                       PositionalRandomFactory random, List<String> failures) {
        report.append(String.format("%n(o) rusted dunes morphology, 0.14c checklist D1-D5%n"));

        HolderLookup.RegistryLookup<DensityFunction> functions = registries.lookupOrThrow(Registries.DENSITY_FUNCTION);
        DensityFunction shape = seed(holder(functions, RelictDensityFunctionGenerator.DUNE_SHAPE), random);
        DensityFunction plain = seed(RelictRidgeField.plain(registries.lookupOrThrow(Registries.NOISE)::getOrThrow), random);

        // Named so the one field that decides how far apart two variants sit is visible in the report.
        report.append(String.format("    variant selector spacing %.0f blocks, shared by both surface grammars%n",
                VariantSelector.SPACING));

        for (int[] origin : MORPHOLOGY_WINDOWS) {
            double[][] grid = shapeGrid(shape, RelictProvinceGenerator.DUNE_AMPLITUDE, plain,
                    RelictProvinceGenerator.DUNE_PLAIN_ROUGHNESS, origin[0], origin[1], DUNE_WINDOW, DUNE_WINDOW_STEP);

            report.append(String.format("%n    window %d blocks at x=%d z=%d, %d blocks per sample%n",
                    DUNE_WINDOW, origin[0], origin[1], DUNE_WINDOW_STEP));

            double windX = Math.cos(DuneWaveFunction.WIND_AZIMUTH);
            double windZ = Math.sin(DuneWaveFunction.WIND_AZIMUTH);
            List<Double> ascending = new ArrayList<>();
            List<Double> descending = new ArrayList<>();

            for (int iz = 1; iz + 1 < grid.length; iz++) {
                for (int ix = 1; ix + 1 < grid.length; ix++) {
                    double alongX = (grid[iz][ix + 1] - grid[iz][ix - 1]) / (2.0 * DUNE_WINDOW_STEP);
                    double alongZ = (grid[iz + 1][ix] - grid[iz - 1][ix]) / (2.0 * DUNE_WINDOW_STEP);
                    double alongWind = alongX * windX + alongZ * windZ;

                    if (alongWind > DUNE_SLOPE_BAND) {
                        ascending.add(alongWind);
                    } else if (alongWind < -DUNE_SLOPE_BAND) {
                        descending.add(-alongWind);
                    }
                }
            }

            double[] up = ascending.stream().mapToDouble(Double::doubleValue).sorted().toArray();
            double[] down = descending.stream().mapToDouble(Double::doubleValue).sorted().toArray();
            double[] heights = sortedHeights(grid);

            bar(report, failures, "D1 ascending fraction", (double) up.length / (up.length + down.length), 0.65, 0.88, "");
            bar(report, failures, "D2 stoss mean slope", Math.toDegrees(Math.atan(Arrays.stream(up).average().orElse(0.0))), 8.0, 15.0, "deg");
            bar(report, failures, "D3 slip p95 slope", Math.toDegrees(Math.atan(percentile(down, 0.95))), 28.0, 38.0, "deg");
            bar(report, failures, "D4 median crest spacing", crestSpacing(grid, windX, windZ), 100.0, 300.0, "blocks");
            bar(report, failures, "D5 relief p2..p98", percentile(heights, 0.98) - percentile(heights, 0.02), 0.0, 28.0, "blocks");
            report.append(String.format("    %-34s %9.3f%n", "slip max slope (informational)",
                    Math.toDegrees(Math.atan(down.length == 0 ? 0.0 : down[down.length - 1]))));
        }
    }

    /**
     * Peaks along wind-direction transects, prominence over a 7-sample window, exactly as the prototype's
     * metric script counted them.
     */
    private static double crestSpacing(double[][] grid, double windX, double windZ) {
        int side = grid.length;
        List<Double> spacings = new ArrayList<>();

        for (int start = 0; start < side; start += CREST_TRANSECT_STRIDE) {
            List<Double> line = new ArrayList<>();
            for (int i = 0; i < side; i++) {
                int ix = (int) (i * windX);
                int iz = start + (int) (i * windZ);
                if (ix < 0 || ix >= side || iz < 0 || iz >= side) {
                    break;
                }

                line.add(grid[iz][ix]);
            }

            if (line.size() < 50) {
                continue;
            }

            int previous = Integer.MIN_VALUE;
            for (int i = 3; i + 3 < line.size(); i++) {
                double highest = -Double.MAX_VALUE;
                double lowest = Double.MAX_VALUE;
                for (int d = -3; d <= 3; d++) {
                    highest = Math.max(highest, line.get(i + d));
                    lowest = Math.min(lowest, line.get(i + d));
                }

                if (line.get(i) < highest || line.get(i) < lowest + CREST_PROMINENCE) {
                    continue;
                }

                if (previous != Integer.MIN_VALUE && i - previous <= 8) {
                    continue;
                }

                if (previous != Integer.MIN_VALUE) {
                    spacings.add((i - previous) * (double) DUNE_WINDOW_STEP);
                }

                previous = i;
            }
        }

        if (spacings.isEmpty()) {
            return Double.NaN;
        }

        return percentile(spacings.stream().mapToDouble(Double::doubleValue).sorted().toArray(), 0.50);
    }

    // ------------------------------------------------------------------------ (p) fretted mesas morphology

    /** The 0.14c mesa checklist M1, M2, M4, M5, M7. M3/M6/M8/M9 are visual and stay the in-game pass. */
    private static void mesaMorphology(StringBuilder report, HolderLookup.Provider registries,
                                       PositionalRandomFactory random, List<String> failures) {
        report.append(String.format("%n(p) fretted mesas morphology, 0.14c checklist M1/M2/M4/M5/M7%n"));

        HolderLookup.RegistryLookup<DensityFunction> functions = registries.lookupOrThrow(Registries.DENSITY_FUNCTION);
        DensityFunction shape = seed(holder(functions, RelictDensityFunctionGenerator.MESA_SHAPE), random);
        DensityFunction plain = seed(RelictRidgeField.plain(registries.lookupOrThrow(Registries.NOISE)::getOrThrow), random);

        for (int[] origin : MORPHOLOGY_WINDOWS) {
            double[][] grid = shapeGrid(shape, RelictProvinceGenerator.MESA_AMPLITUDE, plain,
                    RelictProvinceGenerator.MESA_PLAIN_ROUGHNESS, origin[0], origin[1], MESA_WINDOW, MESA_WINDOW_STEP);

            report.append(String.format("%n    window %d blocks at x=%d z=%d, %d blocks per sample%n",
                    MESA_WINDOW, origin[0], origin[1], MESA_WINDOW_STEP));

            int side = grid.length;
            int counted = 0;
            int flat = 0;
            int trough = 0;
            int talus = 0;
            int cliff = 0;
            double steepest = 0.0;
            double[][] angles = new double[side][side];

            for (int iz = 1; iz + 1 < side; iz++) {
                for (int ix = 1; ix + 1 < side; ix++) {
                    double alongX = (grid[iz][ix + 1] - grid[iz][ix - 1]) / (2.0 * MESA_WINDOW_STEP);
                    double alongZ = (grid[iz + 1][ix] - grid[iz - 1][ix]) / (2.0 * MESA_WINDOW_STEP);
                    double angle = Math.toDegrees(Math.atan(Math.hypot(alongX, alongZ)));
                    angles[iz][ix] = angle;

                    counted++;
                    steepest = Math.max(steepest, angle);
                    if (angle < 8.0) {
                        flat++;
                    } else if (angle < 12.0) {
                        trough++;
                    }

                    if (angle >= 12.0 && angle < 35.0) {
                        talus++;
                    }

                    if (angle > 45.0) {
                        cliff++;
                    }
                }
            }

            double[] heights = sortedHeights(grid);
            double[] modes = modes(heights);
            double floorMode = modes[0];
            double capMode = modes[1];

            bar(report, failures, "M1 mode separation", capMode - floorMode, 20.0, 35.0, "blocks");
            bar(report, failures, "M2 flat below 8 deg", (double) flat / counted, 0.55, 1.0, "");
            bar(report, failures, "M2 cliff above 45 deg", (double) cliff / counted, MESA_CLIFF_FLOOR, 0.12, "");
            bar(report, failures, "M2 talus 12..35 deg", (double) talus / counted, 0.005, 1.0, "");
            bar(report, failures, "M4 max slope", steepest, 60.0, 90.0, "deg");
            bar(report, failures, "M5 cap roughness sd", spreadNear(heights, capMode), 0.0, 2.0, "blocks");
            bar(report, failures, "M5 floor roughness sd", spreadNear(heights, floorMode), 0.0, 3.0, "blocks");

            report.append(String.format("    %-34s %9.3f%n", "M2 trough 8..12 deg (informational)", (double) trough / counted));
            report.append(String.format("    %-34s %9.3f%n", "M7 cap-area fraction (informational)",
                    plateauArea(grid, angles, capMode)));
        }
    }

    /** The two dominant height modes: the fullest histogram bin, and the fullest one well away from it. */
    private static double[] modes(double[] sortedHeights) {
        double lowest = sortedHeights[0];
        double highest = sortedHeights[sortedHeights.length - 1];
        double width = (highest - lowest) / MODE_BINS;
        int[] counts = new int[MODE_BINS];

        for (double value : sortedHeights) {
            counts[Math.min(MODE_BINS - 1, (int) ((value - lowest) / width))]++;
        }

        int first = 0;
        for (int bin = 1; bin < MODE_BINS; bin++) {
            if (counts[bin] > counts[first]) {
                first = bin;
            }
        }

        double firstCentre = lowest + (first + 0.5) * width;
        int second = -1;
        for (int bin = 0; bin < MODE_BINS; bin++) {
            double centre = lowest + (bin + 0.5) * width;
            if (Math.abs(centre - firstCentre) > MODE_MIN_SEPARATION && (second < 0 || counts[bin] > counts[second])) {
                second = bin;
            }
        }

        double secondCentre = second < 0 ? firstCentre : lowest + (second + 0.5) * width;
        return new double[]{Math.min(firstCentre, secondCentre), Math.max(firstCentre, secondCentre)};
    }

    private static double spreadNear(double[] sortedHeights, double mode) {
        double total = 0.0;
        double totalSquares = 0.0;
        int counted = 0;

        for (double value : sortedHeights) {
            if (Math.abs(value - mode) < MODE_BAND) {
                total += value;
                totalSquares += value * value;
                counted++;
            }
        }

        if (counted == 0) {
            return Double.NaN;
        }

        return Math.sqrt(Math.max(0.0, totalSquares / counted - (total / counted) * (total / counted)));
    }

    private static double plateauArea(double[][] grid, double[][] angles, double capMode) {
        int counted = 0;
        int onCap = 0;

        for (int iz = 1; iz + 1 < grid.length; iz++) {
            for (int ix = 1; ix + 1 < grid.length; ix++) {
                counted++;
                if (Math.abs(grid[iz][ix] - capMode) < 3.0 && angles[iz][ix] < 8.0) {
                    onCap++;
                }
            }
        }

        return (double) onCap / counted;
    }

    // ------------------------------------------------------------------------- (q) per-province relief

    /**
     * What each surface province actually builds, before and after this round's landform channels: the same
     * columns sampled with the whole relief graph and then with only the dimension-wide plain, which is what
     * every province except wrinkle_plains carried before. The window is a patch deep inside the cell each
     * province sits furthest into, so the blend law is not diluting the numbers.
     */
    private static void provinceRelief(StringBuilder report, HolderLookup.Provider registries, VoronoiSource source,
                                       PositionalRandomFactory random) {
        report.append(String.format("%n(q) per-province relief over a %d-block patch deep inside one cell%n", RELIEF_PATCH));

        HolderLookup.RegistryLookup<DensityFunction> functions = registries.lookupOrThrow(Registries.DENSITY_FUNCTION);
        DensityFunction relief = seed(holder(functions, RelictDensityFunctionGenerator.RELIEF), random);
        DensityFunction plain = seed(RelictRidgeField.plain(registries.lookupOrThrow(Registries.NOISE)::getOrThrow), random);

        report.append(String.format("    %-22s %8s %8s %8s %8s %8s %8s %8s %8s%n",
                "province", "p2", "p50", "p98", "p2..p98", "plain", "ridge amp", "dune amp", "mesa amp"));

        for (Map.Entry<String, int[]> entry : deepestSurfaceCells(source).entrySet()) {
            int centreX = entry.getValue()[0];
            int centreZ = entry.getValue()[1];
            List<Double> full = new ArrayList<>();
            List<Double> plainOnly = new ArrayList<>();

            for (int dz = -RELIEF_PATCH / 2; dz <= RELIEF_PATCH / 2; dz += RELIEF_PATCH_STEP) {
                for (int dx = -RELIEF_PATCH / 2; dx <= RELIEF_PATCH / 2; dx += RELIEF_PATCH_STEP) {
                    full.add(sample(relief, centreX + dx, centreZ + dz));
                    plainOnly.add(source.blend(centreX + dx, centreZ + dz, (province, cellX, cellZ) -> province.plainRoughness())
                            * sample(plain, centreX + dx, centreZ + dz));
                }
            }

            double[] sorted = full.stream().mapToDouble(Double::doubleValue).sorted().toArray();
            double[] bare = plainOnly.stream().mapToDouble(Double::doubleValue).sorted().toArray();
            report.append(String.format("    %-22s %8.2f %8.2f %8.2f %8.2f %8.2f %8.2f %8.2f %8.2f%n", entry.getKey(),
                    percentile(sorted, 0.02), percentile(sorted, 0.50), percentile(sorted, 0.98),
                    percentile(sorted, 0.98) - percentile(sorted, 0.02),
                    percentile(bare, 0.98) - percentile(bare, 0.02),
                    source.blend(centreX, centreZ, (province, cellX, cellZ) -> province.ridgeAmplitude()),
                    source.blend(centreX, centreZ, (province, cellX, cellZ) -> province.duneAmplitude()),
                    source.blend(centreX, centreZ, (province, cellX, cellZ) -> province.mesaAmplitude())));
        }
    }

    // -------------------------------------------------------------------------- 1.9 palette-map plates

    /**
     * Top-down palette map of {@code rusted_dunes}: each column coloured by the surface block the
     * rule resolves to, shaded by the same Lambert hillshade {@link #maps} uses so the dune geometry
     * stays legible under the paint. Reuses the (o) morphology windows and the same
     * {@link RelictProvinceGenerator#DUNE_AMPLITUDE}/{@link RelictProvinceGenerator#DUNE_PLAIN_ROUGHNESS}
     * the shipped province blends to, and {@link DuneCrest#isCrest} — the exact test
     * {@code DuneCrestCondition} runs at world-gen time, just fed heights sampled straight off
     * {@code terrain/dune_shape} instead of the world's heightmap, so this plate cannot silently drift
     * from what {@code runServerData} ships.
     */
    private static void dunePalette(StringBuilder report, HolderLookup.Provider registries, Path directory,
                                    PositionalRandomFactory random) {
        report.append(String.format("%n(1.9-a) rusted_dunes crest palette map: dark smooth_basalt body, red_sand crest%n"));

        if (directory == null) {
            report.append("    relict.terrainReportDir unset, plates skipped\n");
            return;
        }

        HolderLookup.RegistryLookup<DensityFunction> functions = registries.lookupOrThrow(Registries.DENSITY_FUNCTION);
        DensityFunction shape = seed(holder(functions, RelictDensityFunctionGenerator.DUNE_SHAPE), random);
        DensityFunction plain = seed(RelictRidgeField.plain(registries.lookupOrThrow(Registries.NOISE)::getOrThrow), random);

        int side = DUNE_WINDOW / DUNE_WINDOW_STEP;
        int window = 0;

        for (int[] origin : MORPHOLOGY_WINDOWS) {
            double[][] heights = new double[side][side];
            for (int iz = 0; iz < side; iz++) {
                for (int ix = 0; ix < side; ix++) {
                    heights[iz][ix] = duneHeightAt(shape, plain, origin[0] + ix * DUNE_WINDOW_STEP, origin[1] + iz * DUNE_WINDOW_STEP);
                }
            }

            int[][][] rgb = new int[side][side][3];
            int crestPixels = 0;
            List<int[]> crestSpots = new ArrayList<>();

            for (int iz = 0; iz < side; iz++) {
                for (int ix = 0; ix < side; ix++) {
                    int x = origin[0] + ix * DUNE_WINDOW_STEP;
                    int z = origin[1] + iz * DUNE_WINDOW_STEP;
                    boolean crest = DuneCrest.isCrest((dx, dz) -> duneHeightAt(shape, plain, x + dx, z + dz));
                    double shade = lambert(heights, ix, iz, side, DUNE_WINDOW_STEP);
                    double[] base = crest ? RED_SAND_RGB : BASALT_RGB;

                    for (int c = 0; c < 3; c++) {
                        rgb[iz][ix][c] = (int) Math.round(base[c] * shade);
                    }

                    if (crest) {
                        crestPixels++;

                        // One spot per window quadrant crossed, so the teleport list is spread across the
                        // plate rather than three copies of the same corner.
                        if ((ix > side / 4 && iz > side / 4 && crestSpots.isEmpty())
                                || (ix > 3 * side / 4 && iz > 3 * side / 4 && crestSpots.size() == 1)) {
                            crestSpots.add(new int[]{x, z, (int) Math.ceil(heights[iz][ix]) + 2});
                        }
                    }
                }
            }

            String name = String.format("1.9-dunes-w%d-palette.ppm", window);
            try {
                Files.createDirectories(directory);
                writeColor(directory.resolve(name), rgb);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            report.append(String.format("    window %d blocks at x=%d z=%d, %d blocks/pixel -> %s  (crest %.1f%% of pixels)%n",
                    DUNE_WINDOW, origin[0], origin[1], DUNE_WINDOW_STEP,
                    directory.resolve(name).toAbsolutePath(), 100.0 * crestPixels / (side * (double) side)));
            for (int[] spot : crestSpots) {
                report.append(String.format("        crest spot   /execute in relict:mars run tp @s %d %d %d%n",
                        spot[0], spot[2], spot[1]));
            }
            window++;
        }
    }

    /** The same per-column height formula (o) uses, factored out so the palette plate cannot drift from it. */
    private static double duneHeightAt(DensityFunction shape, DensityFunction plain, int x, int z) {
        return SEA_LEVEL + RelictProvinceGenerator.DUNE_AMPLITUDE * sample(shape, x, z)
                + RelictProvinceGenerator.DUNE_PLAIN_ROUGHNESS * sample(plain, x, z);
    }

    /** Lambert shading from a fixed low sun, as a 0..1 multiplier — the same lighting {@link #writeHillshade} uses. */
    private static double lambert(double[][] heights, int ix, int iz, int side, int step) {
        double dx = heights[iz][Math.min(ix + 1, side - 1)] - heights[iz][Math.max(ix - 1, 0)];
        double dz = heights[Math.min(iz + 1, side - 1)][ix] - heights[Math.max(iz - 1, 0)][ix];
        double nx = -dx / (2.0 * step);
        double nz = -dz / (2.0 * step);
        double length = Math.sqrt(nx * nx + 1.0 + nz * nz);
        double lit = (nx * -0.612 + 0.5 + nz * -0.612) / length;
        return 0.35 + 0.65 * Math.clamp(lit * 1.9, 0.0, 1.0);
    }

    /**
     * Sanity plate for the {@code wrinkle_plains} gravel patch: grey where
     * {@link RelictSurfaceRules#PAVEMENT_LO}..{@link RelictSurfaceRules#PAVEMENT_HI} fires on
     * {@code Noises.SURFACE}, red_sand otherwise, sampled at raw block coordinates exactly the way
     * {@code noiseCondition2d} reads it in-game (no external scale, unlike the dune warp/crenulation
     * channels) — so the patch density and scale in the plate is what the game will actually render.
     */
    private static void wrinklePlainsGravelPalette(StringBuilder report, HolderLookup.Provider registries, Path directory,
                                                    VoronoiSource source, DensityFunction surfaceY, PositionalRandomFactory random) {
        report.append(String.format("%n(1.9-b) wrinkle_plains gravel-patch sanity plate%n"));

        if (directory == null) {
            report.append("    relict.terrainReportDir unset, plate skipped\n");
            return;
        }

        int[] centre = deepestSurfaceCells(source).get("wrinkle_plains");
        if (centre == null) {
            report.append("    no wrinkle_plains cell found, plate skipped\n");
            return;
        }

        HolderLookup.RegistryLookup<NormalNoise.NoiseParameters> parameters = registries.lookupOrThrow(Registries.NOISE);
        NormalNoise surface = NormalNoise.create(random.fromHashOf(Noises.SURFACE.identifier()), parameters.getOrThrow(Noises.SURFACE).value());

        int side = GRAVEL_WINDOW / GRAVEL_WINDOW_STEP;
        int[][][] rgb = new int[side][side][3];
        int gravelPixels = 0;
        List<int[]> gravelSpots = new ArrayList<>();

        for (int iz = 0; iz < side; iz++) {
            for (int ix = 0; ix < side; ix++) {
                int x = centre[0] - GRAVEL_WINDOW / 2 + ix * GRAVEL_WINDOW_STEP;
                int z = centre[1] - GRAVEL_WINDOW / 2 + iz * GRAVEL_WINDOW_STEP;
                double value = surface.getValue(x, 0.0, z);
                boolean gravel = value >= RelictSurfaceRules.PAVEMENT_LO && value <= RelictSurfaceRules.PAVEMENT_HI;
                double[] base = gravel ? GRAVEL_RGB : RED_SAND_RGB;

                for (int c = 0; c < 3; c++) {
                    rgb[iz][ix][c] = (int) Math.round(base[c]);
                }

                if (gravel) {
                    gravelPixels++;

                    if ((ix > side / 4 && iz > side / 4 && gravelSpots.isEmpty())
                            || (ix > 3 * side / 4 && iz > 3 * side / 4 && gravelSpots.size() == 1)) {
                        gravelSpots.add(new int[]{x, z, Mth.ceil(sample(surfaceY, x, z)) + 2});
                    }
                }
            }
        }

        String name = "1.9-wrinkle-plains-palette.ppm";
        try {
            Files.createDirectories(directory);
            writeColor(directory.resolve(name), rgb);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        report.append(String.format("    window %d blocks at x=%d z=%d, %d blocks/pixel -> %s  (gravel %.1f%% of pixels)%n",
                GRAVEL_WINDOW, centre[0], centre[1], GRAVEL_WINDOW_STEP,
                directory.resolve(name).toAbsolutePath(), 100.0 * gravelPixels / (side * (double) side)));
        for (int[] spot : gravelSpots) {
            report.append(String.format("        gravel spot  /execute in relict:mars run tp @s %d %d %d%n",
                    spot[0], spot[2], spot[1]));
        }
    }

    private static void writeColor(Path path, int[][][] rgb) throws IOException {
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(path))) {
            out.write(("P6\n" + rgb[0].length + " " + rgb.length + "\n255\n").getBytes(StandardCharsets.US_ASCII));
            for (int[][] row : rgb) {
                for (int[] pixel : row) {
                    out.write(Math.clamp(pixel[0], 0, 255));
                    out.write(Math.clamp(pixel[1], 0, 255));
                    out.write(Math.clamp(pixel[2], 0, 255));
                }
            }
        }
    }

    /** Teleport spots for a manual in-game pass: the cell each surface province sits deepest inside. */
    private static void surfaceTeleports(StringBuilder report, VoronoiSource source, DensityFunction surfaceY) {
        report.append(String.format("%n(r) surface teleport spots (cell centres, this seed)%n"));

        deepestSurfaceCells(source).forEach((name, centre) -> report.append(String.format(
                "    %-22s /execute in relict:mars run tp @s %d %d %d%n",
                name, centre[0], Mth.ceil(sample(surfaceY, centre[0], centre[1])) + 2, centre[1])));

        // Borders too: that is where the blend law dilutes two landforms into each other, and the one place a
        // seam shows to a walking player rather than only to check (a).
        report.append(String.format("%n    borders, at the midpoint of two adjacent cell centres%n"));
        int cells = VERTEX_SCAN_RADIUS / source.cellSize();
        Set<String> seen = new LinkedHashSet<>();

        for (int cz = -cells; cz <= cells; cz++) {
            for (int cx = -cells; cx <= cells; cx++) {
                for (int[] step : new int[][]{{1, 0}, {0, 1}}) {
                    String near = source.provinceAt(cx, cz).unwrapKey().orElseThrow().identifier().getPath();
                    String far = source.provinceAt(cx + step[0], cz + step[1]).unwrapKey().orElseThrow().identifier().getPath();
                    if (near.equals(far) || !seen.add(near + " -> " + far)) {
                        continue;
                    }

                    int midX = (int) Math.round((source.centerX(cx, cz) + source.centerX(cx + step[0], cz + step[1])) / 2.0);
                    int midZ = (int) Math.round((source.centerZ(cx, cz) + source.centerZ(cx + step[0], cz + step[1])) / 2.0);
                    report.append(String.format("    %-22s /execute in relict:mars run tp @s %d %d %d%n",
                            near + " -> " + far, midX, Mth.ceil(sample(surfaceY, midX, midZ)) + 2, midZ));
                }
            }
        }
    }

    private static Map<String, int[]> deepestSurfaceCells(VoronoiSource source) {
        Map<String, double[]> deepest = new LinkedHashMap<>();
        int cells = VERTEX_SCAN_RADIUS / source.cellSize();

        for (int cz = -cells; cz <= cells; cz++) {
            for (int cx = -cells; cx <= cells; cx++) {
                double centreX = source.centerX(cx, cz);
                double centreZ = source.centerZ(cx, cz);
                VoronoiSource.Cell cell = source.nearest((int) centreX, (int) centreZ);
                if (cell.cellX() != cx || cell.cellZ() != cz) {
                    continue;
                }

                String name = source.provinceAt(cx, cz).unwrapKey().orElseThrow().identifier().getPath();
                double margin = cell.distanceToSecondCenter() - cell.distanceToCenter();
                double[] best = deepest.get(name);
                if (best == null || margin > best[0]) {
                    deepest.put(name, new double[]{margin, centreX, centreZ});
                }
            }
        }

        Map<String, int[]> centres = new LinkedHashMap<>();
        deepest.forEach((name, entry) -> centres.put(name, new int[]{(int) entry[1], (int) entry[2]}));
        return centres;
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
        Holder<Province> low = Holder.direct(new Province(biomes.getOrThrow(Biomes.DESERT), ElevationClass.LOW, -1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Holder<Province> high = Holder.direct(new Province(biomes.getOrThrow(Biomes.BADLANDS), ElevationClass.HIGH, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));

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

                // Off-ridge only, so this measures the plain between ridges rather than a
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
        Holder<Province> only = Holder.direct(new Province(biomes.getOrThrow(Biomes.DESERT), ElevationClass.MID, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
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

    // ---------------------------------------------------------------------------- (i) surface biome field

    /**
     * The 0.7 bug as a permanent check, both directions. Runs against the real registered level stem
     * (0.16b), so it exercises the actual {@code VoronoiBiomeSource} and a real, seeded {@code
     * Climate.Sampler} taken from {@code RandomState.sampler()} — the legal, instantiated route 0.16 §3
     * establishes. Also doubles as the headless proof that surface columns
     * resolve surface biomes and cave-band columns resolve underground biomes.
     */
    private static void surfaceBiomeInvariant(StringBuilder report, HolderLookup.Provider registries,
                                               VoronoiSource mars, DensityFunction surfaceY) {
        report.append(String.format("%n(i) surface-biome-at-surface / underground-biome-below-cut%n"));

        Optional<NoiseBasedChunkGenerator> generator = liveGenerator(registries);
        if (generator.isEmpty()) {
            report.append("    skipped: the Mars level stem generator is not noise-based\n");
            return;
        }

        NoiseGeneratorSettings settings = registries.lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(RelictDimension.MARS_NOISE_SETTINGS).value();
        RandomState state = RandomState.create(settings, registries.lookupOrThrow(Registries.NOISE), SEED);
        Climate.Sampler sampler = state.sampler();
        BiomeSource biomeSource = generator.get().getBiomeSource();

        Set<Identifier> surfaceBiomes = biomeIds(mars);
        // The real VoronoiBiomeSource seeds its sources lazily, on first read, from the running server's
        // world seed (VoronoiSource.seeded). There is no server in datagen, so this must bind the
        // underground source itself before the real getNoiseBiome path can touch it. Bound with
        // UNDERGROUND_SEED, the same value undergroundReport binds it with later in the same run — a
        // membership check does not care which seed picks the cell, and reusing the value keeps that
        // report's own bindSeed call a no-op instead of silently losing its documented fixed seed.
        Holder<VoronoiSource> undergroundHolder = registries.lookupOrThrow(RelictCustomRegistries.VORONOI_SOURCE_REGISTRY)
                .getOrThrow(RelictVoronoiSources.MARS_UNDERGROUND);
        undergroundHolder.value().bindSeed(undergroundHolder.unwrapKey().orElseThrow().identifier(), UNDERGROUND_SEED);
        Set<Identifier> undergroundBiomes = biomeIds(undergroundHolder.value());

        int checked = 0;
        int surfaceFailed = 0;
        int undergroundFailed = 0;
        int[] worstSurface = null;
        int[] worstUnderground = null;

        for (int z = -INVARIANT_SCAN_RADIUS; z <= INVARIANT_SCAN_RADIUS; z += INVARIANT_SCAN_STEP) {
            for (int x = -INVARIANT_SCAN_RADIUS; x <= INVARIANT_SCAN_RADIUS; x += INVARIANT_SCAN_STEP) {
                checked++;
                double surface = sample(surfaceY, x, z);
                int surfaceBlockY = Mth.floor(surface);

                Identifier atSurface = biomeAt(biomeSource, x, surfaceBlockY, z, sampler);
                if (!surfaceBiomes.contains(atSurface)) {
                    surfaceFailed++;
                    if (worstSurface == null) {
                        worstSurface = new int[]{x, z};
                    }
                }

                int belowCut = surfaceBlockY - RelictNoiseRouter.UNDERGROUND_MARGIN - UNDERGROUND_PROBE_DEPTH;
                Identifier underCut = biomeAt(biomeSource, x, belowCut, z, sampler);
                if (!undergroundBiomes.contains(underCut)) {
                    undergroundFailed++;
                    if (worstUnderground == null) {
                        worstUnderground = new int[]{x, z};
                    }
                }
            }
        }

        report.append(String.format("    checked %d columns%n", checked));
        report.append(String.format("    at each column's own surface height, resolves to a surface biome     %d/%d failed%s  %s%n",
                surfaceFailed, checked, worstSurface == null ? "" : String.format(" (first at x=%d z=%d)", worstSurface[0], worstSurface[1]),
                surfaceFailed == 0 ? "PASS" : "FAIL"));
        report.append(String.format("    %d blocks under the cut, resolves to an underground biome            %d/%d failed%s  %s%n",
                UNDERGROUND_PROBE_DEPTH, undergroundFailed, checked,
                worstUnderground == null ? "" : String.format(" (first at x=%d z=%d)", worstUnderground[0], worstUnderground[1]),
                undergroundFailed == 0 ? "PASS" : "FAIL"));
    }

    private static Identifier biomeAt(BiomeSource biomeSource, int blockX, int blockY, int blockZ, Climate.Sampler sampler) {
        Holder<Biome> biome = biomeSource.getNoiseBiome(QuartPos.fromBlock(blockX), QuartPos.fromBlock(blockY), QuartPos.fromBlock(blockZ), sampler);
        return biome.unwrapKey().map(key -> key.identifier()).orElse(null);
    }

    private static Set<Identifier> biomeIds(VoronoiSource source) {
        return source.provinces().unwrap().stream()
                .map(Weighted::value)
                .map(Holder::value)
                .map(Province::biome)
                .map(biome -> biome.unwrapKey().orElseThrow().identifier())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Optional<NoiseBasedChunkGenerator> liveGenerator(HolderLookup.Provider registries) {
        LevelStem levelStem = registries.lookupOrThrow(Registries.LEVEL_STEM).getOrThrow(RelictDimension.MARS_LEVELSTEM).value();
        return levelStem.generator() instanceof NoiseBasedChunkGenerator generator ? Optional.of(generator) : Optional.empty();
    }

    // -------------------------------------------------------------------------- (k) margin vs. worst drop

    /**
     * Turns 0.16 §8's derivation into a guarantee: the worst |d surfaceY| between quart-adjacent columns
     * (the biome grid's own resolution) must stay under {@code UNDERGROUND_MARGIN}, or a future primitive
     * with a taller scarp could paint a lit cave face on a cliff (0.16 §7.2). Reuses the vertex list from
     * (a); that is where two provinces meet and the drop is worst.
     */
    private static void marginVsWorstDrop(StringBuilder report, DensityFunction surfaceY, List<long[]> vertices) {
        report.append(String.format("%n(k) margin vs. worst drop between adjacent quart columns%n"));

        double worst = 0.0;
        long[] worstAt = {0, 0};
        int swept = 0;

        for (int v = 0; v < vertices.size() && swept < VERTICES_SWEPT; v += Math.max(1, vertices.size() / VERTICES_SWEPT)) {
            long[] vertex = vertices.get(v);
            swept++;

            for (boolean alongX : new boolean[]{true, false}) {
                double previous = Double.NaN;

                for (int d = -SWEEP_HALF_LENGTH; d <= SWEEP_HALF_LENGTH; d += QuartPos.toBlock(1)) {
                    int x = (int) vertex[0] + (alongX ? d : 0);
                    int z = (int) vertex[1] + (alongX ? 0 : d);
                    double surface = sample(surfaceY, x, z);

                    if (!Double.isNaN(previous)) {
                        double drop = Math.abs(surface - previous);
                        if (drop > worst) {
                            worst = drop;
                            worstAt = new long[]{x, z};
                        }
                    }

                    previous = surface;
                }
            }
        }

        report.append(String.format("    swept %d vertices, two quart-stride (%d block) lines of %d blocks each%n",
                swept, QuartPos.toBlock(1), 2 * SWEEP_HALF_LENGTH + 1));
        report.append(String.format("    max |d surfaceY| between adjacent quart columns  %.4f blocks at x=%d z=%d  (margin %d)  %s%n",
                worst, worstAt[0], worstAt[1], RelictNoiseRouter.UNDERGROUND_MARGIN,
                worst <= RelictNoiseRouter.UNDERGROUND_MARGIN ? "PASS" : "FAIL"));
    }

    // ------------------------------------------------------------------ (l) RELIEF is the composition point

    /**
     * Pins the invariant 0.16 §6 depends on and §11.3 risk 1 warns about: surfaceY computed straight from
     * the registered RELIEF handle must equal both the level stem's preliminary_surface_level slot and its
     * continents slot. If a later session adds a height channel outside RELIEF, or repurposes continents,
     * this fails loudly instead of the biome field silently going stale.
     */
    private static void compositionInvariant(StringBuilder report, HolderLookup.Provider registries,
                                              DensityFunction surfaceY, PositionalRandomFactory random) {
        report.append(String.format("%n(l) RELIEF is the single composition point%n"));

        NoiseGeneratorSettings settings = registries.lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(RelictDimension.MARS_NOISE_SETTINGS).value();
        NoiseRouter router = settings.noiseRouter();
        DensityFunction preliminary = seed(router.preliminarySurfaceLevel(), random);
        DensityFunction continents = seed(router.continents(), random);

        double worstPreliminary = 0.0;
        double worstContinents = 0.0;

        for (int z = -COMPOSITION_CHECK_RADIUS; z <= COMPOSITION_CHECK_RADIUS; z += COMPOSITION_CHECK_STEP) {
            for (int x = -COMPOSITION_CHECK_RADIUS; x <= COMPOSITION_CHECK_RADIUS; x += COMPOSITION_CHECK_STEP) {
                double expected = sample(surfaceY, x, z);
                worstPreliminary = Math.max(worstPreliminary, Math.abs(sample(preliminary, x, z) - expected));
                worstContinents = Math.max(worstContinents, Math.abs(sample(continents, x, z) - expected));
            }
        }

        report.append(String.format("    surfaceY vs preliminary_surface_level slot   max delta %.6f  %s%n",
                worstPreliminary, worstPreliminary < 1.0e-6 ? "PASS" : "FAIL"));
        report.append(String.format("    surfaceY vs continents slot                  max delta %.6f  %s%n",
                worstContinents, worstContinents < 1.0e-6 ? "PASS" : "FAIL"));
    }

    // ------------------------------------------------------------------------- (m) F3 readout agreement

    /**
     * Stops the debug path ({@code addDebugInfo}, the F3 readout) drifting from the real one ({@code
     * getNoiseBiome}). Parses the province the F3 line reports and compares its biome to the one the real
     * biome pick resolves, at the same position, both above and below the cut.
     */
    private static void debugReadoutAgreement(StringBuilder report, HolderLookup.Provider registries, List<long[]> vertices) {
        report.append(String.format("%n(m) F3 readout agrees with the real biome pick%n"));

        Optional<NoiseBasedChunkGenerator> generator = liveGenerator(registries);
        if (generator.isEmpty() || vertices.isEmpty()) {
            report.append("    skipped: no generator or no vertices to sample\n");
            return;
        }

        NoiseGeneratorSettings settings = registries.lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(RelictDimension.MARS_NOISE_SETTINGS).value();
        RandomState state = RandomState.create(settings, registries.lookupOrThrow(Registries.NOISE), SEED);
        Climate.Sampler sampler = state.sampler();
        BiomeSource biomeSource = generator.get().getBiomeSource();
        HolderLookup.RegistryLookup<Province> provinces = registries.lookupOrThrow(RelictCustomRegistries.PROVINCE_REGISTRY);

        int checked = 0;
        int mismatched = 0;
        long[] worstAt = null;

        for (int v = 0; v < vertices.size() && v < DEBUG_READOUT_SAMPLES; v++) {
            long[] vertex = vertices.get(v);
            int x = (int) vertex[0];
            int z = (int) vertex[1];

            // y=64 (above any plausible cut) and y=-32 (below any plausible cut) — two positions, one column.
            for (int y : new int[]{64, -32}) {
                checked++;
                BlockPos pos = new BlockPos(x, y, z);

                List<String> debugLines = new ArrayList<>();
                biomeSource.addDebugInfo(debugLines, pos, sampler);
                String line = debugLines.getFirst();
                String provinceId = line.substring("Province: ".length(), line.indexOf(" cell"));

                Identifier fromDebug = provinceId.equals("(inline)") ? null
                        : provinces.getOrThrow(ResourceKey.create(RelictCustomRegistries.PROVINCE_REGISTRY, Identifier.parse(provinceId)))
                                .value().biome().unwrapKey().orElseThrow().identifier();
                Identifier fromReal = biomeAt(biomeSource, x, y, z, sampler);

                if (!Objects.equals(fromDebug, fromReal)) {
                    mismatched++;
                    if (worstAt == null) {
                        worstAt = new long[]{x, y, z};
                    }
                }
            }
        }

        report.append(String.format("    checked %d positions across %d vertex columns%n", checked, Math.min(vertices.size(), DEBUG_READOUT_SAMPLES)));
        report.append(String.format("    F3 province's biome matches getNoiseBiome's   %d/%d mismatched%s  %s%n",
                mismatched, checked, worstAt == null ? "" : String.format(" (first at x=%d y=%d z=%d)", worstAt[0], worstAt[1], worstAt[2]),
                mismatched == 0 ? "PASS" : "FAIL"));
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

    // ------------------------------------------------------------------------------- crater field checks

    /**
     * (s) The size-frequency law, re-measured through the same rolls the density function makes. A cumulative
     * log-log slope near -2 is the whole reason the diameter draw is a truncated Pareto, and it is the one
     * property that silently dies if the exponent or the layer bands are retuned.
     */
    private static void craterCensus(StringBuilder report, VoronoiSource source, List<String> failures) {
        report.append(String.format("%n(s) crater census over a %d-block window, through the field's own rolls%n",
                CRATER_CENSUS_SPAN));

        long seed = CraterFieldFunction.craterSeed(source.seed());
        int half = CRATER_CENSUS_SPAN / 2;
        double area = (double) CRATER_CENSUS_SPAN * CRATER_CENSUS_SPAN / BLOCKS_PER_SQUARE_KILOMETRE;
        List<Double> diameters = new ArrayList<>();

        report.append(String.format("    %-8s %10s %12s %14s %14s%n", "layer", "cells", "craters", "per cell", "per km^2"));

        for (CraterFieldFunction.Layer layer : CraterFieldFunction.LAYERS) {
            int from = Mth.floor(-half / layer.cell());
            int to = Mth.floor(half / layer.cell());
            long cells = 0;
            long found = 0;

            for (int cellZ = from; cellZ <= to; cellZ++) {
                for (int cellX = from; cellX <= to; cellX++) {
                    cells++;
                    CraterFieldFunction.Crater crater = CraterFieldFunction.rollCrater(seed, layer, cellX, cellZ, source);
                    if (crater != null) {
                        found++;
                        diameters.add(crater.diameter());
                    }
                }
            }

            report.append(String.format("    %-8.0f %10d %12d %14.4f %14.1f%n",
                    layer.cell(), cells, found, (double) found / cells, found / area));
        }

        report.append(String.format("    %-8s %10s %12d %14s %14.1f%n", "all", "", diameters.size(), "", diameters.size() / area));

        double[] logDiameter = new double[CRATER_SIZE_THRESHOLDS.length];
        double[] logCount = new double[CRATER_SIZE_THRESHOLDS.length];
        int fitted = 0;

        report.append(String.format("%n    %-14s %14s%n", "diameter >=", "cumulative"));

        for (double threshold : CRATER_SIZE_THRESHOLDS) {
            long above = diameters.stream().filter(diameter -> diameter >= threshold).count();
            report.append(String.format("    %-14.1f %14d%n", threshold, above));

            if (above > 0) {
                logDiameter[fitted] = Math.log(threshold);
                logCount[fitted] = Math.log(above);
                fitted++;
            }
        }

        bar(report, failures, "size-frequency slope", leastSquaresSlope(logDiameter, logCount, fitted),
                CRATER_SLOPE_LOWEST, CRATER_SLOPE_HIGHEST, "");
    }

    private static double leastSquaresSlope(double[] xs, double[] ys, int count) {
        double meanX = 0.0;
        double meanY = 0.0;
        for (int i = 0; i < count; i++) {
            meanX += xs[i] / count;
            meanY += ys[i] / count;
        }

        double covariance = 0.0;
        double variance = 0.0;
        for (int i = 0; i < count; i++) {
            covariance += (xs[i] - meanX) * (ys[i] - meanY);
            variance += (xs[i] - meanX) * (xs[i] - meanX);
        }

        return covariance / variance;
    }

    /**
     * (t) Density against the age of the ground it sits on, measured rather than asserted: cells drawn from
     * across the world, bucketed by the oldness at their own centre, counted for whether they produced a
     * crater. A sign error in the epoch hookup is the classic silent failure here — the field would still
     * look like a crater field, just with the chronology reversed — and only monotonicity catches it.
     */
    private static void craterEpochDensity(StringBuilder report, VoronoiSource source, List<String> failures) {
        report.append(String.format("%n(t) crater density against epoch, %d cells per layer over +-%d blocks%n",
                CRATER_EPOCH_CELLS, CRATER_EPOCH_SPAN));

        long seed = CraterFieldFunction.craterSeed(source.seed());
        int layerCount = CraterFieldFunction.LAYERS.size();
        long[][] cells = new long[layerCount][CRATER_EPOCH_BINS];
        long[][] found = new long[layerCount][CRATER_EPOCH_BINS];

        for (int index = 0; index < layerCount; index++) {
            CraterFieldFunction.Layer layer = CraterFieldFunction.LAYERS.get(index);
            long range = (long) (CRATER_EPOCH_SPAN / layer.cell());

            for (int draw = 0; draw < CRATER_EPOCH_CELLS; draw++) {
                long spread = LatticeHash.mix(draw * 0x9E3779B97F4A7C15L + index * 0xC2B2AE3D27D4EB4FL);
                int cellX = (int) (Math.floorMod(spread, 2L * range + 1L) - range);
                int cellZ = (int) (Math.floorMod(LatticeHash.mix(spread), 2L * range + 1L) - range);

                double[] centre = CraterFieldFunction.cellCenter(seed, layer, cellX, cellZ);
                double oldness = CraterFieldFunction.oldnessAt(source, centre[0], centre[1]);
                int bin = Math.min((int) (oldness * CRATER_EPOCH_BINS), CRATER_EPOCH_BINS - 1);

                cells[index][bin]++;
                if (CraterFieldFunction.rollCrater(seed, layer, cellX, cellZ, source) != null) {
                    found[index][bin]++;
                }
            }
        }

        report.append(String.format("    %-12s %10s %14s%n", "oldness", "cells", "per km^2"));

        double previous = Double.NEGATIVE_INFINITY;
        int compared = 0;
        boolean monotone = true;

        for (int bin = 0; bin < CRATER_EPOCH_BINS; bin++) {
            long binCells = 0;
            double density = 0.0;
            boolean usable = true;

            for (int index = 0; index < layerCount; index++) {
                binCells += cells[index][bin];
                if (cells[index][bin] < CRATER_EPOCH_MIN_CELLS) {
                    usable = false;
                    continue;
                }

                double cell = CraterFieldFunction.LAYERS.get(index).cell();
                density += (double) found[index][bin] / cells[index][bin] / (cell * cell) * BLOCKS_PER_SQUARE_KILOMETRE;
            }

            report.append(String.format("    %-12s %10d %14s%n",
                    String.format("%.2f..%.2f", (double) bin / CRATER_EPOCH_BINS, (bin + 1.0) / CRATER_EPOCH_BINS),
                    binCells, usable ? String.format("%.1f", density) : "(too few cells)"));

            if (!usable) {
                continue;
            }

            if (compared > 0 && density <= previous) {
                monotone = false;
            }

            previous = density;
            compared++;
        }

        bar(report, failures, "epoch bins compared", compared, 8.0, CRATER_EPOCH_BINS, "bins");
        bar(report, failures, "density strictly increasing", monotone ? 1.0 : 0.0, 1.0, 1.0, "");
    }

    /**
     * (u) The depth law as a permanent pin, in two halves because the field cannot supply both.
     *
     * <p>The synthetic half sweeps the three calibrated size classes on a sub-block radius grid, so block
     * quantization cannot blunt the rim crest of the smallest class. It measures shape, not just endpoints:
     * the crest has to sit at the rim radius and the bowl has to climb to it without a local maximum, which
     * is what a wrong bowl exponent or a wrong flank exponent would break.
     *
     * <p>The live half repeats the measurement on a real crater the field placed, which pins the gather —
     * the layer scan, the epoch read, the wobble remap, the truncation product — and not only the profile.
     * Only the smaller classes can supply one: a 224-block bowl has a footprint of some 114,000 blocks, and
     * at even the youngest crater densities something else always lands inside it. That is the field working
     * as designed, not a gap in it.
     */
    private static void craterProfile(StringBuilder report, HolderLookup.Provider registries, VoronoiSource source,
                                      CraterFieldFunction craters, PositionalRandomFactory random, List<String> failures) {
        report.append(String.format("%n(u) crater profile against the depth law, tolerance %.0f%%%n",
                100.0 * CRATER_PROFILE_TOLERANCE));

        long seed = CraterFieldFunction.craterSeed(source.seed());
        report.append(String.format("%n    synthetic, fresh, swept at %.3f in r%n", CRATER_PROFILE_RADIUS_STEP));
        report.append(String.format("    %-10s %10s %10s %10s %10s %10s %8s%n",
                "diameter", "floor", "law", "rim", "law", "crest r", "beyond"));

        for (double diameter : CRATER_PROFILE_DIAMETERS) {
            double lawDepth = CraterFieldFunction.freshDepth(diameter);
            double lawRim = CraterFieldFunction.RIM_RATIO * lawDepth;

            double floor = 0.0;
            double rim = Double.NEGATIVE_INFINITY;
            double crest = 0.0;
            double beyond = 0.0;
            double previous = Double.NEGATIVE_INFINITY;
            int bowlReversals = 0;

            for (double r = 0.0; r <= CraterFieldFunction.FOOTPRINT + 0.3; r += CRATER_PROFILE_RADIUS_STEP) {
                double height = CraterFieldFunction.craterDelta(r, diameter, 0.0);
                floor = Math.min(floor, height);

                if (height > rim) {
                    rim = height;
                    crest = r;
                }

                if (r < 1.0 && height < previous) {
                    bowlReversals++;
                }

                if (r >= CraterFieldFunction.FOOTPRINT) {
                    beyond = Math.max(beyond, Math.abs(height));
                }

                previous = height;
            }

            report.append(String.format("    %-10.0f %10.3f %10.3f %10.3f %10.3f %10.3f %8.1e%n",
                    diameter, floor, -lawDepth, rim, lawRim, crest, beyond));

            bar(report, failures, String.format("D%.0f floor / law", diameter), floor / -lawDepth,
                    1.0 - CRATER_PROFILE_TOLERANCE, 1.0 + CRATER_PROFILE_TOLERANCE, "");
            bar(report, failures, String.format("D%.0f rim / law", diameter), rim / lawRim,
                    1.0 - CRATER_PROFILE_TOLERANCE, 1.0 + CRATER_PROFILE_TOLERANCE, "");
            bar(report, failures, String.format("D%.0f crest radius", diameter), crest, 0.99, 1.01, "");
            bar(report, failures, String.format("D%.0f bowl reversals", diameter), bowlReversals, 0.0, 0.0, "");
            bar(report, failures, String.format("D%.0f beyond footprint", diameter), beyond, 0.0, ROUNDING, "blocks");
        }

        report.append(String.format("%n    live, the freshest isolated crater the field places%n"));
        report.append(String.format("    %-10s %10s %10s %10s %10s %10s %8s%n",
                "diameter", "age", "floor", "law", "rim", "law", "beyond"));

        CraterFieldFunction.Crater crater = findIsolatedCrater(seed, source, craters);

        if (crater == null) {
            report.append("    none found  FAIL\n");
            failures.add("crater profile, live: the field placed no isolated fresh crater to measure");
        } else {
            double radius = 0.5 * crater.diameter();
            double lawDepth = CraterFieldFunction.freshDepth(crater.diameter())
                    * (1.0 - CraterFieldFunction.DEPTH_LOSS * crater.degradation());
            double lawRim = CraterFieldFunction.RIM_RATIO * CraterFieldFunction.freshDepth(crater.diameter())
                    * (1.0 - CraterFieldFunction.RIM_LOSS * crater.degradation());

            double floor = craters.sampleAt(source, crater.centerX(), crater.centerZ()).delta();
            double rim = Double.NEGATIVE_INFINITY;
            double beyond = 0.0;

            for (int step = 0; step < CRATER_PROFILE_RING_SAMPLES; step++) {
                double angle = 2.0 * Math.PI * step / CRATER_PROFILE_RING_SAMPLES;

                for (double r = 0.85; r <= 1.25; r += CRATER_PROFILE_RADIUS_STEP) {
                    rim = Math.max(rim, delta(craters, source, crater, radius * r, angle));
                }

                beyond = Math.max(beyond, Math.abs(delta(craters, source, crater,
                        radius * (CraterFieldFunction.FOOTPRINT + 0.2), angle)));
            }

            report.append(String.format("    %-10.1f %10.3f %10.3f %10.3f %10.3f %10.3f %8.1e%n",
                    crater.diameter(), crater.degradation(), floor, -lawDepth, rim, lawRim, beyond));

            bar(report, failures, "live floor / law", floor / -lawDepth,
                    1.0 - CRATER_PROFILE_TOLERANCE, 1.0 + CRATER_PROFILE_TOLERANCE, "");
            bar(report, failures, "live rim / law", rim / lawRim,
                    1.0 - CRATER_PROFILE_TOLERANCE, 1.0 + CRATER_PROFILE_TOLERANCE, "");
            bar(report, failures, "live beyond footprint", beyond, 0.0, ROUNDING, "blocks");
        }

        HolderLookup.RegistryLookup<DensityFunction> functions = registries.lookupOrThrow(Registries.DENSITY_FUNCTION);
        DensityFunction registered = seed(holder(functions, RelictDensityFunctionGenerator.CRATER_DELTA), random);
        double worst = 0.0;

        for (int z = -COMPOSITION_CHECK_RADIUS; z <= COMPOSITION_CHECK_RADIUS; z += COMPOSITION_CHECK_STEP) {
            for (int x = -COMPOSITION_CHECK_RADIUS; x <= COMPOSITION_CHECK_RADIUS; x += COMPOSITION_CHECK_STEP) {
                worst = Math.max(worst, Math.abs(sample(registered, x, z) - craters.sampleAt(source, x, z).delta()));
            }
        }

        bar(report, failures, "registered df vs gather", worst, 0.0, ROUNDING, "blocks");
    }

    private static double delta(CraterFieldFunction craters, VoronoiSource source, CraterFieldFunction.Crater crater,
                                double distance, double angle) {
        return craters.sampleAt(source, crater.centerX() + distance * Math.cos(angle),
                crater.centerZ() + distance * Math.sin(angle)).delta();
    }

    /**
     * The widest fresh crater with nothing else inside its footprint, so its delta is its own profile and not
     * a truncation product. Searched widest layer first and across the whole world rather than near the
     * origin, because isolation only happens on young ground.
     */
    private static CraterFieldFunction.Crater findIsolatedCrater(long seed, VoronoiSource source, CraterFieldFunction craters) {
        for (int index = CraterFieldFunction.LAYERS.size() - 1; index >= 0; index--) {
            CraterFieldFunction.Layer layer = CraterFieldFunction.LAYERS.get(index);
            long range = (long) (CRATER_EPOCH_SPAN / layer.cell());

            for (int draw = 0; draw < CRATER_PROFILE_SEARCH_CELLS; draw++) {
                long spread = LatticeHash.mix(draw * 0x2545F4914F6CDD1DL + index * 0x14057B7EF767814FL);
                int cellX = (int) (Math.floorMod(spread, 2L * range + 1L) - range);
                int cellZ = (int) (Math.floorMod(LatticeHash.mix(spread), 2L * range + 1L) - range);

                CraterFieldFunction.Crater crater = CraterFieldFunction.rollCrater(seed, layer, cellX, cellZ, source);
                if (crater != null && crater.degradation() <= CRATER_PROFILE_FRESH && isolated(craters, source, crater)) {
                    return crater;
                }
            }
        }

        return null;
    }

    private static boolean isolated(CraterFieldFunction craters, VoronoiSource source, CraterFieldFunction.Crater crater) {
        double radius = 0.5 * crater.diameter();

        if (craters.sampleAt(source, crater.centerX(), crater.centerZ()).covering() != 1) {
            return false;
        }

        for (int step = 0; step < CRATER_PROFILE_RING_SAMPLES; step++) {
            double angle = 2.0 * Math.PI * step / CRATER_PROFILE_RING_SAMPLES;

            for (double r : new double[]{0.5, 1.0, 1.4}) {
                if (covering(craters, source, crater, radius * r, angle) != 1) {
                    return false;
                }
            }

            if (covering(craters, source, crater, radius * (CraterFieldFunction.FOOTPRINT + 0.2), angle) != 0) {
                return false;
            }
        }

        return true;
    }

    private static int covering(CraterFieldFunction craters, VoronoiSource source, CraterFieldFunction.Crater crater,
                                double distance, double angle) {
        return craters.sampleAt(source, crater.centerX() + distance * Math.cos(angle),
                crater.centerZ() + distance * Math.sin(angle)).covering();
    }

    /**
     * (v) The gather's fixed array is the one place this field can lose craters silently: past the ceiling it
     * drops them rather than growing. This measures the real crowding so a future presence retune cannot walk
     * into the ceiling unnoticed.
     */
    private static void craterOverlapBound(StringBuilder report, VoronoiSource source, CraterFieldFunction craters,
                                           List<String> failures) {
        report.append(String.format("%n(v) craters covering one column, over a %d-block window at %d-block stride%n",
                2 * CRATER_OVERLAP_SPAN, CRATER_OVERLAP_STEP));

        int[] histogram = new int[CraterFieldFunction.MAX_COVERING + 1];
        long columns = 0;
        long total = 0;
        int highest = 0;

        for (int z = -CRATER_OVERLAP_SPAN; z <= CRATER_OVERLAP_SPAN; z += CRATER_OVERLAP_STEP) {
            for (int x = -CRATER_OVERLAP_SPAN; x <= CRATER_OVERLAP_SPAN; x += CRATER_OVERLAP_STEP) {
                int covering = craters.sampleAt(source, x, z).covering();
                histogram[covering]++;
                columns++;
                total += covering;
                highest = Math.max(highest, covering);
            }
        }

        long running = 0;
        int ninetyNinth = 0;
        for (int covering = 0; covering < histogram.length; covering++) {
            running += histogram[covering];
            if (running <= 0.99 * columns) {
                ninetyNinth = covering + 1;
            }
        }

        report.append(String.format("    columns %d   mean %.2f   p99 %d   max %d   ceiling %d%n",
                columns, (double) total / columns, ninetyNinth, highest, CraterFieldFunction.MAX_COVERING));
        bar(report, failures, "max craters over a column", highest, 0.0, CraterFieldFunction.MAX_COVERING - 1.0, "craters");
    }

    /**
     * (j) The crater specialization of (i), deferred from 0.16b until a crater field existed. A fresh bowl is
     * the deepest thing that can sit under an otherwise flat column, so it is the landform most able to drop
     * a column's own surface far enough below its neighbour's that the surface-relative underground cut
     * paints cave visuals on a crater wall. Reports the worst quart-adjacent drop across real bowls against
     * the margin, and checks the floors themselves still resolve to surface biomes.
     */
    private static void craterFloorInvariant(StringBuilder report, HolderLookup.Provider registries, VoronoiSource source,
                                             CraterFieldFunction craters, DensityFunction surfaceY) {
        report.append(String.format("%n(j) crater-floor-stays-surface%n"));

        Optional<NoiseBasedChunkGenerator> generator = liveGenerator(registries);
        if (generator.isEmpty()) {
            report.append("    skipped: the Mars level stem generator is not noise-based\n");
            return;
        }

        NoiseGeneratorSettings settings = registries.lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(RelictDimension.MARS_NOISE_SETTINGS).value();
        RandomState state = RandomState.create(settings, registries.lookupOrThrow(Registries.NOISE), SEED);
        Climate.Sampler sampler = state.sampler();
        BiomeSource biomeSource = generator.get().getBiomeSource();
        Set<Identifier> surfaceBiomes = biomeIds(source);

        List<CraterFieldFunction.Crater> deepest = deepestFreshCraters(source);
        report.append(String.format("    %-12s %10s %10s %12s %10s %-24s%n",
                "diameter", "age", "bowl", "worst quart", "floor y", "biome at floor"));

        double worst = 0.0;
        int failed = 0;

        for (CraterFieldFunction.Crater crater : deepest) {
            int centreX = Mth.floor(crater.centerX());
            int centreZ = Mth.floor(crater.centerZ());
            double bowl = craters.sampleAt(source, crater.centerX(), crater.centerZ()).delta();
            double drop = quartDropAcross(surfaceY, crater);
            worst = Math.max(worst, drop);
            int floorY = Mth.floor(sample(surfaceY, centreX, centreZ));
            Identifier atFloor = biomeAt(biomeSource, centreX, floorY, centreZ, sampler);
            boolean onSurface = surfaceBiomes.contains(atFloor);
            if (!onSurface) {
                failed++;
            }

            report.append(String.format("    %-12.1f %10.3f %10.2f %12.4f %10d %-24s %s%n",
                    crater.diameter(), crater.degradation(), bowl, drop, floorY,
                    atFloor == null ? "(none)" : atFloor.getPath(), onSurface ? "PASS" : "FAIL"));
        }

        report.append(String.format("    worst quart-adjacent drop across %d bowls  %.4f blocks  (margin %d)  %s%n",
                deepest.size(), worst, RelictNoiseRouter.UNDERGROUND_MARGIN,
                worst <= RelictNoiseRouter.UNDERGROUND_MARGIN ? "PASS" : "FAIL"));
        report.append(String.format("    floors resolving to a surface biome        %d/%d failed  %s%n",
                failed, deepest.size(), failed == 0 ? "PASS" : "FAIL"));
    }

    /** The deepest bowls the field actually places within reach of the origin, deepest first. */
    private static List<CraterFieldFunction.Crater> deepestFreshCraters(VoronoiSource source) {
        List<CraterFieldFunction.Crater> found = new ArrayList<>(
                scanCraters(source, VERTEX_SCAN_RADIUS * 4, CRATER_FLOOR_SEARCH_CELLS, 0x165667B19E3779F9L).values());
        found.sort(Comparator.comparingDouble(crater -> -bowlDepth(crater)));
        return found.subList(0, Math.min(CRATER_FLOOR_CASES, found.size()));
    }

    private static double bowlDepth(CraterFieldFunction.Crater crater) {
        return CraterFieldFunction.freshDepth(crater.diameter()) * (1.0 - CraterFieldFunction.DEPTH_LOSS * crater.degradation());
    }

    /**
     * Craters drawn from cells scattered over a square of the given half-width, keyed by cell so a repeated
     * draw cannot report the same crater twice.
     */
    private static Map<Long, CraterFieldFunction.Crater> scanCraters(VoronoiSource source, int reach, int draws, long salt) {
        long seed = CraterFieldFunction.craterSeed(source.seed());
        Map<Long, CraterFieldFunction.Crater> found = new LinkedHashMap<>();

        for (int index = 0; index < CraterFieldFunction.LAYERS.size(); index++) {
            CraterFieldFunction.Layer layer = CraterFieldFunction.LAYERS.get(index);
            long range = (long) (reach / layer.cell());

            for (int draw = 0; draw < draws; draw++) {
                long spread = LatticeHash.mix(draw * salt + index * 0x27D4EB2F165667C5L);
                int cellX = (int) (Math.floorMod(spread, 2L * range + 1L) - range);
                int cellZ = (int) (Math.floorMod(LatticeHash.mix(spread), 2L * range + 1L) - range);

                CraterFieldFunction.Crater crater = CraterFieldFunction.rollCrater(seed, layer, cellX, cellZ, source);
                if (crater != null) {
                    found.putIfAbsent(((long) index << 62) ^ ((long) cellZ << 31) ^ cellX, crater);
                }
            }
        }

        return found;
    }

    /**
     * Crater spots for a manual in-game pass, one per province and one per corner of the age and size
     * space, because the field's whole claim is that those two axes read differently on the ground.
     */
    private static void craterTeleports(StringBuilder report, VoronoiSource source, CraterFieldFunction craters,
                                        DensityFunction surfaceY) {
        report.append(String.format("%n(w) crater teleport spots (this seed)%n"));

        Map<String, CraterFieldFunction.Crater> picks = new LinkedHashMap<>();
        Map<String, Double> scores = new LinkedHashMap<>();

        for (CraterFieldFunction.Crater crater : scanCraters(source, VERTEX_SCAN_RADIUS * 2,
                CRATER_FLOOR_SEARCH_CELLS, 0x9E3779B97F4A7C15L).values()) {
            int centreX = Mth.floor(crater.centerX());
            int centreZ = Mth.floor(crater.centerZ());
            VoronoiSource.Cell cell = source.nearest(centreX, centreZ);
            String province = source.provinceAt(cell.cellX(), cell.cellZ()).unwrapKey().orElseThrow().identifier().getPath();

            boolean fresh = crater.degradation() < 0.15;
            boolean ghost = crater.degradation() > 0.7;

            // Bigger is more legible on foot, so every category keeps its widest example rather than its first.
            if (fresh) {
                offer(picks, scores, "fresh, on " + province, crater, crater.diameter());
                offer(picks, scores, "fresh, smallest", crater, -crater.diameter());
                offer(picks, scores, "fresh, largest", crater, crater.diameter());
            }

            if (ghost) {
                offer(picks, scores, "degraded, on " + province, crater, crater.diameter());
                offer(picks, scores, "degraded, largest", crater, crater.diameter());
            }

            if (craters.sampleAt(source, crater.centerX(), crater.centerZ()).covering() > 2) {
                offer(picks, scores, "crowded, crater on crater", crater, crater.diameter());
            }

            // Margin stress: a bowl cut into the mesa scarps, where the crater relief and the province's own
            // cliffs stack into the same column drop check (k) and check (j) both police.
            if ("fretted_mesas".equals(province)) {
                offer(picks, scores, "on a mesa scarp", crater, quartDropAcross(surfaceY, crater));
            }
        }

        picks.forEach((label, crater) -> {
            int centreX = Mth.floor(crater.centerX());
            int centreZ = Mth.floor(crater.centerZ());
            report.append(String.format("    %-32s D%-6.0f g%-5.2f /execute in relict:mars run tp @s %d %d %d%n",
                    label, crater.diameter(), crater.degradation(), centreX,
                    Mth.ceil(sample(surfaceY, centreX, centreZ)) + 2, centreZ));
        });
    }

    private static void offer(Map<String, CraterFieldFunction.Crater> picks, Map<String, Double> scores,
                              String label, CraterFieldFunction.Crater crater, double score) {
        if (score > scores.getOrDefault(label, Double.NEGATIVE_INFINITY)) {
            scores.put(label, score);
            picks.put(label, crater);
        }
    }

    /** The worst step between quart-adjacent columns on two transects through a crater's footprint. */
    private static double quartDropAcross(DensityFunction surfaceY, CraterFieldFunction.Crater crater) {
        int centreX = Mth.floor(crater.centerX());
        int centreZ = Mth.floor(crater.centerZ());
        int span = Mth.ceil(CraterFieldFunction.FOOTPRINT * 0.5 * crater.diameter());
        double worst = 0.0;

        for (boolean alongX : new boolean[]{true, false}) {
            double previous = Double.NaN;

            for (int d = -span; d <= span; d += QuartPos.toBlock(1)) {
                double surface = sample(surfaceY, centreX + (alongX ? d : 0), centreZ + (alongX ? 0 : d));
                if (!Double.isNaN(previous)) {
                    worst = Math.max(worst, Math.abs(surface - previous));
                }

                previous = surface;
            }
        }

        return worst;
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

        caveFloorCensus(report, registries);

        return report;
    }

    private static void caveFloorCensus(StringBuilder report, HolderLookup.Provider registries) {
        NoiseGeneratorSettings settings = registries.lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(RelictDimension.MARS_NOISE_SETTINGS).value();
        LevelStem levelStem = registries.lookupOrThrow(Registries.LEVEL_STEM)
                .getOrThrow(RelictDimension.MARS_LEVELSTEM).value();

        if (!(levelStem.generator() instanceof NoiseBasedChunkGenerator generator)) {
            report.append("\ncave floor census, skipped: the Mars level stem generator is not noise-based\n");
            return;
        }

        RandomState state = RandomState.create(settings, registries.lookupOrThrow(Registries.NOISE), SEED);
        LevelHeightAccessor height = LevelHeightAccessor.create(settings.noiseSettings().minY(), settings.noiseSettings().height());

        // Same seed the block generation above uses, so the band this reports is the band those blocks
        // actually came from. 0.16 re-bases the census from an absolute y range to surface-relative: the
        // band now runs from each column's own underground cut (surfaceY - UNDERGROUND_MARGIN) down
        // CAVE_BAND_DEPTH blocks, instead of a fixed y -54..80.
        PositionalRandomFactory random = new XoroshiroRandomSource(SEED).forkPositional();
        HolderLookup.RegistryLookup<DensityFunction> functions = registries.lookupOrThrow(Registries.DENSITY_FUNCTION);
        DensityFunction surfaceHeight = seed(holder(functions, RelictDensityFunctionGenerator.VORONOI_SURFACE_HEIGHT), random);
        DensityFunction relief = seed(holder(functions, RelictDensityFunctionGenerator.RELIEF), random);
        DensityFunction surfaceY = RelictNoiseRouter.surfaceY(surfaceHeight, relief, SEA_LEVEL);

        long banded = 0;
        long air = 0;
        long floors = 0;
        int columns = 0;
        int richestFloors = 0;
        int[] richest = null;
        int lowestCut = Integer.MAX_VALUE;
        int highestCut = Integer.MIN_VALUE;

        int half = CAVE_CENSUS_GRID * CAVE_CENSUS_STEP / 2;
        for (int ix = 0; ix < CAVE_CENSUS_GRID; ix++) {
            for (int iz = 0; iz < CAVE_CENSUS_GRID; iz++) {
                int x = ix * CAVE_CENSUS_STEP - half;
                int z = iz * CAVE_CENSUS_STEP - half;
                NoiseColumn blocks = generator.getBaseColumn(x, z, height, state);
                columns++;
                int columnFloors = 0;

                int cut = Math.min(Mth.floor(sample(surfaceY, x, z)) - RelictNoiseRouter.UNDERGROUND_MARGIN, height.getMaxY());
                int bandBottom = Math.max(cut - CAVE_BAND_DEPTH, height.getMinY());
                lowestCut = Math.min(lowestCut, cut);
                highestCut = Math.max(highestCut, cut);

                for (int y = bandBottom; y <= cut; y++) {
                    banded++;
                    if (blocks.getBlock(y).isAir()) {
                        air++;
                        if (!blocks.getBlock(y - 1).isAir()) {
                            columnFloors++;
                        }
                    }
                }

                floors += columnFloors;
                if (columnFloors > richestFloors) {
                    richestFloors = columnFloors;
                    richest = new int[] {x, z};
                }
            }
        }

        double airShare = (double) air / banded;
        double floorShare = (double) floors / (columns * (double) FULL_COLUMN_SPAN);

        report.append(String.format("%ncave floor census over %d columns, band = cut..cut-%d per column, cut (surfaceY - margin) ranged y %d..%d%n",
                columns, CAVE_BAND_DEPTH, lowestCut, highestCut));
        report.append(String.format("    cave air in the band            %.2f%% of %d blocks%n", 100.0 * airShare, banded));
        report.append(String.format("    cave floors per column          %.2f%n", (double) floors / columns));
        report.append(String.format("    one attempt survives, band + environment_scan   %.2f%%%n", 100.0 * airShare));
        report.append(String.format("    one attempt survives, whole-column height_range %.2f%%%n", 100.0 * floorShare));

        if (richest != null) {
            report.append(String.format("    richest column %d floors  /execute in relict:mars run tp @s %d %d %d%n",
                    richestFloors, richest[0], UNDERGROUND_TELEPORT_Y, richest[1]));
        }
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
