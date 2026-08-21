package us.drullk.relict.reports;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import us.drullk.relict.Relict;
import us.drullk.relict.datagen.RelictDatagen;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * The terrain report samplers' own entrypoint, split out of {@link RelictDatagen} so a report run pays
 * only for the registry graph plus whichever samplers it asked for — not the tag/loot/advancement/
 * registry-JSON providers {@code serverData} exists for.
 *
 * <p>Reuses {@link RelictDatagen#datapackRegistryEntries()} by reference rather than keeping a second copy,
 * so the report samplers' registry graph cannot drift from the real datagen's.
 */
@EventBusSubscriber(modid = Relict.MODID)
public final class RelictReports {

    @SubscribeEvent
    public static void generateData(GatherDataEvent.Server event) {
        PackOutput output = event.getGenerator().getPackOutput();
        DatapackBuiltinEntriesProvider builtinDatapack = event.addProvider(new DatapackBuiltinEntriesProvider(
                output, event.getLookupProvider(), RelictDatagen.datapackRegistryEntries(), Set.of(Relict.MODID)));
        CompletableFuture<HolderLookup.Provider> registries = builtinDatapack.getRegistryProvider();

        for (String name : selectedReports()) {
            switch (name) {
                case "ridge" -> event.addProvider(new RidgeFieldSampler(output, registries));
                case "voronoi" -> event.addProvider(new VoronoiFieldSampler(output, registries));
                case "performance" -> event.addProvider(new TerrainPerformanceSampler(output, registries));
                default -> throw new IllegalArgumentException("Unknown relict.reports entry: " + name
                        + " (expected one of: ridge, voronoi, performance)");
            }
        }
    }

    /** Comma-separated report names via {@code -Drelict.reports=...}; every report if unset. */
    private static List<String> selectedReports() {
        return List.of(System.getProperty("relict.reports", "ridge,voronoi,performance").split(","));
    }

}
