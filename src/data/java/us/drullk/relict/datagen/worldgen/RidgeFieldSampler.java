package us.drullk.relict.datagen.worldgen;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import us.drullk.relict.datagen.worldgen.densityfields.RelictRidgeField;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Evaluates the real {@link RelictRidgeField} graph during datagen and reports whether the port landed.
 *
 * <h2>Why this exists</h2>
 * The ridge field was designed in a GLSL prototype and rebuilt as a density function graph. Those are two
 * implementations of one intended shape, and the failure mode is not a crash — it is a shape that is subtly
 * the wrong width, or a plain that stopped being flat, neither of which is visible in the emitted JSON.
 * Answering the question from the game means datagen, a dedicated server, and a region-file parser: about
 * three minutes. This answers it during a datagen run that was happening anyway.
 *
 * <h2>Why a data provider and not a standalone tool</h2>
 * The obvious shape for this is a {@code main} on the data source set, the way {@code runMoonConfig} works.
 * It does not work here. {@code DensityFunctions}' static initializer registers codecs into
 * {@code BuiltInRegistries}, which requires {@code Bootstrap}, which touches {@code SharedConstants}, whose
 * own initializer asks {@code FMLEnvironment} whether it is in production and throws without a loaded FML.
 * Overriding {@code mainClass} on a moddev run does not help — that skips the very setup that installs the
 * loader. Running inside datagen sidesteps all of it, and pays a bonus: the noise holders here are real
 * registry references, so each one can be seeded from its own ID exactly as {@code RandomState} does,
 * instead of the identity bookkeeping a registry-less tool would need to tell six identically-configured
 * noises apart.
 *
 * <h2>What the numbers mean</h2>
 * <ul>
 *   <li><b>noise sd</b> feeds {@code RelictRidgeField}'s {@code MINECRAFT_NOISE_SD}, which reconciles the
 *       two noise implementations' different spreads. It is the one measurement the graph's constants
 *       depend on, so it is reported every run rather than trusted.</li>
 *   <li><b>coverage</b> is the fraction of the plain carrying any ridge. Most of it must not.</li>
 *   <li><b>ridge relief</b> is the greatest rise over a 40-block window — the numeric form of the province's
 *       standing obligation that its ridges be legible at eye level, not only from orbit.</li>
 *   <li><b>plain range</b> is the height spread over a 64-block window away from ridges. The plain has to
 *       stay a plain; that is the entire reason the ridges are masked rather than everywhere.</li>
 * </ul>
 */
public final class RidgeFieldSampler implements DataProvider {

    /** Arbitrary and fixed, so two runs are comparable. Not the seed of any world. */
    private static final long SEED = 0x5EEDL;

    private static final int STEP = 8;
    private static final int GRID = 512;

    /** Matches the grid the prototype's spread was measured on, so the two numbers mean the same thing. */
    private static final double LATTICE_STEP = 0.0917;
    private static final int LATTICE_SAMPLES = 400;

    /** Mirrors the wrinkle_plains province, so the report is in the blocks that province will actually build. */
    private static final double RIDGE_AMPLITUDE = 26.0;
    private static final double PLAIN_ROUGHNESS = 3.0;

    /**
     * Standard deviation of the prototype's plain relief, {@code PLAIN_ROUGH * fbm}, in blocks. Its nominal
     * amplitude of 2.5 is misleading: four halving octaves of a noise that is itself well inside -1..1 put
     * the actual spread far below that, and the visible range at about ±1.5 blocks.
     */
    private static final double PROTOTYPE_PLAIN_SD_BLOCKS = 0.450;

    private final CompletableFuture<HolderLookup.Provider> lookupProvider;

    public RidgeFieldSampler(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        this.lookupProvider = lookupProvider;
    }

