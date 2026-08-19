package us.drullk.relict.datagen.worldgen;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import us.drullk.relict.init.custom.RelictCustomRegistries;
import us.drullk.relict.init.custom.RelictVoronoiSources;
import us.drullk.relict.worldgen.Province;
import us.drullk.relict.worldgen.VoronoiSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Headless proof that the underground biome pick is epoch-biased. Emits no datapack files —
 * writes a plain-text report under {@code prototypes/out/}, mirroring the {@code RidgeFieldSampler} /
 * {@code VoronoiFieldSampler} rig referenced in the voronoi handoff. Seed is injected directly (does not
 * touch {@code ServerLifecycleHooks}), so this runs during {@code runServerData} with no running server.
 */
public class VoronoiFieldSampler implements DataProvider {

    private static final long TEST_SEED = 20260818L;
    private static final int CELL_RADIUS = 60;
    private static final int TELEPORT_Y = 40;

    private final CompletableFuture<HolderLookup.Provider> registries;

    public VoronoiFieldSampler(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        this.registries = registries;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        return this.registries.thenAccept(VoronoiFieldSampler::sample);
    }

    private static void sample(HolderLookup.Provider registries) {
        HolderLookup.RegistryLookup<VoronoiSource> sources = registries.lookupOrThrow(RelictCustomRegistries.VORONOI_SOURCE_REGISTRY);
        Holder<VoronoiSource> holder = sources.getOrThrow(RelictVoronoiSources.MARS_UNDERGROUND);
        VoronoiSource source = holder.value();
        source.bindSeed(holder.unwrapKey().orElseThrow().identifier(), TEST_SEED);

        // bucket: 0 = young epoch (<-0.25), 1 = mid, 2 = old (>0.25)
        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, int[]> nearestCell = new LinkedHashMap<>();
        Map<String, Integer> nearestDistance = new LinkedHashMap<>();

        for (int cellX = -CELL_RADIUS; cellX <= CELL_RADIUS; cellX++) {
            for (int cellZ = -CELL_RADIUS; cellZ <= CELL_RADIUS; cellZ++) {
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
        Map<String, int[]> firstCell = nearestCell;

        StringBuilder report = new StringBuilder();
        report.append("=== underground voronoi field: epoch bias (0.8 T1) ===\n\n");
        report.append("seed ").append(TEST_SEED)
                .append("   cell_size ").append(source.cellSize())
                .append("   epoch_spacing ").append(source.epochSpacing())
                .append("   grid [").append(-CELL_RADIUS).append(", ").append(CELL_RADIUS).append("]\n\n");
        report.append(String.format("%-28s %12s %12s %12s%n", "province", "young(<-.25)", "mid", "old(>.25)"));
        for (Map.Entry<String, int[]> entry : counts.entrySet()) {
            int[] c = entry.getValue();
            report.append(String.format("%-28s %12d %12d %12d%n", entry.getKey(), c[0], c[1], c[2]));
        }

        report.append("\nteleport spots (cell centers, this seed, y=").append(TELEPORT_Y).append("):\n");
        for (Map.Entry<String, int[]> entry : firstCell.entrySet()) {
            int[] cell = entry.getValue();
            double worldX = source.centerX(cell[0], cell[1]);
            double worldZ = source.centerZ(cell[0], cell[1]);
            report.append(String.format("  %-28s cell [%d, %d]  /execute in relict:mars run tp @s %.1f %d %.1f%n",
                    entry.getKey(), cell[0], cell[1], worldX, TELEPORT_Y, worldZ));
        }

        writeReport(report.toString());
    }

    private static void writeReport(String text) {
        String dir = System.getProperty("relict.terrainReportDir");
        if (dir == null) {
            return;
        }

        try {
            Path path = Path.of(dir, "voronoi_underground_report.txt");
            Files.createDirectories(path.getParent());
            Files.writeString(path, text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String getName() {
        return "Relict Underground Voronoi Field Sampler";
    }

}
