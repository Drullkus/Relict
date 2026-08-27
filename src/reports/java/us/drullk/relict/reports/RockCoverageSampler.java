package us.drullk.relict.reports;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.resources.Identifier;
import us.drullk.relict.datagen.worldgen.RockFeatureGenerator;
import us.drullk.relict.datagen.worldgen.RelictDensityFunctionGenerator;
import us.drullk.relict.datagen.worldgen.RelictNoiseRouter;
import us.drullk.relict.init.custom.RelictCustomRegistries;
import us.drullk.relict.init.custom.RelictVoronoiSources;
import us.drullk.relict.init.worldgen.RelictDimension;
import us.drullk.relict.worldgen.DuneCrest;
import us.drullk.relict.worldgen.RockFeature;
import us.drullk.relict.worldgen.RockFeatureConfiguration.PlacementRule;
import us.drullk.relict.worldgen.LatticeHash;
import us.drullk.relict.worldgen.VoronoiSource;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Eyes-on coverage plates and machine coverage numbers for {@code relict:rock}, in the same top-down
 * synthetic-palette style {@link DustLayerCoverageSampler} already uses. Coverage is defined the same way
 * every other landform metric in this module is stated: the fraction of columns with a placed rock within
 * an 8-block radius.
 *
 * <p>The real placement chain (count/rarity + {@code InSquarePlacement} + heightmap + biome filter) runs
 * inside live chunk generation this report never touches, so this re-derives it statistically: one local,
 * deterministic {@link RandomSource} per chunk rolls the same shape of attempt the real placement would
 * (an attempt count, then a random column in the chunk), and each attempt is graded by {@link
 * RockFeature#passesPlacementRule}, the exact predicate the real feature runs, off the same {@code
 * surfaceY} density-function proxy for the heightmap {@link DustLayerCoverageSampler}'s crest test already
 * uses. This does not reproduce vanilla's own decoration RNG bit-for-bit, so absolute placement positions
 * will not match a live world; the coverage percentage it converges on does, because both processes draw
 * the same number of uniformly-placed attempts per chunk and grade them with the same gate.
 */
public final class RockCoverageSampler implements DataProvider {

    private static final long SEED = 0x5EEDL;

    private static final int WINDOW = 2000;
    private static final int STEP = 2;
    private static final int COVERAGE_RADIUS = 8;

    /** Column-attempt salts, one per placement so the per-chunk local randoms don't correlate. */
    private static final long PLAINS_S_SALT = 0x0FA1_0001L;
    private static final long PLAINS_RIDGE_M_SALT = 0x0FA1_0002L;
    private static final long PLAINS_EJECTA_L_SALT = 0x0FA1_0003L;
    private static final long DUNES_S_SALT = 0x0FA1_0004L;
    private static final long MESAS_TALUS_S_SALT = 0x0FA1_0005L;
    private static final long MESAS_TALUS_M_SALT = 0x0FA1_0006L;
    private static final long MESAS_CAP_S_SALT = 0x0FA1_0007L;
    private static final long MESAS_FLOOR_S_SALT = 0x0FA1_0008L;

    private final CompletableFuture<HolderLookup.Provider> registries;

    public RockCoverageSampler(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        this.registries = registries;
    }

