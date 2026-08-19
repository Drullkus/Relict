package us.drullk.relict.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import us.drullk.relict.init.custom.RelictCustomRegistries;

public record VoronoiParameterFunction(Holder<VoronoiSource> voronoiSource, ProvinceParameter parameter) implements DensityFunction.SimpleFunction {

    public static final MapCodec<VoronoiParameterFunction> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            RegistryFixedCodec.create(RelictCustomRegistries.VORONOI_SOURCE_REGISTRY).fieldOf("voronoi_source").forGetter(VoronoiParameterFunction::voronoiSource),
            ProvinceParameter.CODEC.fieldOf("parameter").forGetter(VoronoiParameterFunction::parameter)
    ).apply(instance, VoronoiParameterFunction::new));

    private static final KeyDispatchDataCodec<VoronoiParameterFunction> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    @Override
    public double compute(final FunctionContext context) {
        return this.parameter.compute(VoronoiSource.seeded(this.voronoiSource), context.blockX(), context.blockZ());
    }

    @Override
    public double minValue() {
        return this.parameter.minValue();
    }

    @Override
    public double maxValue() {
        return this.parameter.maxValue();
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }

}
