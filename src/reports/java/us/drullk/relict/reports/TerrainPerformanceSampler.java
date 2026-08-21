package us.drullk.relict.reports;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import us.drullk.relict.datagen.worldgen.RelictDensityFunctionGenerator;
import us.drullk.relict.init.custom.RelictCustomRegistries;
import us.drullk.relict.init.custom.RelictVoronoiSources;
import us.drullk.relict.init.worldgen.RelictDimension;
import us.drullk.relict.worldgen.LatticeHash;
import us.drullk.relict.worldgen.ProvinceParameter;
import us.drullk.relict.datagen.worldgen.RelictNoiseRouter;
import us.drullk.relict.worldgen.VoronoiSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntToDoubleFunction;

/**
 * Times the terrain graph and holds the golden hash that says the graph still answers what it answered
 * before.
 *
 * <h2>Why the numbers here are the numbers that matter</h2>
 * Two different callers reach the same terrain graph and pay wildly different prices for it.
 * {@code NoiseChunk} wraps the router and honors every {@code cache2d}/{@code flat_cache} marker, so chunk
 * generation pays for one voronoi blend per column. {@link RandomState#sampler()} instead flattens every
 * marker away, so structure placement, {@code /locate}, and any other {@code Climate.Sampler} reader pays
 * the whole graph again at every single sample. Every timing below is taken on the uncached route, because
 * that is the route that hung the server.
 *
 * <h2>Sections</h2>
 * <ul>
 *   <li><b>(A) field costs</b> — one number per primitive, so a regression names itself.</li>
 *   <li><b>(B) attribution</b> — the uncached surface graph split into its voronoi half and its noise half.</li>
 *   <li><b>(C) biome source</b> — {@code getNoiseBiome} on the uncached sampler, the exact call structure
 *       placement makes.</li>
 *   <li><b>(D) locate projection</b> — the per-candidate cost of a random-spread ring search, and what that
 *       comes to over a full radius-100 scan.</li>
 *   <li><b>(E) column throughput</b> — whole-column block states, the chunk-generation side.</li>
 *   <li><b>(F) golden hash</b> — biome, relief, surface height and epoch over a fixed spread set, folded to
 *       one number. Optimization work must not move it.</li>
 * </ul>
 */
public final class TerrainPerformanceSampler implements DataProvider {

    /** The seed the other samplers bind, so provider order cannot change what any of them measure. */
    private static final long SEED = 0x5EEDL;

    private static final long UNDERGROUND_SEED = 20260818L;

    /**
     * Fold of the field outputs over {@link #GOLDEN_SAMPLES} spread positions. Set to 0 to print a fresh
     * value instead of checking against this one. Re-baselined when the crater field entered RELIEF, which
     * moves every surface height on purpose; before that it read {@code 0xBA6B5935249A3C77L}.
     */
    private static final long GOLDEN_HASH = 0x6E33514A28623FFEL;

    private static final int GOLDEN_SAMPLES = 4096;

    /** Wide enough that the spread set crosses many cells, epoch octaves, and both elevation classes. */
    private static final int GOLDEN_SPAN = 200000;

    private static final int UNDERGROUND_PROBE_DEPTH = 60;

    private static final int TIMED_SAMPLES = 20000;
    private static final int WARMUP_SAMPLES = 5000;

    /** The scattered sample set spans this much in each direction, which is far wider than one cell. */
    private static final int TIMED_SPAN = 40000;

    private static final int QUARTS_PER_CHUNK = 16;
    private static final int RASTER_CHUNKS = 64;

    /** Column block-state timing is orders of magnitude slower per sample than a field read. */
    private static final int COLUMN_SAMPLES = 64;
    private static final int COLUMN_WARMUP = 8;

    /** The human_wreck placement: {@code random_spread}, 20-chunk spacing, and the vanilla locate radius. */
    private static final int LOCATE_SPACING_CHUNKS = 20;
    private static final int LOCATE_MEASURED_RINGS = 10;
    private static final int LOCATE_FULL_RINGS = 100;

    private static final int BLOCKS_PER_CHUNK_COLUMNS = 256;

    /** Nothing is beardified in a timing probe, and the vanilla marker for that is package private. */
    private static final DensityFunctions.BeardifierOrMarker NO_BEARDIFIER = new DensityFunctions.BeardifierOrMarker() {
        @Override
        public double compute(FunctionContext context) {
            return 0.0;
        }

        @Override
        public double minValue() {
            return 0.0;
        }

        @Override
        public double maxValue() {
            return 0.0;
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return DensityFunctions.BeardifierOrMarker.CODEC;
        }
    };

