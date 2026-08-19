package us.drullk.relict.datagen.worldgen;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ARGB;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.timeline.Timeline;
import net.neoforged.neoforge.common.world.NeoForgeEnvironmentAttributes;
import us.drullk.relict.Relict;
import us.drullk.relict.init.custom.RelictCustomRegistries;
import us.drullk.relict.init.custom.RelictVoronoiSources;
import us.drullk.relict.init.worldgen.RelictDimension;
import us.drullk.relict.worldgen.VoronoiBiomeSource;
import us.drullk.relict.worldgen.VoronoiSource;

import java.util.List;
import java.util.Optional;

public class RelictDimensionGenerator {

    private final int minY;
    private final int height;
    private final int seaLevel;

    private final RelictSurfaceRules relictSurfaceRules;

    public RelictDimensionGenerator(int minY, int height, int seaLevel) {
        this.minY = minY;
        this.height = height;
        this.seaLevel = seaLevel;
        this.relictSurfaceRules = new RelictSurfaceRules();
    }

    public void bootstrapDimensionType(BootstrapContext<DimensionType> context) {
        HolderGetter<Block> blocks = context.lookup(Registries.BLOCK);
        HolderGetter<Timeline> timelines = context.lookup(Registries.TIMELINE);
        HolderGetter<WorldClock> clocks = context.lookup(Registries.WORLD_CLOCK);

        EnvironmentAttributeMap attributes = EnvironmentAttributeMap.builder()
                .set(EnvironmentAttributes.FOG_COLOR, ARGB.opaque(0xC08A63))
                .set(EnvironmentAttributes.SKY_COLOR, ARGB.opaque(0xD8A07A))
                .set(EnvironmentAttributes.CLOUD_COLOR, ARGB.white(0.35F))
                .set(EnvironmentAttributes.AMBIENT_LIGHT_COLOR, ARGB.opaque(0x0D0B0A))
                .set(EnvironmentAttributes.SKY_LIGHT_COLOR, ARGB.opaque(0xFFD6BE))
                .set(EnvironmentAttributes.BED_RULE, BedRule.CAN_SLEEP_WHEN_DARK)
                .set(EnvironmentAttributes.RESPAWN_ANCHOR_WORKS, false)
                .set(NeoForgeEnvironmentAttributes.CUSTOM_SKYBOX, Relict.id("mars"))
                .build();

        context.register(RelictDimension.MARS_TYPE, new DimensionType(
                false,
                true,
                false,
                false,
                0.5,
                this.minY,
                this.height,
                this.height,
                blocks.getOrThrow(BlockTags.INFINIBURN_OVERWORLD),
                0.0F,
                new DimensionType.MonsterSettings(UniformInt.of(0, 0), 0),
                DimensionType.Skybox.OVERWORLD,
                CardinalLighting.Type.DEFAULT,
                attributes,
                timelines.getOrThrow(RelictDimension.MARS_TIMELINES),
                Optional.of(clocks.getOrThrow(RelictDimension.MARS_CLOCK))
        ));
    }

    public void bootstrapLevelStem(BootstrapContext<LevelStem> context) {
        HolderGetter<DimensionType> dimensionTypes = context.lookup(Registries.DIMENSION_TYPE);
        HolderGetter<NoiseGeneratorSettings> noiseSettings = context.lookup(Registries.NOISE_SETTINGS);
        HolderGetter<VoronoiSource> voronoiSources = context.lookup(RelictCustomRegistries.VORONOI_SOURCE_REGISTRY);

        VoronoiBiomeSource biomeSource = new VoronoiBiomeSource(voronoiSources.getOrThrow(RelictVoronoiSources.MARS), Optional.of(voronoiSources.getOrThrow(RelictVoronoiSources.MARS_UNDERGROUND)), this.seaLevel - 32 - 8);
        NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(biomeSource, noiseSettings.getOrThrow(RelictDimension.MARS_NOISE_SETTINGS));
        context.register(RelictDimension.MARS_LEVELSTEM, new LevelStem(dimensionTypes.getOrThrow(RelictDimension.MARS_TYPE), generator));
    }

    public void bootstrapNoiseSettings(BootstrapContext<NoiseGeneratorSettings> context) {
        HolderGetter<DensityFunction> densityFunctions = context.lookup(Registries.DENSITY_FUNCTION);

        DensityFunction surfaceHeight = new DensityFunctions.HolderHolder(densityFunctions.getOrThrow(RelictDensityFunctionGenerator.VORONOI_SURFACE_HEIGHT));
        DensityFunction relief = new DensityFunctions.HolderHolder(densityFunctions.getOrThrow(RelictDensityFunctionGenerator.RELIEF));
        NoiseRouter noiseRouter = RelictNoiseRouter.route(densityFunctions, context.lookup(Registries.NOISE), surfaceHeight, relief, this.seaLevel, this.minY, this.height);

        context.register(RelictDimension.MARS_NOISE_SETTINGS, new NoiseGeneratorSettings(
                NoiseSettings.create(this.minY, this.height, 1, 2),
                Blocks.SMOOTH_BASALT.defaultBlockState(),
                Blocks.AIR.defaultBlockState(),
                noiseRouter,
                this.relictSurfaceRules.composeSurface(context),
                List.of(),
                this.seaLevel,
                false,
                false,
                true,
                false
        ));
    }

}
