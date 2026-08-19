package us.drullk.relict.init.custom;

import net.minecraft.resources.ResourceKey;
import us.drullk.relict.Relict;
import us.drullk.relict.worldgen.Province;

public class RelictProvinces {

    public static final ResourceKey<Province> WRINKLE_PLAINS = create("wrinkle_plains");

    // Placeholders FIXME remove
    public static final ResourceKey<Province> BADLANDS = create("badlands");
    public static final ResourceKey<Province> DESERT = create("desert");
    public static final ResourceKey<Province> DRIPSTONE_CAVES = create("dripstone_caves");
    public static final ResourceKey<Province> LUSH_CAVES = create("lush_caves");

    private static ResourceKey<Province> create(String name) {
        return ResourceKey.create(RelictCustomRegistries.PROVINCE_REGISTRY, Relict.id(name));
    }

}
