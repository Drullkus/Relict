package us.drullk.relict.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import us.drullk.relict.Relict;
import us.drullk.relict.datagen.celestial.RelictCelestialSprites;
import us.drullk.relict.datagen.loottables.RelictLootTables;
import us.drullk.relict.datagen.tags.RelictBiomeTags;
import us.drullk.relict.datagen.tags.RelictBlockTags;
import us.drullk.relict.datagen.tags.RelictDamageTypeTags;
import us.drullk.relict.datagen.tags.RelictDimensionTypeTags;
import us.drullk.relict.datagen.tags.RelictItemTags;
import us.drullk.relict.datagen.tags.RelictStructureTags;
import us.drullk.relict.datagen.tags.RelictTimelineTags;
import us.drullk.relict.datagen.worldgen.*;
import us.drullk.relict.init.custom.RelictCustomRegistries;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid = Relict.MODID)
public class RelictDatagen {

    @SubscribeEvent
    public static void generateData(GatherDataEvent.Server event) {
        if (Boolean.getBoolean("relict.reportsOnly")) {
            return;
        }

        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();

        DatapackBuiltinEntriesProvider builtinDatapack = event.addProvider(new DatapackBuiltinEntriesProvider(output, lookupProvider, datapackRegistryEntries(), Set.of(Relict.MODID)));
        CompletableFuture<HolderLookup.Provider> builtinDatapackProvider = builtinDatapack.getRegistryProvider();

        event.addProvider(new RelictBiomeTags(output, builtinDatapackProvider));
        event.addProvider(new RelictBlockTags(output, builtinDatapackProvider));
        event.addProvider(new RelictDamageTypeTags(output, builtinDatapackProvider));
        event.addProvider(new RelictDimensionTypeTags(output, builtinDatapackProvider));
        event.addProvider(new RelictItemTags(output, builtinDatapackProvider));
        event.addProvider(new RelictStructureTags(output, builtinDatapackProvider));
        event.addProvider(new RelictTimelineTags(output, builtinDatapackProvider));

        event.addProvider(new RelictAdvancements(output, builtinDatapackProvider));
        event.addProvider(new RelictLootTables(output, builtinDatapackProvider));
    }

    public static RegistrySetBuilder datapackRegistryEntries() {
        RelictDimensionGenerator dimensionGenerator = new RelictDimensionGenerator(-64, 384, 128);
        RelictTimelineGenerator timelineGenerator = new RelictTimelineGenerator(22);

        return new RegistrySetBuilder()
                .add(Registries.BIOME, RelictBiomeGenerator::bootstrapBiomes)
                .add(Registries.CONFIGURED_CARVER, RelictCarverGenerator::bootstrapCarvers)
                .add(Registries.CONFIGURED_FEATURE, RelictConfiguredFeatureGenerator::bootstrapConfiguredFeatures)
                .add(Registries.DAMAGE_TYPE, RelictDamageTypeGenerator::bootstrapDamageTypes)
                .add(Registries.DENSITY_FUNCTION, RelictDensityFunctionGenerator::bootstrapDensityFunctions)
                .add(Registries.DIMENSION_TYPE, dimensionGenerator::bootstrapDimensionType)
                .add(Registries.LEVEL_STEM, dimensionGenerator::bootstrapLevelStem)
                .add(Registries.NOISE, RelictProvinceGenerator::bootstrapNoises)
                .add(Registries.NOISE_SETTINGS, dimensionGenerator::bootstrapNoiseSettings)
                .add(Registries.PLACED_FEATURE, RelictPlacedFeatureGenerator::bootstrapPlacedFeatures)
                .add(RelictCustomRegistries.PROVINCE_REGISTRY, RelictProvinceGenerator::bootstrapProvinces)
                .add(RelictCustomRegistries.VORONOI_SOURCE_REGISTRY, RelictProvinceGenerator::bootstrapVoronoiSources)
                .add(Registries.STRUCTURE, RelictStructureGenerator::bootstrapStructures)
                .add(Registries.STRUCTURE_SET, RelictStructureGenerator::bootstrapStructureSet)
                .add(Registries.TEMPLATE_POOL, RelictTemplatePoolGenerator::bootstrapTemplatePools)
                .add(Registries.TIMELINE, timelineGenerator::bootstrapTimelines)
                .add(Registries.WORLD_CLOCK, timelineGenerator::bootstrapWorldClocks);
    }

    @SubscribeEvent
    public static void generateClientData(GatherDataEvent.Client event) {
        PackOutput output = event.getGenerator().getPackOutput();

        event.addProvider(new RelictCelestialSprites(output));
        event.addProvider(new RelictEquipmentAssets(output));
        event.addProvider(new RelictModels(output));
    }

}
