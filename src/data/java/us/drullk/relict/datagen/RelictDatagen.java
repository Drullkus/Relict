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
import us.drullk.relict.datagen.loottables.RelictLootTables;
import us.drullk.relict.datagen.tags.RelictBiomeTags;
import us.drullk.relict.datagen.tags.RelictBlockTags;
import us.drullk.relict.datagen.tags.RelictDamageTypeTags;
import us.drullk.relict.datagen.tags.RelictItemTags;
import us.drullk.relict.datagen.worldgen.*;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid = Relict.MODID)
public class RelictDatagen {

    private static final RegistrySetBuilder REGISTRY_SET_BUILDER = new RegistrySetBuilder()
            .add(Registries.BIOME, RelictBiomes::bootstrapBiomes)
            .add(Registries.CONFIGURED_FEATURE, RelictFeatures::bootstrapConfiguredFeatures)
            .add(Registries.DENSITY_FUNCTION, RelictDensityFunctions::bootstrapDensityFunctions)
            .add(Registries.DIMENSION_TYPE, RelictDimension::bootstrapDimensionType)
            .add(Registries.LEVEL_STEM, RelictDimension::bootstrapLevelStem)
            .add(Registries.NOISE_SETTINGS, RelictDimension::bootstrapNoiseSettings)
            .add(Registries.PLACED_FEATURE, RelictFeatures::bootstrapPlacedFeatures)
            .add(Registries.STRUCTURE, RelictStructures::bootstrapStructures)
            .add(Registries.STRUCTURE_SET, RelictStructures::bootstrapStructureSet)
            ;

    @SubscribeEvent
    public static void generateData(GatherDataEvent.Server event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();

        DatapackBuiltinEntriesProvider builtinDatapack = event.addProvider(new DatapackBuiltinEntriesProvider(output, lookupProvider, REGISTRY_SET_BUILDER, Set.of(Relict.MODID)));
        CompletableFuture<HolderLookup.Provider> builtinDatapackProvider = builtinDatapack.getRegistryProvider();

        event.addProvider(new RelictBiomeTags(output, builtinDatapackProvider));
        event.addProvider(new RelictBlockTags(output, builtinDatapackProvider));
        event.addProvider(new RelictDamageTypeTags(output, builtinDatapackProvider));
        event.addProvider(new RelictItemTags(output, builtinDatapackProvider));

        event.addProvider(new RelictAdvancements(output, builtinDatapackProvider));
        event.addProvider(new RelictLootTables(output, builtinDatapackProvider));
    }

}
