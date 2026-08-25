package us.drullk.relict.datagen.worldgen;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.util.ARGB;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.attribute.modifier.FloatModifier;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import us.drullk.relict.init.worldgen.RelictBiomes;
import us.drullk.relict.init.worldgen.RelictCarvers;
import us.drullk.relict.init.worldgen.RelictPlacedFeatures;

public class RelictBiomeGenerator {

    private static final int WATER_COLOR = 0x3F76E4;
    private static final float TEMPERATURE = -0.7F;

    private static final int CAVE_FOG_COLOR = 0x26_1C_14;
    private static final float CAVE_FOG_START_DISTANCE = 0.0F;
    private static final float CAVE_FOG_END_SCALAR = 0.5F;

    public static void bootstrapBiomes(BootstrapContext<Biome> context) {
        HolderGetter<PlacedFeature> placedFeatures = context.lookup(Registries.PLACED_FEATURE);
        HolderGetter<ConfiguredWorldCarver<?>> carvers = context.lookup(Registries.CONFIGURED_CARVER);

        context.register(RelictBiomes.WRINKLE_PLAINS, surfaceBiome(caves(placedFeatures, carvers)
                .addFeature(GenerationStep.Decoration.TOP_LAYER_MODIFICATION, placedFeatures.getOrThrow(RelictPlacedFeatures.DUST_LAYER_WRINKLE_PLAINS))
                .build()));
        context.register(RelictBiomes.RUSTED_DUNES, surfaceBiome(caves(placedFeatures, carvers)
                .addFeature(GenerationStep.Decoration.TOP_LAYER_MODIFICATION, placedFeatures.getOrThrow(RelictPlacedFeatures.DUST_LAYER_RUSTED_DUNES))
                .build()));
        context.register(RelictBiomes.FRETTED_MESAS, surfaceBiome(caves(placedFeatures, carvers)
                .addFeature(GenerationStep.Decoration.TOP_LAYER_MODIFICATION, placedFeatures.getOrThrow(RelictPlacedFeatures.DUST_LAYER_FRETTED_MESAS))
                .build()));
        context.register(RelictBiomes.SHATTERED_HIGHLANDS, surfaceBiome(caves(placedFeatures, carvers).build()));

        basaltCaves(context, placedFeatures, carvers);
        calciteCaves(context, placedFeatures, carvers);
        iceCaves(context, placedFeatures, carvers);
        sulfurCaves(context, placedFeatures, carvers);
    }

    private static void basaltCaves(BootstrapContext<Biome> context, HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> carvers) {
        var basalt = caves(placedFeatures, carvers)
                .addFeature(GenerationStep.Decoration.UNDERGROUND_DECORATION, placedFeatures.getOrThrow(RelictPlacedFeatures.BASALT_COLUMNS))
                .addFeature(GenerationStep.Decoration.UNDERGROUND_DECORATION, placedFeatures.getOrThrow(RelictPlacedFeatures.BLACKSTONE_BLOBS))
                .addFeature(GenerationStep.Decoration.UNDERGROUND_DECORATION, placedFeatures.getOrThrow(RelictPlacedFeatures.GRAVEL_FLOOR))
                .addFeature(GenerationStep.Decoration.UNDERGROUND_DECORATION, placedFeatures.getOrThrow(RelictPlacedFeatures.MEGABRECCIA))
                .addFeature(GenerationStep.Decoration.UNDERGROUND_DECORATION, placedFeatures.getOrThrow(RelictPlacedFeatures.SULFUR_GEODE))
                .addFeature(GenerationStep.Decoration.UNDERGROUND_DECORATION, placedFeatures.getOrThrow(RelictPlacedFeatures.MAGMA_PATCH))
                .addFeature(GenerationStep.Decoration.UNDERGROUND_DECORATION, placedFeatures.getOrThrow(RelictPlacedFeatures.SPRING_LAVA))
                .addFeature(GenerationStep.Decoration.UNDERGROUND_DECORATION, placedFeatures.getOrThrow(RelictPlacedFeatures.ANDESITE_POCKET_UPPER))
                .addFeature(GenerationStep.Decoration.UNDERGROUND_DECORATION, placedFeatures.getOrThrow(RelictPlacedFeatures.ANDESITE_POCKET_LOWER))
                .addFeature(GenerationStep.Decoration.UNDERGROUND_DECORATION, placedFeatures.getOrThrow(RelictPlacedFeatures.GRANITE_POCKET_UPPER))
                .addFeature(GenerationStep.Decoration.UNDERGROUND_DECORATION, placedFeatures.getOrThrow(RelictPlacedFeatures.GRANITE_POCKET_LOWER));
        context.register(RelictBiomes.BASALT_CAVES, undergroundBiome(basalt.build()));
    }

