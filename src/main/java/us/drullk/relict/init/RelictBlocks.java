package us.drullk.relict.init;

import net.minecraft.util.ColorRGBA;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ColoredFallingBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import us.drullk.relict.Relict;
import us.drullk.relict.block.DrySnowLayerBlock;
import us.drullk.relict.block.DustLayerBlock;
import us.drullk.relict.block.RelictPortalBlock;
import us.drullk.relict.block.cipherchest.CipherChestBlock;
import us.drullk.relict.block.wreck.LabMastBlock;
import us.drullk.relict.block.wreck.LabShaftBlock;
import us.drullk.relict.block.wreck.SolarPanelBlock;

import java.util.function.UnaryOperator;

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

    public static final DeferredBlock<SlabBlock> LAB_SLAB = BLOCKS.registerBlock("lab_slab", SlabBlock::new, properties -> properties
            .mapColor(MapColor.METAL)
            .strength(WRECK_HULL_HARDNESS, WRECK_HULL_RESISTANCE)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops());

    public static final DeferredBlock<StairBlock> LAB_STAIRS = BLOCKS.registerBlock("lab_stairs",
            properties -> new StairBlock(LAB_BLOCK.get().defaultBlockState(), properties), properties -> properties
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

    private static final float LAYER_HARDNESS = 0.1F;
    private static final float DRY_SNOW_BLOCK_HARDNESS = 0.2F;

    public static final DeferredBlock<DustLayerBlock> DUST_LAYER = BLOCKS.registerBlock("dust_layer", DustLayerBlock::new, properties -> properties
            .mapColor(MapColor.TERRACOTTA_ORANGE)
            .strength(LAYER_HARDNESS)
            .sound(SoundType.SAND)
            .noOcclusion()
            .pushReaction(PushReaction.DESTROY)
            .randomTicks()
            .requiresCorrectToolForDrops());

    public static final DeferredBlock<net.minecraft.world.level.block.Block> DRY_SNOW = BLOCKS.registerSimpleBlock("dry_snow", properties -> properties
            .mapColor(MapColor.SNOW)
            .strength(DRY_SNOW_BLOCK_HARDNESS)
            .sound(SoundType.SNOW)
            .requiresCorrectToolForDrops());

    public static final DeferredBlock<DrySnowLayerBlock> DRY_SNOW_LAYER = BLOCKS.registerBlock("dry_snow_layer", DrySnowLayerBlock::new, properties -> properties
            .mapColor(MapColor.SNOW)
            .strength(LAYER_HARDNESS)
            .sound(SoundType.SNOW)
            .noOcclusion()
            .pushReaction(PushReaction.DESTROY)
            .requiresCorrectToolForDrops());

    private static final ColorRGBA BASALT_SAND_DUST_COLOR = new ColorRGBA(0x3D3E47);
    private static final float SAND_HARDNESS = 0.5F;

    public static final DeferredBlock<ColoredFallingBlock> BASALT_SAND = BLOCKS.registerBlock("basalt_sand",
            properties -> new ColoredFallingBlock(BASALT_SAND_DUST_COLOR, properties), properties -> properties
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(SAND_HARDNESS)
                    .sound(SoundType.SAND));

    private static final float CIPHER_CHEST_HARDNESS = 3.0F;
    private static final float CIPHER_CHEST_RESISTANCE = 6.0F;

    public static final DeferredBlock<CipherChestBlock> CIPHER_CHEST = BLOCKS.registerBlock("cipher_chest", CipherChestBlock::new, properties -> properties
            .mapColor(MapColor.STONE)
            .strength(CIPHER_CHEST_HARDNESS, CIPHER_CHEST_RESISTANCE)
            .sound(SoundType.STONE)
            .noOcclusion()
            .requiresCorrectToolForDrops());

    // Stone-class strength, matching vanilla stone (1.5/6.0) uniformly across block/slab/stairs/wall --
    // vanilla itself varies this slightly per shape (e.g. smooth_stone_slab is 2.0); a reasonable, tunable default.
    private static final float RUIN_STONE_HARDNESS = 1.5F;
    private static final float RUIN_STONE_RESISTANCE = 6.0F;

    private static UnaryOperator<BlockBehaviour.Properties> ruinStone(MapColor mapColor) {
        return properties -> properties
                .mapColor(mapColor)
                .strength(RUIN_STONE_HARDNESS, RUIN_STONE_RESISTANCE)
                .sound(SoundType.STONE)
                .requiresCorrectToolForDrops();
    }

    public static final DeferredBlock<Block> OCHRE = BLOCKS.registerSimpleBlock("ochre", ruinStone(MapColor.COLOR_ORANGE));
    public static final DeferredBlock<SlabBlock> OCHRE_SLAB = BLOCKS.registerBlock("ochre_slab", SlabBlock::new, ruinStone(MapColor.COLOR_ORANGE));
    public static final DeferredBlock<StairBlock> OCHRE_STAIRS = BLOCKS.registerBlock("ochre_stairs",
            properties -> new StairBlock(OCHRE.get().defaultBlockState(), properties), ruinStone(MapColor.COLOR_ORANGE));
    public static final DeferredBlock<WallBlock> OCHRE_WALL = BLOCKS.registerBlock("ochre_wall", WallBlock::new, ruinStone(MapColor.COLOR_ORANGE));

    public static final DeferredBlock<Block> POLISHED_OCHRE = BLOCKS.registerSimpleBlock("polished_ochre", ruinStone(MapColor.COLOR_ORANGE));
    public static final DeferredBlock<SlabBlock> POLISHED_OCHRE_SLAB = BLOCKS.registerBlock("polished_ochre_slab", SlabBlock::new, ruinStone(MapColor.COLOR_ORANGE));
    public static final DeferredBlock<StairBlock> POLISHED_OCHRE_STAIRS = BLOCKS.registerBlock("polished_ochre_stairs",
            properties -> new StairBlock(POLISHED_OCHRE.get().defaultBlockState(), properties), ruinStone(MapColor.COLOR_ORANGE));
    public static final DeferredBlock<WallBlock> POLISHED_OCHRE_WALL = BLOCKS.registerBlock("polished_ochre_wall", WallBlock::new, ruinStone(MapColor.COLOR_ORANGE));

    public static final DeferredBlock<Block> SERPENTINE = BLOCKS.registerSimpleBlock("serpentine", ruinStone(MapColor.COLOR_GREEN));
    public static final DeferredBlock<SlabBlock> SERPENTINE_SLAB = BLOCKS.registerBlock("serpentine_slab", SlabBlock::new, ruinStone(MapColor.COLOR_GREEN));
    public static final DeferredBlock<StairBlock> SERPENTINE_STAIRS = BLOCKS.registerBlock("serpentine_stairs",
            properties -> new StairBlock(SERPENTINE.get().defaultBlockState(), properties), ruinStone(MapColor.COLOR_GREEN));
    public static final DeferredBlock<WallBlock> SERPENTINE_WALL = BLOCKS.registerBlock("serpentine_wall", WallBlock::new, ruinStone(MapColor.COLOR_GREEN));

    public static final DeferredBlock<Block> POLISHED_SERPENTINE = BLOCKS.registerSimpleBlock("polished_serpentine", ruinStone(MapColor.COLOR_GREEN));
    public static final DeferredBlock<SlabBlock> POLISHED_SERPENTINE_SLAB = BLOCKS.registerBlock("polished_serpentine_slab", SlabBlock::new, ruinStone(MapColor.COLOR_GREEN));
    public static final DeferredBlock<StairBlock> POLISHED_SERPENTINE_STAIRS = BLOCKS.registerBlock("polished_serpentine_stairs",
            properties -> new StairBlock(POLISHED_SERPENTINE.get().defaultBlockState(), properties), ruinStone(MapColor.COLOR_GREEN));
    public static final DeferredBlock<WallBlock> POLISHED_SERPENTINE_WALL = BLOCKS.registerBlock("polished_serpentine_wall", WallBlock::new, ruinStone(MapColor.COLOR_GREEN));

}
