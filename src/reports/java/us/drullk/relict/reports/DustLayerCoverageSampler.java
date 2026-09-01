package us.drullk.relict.reports;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import us.drullk.relict.datagen.worldgen.RelictDensityFunctionGenerator;
import us.drullk.relict.datagen.worldgen.DustLayerFeatureGenerator;
import us.drullk.relict.datagen.worldgen.RelictNoiseRouter;
import us.drullk.relict.init.custom.RelictCustomRegistries;
import us.drullk.relict.init.custom.RelictVoronoiSources;
import us.drullk.relict.init.worldgen.RelictDimension;
import us.drullk.relict.worldgen.DuneCrest;
import us.drullk.relict.worldgen.DustLayerFeatureConfiguration;
import us.drullk.relict.worldgen.LatticeHash;
import us.drullk.relict.worldgen.VoronoiSource;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Eyes-on coverage plates for {@code relict:dust_layer}'s worldgen baseline, in the same top-down
 * synthetic-palette style the rest of the reports module already uses for terrain verification: one plate per
 * province, colored by the exact roll {@link us.drullk.relict.worldgen.DustLayerFeature}
 * makes (crest gate, coverage-cell hash, depth), read straight off {@link DustLayerFeatureGenerator}'s
 * registered configs so a plate can never drift from what the feature actually places. Height for the dune
 * crest test comes from the same {@code surfaceY} density-function proxy the rest of the reports module
 * already treats as the world's surface (see {@code TerrainPerformanceSampler}'s golden hash, which samples
 * the identical function).
 */
public final class DustLayerCoverageSampler implements DataProvider {

    private static final long SEED = 0x5EEDL;

    private static final int WINDOW = 2000;
    private static final int STEP = 2;

    private final CompletableFuture<HolderLookup.Provider> registries;

    public DustLayerCoverageSampler(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        this.registries = registries;
    }

    @Override
    public String getName() {
        return "Relict Dust Layer Coverage Sampler";
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        return this.registries.thenAccept(registries -> {
            StringBuilder report = new StringBuilder("\n=== dust layer coverage plates ===\n");
            Bench bench = Bench.create(registries);

            plate(report, bench, "wrinkle_plains", DustLayerFeatureGenerator.WRINKLE_PLAINS_CONFIG, 0, 0);
            plate(report, bench, "rusted_dunes", DustLayerFeatureGenerator.RUSTED_DUNES_CONFIG, 40000, -25000);
            plate(report, bench, "fretted_mesas", DustLayerFeatureGenerator.FRETTED_MESAS_CONFIG, -31000, 47000);

            writeReport(report.toString());
            System.out.print(report);
        });
    }

