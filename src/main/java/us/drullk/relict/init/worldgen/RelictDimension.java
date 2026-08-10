package us.drullk.relict.init.worldgen;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import us.drullk.relict.Relict;

public class RelictDimension {

    public static final ResourceKey<DimensionType> MARS_TYPE = ResourceKey.create(Registries.DIMENSION_TYPE, Relict.id("mars"));
    public static final ResourceKey<Level> MARS_LEVEL = ResourceKey.create(Registries.DIMENSION, Relict.id("mars"));
    public static final ResourceKey<LevelStem> MARS_LEVELSTEM = ResourceKey.create(Registries.LEVEL_STEM, Relict.id("mars"));

}
