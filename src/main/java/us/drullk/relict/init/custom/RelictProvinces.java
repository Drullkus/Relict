package us.drullk.relict.init.custom;

import net.minecraft.resources.ResourceKey;
import us.drullk.relict.Relict;
import us.drullk.relict.worldgen.Province;

public class RelictProvinces {

    public static final ResourceKey<Province> WRINKLE_PLAINS = create("wrinkle_plains");
    public static final ResourceKey<Province> RUSTED_DUNES = create("rusted_dunes");
    public static final ResourceKey<Province> FRETTED_MESAS = create("fretted_mesas");

    /** Registered but unplaced: its Himalaya primitive is deferred, and no voronoi source references it. */
    public static final ResourceKey<Province> SHATTERED_HIGHLANDS = create("shattered_highlands");

    public static final ResourceKey<Province> BASALT_CAVES = create("basalt_caves");
    public static final ResourceKey<Province> SULFUR_CAVES = create("sulfur_caves");
    public static final ResourceKey<Province> ICE_CAVES = create("ice_caves");
    public static final ResourceKey<Province> CALCITE_CAVES = create("calcite_caves");

    private static ResourceKey<Province> create(String name) {
        return ResourceKey.create(RelictCustomRegistries.PROVINCE_REGISTRY, Relict.id(name));
    }

}
