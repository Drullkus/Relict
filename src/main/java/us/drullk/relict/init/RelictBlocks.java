package us.drullk.relict.init;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import us.drullk.relict.Relict;

public class RelictBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Relict.MODID);

    public static final DeferredBlock<Block> EXAMPLE_BLOCK = BLOCKS.registerSimpleBlock("example_block", p -> p.mapColor(MapColor.STONE));

}
