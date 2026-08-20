package us.drullk.relict.init.worldgen;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import us.drullk.relict.Relict;

public class RelictCarvers {

    public static final ResourceKey<ConfiguredWorldCarver<?>> CAVE = create("cave");
    public static final ResourceKey<ConfiguredWorldCarver<?>> CAVE_EXTRA_UNDERGROUND = create("cave_extra_underground");
    public static final ResourceKey<ConfiguredWorldCarver<?>> CANYON = create("canyon");

    private static ResourceKey<ConfiguredWorldCarver<?>> create(String name) {
        return ResourceKey.create(Registries.CONFIGURED_CARVER, Relict.id(name));
    }

}
