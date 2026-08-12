package us.drullk.relict.init.worldgen;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.timeline.Timeline;
import us.drullk.relict.Relict;

public class RelictDimension {

    // Mars world
    public static final ResourceKey<DimensionType> MARS_TYPE = ResourceKey.create(Registries.DIMENSION_TYPE, Relict.id("mars"));
    public static final ResourceKey<Level> MARS_LEVEL = ResourceKey.create(Registries.DIMENSION, Relict.id("mars"));
    public static final ResourceKey<LevelStem> MARS_LEVELSTEM = ResourceKey.create(Registries.LEVEL_STEM, Relict.id("mars"));
    public static final ResourceKey<NoiseGeneratorSettings> MARS_NOISE_SETTINGS = ResourceKey.create(Registries.NOISE_SETTINGS, Relict.id("mars"));

    // Mars time
    public static final ResourceKey<WorldClock> MARS_CLOCK = ResourceKey.create(Registries.WORLD_CLOCK, Relict.id("mars"));
    public static final ResourceKey<Timeline> MARS_SOL = ResourceKey.create(Registries.TIMELINE, Relict.id("mars_sol"));

    public static final TagKey<Timeline> MARS_TIMELINES = TagKey.create(Registries.TIMELINE, Relict.id("in_mars"));

    // Mars moons
    /**
     * Phase frames each moon is drawn with: one number driving the sprites datagen writes, the frame the
     * sky renderer picks, and the filmstrip the config tool shows. Keep them divisible by 4 so that full,
     * both quarters, and new land exactly on a frame.
     */
    public static final int PHOBOS_PHASES = 16;
    public static final int DEIMOS_PHASES = 8;

}
