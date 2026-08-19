package us.drullk.relict.init.worldgen;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import us.drullk.relict.Relict;
import us.drullk.relict.worldgen.VoronoiBiomeSource;
import us.drullk.relict.worldgen.VoronoiParameterFunction;

public class RelictWorldgenTypes {

    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES = DeferredRegister.create(BuiltInRegistries.BIOME_SOURCE, Relict.MODID);
    public static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTION_TYPES = DeferredRegister.create(BuiltInRegistries.DENSITY_FUNCTION_TYPE, Relict.MODID);

    static {
        BIOME_SOURCES.register("voronoi", () -> VoronoiBiomeSource.CODEC);
        DENSITY_FUNCTION_TYPES.register("voronoi_parameter", () -> VoronoiParameterFunction.MAP_CODEC);
    }

    public static void register(IEventBus modEventBus) {
        BIOME_SOURCES.register(modEventBus);
        DENSITY_FUNCTION_TYPES.register(modEventBus);
    }

}