    private static void calciteCaves(BootstrapContext<Biome> context, HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> carvers) {
        var calcite = caves(placedFeatures, carvers)
                .addFeature(GenerationStep.Decoration.UNDERGROUND_DECORATION, placedFeatures.getOrThrow(RelictPlacedFeatures.CALCITE_BLOBS))
                .addFeature(GenerationStep.Decoration.UNDERGROUND_DECORATION, placedFeatures.getOrThrow(RelictPlacedFeatures.CALCITE_SPELEOTHEM_CLUSTER))
                .addFeature(GenerationStep.Decoration.UNDERGROUND_DECORATION, placedFeatures.getOrThrow(RelictPlacedFeatures.CALCITE_SPELEOTHEM))
                .addFeature(GenerationStep.Decoration.UNDERGROUND_DECORATION, placedFeatures.getOrThrow(RelictPlacedFeatures.CALCITE_LARGE_DRIPSTONE))
                .addFeature(GenerationStep.Decoration.UNDERGROUND_DECORATION, placedFeatures.getOrThrow(RelictPlacedFeatures.DIORITE_POCKET_UPPER))
                .addFeature(GenerationStep.Decoration.UNDERGROUND_DECORATION, placedFeatures.getOrThrow(RelictPlacedFeatures.DIORITE_POCKET_LOWER));
        context.register(RelictBiomes.CALCITE_CAVES, undergroundBiome(calcite.build()));
    }

    private static void iceCaves(BootstrapContext<Biome> context, HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> carvers) {
        var ice = caves(placedFeatures, carvers)
                .addFeature(GenerationStep.Decoration.UNDERGROUND_DECORATION, placedFeatures.getOrThrow(RelictPlacedFeatures.PACKED_ICE_LENS))
                .addFeature(GenerationStep.Decoration.UNDERGROUND_DECORATION, placedFeatures.getOrThrow(RelictPlacedFeatures.ICE_MARGIN))
                .addFeature(GenerationStep.Decoration.UNDERGROUND_DECORATION, placedFeatures.getOrThrow(RelictPlacedFeatures.BLUE_ICE_CORE))
                .addFeature(GenerationStep.Decoration.UNDERGROUND_DECORATION, placedFeatures.getOrThrow(RelictPlacedFeatures.FROST_FLOOR))
                .addFeature(GenerationStep.Decoration.UNDERGROUND_DECORATION, placedFeatures.getOrThrow(RelictPlacedFeatures.ICE_LENS_RIM))
                .addFeature(GenerationStep.Decoration.UNDERGROUND_DECORATION, placedFeatures.getOrThrow(RelictPlacedFeatures.ICE_WALL_POCKET))
                .addFeature(GenerationStep.Decoration.UNDERGROUND_DECORATION, placedFeatures.getOrThrow(RelictPlacedFeatures.ANDESITE_POCKET_UPPER))
                .addFeature(GenerationStep.Decoration.UNDERGROUND_DECORATION, placedFeatures.getOrThrow(RelictPlacedFeatures.ANDESITE_POCKET_LOWER))
                .addFeature(GenerationStep.Decoration.UNDERGROUND_DECORATION, placedFeatures.getOrThrow(RelictPlacedFeatures.DIORITE_POCKET_UPPER))
                .addFeature(GenerationStep.Decoration.UNDERGROUND_DECORATION, placedFeatures.getOrThrow(RelictPlacedFeatures.DIORITE_POCKET_LOWER));
        context.register(RelictBiomes.ICE_CAVES, undergroundBiome(ice.build()));
    }

