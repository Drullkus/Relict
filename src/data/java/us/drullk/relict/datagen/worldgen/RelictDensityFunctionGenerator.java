package us.drullk.relict.datagen.worldgen;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import us.drullk.relict.Relict;
import us.drullk.relict.datagen.worldgen.densityfields.RelictRidgeField;
import us.drullk.relict.datagen.worldgen.densityfields.RelictMesaField;
import us.drullk.relict.init.custom.RelictCustomRegistries;
import us.drullk.relict.init.custom.RelictVoronoiSources;
import us.drullk.relict.worldgen.ProvinceParameter;
import us.drullk.relict.worldgen.VoronoiParameterFunction;
import us.drullk.relict.worldgen.VoronoiSource;

public class RelictDensityFunctionGenerator {

    public static final ResourceKey<DensityFunction> VORONOI_SURFACE_HEIGHT = create("voronoi/surface_height");

    public static final ResourceKey<DensityFunction> VORONOI_EPOCH = create("voronoi/epoch");

    public static final ResourceKey<DensityFunction> RIDGE_SHAPE = create("terrain/ridge_shape");

    public static final ResourceKey<DensityFunction> DUNE_SHAPE = create("terrain/dune_shape");

    public static final ResourceKey<DensityFunction> MESA_SHAPE = create("terrain/mesa_shape");

    public static final ResourceKey<DensityFunction> RELIEF = create("terrain/relief");

    public static void bootstrapDensityFunctions(BootstrapContext<DensityFunction> context) {
        HolderGetter<VoronoiSource> voronoiSources = context.lookup(RelictCustomRegistries.VORONOI_SOURCE_REGISTRY);
        HolderGetter<NormalNoise.NoiseParameters> noises = context.lookup(Registries.NOISE);
        HolderGetter<DensityFunction> functions = context.lookup(Registries.DENSITY_FUNCTION);

        Holder<VoronoiSource> mars = voronoiSources.getOrThrow(RelictVoronoiSources.MARS);

        context.register(VORONOI_SURFACE_HEIGHT, DensityFunctions.cache2d(parameter(mars, ProvinceParameter.SURFACE_HEIGHT)));

        // Not read by the terrain graph. Registered to classify epoch
        context.register(VORONOI_EPOCH, DensityFunctions.cache2d(parameter(mars, ProvinceParameter.EPOCH)));

        context.register(RIDGE_SHAPE, RelictRidgeField.shape(noises::getOrThrow));
        context.register(DUNE_SHAPE, RelictMesaField.duneShape(noises::getOrThrow));
        context.register(MESA_SHAPE, RelictMesaField.mesaShape(noises::getOrThrow));

        // One landform channel per province signature, each gated by its own blended province scalar, plus the
        // dimension-wide plain. RELIEF is the single composition point: the underground biome cut and the
        // preliminary surface level both read it, so a height channel added anywhere else goes unseen.
        context.register(RELIEF, DensityFunctions.cache2d(DensityFunctions.add(
                DensityFunctions.add(
                        DensityFunctions.mul(
                                parameter(mars, ProvinceParameter.RIDGE_AMPLITUDE),
                                new DensityFunctions.HolderHolder(functions.getOrThrow(RIDGE_SHAPE))
                        ),
                        DensityFunctions.mul(
                                parameter(mars, ProvinceParameter.PLAIN_ROUGHNESS),
                                RelictRidgeField.plain(noises::getOrThrow)
                        )
                ),
                DensityFunctions.add(
                        DensityFunctions.mul(
                                parameter(mars, ProvinceParameter.DUNE_AMPLITUDE),
                                new DensityFunctions.HolderHolder(functions.getOrThrow(DUNE_SHAPE))
                        ),
                        DensityFunctions.mul(
                                parameter(mars, ProvinceParameter.MESA_AMPLITUDE),
                                new DensityFunctions.HolderHolder(functions.getOrThrow(MESA_SHAPE))
                        )
                )
        )));
    }

    private static DensityFunction parameter(Holder<VoronoiSource> source, ProvinceParameter parameter) {
        return new VoronoiParameterFunction(source, parameter);
    }

    private static ResourceKey<DensityFunction> create(String name) {
        return ResourceKey.create(Registries.DENSITY_FUNCTION, Relict.id(name));
    }

}
