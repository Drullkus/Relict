package us.drullk.relict.init.worldgen;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import us.drullk.relict.Relict;

public class RelictPlacedFeatures {

    public static final ResourceKey<PlacedFeature> SULFUR_GEODE = create("sulfur_geode");

    // basalt_caves
    public static final ResourceKey<PlacedFeature> BASALT_STALAGMITE = create("basalt_stalagmite");
    public static final ResourceKey<PlacedFeature> BASALT_STALACTITE = create("basalt_stalactite");
    public static final ResourceKey<PlacedFeature> BLACKSTONE_BLOBS = create("blackstone_blobs");
    public static final ResourceKey<PlacedFeature> GRAVEL_FLOOR = create("gravel_floor");
    public static final ResourceKey<PlacedFeature> MAGMA_PATCH = create("magma_patch");
    public static final ResourceKey<PlacedFeature> SPRING_LAVA = create("spring_lava");
    public static final ResourceKey<PlacedFeature> MEGABRECCIA = create("megabreccia");

    // calcite_caves
    public static final ResourceKey<PlacedFeature> CALCITE_BLOBS = create("calcite_blobs");
    public static final ResourceKey<PlacedFeature> CALCITE_SPELEOTHEM_CLUSTER = create("calcite_speleothem_cluster");
    public static final ResourceKey<PlacedFeature> CALCITE_SPELEOTHEM = create("calcite_speleothem");
    public static final ResourceKey<PlacedFeature> CALCITE_LARGE_DRIPSTONE = create("calcite_large_dripstone");

    // sulfur_caves
    public static final ResourceKey<PlacedFeature> SULFUR_BLOBS = create("sulfur_blobs");
    public static final ResourceKey<PlacedFeature> CINNABAR_BLOBS = create("cinnabar_blobs");
    public static final ResourceKey<PlacedFeature> TUFF_SCATTER = create("tuff_scatter");
    public static final ResourceKey<PlacedFeature> SULFUR_SPIKE_CLUSTER = create("sulfur_spike_cluster");
    public static final ResourceKey<PlacedFeature> SULFUR_SPIKE = create("sulfur_spike");
    public static final ResourceKey<PlacedFeature> SULFUR_POOL = create("sulfur_pool");
    public static final ResourceKey<PlacedFeature> SULFUR_GEYSER = create("sulfur_geyser");
    public static final ResourceKey<PlacedFeature> SULFUR_DEEP_LAKE = create("sulfur_deep_lake");

    // ice_caves
    public static final ResourceKey<PlacedFeature> PACKED_ICE_LENS = create("packed_ice_lens");
    public static final ResourceKey<PlacedFeature> ICE_MARGIN = create("ice_margin");
    public static final ResourceKey<PlacedFeature> BLUE_ICE_CORE = create("blue_ice_core");
    public static final ResourceKey<PlacedFeature> FROST_FLOOR = create("frost_floor");
    public static final ResourceKey<PlacedFeature> ICE_LENS_RIM = create("ice_lens_rim");
    public static final ResourceKey<PlacedFeature> ICE_WALL_POCKET = create("ice_wall_pocket");

    // igneous pockets, spread over the underground biomes by rock
    public static final ResourceKey<PlacedFeature> ANDESITE_POCKET_UPPER = create("andesite_pocket_upper");
    public static final ResourceKey<PlacedFeature> ANDESITE_POCKET_LOWER = create("andesite_pocket_lower");
    public static final ResourceKey<PlacedFeature> GRANITE_POCKET_UPPER = create("granite_pocket_upper");
    public static final ResourceKey<PlacedFeature> GRANITE_POCKET_LOWER = create("granite_pocket_lower");
    public static final ResourceKey<PlacedFeature> DIORITE_POCKET_UPPER = create("diorite_pocket_upper");
    public static final ResourceKey<PlacedFeature> DIORITE_POCKET_LOWER = create("diorite_pocket_lower");

    // dust layer baseline, one per placed province
    public static final ResourceKey<PlacedFeature> DUST_LAYER_WRINKLE_PLAINS = create("dust_layer_wrinkle_plains");
    public static final ResourceKey<PlacedFeature> DUST_LAYER_RUSTED_DUNES = create("dust_layer_rusted_dunes");
    public static final ResourceKey<PlacedFeature> DUST_LAYER_FRETTED_MESAS = create("dust_layer_fretted_mesas");

    // rocks, one placed feature per size per placed province
    public static final ResourceKey<PlacedFeature> ROCK_WRINKLE_PLAINS_S = create("rock_wrinkle_plains_s");
    public static final ResourceKey<PlacedFeature> ROCK_WRINKLE_PLAINS_RIDGE_M = create("rock_wrinkle_plains_ridge_m");
    public static final ResourceKey<PlacedFeature> ROCK_WRINKLE_PLAINS_EJECTA_L = create("rock_wrinkle_plains_ejecta_l");
    public static final ResourceKey<PlacedFeature> ROCK_RUSTED_DUNES_S = create("rock_rusted_dunes_s");
    public static final ResourceKey<PlacedFeature> ROCK_FRETTED_MESAS_TALUS_S = create("rock_fretted_mesas_talus_s");
    public static final ResourceKey<PlacedFeature> ROCK_FRETTED_MESAS_TALUS_M = create("rock_fretted_mesas_talus_m");
    public static final ResourceKey<PlacedFeature> ROCK_FRETTED_MESAS_CAP_S = create("rock_fretted_mesas_cap_s");
    public static final ResourceKey<PlacedFeature> ROCK_FRETTED_MESAS_FLOOR_S = create("rock_fretted_mesas_floor_s");

    private static ResourceKey<PlacedFeature> create(String name) {
        return ResourceKey.create(Registries.PLACED_FEATURE, Relict.id(name));
    }

}
