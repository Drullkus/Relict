package us.drullk.relict.init.worldgen;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import us.drullk.relict.Relict;
import us.drullk.relict.worldgen.CraterFieldFunction;
import us.drullk.relict.worldgen.DuneCrestCondition;
import us.drullk.relict.worldgen.DuneWaveFunction;
import us.drullk.relict.worldgen.DustLayerFeature;
import us.drullk.relict.worldgen.DustLayerFeatureConfiguration;
import us.drullk.relict.worldgen.MesaFieldFunction;
import us.drullk.relict.worldgen.RelictChunkGenerator;
import us.drullk.relict.worldgen.VoronoiBiomeSource;
import us.drullk.relict.worldgen.VoronoiParameterFunction;

public class RelictWorldgenTypes {

    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES = DeferredRegister.create(BuiltInRegistries.BIOME_SOURCE, Relict.MODID);
    public static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTION_TYPES = DeferredRegister.create(BuiltInRegistries.DENSITY_FUNCTION_TYPE, Relict.MODID);
    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS = DeferredRegister.create(BuiltInRegistries.CHUNK_GENERATOR, Relict.MODID);
    public static final DeferredRegister<MapCodec<? extends SurfaceRules.ConditionSource>> SURFACE_CONDITIONS = DeferredRegister.create(BuiltInRegistries.MATERIAL_CONDITION, Relict.MODID);
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(BuiltInRegistries.FEATURE, Relict.MODID);

    public static final DeferredHolder<Feature<?>, DustLayerFeature> DUST_LAYER_FEATURE;

    static {
        BIOME_SOURCES.register("voronoi", () -> VoronoiBiomeSource.CODEC);
        CHUNK_GENERATORS.register("noise", () -> RelictChunkGenerator.MAP_CODEC);
        DENSITY_FUNCTION_TYPES.register("voronoi_parameter", () -> VoronoiParameterFunction.MAP_CODEC);
        DENSITY_FUNCTION_TYPES.register("dune_wave", () -> DuneWaveFunction.MAP_CODEC);
        DENSITY_FUNCTION_TYPES.register("mesa_field", () -> MesaFieldFunction.MAP_CODEC);
        DENSITY_FUNCTION_TYPES.register("crater_field", () -> CraterFieldFunction.MAP_CODEC);
        SURFACE_CONDITIONS.register("dune_crest", DuneCrestCondition.INSTANCE::codec);
        DUST_LAYER_FEATURE = FEATURES.register("dust_layer", () -> new DustLayerFeature(DustLayerFeatureConfiguration.CODEC));
    }

    public static void register(IEventBus modEventBus) {
        BIOME_SOURCES.register(modEventBus);
        DENSITY_FUNCTION_TYPES.register(modEventBus);
        CHUNK_GENERATORS.register(modEventBus);
        SURFACE_CONDITIONS.register(modEventBus);
        FEATURES.register(modEventBus);
    }

}
