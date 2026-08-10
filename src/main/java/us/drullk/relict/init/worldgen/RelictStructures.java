package us.drullk.relict.init.worldgen;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import us.drullk.relict.Relict;

public class RelictStructures {

    public static ResourceKey<StructureSet> MARS_PORTAL_SET = ResourceKey.create(Registries.STRUCTURE_SET, Relict.id("mars_portal"));
    public static ResourceKey<StructureSet> UNMANNED_RUINS_SET = ResourceKey.create(Registries.STRUCTURE_SET, Relict.id("unmanned_ruins"));

    public static ResourceKey<Structure> MARS_PORTAL = ResourceKey.create(Registries.STRUCTURE, Relict.id("mars_portal"));
    public static ResourceKey<Structure> UNMANNED_RUINS = ResourceKey.create(Registries.STRUCTURE, Relict.id("unmanned_ruins"));

}
