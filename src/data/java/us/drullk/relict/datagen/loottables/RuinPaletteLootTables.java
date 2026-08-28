package us.drullk.relict.datagen.loottables;

import net.minecraft.advancements.predicates.StatePropertiesPredicate;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import us.drullk.relict.init.RelictBlocks;

import java.util.function.BiConsumer;

public final class RuinPaletteLootTables {

    private RuinPaletteLootTables() {
    }

    public static void blockDrops(BiConsumer<ResourceKey<LootTable>, LootTable.Builder> generator) {
        generator.accept(blockLootKey(RelictBlocks.OCHRE.get()), selfDrop(RelictBlocks.OCHRE.get()));
        generator.accept(blockLootKey(RelictBlocks.OCHRE_SLAB.get()), slabDrop(RelictBlocks.OCHRE_SLAB.get()));
        generator.accept(blockLootKey(RelictBlocks.OCHRE_STAIRS.get()), selfDrop(RelictBlocks.OCHRE_STAIRS.get()));
        generator.accept(blockLootKey(RelictBlocks.OCHRE_WALL.get()), selfDrop(RelictBlocks.OCHRE_WALL.get()));

        generator.accept(blockLootKey(RelictBlocks.POLISHED_OCHRE.get()), selfDrop(RelictBlocks.POLISHED_OCHRE.get()));
        generator.accept(blockLootKey(RelictBlocks.POLISHED_OCHRE_SLAB.get()), slabDrop(RelictBlocks.POLISHED_OCHRE_SLAB.get()));
        generator.accept(blockLootKey(RelictBlocks.POLISHED_OCHRE_STAIRS.get()), selfDrop(RelictBlocks.POLISHED_OCHRE_STAIRS.get()));
        generator.accept(blockLootKey(RelictBlocks.POLISHED_OCHRE_WALL.get()), selfDrop(RelictBlocks.POLISHED_OCHRE_WALL.get()));

        generator.accept(blockLootKey(RelictBlocks.SERPENTINE.get()), selfDrop(RelictBlocks.SERPENTINE.get()));
        generator.accept(blockLootKey(RelictBlocks.SERPENTINE_SLAB.get()), slabDrop(RelictBlocks.SERPENTINE_SLAB.get()));
        generator.accept(blockLootKey(RelictBlocks.SERPENTINE_STAIRS.get()), selfDrop(RelictBlocks.SERPENTINE_STAIRS.get()));
        generator.accept(blockLootKey(RelictBlocks.SERPENTINE_WALL.get()), selfDrop(RelictBlocks.SERPENTINE_WALL.get()));

        generator.accept(blockLootKey(RelictBlocks.POLISHED_SERPENTINE.get()), selfDrop(RelictBlocks.POLISHED_SERPENTINE.get()));
        generator.accept(blockLootKey(RelictBlocks.POLISHED_SERPENTINE_SLAB.get()), slabDrop(RelictBlocks.POLISHED_SERPENTINE_SLAB.get()));
        generator.accept(blockLootKey(RelictBlocks.POLISHED_SERPENTINE_STAIRS.get()), selfDrop(RelictBlocks.POLISHED_SERPENTINE_STAIRS.get()));
        generator.accept(blockLootKey(RelictBlocks.POLISHED_SERPENTINE_WALL.get()), selfDrop(RelictBlocks.POLISHED_SERPENTINE_WALL.get()));
    }

    private static ResourceKey<LootTable> blockLootKey(Block block) {
        return block.getLootTable().orElseThrow(() -> new IllegalStateException("Block " + block + " has no loot table key"));
    }

    private static LootTable.Builder selfDrop(Block block) {
        return LootTable.lootTable().withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1.0F)).add(LootItem.lootTableItem(block)));
    }

    private static LootTable.Builder slabDrop(SlabBlock slab) {
        return LootTable.lootTable().withPool(LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1.0F))
                .add(LootItem.lootTableItem(slab)
                        .apply(SetItemCountFunction.setCount(ConstantValue.exactly(2.0F))
                                .when(LootItemBlockStatePropertyCondition.hasBlockStateProperties(slab)
                                        .setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(SlabBlock.TYPE, SlabType.DOUBLE))))));
    }

}
