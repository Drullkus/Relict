package us.drullk.relict.datagen.worldgen;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.Carvers;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import us.drullk.relict.init.worldgen.RelictBiomes;

public class RelictBiomeGenerator {

    private static final int WATER_COLOR = 0x3F76E4;
    private static final float TEMPERATURE = -0.7F;

    public static void bootstrapBiomes(BootstrapContext<Biome> context) {
        HolderGetter<PlacedFeature> placedFeatures = context.lookup(Registries.PLACED_FEATURE);
        HolderGetter<ConfiguredWorldCarver<?>> carvers = context.lookup(Registries.CONFIGURED_CARVER);

        BiomeGenerationSettings.Builder generation = new BiomeGenerationSettings.Builder(placedFeatures, carvers)
                .addCarver(Carvers.CAVE)
                .addCarver(Carvers.CAVE_EXTRA_UNDERGROUND)
                .addCarver(Carvers.CANYON);

        context.register(RelictBiomes.WRINKLE_PLAINS, new Biome.BiomeBuilder()
                .hasPrecipitation(false)
                .temperature(TEMPERATURE)
                .downfall(0.0F)
                .specialEffects(new BiomeSpecialEffects.Builder().waterColor(WATER_COLOR).build())
                .mobSpawnSettings(MobSpawnSettings.EMPTY)
                .generationSettings(generation.build())
                .build());
    }

}