    /** Aquifers are permanently off on Mars, so the picker a noise chunk holds is never read. */
    private static final Aquifer.FluidPicker AIR_EVERYWHERE =
            (blockX, blockY, blockZ) -> new Aquifer.FluidStatus(Integer.MIN_VALUE, Blocks.AIR.defaultBlockState());

    /** Keeps the timed work from being optimized away. */
    private static volatile double sink;

    private final CompletableFuture<HolderLookup.Provider> registries;

    public TerrainPerformanceSampler(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        this.registries = registries;
    }

    @Override
    public String getName() {
        return "Relict Terrain Performance Sampler";
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        return this.registries.thenAccept(registries -> {
            StringBuilder report = new StringBuilder("\n=== terrain performance ===\n");
            List<String> failures = new ArrayList<>();

            Bench bench = Bench.create(registries);

            fieldCosts(report, bench);
            attribution(report, bench);
            biomeSource(report, bench);
            locateProjection(report, bench);
            columnSplit(report, bench);
            columnThroughput(report, bench);
            golden(report, bench, failures);

            writeReport(report.toString());
            System.out.print(report);

            if (!failures.isEmpty()) {
                throw new IllegalStateException("Terrain performance checks failed:\n  " + String.join("\n  ", failures));
            }
        });
    }

    // ------------------------------------------------------------------------------------------ sections

    private static void fieldCosts(StringBuilder report, Bench bench) {
        report.append(String.format("%n(A) field costs, uncached route, %d samples each%n", TIMED_SAMPLES));
        header(report);

        row(report, "voronoi nearest cell", index -> bench.mars().nearest(x(index), z(index)).distanceToCenter());
        row(report, "voronoi cell epoch", index -> bench.mars().cellEpoch(x(index) >> 9, z(index) >> 9));
        row(report, "voronoi province pick", index -> bench.mars().provinceAt(x(index) >> 9, z(index) >> 9).value().elevationOffset());
        row(report, "voronoi blend, surface_height", index -> ProvinceParameter.SURFACE_HEIGHT.compute(bench.mars(), x(index), z(index)));
        row(report, "voronoi blend, ridge_amplitude", index -> ProvinceParameter.RIDGE_AMPLITUDE.compute(bench.mars(), x(index), z(index)));
        row(report, "density function, ridge shape", index -> sample(bench.ridgeShape(), x(index), z(index)));
        row(report, "density function, dune shape", index -> sample(bench.duneShape(), x(index), z(index)));
        row(report, "density function, mesa shape", index -> sample(bench.mesaShape(), x(index), z(index)));
        row(report, "density function, crater delta", index -> sample(bench.craterDelta(), x(index), z(index)));
        row(report, "density function, crater damp", index -> sample(bench.craterDamp(), x(index), z(index)));

        report.append(String.format("%n    the same voronoi reads on a scattered sample set, one cell per call%n"));
        row(report, "voronoi nearest cell, scattered", index -> bench.mars().nearest(scatterX(index), scatterZ(index)).distanceToCenter());
        row(report, "voronoi blend, scattered", index -> ProvinceParameter.SURFACE_HEIGHT.compute(bench.mars(), scatterX(index), scatterZ(index)));
    }

