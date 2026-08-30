package us.drullk.relict.init.worldgen;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import us.drullk.relict.Relict;

public class RelictTemplatePools {

    public static final ResourceKey<StructureTemplatePool> PORTAL_RUIN_START = key("mars_portal_ruin/start");

    public static final ResourceKey<StructureTemplatePool> UNMANNED_WRECK_START = key("unmanned_wreck/start");

    public static final ResourceKey<StructureTemplatePool> OVERCAST_MOORING_START = key("overcast_mooring/start");
    public static final ResourceKey<StructureTemplatePool> OVERCAST_MOORING_DEPOT = key("overcast_mooring/depot");

    private static ResourceKey<StructureTemplatePool> key(String path) {
        return ResourceKey.create(Registries.TEMPLATE_POOL, Relict.id(path));
    }

}
