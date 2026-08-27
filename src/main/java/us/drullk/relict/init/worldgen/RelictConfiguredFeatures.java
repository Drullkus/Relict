package us.drullk.relict.init.worldgen;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import us.drullk.relict.Relict;

public class RelictConfiguredFeatures {

    public static final ResourceKey<ConfiguredFeature<?, ?>> SULFUR_GEODE = create("sulfur_geode");

    // basalt_caves
    public static final ResourceKey<ConfiguredFeature<?, ?>> BASALT_COLUMNS = create("basalt_columns");
    public static final ResourceKey<ConfiguredFeature<?, ?>> BLACKSTONE_BLOBS = create("blackstone_blobs");
    public static final ResourceKey<ConfiguredFeature<?, ?>> GRAVEL_FLOOR = create("gravel_floor");
    public static final ResourceKey<ConfiguredFeature<?, ?>> MAGMA_PATCH = create("magma_patch");
    public static final ResourceKey<ConfiguredFeature<?, ?>> SPRING_LAVA = create("spring_lava");
    public static final ResourceKey<ConfiguredFeature<?, ?>> MEGABRECCIA_COBBLED = create("megabreccia_cobbled");
    public static final ResourceKey<ConfiguredFeature<?, ?>> MEGABRECCIA_TUFF = create("megabreccia_tuff");
    public static final ResourceKey<ConfiguredFeature<?, ?>> MEGABRECCIA = create("megabreccia");

    // calcite_caves
    public static final ResourceKey<ConfiguredFeature<?, ?>> CALCITE_BLOBS = create("calcite_blobs");
    public static final ResourceKey<ConfiguredFeature<?, ?>> CALCITE_SPELEOTHEM_CLUSTER = create("calcite_speleothem_cluster");
    public static final ResourceKey<ConfiguredFeature<?, ?>> CALCITE_SPELEOTHEM = create("calcite_speleothem");
    public static final ResourceKey<ConfiguredFeature<?, ?>> CALCITE_LARGE_DRIPSTONE = create("calcite_large_dripstone");

    // sulfur_caves
    public static final ResourceKey<ConfiguredFeature<?, ?>> SULFUR_BLOBS = create("sulfur_blobs");
    public static final ResourceKey<ConfiguredFeature<?, ?>> CINNABAR_BLOBS = create("cinnabar_blobs");
    public static final ResourceKey<ConfiguredFeature<?, ?>> TUFF_SCATTER = create("tuff_scatter");
    public static final ResourceKey<ConfiguredFeature<?, ?>> SULFUR_SPIKE_CLUSTER = create("sulfur_spike_cluster");
    public static final ResourceKey<ConfiguredFeature<?, ?>> SULFUR_SPIKE = create("sulfur_spike");
    public static final ResourceKey<ConfiguredFeature<?, ?>> SULFUR_POOL = create("sulfur_pool");
    public static final ResourceKey<ConfiguredFeature<?, ?>> SULFUR_GEYSER = create("sulfur_geyser");
    public static final ResourceKey<ConfiguredFeature<?, ?>> SULFUR_DEEP_LAKE = create("sulfur_deep_lake");

    // ice_caves
    public static final ResourceKey<ConfiguredFeature<?, ?>> PACKED_ICE_LENS = create("packed_ice_lens");
    public static final ResourceKey<ConfiguredFeature<?, ?>> ICE_MARGIN = create("ice_margin");
    public static final ResourceKey<ConfiguredFeature<?, ?>> BLUE_ICE_CORE = create("blue_ice_core");
    public static final ResourceKey<ConfiguredFeature<?, ?>> FROST_FLOOR = create("frost_floor");
    public static final ResourceKey<ConfiguredFeature<?, ?>> ICE_LENS_RIM = create("ice_lens_rim");
    public static final ResourceKey<ConfiguredFeature<?, ?>> ICE_WALL_POCKET = create("ice_wall_pocket");

    // igneous pockets, spread over the underground biomes by rock
    public static final ResourceKey<ConfiguredFeature<?, ?>> ANDESITE_POCKET = create("andesite_pocket");
    public static final ResourceKey<ConfiguredFeature<?, ?>> GRANITE_POCKET = create("granite_pocket");
    public static final ResourceKey<ConfiguredFeature<?, ?>> DIORITE_POCKET = create("diorite_pocket");

    // dust layer baseline, one per placed province
    public static final ResourceKey<ConfiguredFeature<?, ?>> DUST_LAYER_WRINKLE_PLAINS = create("dust_layer_wrinkle_plains");
    public static final ResourceKey<ConfiguredFeature<?, ?>> DUST_LAYER_RUSTED_DUNES = create("dust_layer_rusted_dunes");
    public static final ResourceKey<ConfiguredFeature<?, ?>> DUST_LAYER_FRETTED_MESAS = create("dust_layer_fretted_mesas");

    // rocks, one configured feature per size per placed province
    public static final ResourceKey<ConfiguredFeature<?, ?>> ROCK_WRINKLE_PLAINS_S = create("rock_wrinkle_plains_s");
    public static final ResourceKey<ConfiguredFeature<?, ?>> ROCK_WRINKLE_PLAINS_RIDGE_M = create("rock_wrinkle_plains_ridge_m");
    public static final ResourceKey<ConfiguredFeature<?, ?>> ROCK_WRINKLE_PLAINS_EJECTA_L = create("rock_wrinkle_plains_ejecta_l");
    public static final ResourceKey<ConfiguredFeature<?, ?>> ROCK_RUSTED_DUNES_S = create("rock_rusted_dunes_s");
    public static final ResourceKey<ConfiguredFeature<?, ?>> ROCK_FRETTED_MESAS_TALUS_S = create("rock_fretted_mesas_talus_s");
    public static final ResourceKey<ConfiguredFeature<?, ?>> ROCK_FRETTED_MESAS_TALUS_M = create("rock_fretted_mesas_talus_m");
    public static final ResourceKey<ConfiguredFeature<?, ?>> ROCK_FRETTED_MESAS_CAP_S = create("rock_fretted_mesas_cap_s");
    public static final ResourceKey<ConfiguredFeature<?, ?>> ROCK_FRETTED_MESAS_FLOOR_S = create("rock_fretted_mesas_floor_s");

    private static ResourceKey<ConfiguredFeature<?, ?>> create(String name) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE, Relict.id(name));
    }

}
