package us.drullk.relict.init.custom;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import us.drullk.relict.Relict;
import us.drullk.relict.worldgen.Province;
import us.drullk.relict.worldgen.VoronoiSource;

public class RelictCustomRegistries {

    public static final Identifier VORONOI_SOURCE_ID = Relict.id("voronoi_source");
    public static final Identifier PROVINCE_ID = Relict.id("province");

    public static final ResourceKey<Registry<VoronoiSource>> VORONOI_SOURCE_REGISTRY = ResourceKey.createRegistryKey(VORONOI_SOURCE_ID);
    public static final ResourceKey<Registry<Province>> PROVINCE_REGISTRY = ResourceKey.createRegistryKey(PROVINCE_ID);

    public void register(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(PROVINCE_REGISTRY, Province.CODEC);
        event.dataPackRegistry(VORONOI_SOURCE_REGISTRY, VoronoiSource.CODEC);
    }

}