    @Override
    public String getName() {
        return "Wrinkle ridge field report";
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        return this.lookupProvider.thenAccept(registries -> {
            HolderLookup.RegistryLookup<NormalNoise.NoiseParameters> noises = registries.lookupOrThrow(Registries.NOISE);
            PositionalRandomFactory random = new XoroshiroRandomSource(SEED).forkPositional();

            DensityFunction shape = seed(RelictRidgeField.shape(noises::getOrThrow), random);
            DensityFunction plain = seed(RelictRidgeField.plain(noises::getOrThrow), random);

            StringBuilder report = new StringBuilder();
            reportNoiseSpread(report, random, noises);

            double[][] shapes = new double[GRID][GRID];
            double[][] heights = new double[GRID][GRID];
            Stats plainStats = new Stats();
            for (int iz = 0; iz < GRID; iz++) {
                for (int ix = 0; ix < GRID; ix++) {
                    double plainValue = sample(plain, ix * STEP, iz * STEP);
                    plainStats.add(plainValue);
                    shapes[iz][ix] = sample(shape, ix * STEP, iz * STEP);
                    heights[iz][ix] = RIDGE_AMPLITUDE * shapes[iz][ix] + PLAIN_ROUGHNESS * plainValue;
                }
            }

            // The plain's amplitude is a province field in blocks, so it needs the multi-octave field's own
            // spread to be set rather than guessed — a different number from the single-octave one above,
            // because PerlinNoise sums four octaves and NormalNoise renormalizes the result.
            report.append(String.format("%nplain noise    sd %.4f   range %+.4f..%+.4f%n",
                    plainStats.sd(), plainStats.min, plainStats.max));
            report.append(String.format("  -> for the prototype's 0.450-block plain sd, plain_roughness = %.2f%n",
                    PROTOTYPE_PLAIN_SD_BLOCKS / plainStats.sd()));

            reportShape(report, shapes, heights);
            reportProfile(report, shape, plain);

            Path directory = reportDirectory();
            if (directory != null) {
                try {
                    Files.createDirectories(directory);
                    Path hillshade = directory.resolve("ridge_hillshade.pgm");
                    writeHillshade(hillshade, heights);
                    report.append(String.format("%nhillshade -> %s  (%d blocks across, %d per pixel)%n",
                            hillshade.toAbsolutePath(), GRID * STEP, STEP));
                    Files.writeString(directory.resolve("ridge_report.txt"), report.toString());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            System.out.print(report);
        });
    }

    private static Path reportDirectory() {
        String dir = System.getProperty("relict.terrainReportDir");
        return dir == null ? null : Path.of(dir);
    }

    // ---------------------------------------------------------------------------------------- reports

    /**
     * The spread of a single-octave {@code minecraft:noise}, in lattice units, on the same off-lattice grid
     * the prototype's was measured on. Off-lattice matters: Perlin noise is exactly zero at every lattice
     * point, so a grid landing on integers would report a spread far below the truth.
     */
    private static void reportNoiseSpread(StringBuilder report, PositionalRandomFactory random,
                                          HolderLookup.RegistryLookup<NormalNoise.NoiseParameters> noises) {
        var key = us.drullk.relict.init.worldgen.RelictNoises.RIDGE;
        NormalNoise single = NormalNoise.create(random.fromHashOf(key.identifier()), noises.getOrThrow(key).value());

        Stats stats = new Stats();
        for (int i = 0; i < LATTICE_SAMPLES; i++) {
            for (int j = 0; j < LATTICE_SAMPLES; j++) {
                stats.add(single.getValue(i * LATTICE_STEP + 0.031, 0.0, j * LATTICE_STEP + 0.017));
            }
        }

        report.append(String.format("%n=== wrinkle ridge field ===%n%n"));
        report.append(String.format("single-octave minecraft:noise   sd %.4f   range %+.4f..%+.4f%n",
                stats.sd(), stats.min, stats.max));
        report.append(String.format("prototype gnoise, for reference sd 0.3104   range -0.9456..+0.9656%n"));
        report.append(String.format("  -> RelictRidgeField.MINECRAFT_NOISE_SD should read %.4f%n", stats.sd()));
    }

    private static void reportShape(StringBuilder report, double[][] shapes, double[][] heights) {
        Stats shapeStats = new Stats();
        int covered = 0;
        for (double[] row : shapes) {
            for (double v : row) {
                shapeStats.add(v);
                if (v > 0.05) {
                    covered++;
                }
            }
        }

        report.append(String.format("%nridge shape    range %.4f..%.4f   mean %.4f%n",
                shapeStats.min, shapeStats.max, shapeStats.mean()));
        report.append(String.format("coverage       %.1f%% of the plain carries any ridge%n",
                100.0 * covered / (GRID * GRID)));

        int window = Math.max(1, 40 / STEP);
        double bestRise = 0.0;
        for (int a = 0; a < GRID; a++) {
            for (int b = 0; b + window < GRID; b++) {
                bestRise = Math.max(bestRise, Math.abs(heights[a][b + window] - heights[a][b]));
                bestRise = Math.max(bestRise, Math.abs(heights[b + window][a] - heights[b][a]));
            }
        }
        report.append(String.format("ridge relief   %.1f blocks over 40, at the steepest scarp%n", bestRise));

        int flat = Math.max(1, 64 / STEP);
        double worst = 0.0;
        for (int iz = 0; iz < GRID; iz++) {
            for (int ix = 0; ix + flat < GRID; ix++) {
                double lo = Double.MAX_VALUE;
                double hi = -Double.MAX_VALUE;
                boolean ridged = false;
                for (int k = 0; k <= flat; k++) {
                    ridged |= shapes[iz][ix + k] > 0.05;
                    lo = Math.min(lo, heights[iz][ix + k]);
                    hi = Math.max(hi, heights[iz][ix + k]);
                }
                if (!ridged) {
                    worst = Math.max(worst, hi - lo);
                }
            }
        }
        report.append(String.format("plain range    %.1f blocks over 64, away from ridges%n", worst));
    }

    /** A cross-section through the steepest scarp, so the asymmetry can be read off directly. */
    private static void reportProfile(StringBuilder report, DensityFunction shape, DensityFunction plain) {
        int bestX = 0;
        int bestZ = 0;
        double bestDrop = 0.0;
        for (int z = 0; z < GRID * STEP; z += STEP) {
            for (int x = 0; x + 16 < GRID * STEP; x += STEP) {
                double drop = sample(shape, x, z) - sample(shape, x + 16, z);
                if (drop > bestDrop) {
                    bestDrop = drop;
                    bestX = x;
                    bestZ = z;
                }
            }
        }

        report.append(String.format("%ncross-section through the steepest scarp, x=%d z=%d, one row per 8 blocks:%n",
                bestX, bestZ));
        for (int d = -168; d <= 96; d += 8) {
            double h = RIDGE_AMPLITUDE * sample(shape, bestX + d, bestZ)
                    + PLAIN_ROUGHNESS * sample(plain, bestX + d, bestZ);
            report.append(String.format("  %+5d  %6.2f  %s%n", d, h, "#".repeat(Math.max(0, (int) Math.round(h)))));
        }
    }

    // ---------------------------------------------------------------------------------------- plumbing

    private static double sample(DensityFunction function, int x, int z) {
        return function.compute(new DensityFunction.SinglePointContext(x, 0, z));
    }

    /**
     * Instantiates every noise in the graph against {@link #SEED}, the way {@code RandomState} does — which
     * is the only way a density function ever sees a seed at all. Datagen's own holders are unseeded, so
     * without this pass every noise reads as a flat zero and the whole field vanishes.
     */
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

    private static final class Stats {
        private double min = Double.MAX_VALUE;
        private double max = -Double.MAX_VALUE;
        private double sum;
        private double sumSquares;
        private int count;

        void add(double v) {
            this.min = Math.min(this.min, v);
            this.max = Math.max(this.max, v);
            this.sum += v;
            this.sumSquares += v * v;
            this.count++;
        }

        double mean() {
            return this.sum / this.count;
        }

        double sd() {
            double mean = mean();
            return Math.sqrt(this.sumSquares / this.count - mean * mean);
        }
    }

    /** Lambert relief from a fixed low sun, as a binary PGM — no image library, and it opens in anything. */
    private static void writeHillshade(Path path, double[][] heights) throws IOException {
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(path))) {
            out.write(("P5\n" + GRID + " " + GRID + "\n255\n").getBytes(StandardCharsets.US_ASCII));
            for (int z = 0; z < GRID; z++) {
                for (int x = 0; x < GRID; x++) {
                    double dx = heights[z][Math.min(x + 1, GRID - 1)] - heights[z][Math.max(x - 1, 0)];
                    double dz = heights[Math.min(z + 1, GRID - 1)][x] - heights[Math.max(z - 1, 0)][x];

                    // Surface normal, then Lambert against a sun 30 degrees up in the northwest — the low
                    // angle the orbital reference is lit at, and the reason its ridges read at all. A sun
                    // overhead would wash this landform out completely: the relief here is tens of blocks
                    // over hundreds, so it is only ever legible by its shadows.
                    double nx = -dx / (2.0 * STEP);
                    double nz = -dz / (2.0 * STEP);
                    double length = Math.sqrt(nx * nx + 1.0 + nz * nz);
                    double lambert = (nx * -0.612 + 0.5 + nz * -0.612) / length;
                    out.write((int) Math.round(255.0 * Math.clamp(lambert * 1.9, 0.0, 1.0)));
                }
            }
        }
    }

}