    private static void attribution(StringBuilder report, Bench bench) {
        report.append(String.format("%n(B) uncached surface graph, split%n"));
        header(report);

        double surfaceY = row(report, "surface y, whole graph", index -> sample(bench.surfaceY(), x(index), z(index)));
        double surfaceHeight = row(report, "  voronoi surface_height", index -> sample(bench.surfaceHeight(), x(index), z(index)));
        double relief = row(report, "  relief, whole", index -> sample(bench.relief(), x(index), z(index)));
        double blends = row(report, "  relief, its four blends", index -> {
            int blockX = x(index);
            int blockZ = z(index);
            return ProvinceParameter.RIDGE_AMPLITUDE.compute(bench.mars(), blockX, blockZ)
                    + ProvinceParameter.PLAIN_ROUGHNESS.compute(bench.mars(), blockX, blockZ)
                    + ProvinceParameter.DUNE_AMPLITUDE.compute(bench.mars(), blockX, blockZ)
                    + ProvinceParameter.MESA_AMPLITUDE.compute(bench.mars(), blockX, blockZ);
        });
        double shapes = row(report, "  relief, its three shapes", index -> {
            int blockX = x(index);
            int blockZ = z(index);
            return sample(bench.ridgeShape(), blockX, blockZ) + sample(bench.duneShape(), blockX, blockZ) + sample(bench.mesaShape(), blockX, blockZ);
        });

        double voronoi = surfaceHeight + blends;
        report.append(String.format("%n    voronoi share of the surface graph   %.1f%%%n", 100.0 * voronoi / surfaceY));
        report.append(String.format("    remainder, shapes and arithmetic     %.1f%%%n", 100.0 * (surfaceY - voronoi) / surfaceY));
        report.append(String.format("    three shapes measured on their own   %.3f us%n", shapes));
        report.append(String.format("    relief whole %.3f us vs. blends + shapes %.3f us%n", relief, blends + shapes));
        report.append(String.format("%n    Relief multiplies each shape by its own blended amplitude, and a multiply whose%n"));
        report.append(String.format("    left side is zero never reads its right side. A province that carries no mesa%n"));
        report.append(String.format("    therefore never pays for the mesa shape, which is why the whole costs less than%n"));
        report.append(String.format("    the sum of its parts.%n"));
    }

    private static void biomeSource(StringBuilder report, Bench bench) {
        report.append(String.format("%n(C) biome source, the call structure placement makes%n"));
        header(report);

        row(report, "getNoiseBiome, surface y", index -> {
            int blockX = x(index);
            int blockZ = z(index);
            return bench.biomeSource().getNoiseBiome(QuartPos.fromBlock(blockX), QuartPos.fromBlock(128), QuartPos.fromBlock(blockZ), bench.sampler()).hashCode();
        });
        row(report, "getNoiseBiome, deep y", index -> {
            int blockX = x(index);
            int blockZ = z(index);
            return bench.biomeSource().getNoiseBiome(QuartPos.fromBlock(blockX), QuartPos.fromBlock(-32), QuartPos.fromBlock(blockZ), bench.sampler()).hashCode();
        });

        report.append(String.format("%n    a 24-section chunk fills 1536 quart cells; on the uncached route that is%n"));
        report.append(String.format("    the cost above times 1536, which is why only the cached route can afford it.%n"));
    }

    private static void locateProjection(StringBuilder report, Bench bench) {
        report.append(String.format("%n(D) random-spread locate, %d-chunk spacing, rings 1..%d measured%n",
                LOCATE_SPACING_CHUNKS, LOCATE_MEASURED_RINGS));

        List<int[]> candidates = ringCandidates();
        long start = System.nanoTime();
        double accumulated = 0.0;

        for (int[] candidate : candidates) {
            accumulated += candidateCost(bench, candidate[0], candidate[1]);
        }

        long elapsed = System.nanoTime() - start;
        sink += accumulated;

        double perCandidateMs = elapsed / 1.0e6 / candidates.size();
        long fullScan = 1L + 4L * LOCATE_FULL_RINGS * (LOCATE_FULL_RINGS + 1L);

        report.append(String.format("    %-42s %12s %14s%n", "measurement", "value", "unit"));
        report.append(String.format("    %-42s %12d %14s%n", "candidate chunks measured", candidates.size(), "chunks"));
        report.append(String.format("    %-42s %12.3f %14s%n", "per candidate", perCandidateMs, "ms"));
        report.append(String.format("    %-42s %12d %14s%n", "candidates in a radius-100 scan", fullScan, "chunks"));
        report.append(String.format("    %-42s %12.1f %14s%n", "projected full scan", perCandidateMs * fullScan / 1000.0, "s"));
        report.append(String.format("%n    Candidate cost here is the height probe plus the biome test. A jigsaw structure%n"));
        report.append(String.format("    also assembles its pieces before the biome test, so a real scan costs more.%n"));
    }

    /**
     * Splits one height probe into the part that builds the noise chunk and the part that walks the column.
     * The build maps the whole router graph into fresh wrapper objects, once, per probe; nothing about that
     * cost is proportional to how far down the column the walk gets.
     */
    private static void columnSplit(StringBuilder report, Bench bench) {
        report.append(String.format("%n(E) one height probe, split%n"));
        header(report);

        double probe = row(report, "getBaseHeight, whole probe", index ->
                bench.generator().getBaseHeight(x(index), z(index), Heightmap.Types.WORLD_SURFACE_WG, bench.height(), bench.state()));
        double build = row(report, "  noise chunk build only", index -> bench.noiseChunk(x(index), z(index)).hashCode());
        double surfaceY = row(report, "  surface graph, one read", index -> sample(bench.surfaceY(), x(index), z(index)));

        report.append(String.format("%n    noise chunk build share of a probe   %.1f%%%n", 100.0 * build / probe));
        report.append(String.format("    column walk share of a probe         %.1f%%%n", 100.0 * (probe - build) / probe));
        report.append(String.format("    surface graph share of a probe       %.1f%%   (four corner columns)%n",
                100.0 * 4.0 * surfaceY / probe));
    }

