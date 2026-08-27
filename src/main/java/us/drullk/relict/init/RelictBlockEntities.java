package us.drullk.relict.init;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import us.drullk.relict.Relict;
import us.drullk.relict.block.cipherchest.CipherChestBlockEntity;

import java.util.Set;

public class RelictBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, Relict.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CipherChestBlockEntity>> CIPHER_CHEST = BLOCK_ENTITY_TYPES.register("cipher_chest",
            () -> new BlockEntityType<>(CipherChestBlockEntity::new, Set.of(RelictBlocks.CIPHER_CHEST.get())));

}
