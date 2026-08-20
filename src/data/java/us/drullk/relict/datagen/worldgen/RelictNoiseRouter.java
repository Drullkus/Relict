package us.drullk.relict.datagen.worldgen;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public class RelictNoiseRouter {

    private static final double DENSITY_PER_BLOCK = 0.1;

    public static final double ELEVATION_SCALE = 64.0;

    /** How far below a column's own surface the underground biome field starts; see VoronoiBiomeSource. */
    public static final int UNDERGROUND_MARGIN = 40;

    private static final double SURFACE_DENSITY_THRESHOLD = 25.0 / 16.0;
    private static final double CAVE_Y_SCALE = 8.0;
    private static final double CHEESE_Y_SCALE = 2.0 / 3.0;

    public static DensityFunction surfaceY(DensityFunction surfaceHeight, DensityFunction relief, int seaLevel) {
        return DensityFunctions.add(DensityFunctions.add(DensityFunctions.constant(seaLevel), DensityFunctions.mul(DensityFunctions.constant(ELEVATION_SCALE), surfaceHeight)), relief);
    }

    public static NoiseRouter route(HolderGetter<DensityFunction> functions, HolderGetter<NormalNoise.NoiseParameters> noises, DensityFunction surfaceHeight, DensityFunction relief, int seaLevel, int minY, int height) {

        DensityFunction surfaceY = surfaceY(surfaceHeight, relief, seaLevel);

        DensityFunction terrain = DensityFunctions.cacheOnce(DensityFunctions.mul(DensityFunctions.constant(DENSITY_PER_BLOCK), DensityFunctions.add(DensityFunctions.add(surfaceY, DensityFunctions.constant(0.5)), DensityFunctions.mul(DensityFunctions.constant(-1.0), function(functions, "y")))));

        DensityFunction finalDensity = DensityFunctions.min(postProcess(slide(caves(functions, noises, terrain), minY, height)), function(functions, "overworld/caves/noodle"));

        return new NoiseRouter(
                DensityFunctions.zero(),
                DensityFunctions.constant(-1.0),
                DensityFunctions.constant(-1.0),
                DensityFunctions.zero(),
                DensityFunctions.zero(),
                DensityFunctions.zero(),
                // continents carries the column's own surface height, flat-cached, so VoronoiBiomeSource can
                // read it cheaply through Climate.Sampler; it does not describe continentalness on Mars.
                DensityFunctions.flatCache(surfaceY),
                DensityFunctions.zero(),
                DensityFunctions.mul(DensityFunctions.constant(1.0 / (DENSITY_PER_BLOCK * 128.0)), terrain),
                DensityFunctions.zero(),
                surfaceY,
                finalDensity,
                DensityFunctions.zero(),
                DensityFunctions.zero(),
                DensityFunctions.zero()
        );
    }

    private static DensityFunction caves(HolderGetter<DensityFunction> functions, HolderGetter<NormalNoise.NoiseParameters> noises, DensityFunction terrain) {
        DensityFunction entrances = function(functions, "overworld/caves/entrances");
        DensityFunction surfaceWithEntrances = DensityFunctions.min(terrain, DensityFunctions.mul(DensityFunctions.constant(5.0), entrances));

        DensityFunction layerizedCaverns = DensityFunctions.mul(DensityFunctions.constant(4.0), DensityFunctions.noise(noises.getOrThrow(Noises.CAVE_LAYER), CAVE_Y_SCALE).square());
        DensityFunction cheese = DensityFunctions.noise(noises.getOrThrow(Noises.CAVE_CHEESE), CHEESE_Y_SCALE);

        DensityFunction solidifiedCheese = DensityFunctions.add(DensityFunctions.add(DensityFunctions.constant(0.27), cheese).clamp(-1.0, 1.0), DensityFunctions.add(DensityFunctions.constant(1.5), DensityFunctions.mul(DensityFunctions.constant(-0.64), terrain)).clamp(0.0, 0.5));

        DensityFunction subtractions = DensityFunctions.min(DensityFunctions.min(DensityFunctions.add(layerizedCaverns, solidifiedCheese), entrances), DensityFunctions.add(function(functions, "overworld/caves/spaghetti_2d"), function(functions, "overworld/caves/spaghetti_roughness_function")));

        DensityFunction pillarsWithoutCutoff = function(functions, "overworld/caves/pillars");
        DensityFunction pillars = DensityFunctions.rangeChoice(pillarsWithoutCutoff, -1000000.0, 0.03, DensityFunctions.constant(-1000000.0), pillarsWithoutCutoff);

        return DensityFunctions.rangeChoice(terrain, -1000000.0, SURFACE_DENSITY_THRESHOLD, surfaceWithEntrances, DensityFunctions.max(subtractions, pillars));
    }

    private static DensityFunction slide(DensityFunction caves, int minY, int height) {
        DensityFunction top = DensityFunctions.lerp(DensityFunctions.yClampedGradient(minY + height - 80, minY + height - 64, 1.0, 0.0), -0.078125, caves);
        return DensityFunctions.lerp(DensityFunctions.yClampedGradient(minY, minY + 24, 0.0, 1.0), 0.1171875, top);
    }

    private static DensityFunction postProcess(DensityFunction slide) {
        return DensityFunctions.interpolated(DensityFunctions.mul(DensityFunctions.blendDensity(slide), DensityFunctions.constant(0.64))).squeeze();
    }

    private static DensityFunction function(HolderGetter<DensityFunction> functions, String name) {
        return new DensityFunctions.HolderHolder(functions.getOrThrow(ResourceKey.create(Registries.DENSITY_FUNCTION, Identifier.withDefaultNamespace(name))));
    }

}
