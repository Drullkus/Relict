package us.drullk.relict.datagen.loottables;

import net.minecraft.advancements.predicates.StatePropertiesPredicate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import us.drullk.relict.init.RelictBlocks;

/**
 * Loot for the dust layer + dry snow pair, split out of {@link RelictLootTables} into its own single-purpose
 * class. The layer drops itself, one item per layer count — [VANILLACOPY, pattern] vanilla's own
 * {@code minecraft:blocks/snow.json}, which conditions one pool entry per {@code layers} value rather than
 * reading the count off the block state directly (loot functions have no "copy this integer property into
 * the stack count" primitive).
 */
public final class DustLayerLootTables {

    private DustLayerLootTables() {
    }

    static LootTable.Builder dustLayer() {
        return selfDropByLayerCount(RelictBlocks.DUST_LAYER.get());
    }

    static LootTable.Builder drySnow() {
        return LootTable.lootTable().withPool(LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1.0F))
                .add(LootItem.lootTableItem(RelictBlocks.DRY_SNOW.get())));
    }

    static LootTable.Builder drySnowLayer() {
        return selfDropByLayerCount(RelictBlocks.DRY_SNOW_LAYER.get());
    }

    private static LootTable.Builder selfDropByLayerCount(Block block) {
        LootPool.Builder pool = LootPool.lootPool().setRolls(ConstantValue.exactly(1.0F));

        for (int layers = 1; layers <= 8; layers++) {
            pool.add(LootItem.lootTableItem(block)
                    .when(LootItemBlockStatePropertyCondition.hasBlockStateProperties(block)
                            .setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(BlockStateProperties.LAYERS, layers)))
                    .apply(SetItemCountFunction.setCount(ConstantValue.exactly(layers))));
        }

        return LootTable.lootTable().withPool(pool);
    }

}