    private static void plate(StringBuilder report, Bench bench, String name, DustLayerFeatureConfiguration config, int centreX, int centreZ) {
        int side = WINDOW / STEP;
        int[][][] rgb = new int[side][side][3];
        long covered = 0;
        long depthSum = 0;

        for (int pz = 0; pz < side; pz++) {
            int z = centreZ - WINDOW / 2 + pz * STEP;
            for (int px = 0; px < side; px++) {
                int x = centreX - WINDOW / 2 + px * STEP;

                boolean crestOk = !config.requireDuneCrest() || isDuneCrest(bench, x, z);
                boolean hit = crestOk && coverageHit(config, x, z);
                int depth = hit ? rollDepth(config, x, z) : 0;

                rgb[pz][px] = pixel(config.requireDuneCrest(), crestOk, depth, config.maxLayers());

                if (depth > 0) {
                    covered++;
                    depthSum += depth;
                }
            }
        }

        String fileName = "2.9-" + name.replace('_', '-') + "-coverage.ppm";
        Path directory = Path.of(System.getProperty("relict.terrainReportDir", "prototypes/out"));
        try {
            Files.createDirectories(directory);
            writeColor(directory.resolve(fileName), rgb);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        double coveragePct = 100.0 * covered / ((double) side * side);
        double meanDepth = covered == 0 ? 0.0 : (double) depthSum / covered;
        report.append(String.format("    %-16s window %d blocks at x=%d z=%d, %d blocks/pixel -> %s%n",
                name, WINDOW, centreX, centreZ, STEP, directory.resolve(fileName).toAbsolutePath()));
        report.append(String.format("    %-16s coverage %.1f%% of pixels, mean depth where placed %.2f layers (config min=%d max=%d chance=%.2f cellSize=%d crestOnly=%s)%n",
                "", coveragePct, meanDepth, config.minLayers(), config.maxLayers(), config.coverageChance(), config.patchCellSize(), config.requireDuneCrest()));
    }

    /** [VANILLACOPY, deterministic re-derivation] the exact roll {@code DustLayerFeature.rollDepth} makes, reseeded per column so the plate is reproducible without a live RandomSource. */
    private static int rollDepth(DustLayerFeatureConfiguration config, int x, int z) {
        int min = config.minLayers();
        int max = config.maxLayers();
        if (min >= max) {
            return min;
        }
        long hash = LatticeHash.hash(config.coverageSalt(), x, z, 0x4445_5054_484C4CL);
        return min + (int) (LatticeHash.unitInterval(hash) * (max - min + 1));
    }

    /** [VANILLACOPY] {@code DustLayerFeature.coverageHit}, identical math, so the plate cannot drift from the real feature. */
    private static boolean coverageHit(DustLayerFeatureConfiguration config, int x, int z) {
        int cellX = Math.floorDiv(x, config.patchCellSize());
        int cellZ = Math.floorDiv(z, config.patchCellSize());
        long hash = LatticeHash.hash(config.coverageSalt(), cellX, cellZ, 0x445553545F434F56L);
        return LatticeHash.unitInterval(hash) < config.coverageChance();
    }

    private static boolean isDuneCrest(Bench bench, int x, int z) {
        return DuneCrest.isCrest((offsetX, offsetZ) -> Mth.floor(sample(bench.surfaceY(), x + offsetX, z + offsetZ)));
    }

    private static double sample(DensityFunction function, int blockX, int blockZ) {
        return function.compute(new DensityFunction.SinglePointContext(blockX, 0, blockZ));
    }

    private static int[] pixel(boolean crestGated, boolean crestOk, int depth, int maxLayers) {
        if (crestGated && !crestOk) {
            return new int[]{40, 30, 20}; // off-crest: excluded entirely, dark basalt read
        }
        if (depth == 0) {
            return new int[]{90, 60, 40}; // eligible but the coverage roll missed: bare ground
        }
        int t = maxLayers <= 1 ? 255 : 120 + (135 * depth) / maxLayers;
        return new int[]{Math.min(255, t + 40), Math.min(255, (int) (t * 0.75)), Math.min(255, (int) (t * 0.45))};
    }

    private static void writeColor(Path path, int[][][] rgb) throws IOException {
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(path))) {
            out.write(("P6\n" + rgb[0].length + " " + rgb.length + "\n255\n").getBytes(StandardCharsets.US_ASCII));
            for (int[][] row : rgb) {
                for (int[] p : row) {
                    out.write(Math.clamp(p[0], 0, 255));
                    out.write(Math.clamp(p[1], 0, 255));
                    out.write(Math.clamp(p[2], 0, 255));
                }
            }
        }
    }

    private static void writeReport(String text) {
        String directory = System.getProperty("relict.terrainReportDir");
        if (directory == null) {
            return;
        }
        try {
            Path path = Path.of(directory);
            Files.createDirectories(path);
            Files.writeString(path.resolve("dust_layer_coverage_report.txt"), text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Just enough of {@code TerrainPerformanceSampler.Bench} to read {@code surfaceY} for the crest test. */
    record Bench(DensityFunction surfaceY) {

        static Bench create(HolderLookup.Provider registries) {
            Holder<VoronoiSource> marsHolder = registries.lookupOrThrow(RelictCustomRegistries.VORONOI_SOURCE_REGISTRY).getOrThrow(RelictVoronoiSources.MARS);
            marsHolder.value().bindSeed(RelictVoronoiSources.MARS.identifier(), SEED);

            LevelStem levelStem = registries.lookupOrThrow(Registries.LEVEL_STEM).getOrThrow(RelictDimension.MARS_LEVELSTEM).value();
            if (!(levelStem.generator() instanceof NoiseBasedChunkGenerator)) {
                throw new IllegalStateException("The Mars level stem generator is not noise-based, so nothing here can be timed.");
            }

            NoiseGeneratorSettings settings = registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(RelictDimension.MARS_NOISE_SETTINGS).value();
            RandomState state = RandomState.create(settings, registries.lookupOrThrow(Registries.NOISE), SEED);
            PositionalRandomFactory random = new XoroshiroRandomSource(SEED).forkPositional();
            HolderLookup.RegistryLookup<DensityFunction> functions = registries.lookupOrThrow(Registries.DENSITY_FUNCTION);

            DensityFunction surfaceHeight = seed(holder(functions, RelictDensityFunctionGenerator.VORONOI_SURFACE_HEIGHT), random);
            DensityFunction relief = seed(holder(functions, RelictDensityFunctionGenerator.RELIEF), random);

            return new Bench(RelictNoiseRouter.surfaceY(surfaceHeight, relief, settings.seaLevel()));
        }

        private static DensityFunction holder(HolderLookup.RegistryLookup<DensityFunction> functions, ResourceKey<DensityFunction> key) {
            return new DensityFunctions.HolderHolder(functions.getOrThrow(key));
        }

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
