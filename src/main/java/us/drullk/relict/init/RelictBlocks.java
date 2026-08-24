package us.drullk.relict.init;

import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import us.drullk.relict.Relict;
import us.drullk.relict.block.RelictPortalBlock;
import us.drullk.relict.block.wreck.LabMastBlock;
import us.drullk.relict.block.wreck.LabShaftBlock;
import us.drullk.relict.block.wreck.SolarPanelBlock;

public class RelictBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Relict.MODID);

    public static final DeferredBlock<RelictPortalBlock> MARS_PORTAL = BLOCKS.registerBlock("mars_portal", RelictPortalBlock::new, properties -> properties
            .noCollision()
            .randomTicks()
            .strength(-1.0F, 3600000.0F)
            .lightLevel(_ -> 11)
            .sound(SoundType.GLASS)
            .pushReaction(PushReaction.BLOCK)
            .noLootTable()
            .noOcclusion());

    private static final float WRECK_HULL_HARDNESS = 3.0F;
    private static final float WRECK_HULL_RESISTANCE = 6.0F;

    public static final DeferredBlock<net.minecraft.world.level.block.Block> LAB_BLOCK = BLOCKS.registerSimpleBlock("lab_block", properties -> properties
            .mapColor(MapColor.METAL)
            .strength(WRECK_HULL_HARDNESS, WRECK_HULL_RESISTANCE)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops());

    public static final DeferredBlock<LabShaftBlock> LAB_SHAFT = BLOCKS.registerBlock("lab_shaft", LabShaftBlock::new, properties -> properties
            .mapColor(MapColor.METAL)
            .strength(WRECK_HULL_HARDNESS, WRECK_HULL_RESISTANCE)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops());

    public static final DeferredBlock<LabMastBlock> LAB_MAST = BLOCKS.registerBlock("lab_mast", LabMastBlock::new, properties -> properties
            .mapColor(MapColor.METAL)
            .strength(WRECK_HULL_HARDNESS, WRECK_HULL_RESISTANCE)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops());

    public static final DeferredBlock<RotatedPillarBlock> ROVER_WHEEL = BLOCKS.registerBlock("rover_wheel", RotatedPillarBlock::new, properties -> properties
            .mapColor(MapColor.METAL)
            .strength(WRECK_HULL_HARDNESS, WRECK_HULL_RESISTANCE)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops());

    private static final float SOLAR_PANEL_HARDNESS = 0.1F;

    public static final DeferredBlock<SolarPanelBlock> SOLAR_PANEL = BLOCKS.registerBlock("solar_panel", SolarPanelBlock::new, properties -> properties
            .mapColor(MapColor.METAL)
            .strength(SOLAR_PANEL_HARDNESS)
            .sound(SoundType.METAL)
            .noOcclusion()
            .randomTicks()
            .requiresCorrectToolForDrops());

    public static final DeferredBlock<SolarPanelBlock> SOLAR_PANEL_SPRINKLED = BLOCKS.registerBlock("solar_panel_sprinkled", SolarPanelBlock::new, properties -> properties
            .mapColor(MapColor.METAL)
            .strength(SOLAR_PANEL_HARDNESS)
            .sound(SoundType.METAL)
            .noOcclusion()
            .randomTicks()
            .requiresCorrectToolForDrops());

    public static final DeferredBlock<SolarPanelBlock> SOLAR_PANEL_DUSTED = BLOCKS.registerBlock("solar_panel_dusted", SolarPanelBlock::new, properties -> properties
            .mapColor(MapColor.METAL)
            .strength(SOLAR_PANEL_HARDNESS)
            .sound(SoundType.METAL)
            .noOcclusion()
            .randomTicks()
            .requiresCorrectToolForDrops());

    public static final DeferredBlock<SolarPanelBlock> SOLAR_PANEL_SANDED = BLOCKS.registerBlock("solar_panel_sanded", SolarPanelBlock::new, properties -> properties
            .mapColor(MapColor.METAL)
            .strength(SOLAR_PANEL_HARDNESS)
            .sound(SoundType.METAL)
            .noOcclusion()
            .randomTicks()
            .requiresCorrectToolForDrops());

}
