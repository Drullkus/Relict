package us.drullk.relict.init.custom;

import net.minecraft.resources.ResourceKey;
import us.drullk.relict.Relict;
import us.drullk.relict.worldgen.VoronoiSource;

public class RelictVoronoiSources {

    public static final ResourceKey<VoronoiSource> MARS = ResourceKey.create(RelictCustomRegistries.VORONOI_SOURCE_REGISTRY, Relict.id("mars"));

    public static final ResourceKey<VoronoiSource> MARS_UNDERGROUND = ResourceKey.create(RelictCustomRegistries.VORONOI_SOURCE_REGISTRY, Relict.id("mars_underground"));

}
