package us.drullk.relict.datagen.loottables.wreck;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.item.Items;
import us.drullk.relict.block.wreck.SolarPanelDecay;
import us.drullk.relict.init.RelictBlocks;

import java.util.function.BiConsumer;

/**
 * Loot tables for the Unmanned Wreck block family: the ten self-drop mining tables, and the three
 * per-stage brush loot tables (sprinkled/dusted/sanded) that replace {@code SolarPanelDecay}'s old
 * hardcoded red sand rolls. The table keys themselves live on {@link SolarPanelDecay} (main source set,
 * since it rolls them at runtime); this class only generates the JSON those keys point at.
 */
public final class WreckLootTables {

    private WreckLootTables() {
    }

    public static void blockDrops(BiConsumer<ResourceKey<LootTable>, LootTable.Builder> generator) {
        generator.accept(blockLootKey(RelictBlocks.LAB_BLOCK.get()), selfDrop(RelictBlocks.LAB_BLOCK.get()));
        generator.accept(blockLootKey(RelictBlocks.LAB_SLAB.get()), selfDrop(RelictBlocks.LAB_SLAB.get()));
        generator.accept(blockLootKey(RelictBlocks.LAB_STAIRS.get()), selfDrop(RelictBlocks.LAB_STAIRS.get()));
        generator.accept(blockLootKey(RelictBlocks.LAB_SHAFT.get()), selfDrop(RelictBlocks.LAB_SHAFT.get()));
        generator.accept(blockLootKey(RelictBlocks.LAB_MAST.get()), selfDrop(RelictBlocks.LAB_MAST.get()));
        generator.accept(blockLootKey(RelictBlocks.ROVER_WHEEL.get()), selfDrop(RelictBlocks.ROVER_WHEEL.get()));
        generator.accept(blockLootKey(RelictBlocks.SOLAR_PANEL.get()), selfDrop(RelictBlocks.SOLAR_PANEL.get()));
        generator.accept(blockLootKey(RelictBlocks.SOLAR_PANEL_SPRINKLED.get()), selfDrop(RelictBlocks.SOLAR_PANEL_SPRINKLED.get()));
        generator.accept(blockLootKey(RelictBlocks.SOLAR_PANEL_DUSTED.get()), selfDrop(RelictBlocks.SOLAR_PANEL_DUSTED.get()));
        generator.accept(blockLootKey(RelictBlocks.SOLAR_PANEL_SANDED.get()), selfDrop(RelictBlocks.SOLAR_PANEL_SANDED.get()));
    }

    /**
     * One table per dusty stage, each a single {@code minecraft:red_sand} entry gated by a
     * {@code random_chance} condition — the stage's own brush-drop chance. Clean has no table: brushing it
     * is a no-op, not a table with a zero-chance roll.
     */
    public static void brushDrops(BiConsumer<ResourceKey<LootTable>, LootTable.Builder> generator) {
        generator.accept(SolarPanelDecay.BRUSH_SOLAR_PANEL_SPRINKLED, redSandChance(0.01F));
        generator.accept(SolarPanelDecay.BRUSH_SOLAR_PANEL_DUSTED, redSandChance(0.02F));
        generator.accept(SolarPanelDecay.BRUSH_SOLAR_PANEL_SANDED, redSandChance(0.05F));
    }

    private static LootTable.Builder redSandChance(float chance) {
        return LootTable.lootTable().withPool(LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1.0F))
                .add(LootItem.lootTableItem(Items.RED_SAND).when(LootItemRandomChanceCondition.randomChance(chance))));
    }

    private static ResourceKey<LootTable> blockLootKey(Block block) {
        return block.getLootTable().orElseThrow(() -> new IllegalStateException("Block " + block + " has no loot table key"));
    }

    private static LootTable.Builder selfDrop(Block block) {
        return LootTable.lootTable().withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1.0F)).add(LootItem.lootTableItem(block)));
    }

}
