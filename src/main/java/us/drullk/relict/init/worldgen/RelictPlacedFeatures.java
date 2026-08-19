package us.drullk.relict.init.worldgen;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import us.drullk.relict.Relict;

public class RelictPlacedFeatures {

    public static final ResourceKey<PlacedFeature> SULFUR_GEODE = create("sulfur_geode");

    // basalt_caves
    public static final ResourceKey<PlacedFeature> BASALT_COLUMNS = create("basalt_columns");
    public static final ResourceKey<PlacedFeature> BLACKSTONE_BLOBS = create("blackstone_blobs");
    public static final ResourceKey<PlacedFeature> GRAVEL_FLOOR = create("gravel_floor");
    public static final ResourceKey<PlacedFeature> MAGMA_PATCH = create("magma_patch");
    public static final ResourceKey<PlacedFeature> SPRING_LAVA = create("spring_lava");
    public static final ResourceKey<PlacedFeature> MEGABRECCIA = create("megabreccia");

    // calcite_caves
    public static final ResourceKey<PlacedFeature> CALCITE_BLOBS = create("calcite_blobs");
    public static final ResourceKey<PlacedFeature> CALCITE_SPELEOTHEM_CLUSTER = create("calcite_speleothem_cluster");
    public static final ResourceKey<PlacedFeature> CALCITE_SPELEOTHEM = create("calcite_speleothem");

    // sulfur_caves
    public static final ResourceKey<PlacedFeature> SULFUR_BLOBS = create("sulfur_blobs");
    public static final ResourceKey<PlacedFeature> CINNABAR_BLOBS = create("cinnabar_blobs");
    public static final ResourceKey<PlacedFeature> TUFF_SCATTER = create("tuff_scatter");
    public static final ResourceKey<PlacedFeature> SULFUR_SPIKE_CLUSTER = create("sulfur_spike_cluster");
    public static final ResourceKey<PlacedFeature> SULFUR_SPIKE = create("sulfur_spike");
    public static final ResourceKey<PlacedFeature> SULFUR_POOL = create("sulfur_pool");
    public static final ResourceKey<PlacedFeature> SULFUR_GEYSER = create("sulfur_geyser");

    // ice_caves
    public static final ResourceKey<PlacedFeature> PACKED_ICE_LENS = create("packed_ice_lens");
    public static final ResourceKey<PlacedFeature> ICE_MARGIN = create("ice_margin");
    public static final ResourceKey<PlacedFeature> BLUE_ICE_CORE = create("blue_ice_core");
    public static final ResourceKey<PlacedFeature> FROST_FLOOR = create("frost_floor");

    private static ResourceKey<PlacedFeature> create(String name) {
        return ResourceKey.create(Registries.PLACED_FEATURE, Relict.id(name));
    }

}