    @Override
    public String getName() {
        return "Relict Rock Coverage Sampler";
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        return this.registries.thenAccept(registries -> {
            StringBuilder report = new StringBuilder("\n=== rock coverage plates ===\n");
            List<String> failures = new ArrayList<>();
            Bench bench = Bench.create(registries);

            plate(report, bench, "wrinkle_plains_s", 0, 0, RockFeatureGenerator.PLAINS_S_COUNT, 0, PlacementRule.ANY, PLAINS_S_SALT);
            plate(report, bench, "wrinkle_plains_ridge_m", 0, 0, RockFeatureGenerator.PLAINS_RIDGE_M_COUNT, 0, PlacementRule.RIDGE_BIAS, PLAINS_RIDGE_M_SALT);
            plate(report, bench, "wrinkle_plains_ejecta_l", 0, 0, null, RockFeatureGenerator.PLAINS_EJECTA_L_RARITY, PlacementRule.ANY, PLAINS_EJECTA_L_SALT);

            plate(report, bench, "rusted_dunes_s", 40000, -25000, null, RockFeatureGenerator.DUNES_S_RARITY, PlacementRule.INTERDUNE_FLOOR, DUNES_S_SALT);

            plate(report, bench, "fretted_mesas_talus_s", -31000, 47000, RockFeatureGenerator.MESAS_TALUS_S_COUNT, 0, PlacementRule.TALUS, MESAS_TALUS_S_SALT);
            plate(report, bench, "fretted_mesas_talus_m", -31000, 47000, RockFeatureGenerator.MESAS_TALUS_M_COUNT, 0, PlacementRule.TALUS, MESAS_TALUS_M_SALT);
            plate(report, bench, "fretted_mesas_cap_s", -31000, 47000, null, RockFeatureGenerator.MESAS_CAP_S_RARITY, PlacementRule.CAP, MESAS_CAP_S_SALT);
            plate(report, bench, "fretted_mesas_floor_s", -31000, 47000, RockFeatureGenerator.MESAS_FLOOR_S_COUNT, 0, PlacementRule.VALLEY_FLOOR, MESAS_FLOOR_S_SALT);

            assertNoCrestRocks(report, failures, bench, 40000, -25000);

            writeReport(report.toString());
            System.out.print(report);

            if (!failures.isEmpty()) {
                throw new IllegalStateException("Rock coverage checks failed:\n  " + String.join("\n  ", failures));
            }
        });
    }

    /**
     * One placement's coverage plate: a chunk-grid Monte Carlo of the real placement's attempt count and
     * gate, marking every column within {@link #COVERAGE_RADIUS} of a passing attempt as "covered" — this
     * class's own definition of coverage, stated above.
     */
    private static void plate(StringBuilder report, Bench bench, String name, int centreX, int centreZ,
            IntProvider countProvider, int rarityDenominator, PlacementRule rule, long salt) {
        int side = WINDOW / STEP;
        int originX = centreX - WINDOW / 2;
        int originZ = centreZ - WINDOW / 2;
        boolean[][] covered = new boolean[side][side];
        List<int[]> hits = new ArrayList<>();

        int chunks = WINDOW / 16;
        for (int cz = 0; cz < chunks; cz++) {
            int chunkZ = Math.floorDiv(originZ, 16) + cz;
            for (int cx = 0; cx < chunks; cx++) {
                int chunkX = Math.floorDiv(originX, 16) + cx;

                RandomSource random = RandomSource.create(LatticeHash.hash(salt, chunkX, chunkZ, 0x464C4F41545F524BL));
                // [VANILLACOPY, pattern] RarityFilter's own "1 attempt with 1/N odds" roll -- same shape as
                // CountPlacement's own sample() call otherwise, so this mirrors the real placement chain's
                // attempt count exactly rather than approximating it.
                int attempts = countProvider != null ? countProvider.sample(random) : (random.nextInt(rarityDenominator) == 0 ? 1 : 0);

                for (int i = 0; i < attempts; i++) {
                    int x = chunkX * 16 + random.nextInt(16);
                    int z = chunkZ * 16 + random.nextInt(16);

                    if (RockFeature.passesPlacementRule(rule, bench.heightAt(x, z))) {
                        hits.add(new int[]{x, z});
                    }
                }
            }
        }

        for (int[] hit : hits) {
            markCovered(covered, hit[0] - originX, hit[1] - originZ, side);
        }

        long coveredCells = 0;
        for (boolean[] row : covered) {
            for (boolean cell : row) {
                if (cell) {
                    coveredCells++;
                }
            }
        }

        writePlate(name, covered);

        double coveragePct = 100.0 * coveredCells / ((double) side * side);
        double rocksPerChunk = hits.size() / (double) (chunks * chunks);
        report.append(String.format("    %-24s window %d blocks at x=%d z=%d -> %d rocks placed (%.3f/chunk), coverage %.1f%% within %d blocks%n",
                name, WINDOW, centreX, centreZ, hits.size(), rocksPerChunk, coveragePct, COVERAGE_RADIUS));
    }