    private static void columnThroughput(StringBuilder report, Bench bench) {
        report.append(String.format("%n(F) column block states, %d columns%n", COLUMN_SAMPLES));

        for (int index = 0; index < COLUMN_WARMUP; index++) {
            sink += bench.generator().getBaseColumn(x(index), z(index), bench.height(), bench.state()).getBlock(128).hashCode();
        }

        long start = System.nanoTime();
        double accumulated = 0.0;

        for (int index = 0; index < COLUMN_SAMPLES; index++) {
            accumulated += bench.generator().getBaseColumn(x(index), z(index), bench.height(), bench.state()).getBlock(128).hashCode();
        }

        long elapsed = System.nanoTime() - start;
        sink += accumulated;

        double perColumnMs = elapsed / 1.0e6 / COLUMN_SAMPLES;
        report.append(String.format("    %-42s %12.3f %14s%n", "per column", perColumnMs, "ms"));
        report.append(String.format("    %-42s %12.2f %14s%n", "chunk equivalent, 256 columns",
                1000.0 / (perColumnMs * BLOCKS_PER_CHUNK_COLUMNS), "chunks/s"));
        report.append(String.format("%n    One column, one NoiseChunk. Real chunk generation shares a NoiseChunk across 256%n"));
        report.append(String.format("    columns, so this overstates setup and understates nothing.%n"));
    }

    private static void golden(StringBuilder report, Bench bench, List<String> failures) {
        report.append(String.format("%n(G) golden output hash, %d spread positions, span +-%d%n", GOLDEN_SAMPLES, GOLDEN_SPAN));

        long hash = 0L;

        for (int index = 0; index < GOLDEN_SAMPLES; index++) {
            long spread = LatticeHash.mix(index * 0x9E3779B97F4A7C15L + 0x2545F4914F6CDD1DL);
            int blockX = (int) Math.floorMod(spread, 2L * GOLDEN_SPAN + 1L) - GOLDEN_SPAN;
            int blockZ = (int) Math.floorMod(LatticeHash.mix(spread), 2L * GOLDEN_SPAN + 1L) - GOLDEN_SPAN;

            double surface = sample(bench.surfaceY(), blockX, blockZ);
            int surfaceBlockY = Mth.floor(surface);

            hash = fold(hash, blockX);
            hash = fold(hash, blockZ);
            hash = fold(hash, Double.doubleToRawLongBits(surface));
            hash = fold(hash, Double.doubleToRawLongBits(sample(bench.surfaceHeight(), blockX, blockZ)));
            hash = fold(hash, Double.doubleToRawLongBits(sample(bench.relief(), blockX, blockZ)));
            hash = fold(hash, Double.doubleToRawLongBits(sample(bench.epoch(), blockX, blockZ)));
            hash = fold(hash, biomeKey(bench, blockX, surfaceBlockY, blockZ));
            hash = fold(hash, biomeKey(bench, blockX, surfaceBlockY - RelictNoiseRouter.UNDERGROUND_MARGIN - UNDERGROUND_PROBE_DEPTH, blockZ));
        }

        report.append(String.format("    hash 0x%016XL%n", hash));

        if (GOLDEN_HASH == 0L) {
            report.append("    RECORDING: no expected hash is set, so nothing was checked.\n");
            return;
        }

        boolean matched = hash == GOLDEN_HASH;
        report.append(String.format("    expected 0x%016XL   %s%n", GOLDEN_HASH, matched ? "PASS" : "FAIL"));

        if (!matched) {
            failures.add(String.format("golden output hash 0x%016XL, expected 0x%016XL: the terrain graph now answers differently", hash, GOLDEN_HASH));
        }
    }

    // ------------------------------------------------------------------------------------------ plumbing