    private static void sulfurCaves(BootstrapContext<Biome> context, HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> carvers) {
        var sulfur = caves(placedFeatures, carvers)
                .addFeature(GenerationStep.Decoration.UNDERGROUND_DECORATION, placedFeatures.getOrThrow(RelictPlacedFeatures.SULFUR_BLOBS))
                .addFeature(GenerationStep.Decoration.UNDERGROUND_DECORATION, placedFeatures.getOrThrow(RelictPlacedFeatures.CINNABAR_BLOBS))
                .addFeature(GenerationStep.Decoration.UNDERGROUND_DECORATION, placedFeatures.getOrThrow(RelictPlacedFeatures.TUFF_SCATTER))
                .addFeature(GenerationStep.Decoration.UNDERGROUND_DECORATION, placedFeatures.getOrThrow(RelictPlacedFeatures.SULFUR_SPIKE_CLUSTER))
                .addFeature(GenerationStep.Decoration.UNDERGROUND_DECORATION, placedFeatures.getOrThrow(RelictPlacedFeatures.SULFUR_SPIKE))
                .addFeature(GenerationStep.Decoration.UNDERGROUND_DECORATION, placedFeatures.getOrThrow(RelictPlacedFeatures.SULFUR_GEODE))
                .addFeature(GenerationStep.Decoration.UNDERGROUND_DECORATION, placedFeatures.getOrThrow(RelictPlacedFeatures.MAGMA_PATCH))
                .addFeature(GenerationStep.Decoration.UNDERGROUND_DECORATION, placedFeatures.getOrThrow(RelictPlacedFeatures.SULFUR_GEYSER))
                .addFeature(GenerationStep.Decoration.UNDERGROUND_DECORATION, placedFeatures.getOrThrow(RelictPlacedFeatures.SULFUR_POOL))
                .addFeature(GenerationStep.Decoration.UNDERGROUND_DECORATION, placedFeatures.getOrThrow(RelictPlacedFeatures.GRANITE_POCKET_UPPER))
                .addFeature(GenerationStep.Decoration.UNDERGROUND_DECORATION, placedFeatures.getOrThrow(RelictPlacedFeatures.GRANITE_POCKET_LOWER));
        context.register(RelictBiomes.SULFUR_CAVES, undergroundBiome(sulfur.build()));
    }

    private static BiomeGenerationSettings.Builder caves(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> carvers) {
        return new BiomeGenerationSettings.Builder(placedFeatures, carvers)
                .addCarver(RelictCarvers.CAVE)
                .addCarver(RelictCarvers.CAVE_EXTRA_UNDERGROUND)
                .addCarver(RelictCarvers.CANYON);
    }

    private static Biome surfaceBiome(BiomeGenerationSettings generation) {
        return new Biome.BiomeBuilder()
                .hasPrecipitation(false)
                .temperature(TEMPERATURE)
                .downfall(0.0F)
                .specialEffects(new BiomeSpecialEffects.Builder().waterColor(WATER_COLOR).build())
                .mobSpawnSettings(MobSpawnSettings.EMPTY)
                .generationSettings(generation)
                .build();
    }

    private static Biome undergroundBiome(BiomeGenerationSettings generation) {
        return new Biome.BiomeBuilder()
                .hasPrecipitation(false)
                .temperature(TEMPERATURE)
                .downfall(0.0F)
                .setAttribute(EnvironmentAttributes.FOG_COLOR, ARGB.opaque(CAVE_FOG_COLOR))
                .setAttribute(EnvironmentAttributes.FOG_START_DISTANCE, CAVE_FOG_START_DISTANCE)
                .modifyAttribute(EnvironmentAttributes.FOG_END_DISTANCE, FloatModifier.MULTIPLY, CAVE_FOG_END_SCALAR)
                .specialEffects(new BiomeSpecialEffects.Builder().waterColor(WATER_COLOR).build())
                .mobSpawnSettings(MobSpawnSettings.EMPTY)
                .generationSettings(generation)
                .build();
    }

}