    private static void markCovered(boolean[][] covered, int gridX, int gridZ, int side) {
        int radiusCells = COVERAGE_RADIUS / STEP;
        int centerGridX = gridX / STEP;
        int centerGridZ = gridZ / STEP;
        for (int dz = -radiusCells; dz <= radiusCells; dz++) {
            int pz = centerGridZ + dz;
            if (pz < 0 || pz >= side) {
                continue;
            }
            for (int dx = -radiusCells; dx <= radiusCells; dx++) {
                int px = centerGridX + dx;
                if (px < 0 || px >= side) {
                    continue;
                }
                if (dx * dx + dz * dz <= radiusCells * radiusCells) {
                    covered[pz][px] = true;
                }
            }
        }
    }

    /** Build-failing: rusted_dunes crest columns must never pass INTERDUNE_FLOOR -- assert it, don't eyeball it. */
    private static void assertNoCrestRocks(StringBuilder report, List<String> failures, Bench bench, int centreX, int centreZ) {
        int checked = 0;
        int crestColumns = 0;
        int crestPasses = 0;

        for (int z = centreZ - WINDOW / 2; z < centreZ + WINDOW / 2; z += STEP) {
            for (int x = centreX - WINDOW / 2; x < centreX + WINDOW / 2; x += STEP) {
                checked++;
                DuneCrest.RelativeHeight height = bench.heightAt(x, z);
                if (DuneCrest.isCrest(height)) {
                    crestColumns++;
                    if (RockFeature.passesPlacementRule(PlacementRule.INTERDUNE_FLOOR, height)) {
                        crestPasses++;
                    }
                }
            }
        }

        report.append(String.format("%n    crest columns rock-free: %d crest columns found (of %d checked), %d passed INTERDUNE_FLOOR   %s%n",
                crestColumns, checked, crestPasses, crestPasses == 0 ? "PASS" : "FAIL"));

        if (crestPasses != 0) {
            failures.add(String.format("rusted_dunes: %d/%d crest columns passed the INTERDUNE_FLOOR gate -- crests must stay rock-free", crestPasses, crestColumns));
        }
    }

    private static void writePlate(String name, boolean[][] covered) {
        int[][][] rgb = new int[covered.length][covered[0].length][3];
        for (int z = 0; z < covered.length; z++) {
            for (int x = 0; x < covered[z].length; x++) {
                rgb[z][x] = covered[z][x] ? new int[]{190, 130, 80} : new int[]{40, 30, 20};
            }
        }

        String fileName = "rock-" + name.replace('_', '-') + "-coverage.ppm";
        Path directory = Path.of(System.getProperty("relict.terrainReportDir", "prototypes/out"));
        try {
            Files.createDirectories(directory);
            writeColor(directory.resolve(fileName), rgb);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
            Files.writeString(path.resolve("rock_coverage_report.txt"), text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Just enough of {@code TerrainPerformanceSampler.Bench} to read {@code surfaceY} for the placement-rule gate. */
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

        DuneCrest.RelativeHeight heightAt(int x, int z) {
            return (offsetX, offsetZ) -> Mth.floor(sample(this.surfaceY, x + offsetX, z + offsetZ));
        }

        private static double sample(DensityFunction function, int blockX, int blockZ) {
            return function.compute(new DensityFunction.SinglePointContext(blockX, 0, blockZ));
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
