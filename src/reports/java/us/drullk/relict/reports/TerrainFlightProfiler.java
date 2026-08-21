package us.drullk.relict.reports;

import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.core.HolderLookup;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Records JDK Flight Recorder captures of the two terrain routes that cost real time, so the attribution
 * tables in {@link TerrainPerformanceSampler} can be checked against a stack-sampled profile rather than
 * trusted on their own.
 *
 * <p>Two captures, each a fixed-seed fixed-batch workload run until its wall-clock budget is spent:
 * <ul>
 *   <li>{@code chunkgen.jfr} — whole-column block states, the raster batch section (F) times.</li>
 *   <li>{@code locate.jfr} — the ring-scan candidate cost, the height probe plus the biome test that
 *       section (D) projects over a full radius-100 scan.</li>
 * </ul>
 *
 * <p>Not part of the checked suite: it writes no numbers a build can fail on. Select it with
 * {@code -Drelict.reports=profile}.
 */
public final class TerrainFlightProfiler implements DataProvider {

    /** Long enough that a 1 ms execution sampler collects tens of thousands of stacks per capture. */
    private static final Duration CAPTURE_BUDGET = Duration.ofSeconds(25);

    private static final Duration WARMUP_BUDGET = Duration.ofSeconds(8);

    private static final Duration SAMPLE_PERIOD = Duration.ofMillis(1);

    private static volatile double sink;

    private final CompletableFuture<HolderLookup.Provider> registries;

    public TerrainFlightProfiler(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        this.registries = registries;
    }

    @Override
    public String getName() {
        return "Relict Terrain Flight Profiler";
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        return this.registries.thenAccept(registries -> {
            TerrainPerformanceSampler.Bench bench = TerrainPerformanceSampler.Bench.create(registries);
            Path directory = outputDirectory();

            capture(directory.resolve("chunkgen.jfr"), () -> columnBatch(bench));
            capture(directory.resolve("locate.jfr"), () -> candidateBatch(bench));
        });
    }

    private static void columnBatch(TerrainPerformanceSampler.Bench bench) {
        for (int index = 0; index < 256; index++) {
            sink += bench.generator()
                    .getBaseColumn(TerrainPerformanceSampler.x(index), TerrainPerformanceSampler.z(index), bench.height(), bench.state())
                    .getBlock(128)
                    .hashCode();
        }
    }

    private static void candidateBatch(TerrainPerformanceSampler.Bench bench) {
        for (int[] candidate : TerrainPerformanceSampler.ringCandidates()) {
            sink += TerrainPerformanceSampler.candidateCost(bench, candidate[0], candidate[1]);
        }
    }

    private static void capture(Path file, Runnable batch) {
        runFor(batch, WARMUP_BUDGET);

        try (Recording recording = new Recording(Configuration.getConfiguration("profile"))) {
            recording.enable("jdk.ExecutionSample").withPeriod(SAMPLE_PERIOD);
            recording.enable("jdk.NativeMethodSample").withPeriod(SAMPLE_PERIOD);
            recording.setDestination(file);
            recording.setToDisk(true);
            recording.start();

            long batches = runFor(batch, CAPTURE_BUDGET);

            recording.stop();
            System.out.printf("    flight recording %s: %d batches over %d s%n",
                    file.getFileName(), batches, CAPTURE_BUDGET.toSeconds());
        } catch (IOException | ParseException e) {
            throw new IllegalStateException("Flight recording " + file + " failed", e);
        }
    }

    private static long runFor(Runnable batch, Duration budget) {
        long deadline = System.nanoTime() + budget.toNanos();
        long batches = 0L;

        while (System.nanoTime() < deadline) {
            batch.run();
            batches++;
        }

        return batches;
    }

    private static Path outputDirectory() {
        String directory = System.getProperty("relict.jfrDir", System.getProperty("relict.terrainReportDir"));

        if (directory == null) {
            throw new IllegalStateException("Set -Drelict.jfrDir (or relict.terrainReportDir) to say where the captures go");
        }

        try {
            Path path = Path.of(directory);
            Files.createDirectories(path);
            return path;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
