package us.drullk.relict.init;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import us.drullk.relict.Relict;
import us.drullk.relict.block.RelictPortalBlock;

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

}
