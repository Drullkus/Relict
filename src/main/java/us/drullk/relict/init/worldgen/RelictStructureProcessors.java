package us.drullk.relict.init.worldgen;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import us.drullk.relict.Relict;

public class RelictStructureProcessors {

    public static final ResourceKey<StructureProcessorList> UNMANNED_WRECK_SOLAR_PANEL_DECAY = ResourceKey.create(Registries.PROCESSOR_LIST, Relict.id("unmanned_wreck/solar_panel_decay"));

}
