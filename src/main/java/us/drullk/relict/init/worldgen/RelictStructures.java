package us.drullk.relict.init.worldgen;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import us.drullk.relict.Relict;

public class RelictStructures {

    public static final ResourceKey<StructureSet> MARS_PORTAL_SET = ResourceKey.create(Registries.STRUCTURE_SET, Relict.id("mars_portal_ruin"));
    public static final ResourceKey<StructureSet> UNMANNED_WRECK_SET = ResourceKey.create(Registries.STRUCTURE_SET, Relict.id("unmanned_wreck"));
    public static final ResourceKey<StructureSet> RUIN_A_SET = ResourceKey.create(Registries.STRUCTURE_SET, Relict.id("ruin_a"));

    public static final ResourceKey<Structure> MARS_PORTAL = ResourceKey.create(Registries.STRUCTURE, Relict.id("mars_portal_ruin"));
    public static final ResourceKey<Structure> UNMANNED_WRECK = ResourceKey.create(Registries.STRUCTURE, Relict.id("unmanned_wreck"));
    public static final ResourceKey<Structure> RUIN_A = ResourceKey.create(Registries.STRUCTURE, Relict.id("ruin_a"));

}