    private static double candidateCost(Bench bench, int chunkX, int chunkZ) {
        int blockX = (chunkX << 4) + 8;
        int blockZ = (chunkZ << 4) + 8;
        int blockY = bench.generator().getBaseHeight(blockX, blockZ, Heightmap.Types.WORLD_SURFACE_WG, bench.height(), bench.state());
        Holder<Biome> biome = bench.biomeSource().getNoiseBiome(
                QuartPos.fromBlock(blockX), QuartPos.fromBlock(blockY), QuartPos.fromBlock(blockZ), bench.sampler());
        return blockY + biome.hashCode();
    }

    private static List<int[]> ringCandidates() {
        List<int[]> candidates = new ArrayList<>();

        for (int radius = 1; radius <= LOCATE_MEASURED_RINGS; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x == -radius || x == radius || z == -radius || z == radius) {
                        candidates.add(new int[]{LOCATE_SPACING_CHUNKS * x, LOCATE_SPACING_CHUNKS * z});
                    }
                }
            }
        }

        return candidates;
    }

    private static long biomeKey(Bench bench, int blockX, int blockY, int blockZ) {
        Holder<Biome> biome = bench.biomeSource().getNoiseBiome(
                QuartPos.fromBlock(blockX), QuartPos.fromBlock(blockY), QuartPos.fromBlock(blockZ), bench.sampler());
        return biome.unwrapKey().map(key -> (long) key.identifier().toString().hashCode()).orElse(-1L);
    }

    private static long fold(long hash, long value) {
        return LatticeHash.mix(hash ^ LatticeHash.mix(value));
    }

    private static void header(StringBuilder report) {
        report.append(String.format("    %-42s %12s %14s%n", "operation", "us/call", "calls/s"));
    }

    /** @return microseconds per call, so callers can build shares out of two rows. */
    private static double row(StringBuilder report, String label, IntToDoubleFunction work) {
        double warm = 0.0;

        for (int index = -WARMUP_SAMPLES; index < 0; index++) {
            warm += work.applyAsDouble(index);
        }

        sink += warm;
        long start = System.nanoTime();
        double accumulated = 0.0;

        for (int index = 0; index < TIMED_SAMPLES; index++) {
            accumulated += work.applyAsDouble(index);
        }

        long elapsed = System.nanoTime() - start;
        sink += accumulated;

        double perCallUs = elapsed / 1000.0 / TIMED_SAMPLES;
        report.append(String.format("    %-42s %12.3f %14.0f%n", label, perCallUs, 1.0e6 / perCallUs));
        return perCallUs;
    }

    /**
     * The pattern every real caller has. A chunk fills sixteen quart columns and generates its terrain from
     * four corner columns, and chunks arrive next to each other, so a sample set that jumps across the world
     * every call measures a machine nobody runs.
     */
    private static int x(int index) {
        int chunk = Math.floorDiv(index, QUARTS_PER_CHUNK);
        return (Math.floorMod(chunk, RASTER_CHUNKS) << 4) + ((Math.floorMod(index, QUARTS_PER_CHUNK) & 3) << 2);
    }

    private static int z(int index) {
        int chunk = Math.floorDiv(index, QUARTS_PER_CHUNK);
        return (Math.floorDiv(chunk, RASTER_CHUNKS) << 4) + ((Math.floorMod(index, QUARTS_PER_CHUNK) >> 2) << 2);
    }

    /** The other extreme: every sample in a different cell, which is what a cache cannot help. */
    private static int scatterX(int index) {
        return (int) Math.floorMod(LatticeHash.mix(index * 0x9E3779B97F4A7C15L), 2L * TIMED_SPAN + 1L) - TIMED_SPAN;
    }

    private static int scatterZ(int index) {
        return (int) Math.floorMod(LatticeHash.mix(index * 0xC2B2AE3D27D4EB4FL + 17L), 2L * TIMED_SPAN + 1L) - TIMED_SPAN;
    }

    private static double sample(DensityFunction function, int blockX, int blockZ) {
        return function.compute(new DensityFunction.SinglePointContext(blockX, 0, blockZ));
    }

    private static void writeReport(String text) {
        String directory = System.getProperty("relict.terrainReportDir");

        if (directory == null) {
            return;
        }

        try {
            Path path = Path.of(directory);
            Files.createDirectories(path);
            Files.writeString(path.resolve("terrain_performance_report.txt"), text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Everything the timings read, built once so no row pays for setup. */
    private record Bench(VoronoiSource mars, NoiseBasedChunkGenerator generator, BiomeSource biomeSource,
                         RandomState state, Climate.Sampler sampler, LevelHeightAccessor height,
                         NoiseGeneratorSettings settings, DensityFunction surfaceY, DensityFunction surfaceHeight,
                         DensityFunction relief, DensityFunction epoch, DensityFunction ridgeShape,
                         DensityFunction duneShape, DensityFunction mesaShape, DensityFunction craterDelta,
                         DensityFunction craterDamp) {

        /** The same build {@code getBaseHeight} makes before it can read a single block of the column. */
        NoiseChunk noiseChunk(int blockX, int blockZ) {
            NoiseSettings noiseSettings = this.settings.noiseSettings().clampToHeightAccessor(this.height);
            int cellWidth = noiseSettings.getCellWidth();
            return new NoiseChunk(1, this.state, Math.floorDiv(blockX, cellWidth) * cellWidth,
                    Math.floorDiv(blockZ, cellWidth) * cellWidth, noiseSettings, NO_BEARDIFIER,
                    this.settings, AIR_EVERYWHERE, Blender.empty());
        }

        static Bench create(HolderLookup.Provider registries) {
            Holder<VoronoiSource> marsHolder = registries.lookupOrThrow(RelictCustomRegistries.VORONOI_SOURCE_REGISTRY)
                    .getOrThrow(RelictVoronoiSources.MARS);
            marsHolder.value().bindSeed(RelictVoronoiSources.MARS.identifier(), SEED);

            Holder<VoronoiSource> undergroundHolder = registries.lookupOrThrow(RelictCustomRegistries.VORONOI_SOURCE_REGISTRY)
                    .getOrThrow(RelictVoronoiSources.MARS_UNDERGROUND);
            undergroundHolder.value().bindSeed(RelictVoronoiSources.MARS_UNDERGROUND.identifier(), UNDERGROUND_SEED);

            LevelStem levelStem = registries.lookupOrThrow(Registries.LEVEL_STEM).getOrThrow(RelictDimension.MARS_LEVELSTEM).value();

            if (!(levelStem.generator() instanceof NoiseBasedChunkGenerator generator)) {
                throw new IllegalStateException("The Mars level stem generator is not noise-based, so nothing here can be timed.");
            }

            NoiseGeneratorSettings settings = registries.lookupOrThrow(Registries.NOISE_SETTINGS)
                    .getOrThrow(RelictDimension.MARS_NOISE_SETTINGS).value();
            RandomState state = RandomState.create(settings, registries.lookupOrThrow(Registries.NOISE), SEED);

            PositionalRandomFactory random = new XoroshiroRandomSource(SEED).forkPositional();
            HolderLookup.RegistryLookup<DensityFunction> functions = registries.lookupOrThrow(Registries.DENSITY_FUNCTION);

            DensityFunction surfaceHeight = seed(holder(functions, RelictDensityFunctionGenerator.VORONOI_SURFACE_HEIGHT), random);
            DensityFunction relief = seed(holder(functions, RelictDensityFunctionGenerator.RELIEF), random);
            DensityFunction epoch = seed(holder(functions, RelictDensityFunctionGenerator.VORONOI_EPOCH), random);

            return new Bench(
                    marsHolder.value(),
                    generator,
                    generator.getBiomeSource(),
                    state,
                    state.sampler(),
                    LevelHeightAccessor.create(settings.noiseSettings().minY(), settings.noiseSettings().height()),
                    settings,
                    RelictNoiseRouter.surfaceY(surfaceHeight, relief, settings.seaLevel()),
                    surfaceHeight,
                    relief,
                    epoch,
                    seed(holder(functions, RelictDensityFunctionGenerator.RIDGE_SHAPE), random),
                    seed(holder(functions, RelictDensityFunctionGenerator.DUNE_SHAPE), random),
                    seed(holder(functions, RelictDensityFunctionGenerator.MESA_SHAPE), random),
                    seed(holder(functions, RelictDensityFunctionGenerator.CRATER_DELTA), random),
                    seed(holder(functions, RelictDensityFunctionGenerator.CRATER_DAMP), random)
            );
        }

        private static DensityFunction holder(HolderLookup.RegistryLookup<DensityFunction> functions, ResourceKey<DensityFunction> key) {
            return new DensityFunctions.HolderHolder(functions.getOrThrow(key));
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
                    Identifier identity = noise.noiseData().unwrapKey().orElseThrow().identifier();
                    RandomSource source = random.fromHashOf(identity);
                    return new DensityFunction.NoiseHolder(noise.noiseData(), NormalNoise.create(source, parameters));
                }
            });
        }
    }

}
