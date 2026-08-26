package us.drullk.relict.init.worldgen;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import us.drullk.relict.Relict;

public class RelictTemplatePools {

    public static final ResourceKey<StructureTemplatePool> PORTAL_RUIN_START = key("mars_portal_ruin/start");

    public static final ResourceKey<StructureTemplatePool> UNMANNED_WRECK_START = key("unmanned_wreck/start");

    public static final ResourceKey<StructureTemplatePool> RUIN_A_START = key("ruin_a/start");
    public static final ResourceKey<StructureTemplatePool> RUIN_A_MESSAGE_ROOM = key("ruin_a/message_room");
    public static final ResourceKey<StructureTemplatePool> RUIN_A_CORRIDORS = key("ruin_a/corridors");
    public static final ResourceKey<StructureTemplatePool> RUIN_A_ROOMS = key("ruin_a/rooms");
    public static final ResourceKey<StructureTemplatePool> RUIN_A_CAPS = key("ruin_a/caps");
    public static final ResourceKey<StructureTemplatePool> RUIN_A_NEXT = key("ruin_a/next");

    private static ResourceKey<StructureTemplatePool> key(String path) {
        return ResourceKey.create(Registries.TEMPLATE_POOL, Relict.id(path));
    }

}
