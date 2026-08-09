package us.drullk.fossilplanet.init;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import us.drullk.fossilplanet.TheFossilizedPlanet;

public class TFPBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(TheFossilizedPlanet.MODID);

    public static final DeferredBlock<Block> EXAMPLE_BLOCK = BLOCKS.registerSimpleBlock("example_block", p -> p.mapColor(MapColor.STONE));

}
