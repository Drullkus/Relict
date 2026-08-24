package us.drullk.relict.datagen.loottables;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import us.drullk.relict.Relict;
import us.drullk.relict.init.RelictBlocks;
import us.drullk.relict.init.RelictItems;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

public class RelictLootTables extends LootTableProvider {

    public static final ResourceKey<LootTable> PORTAL_RUIN = key("chests/portal_ruin");

    public static final ResourceKey<LootTable> RUIN_A_KNOWLEDGE = key("ruin_a/knowledge");
    public static final ResourceKey<LootTable> RUIN_A_MATERIAL = key("ruin_a/material");

    public static final ResourceKey<LootTable> UNMANNED_WRECK = key("chests/unmanned_wreck");

    public RelictLootTables(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, Set.of(), List.of(
                new SubProviderEntry(_ -> RelictLootTables::generateChestLoot, LootContextParamSets.CHEST),
                new SubProviderEntry(_ -> RelictLootTables::generateBlockDrops, LootContextParamSets.BLOCK)
        ), registries);
    }

    private static void generateChestLoot(BiConsumer<ResourceKey<LootTable>, LootTable.Builder> generator) {
        generator.accept(PORTAL_RUIN, kitLoot());
        generator.accept(RUIN_A_KNOWLEDGE, knowledgeLoot());
        generator.accept(RUIN_A_MATERIAL, kitLoot());
        generator.accept(UNMANNED_WRECK, locatorLoot());
    }

    private static void generateBlockDrops(BiConsumer<ResourceKey<LootTable>, LootTable.Builder> generator) {
        generator.accept(blockLootKey(RelictBlocks.LAB_BLOCK.get()), selfDrop(RelictBlocks.LAB_BLOCK.get()));
        generator.accept(blockLootKey(RelictBlocks.LAB_SHAFT.get()), selfDrop(RelictBlocks.LAB_SHAFT.get()));
        generator.accept(blockLootKey(RelictBlocks.LAB_MAST.get()), selfDrop(RelictBlocks.LAB_MAST.get()));
        generator.accept(blockLootKey(RelictBlocks.ROVER_WHEEL.get()), selfDrop(RelictBlocks.ROVER_WHEEL.get()));
        generator.accept(blockLootKey(RelictBlocks.SOLAR_PANEL.get()), selfDrop(RelictBlocks.SOLAR_PANEL.get()));
        generator.accept(blockLootKey(RelictBlocks.SOLAR_PANEL_SPRINKLED.get()), selfDrop(RelictBlocks.SOLAR_PANEL_SPRINKLED.get()));
        generator.accept(blockLootKey(RelictBlocks.SOLAR_PANEL_DUSTED.get()), selfDrop(RelictBlocks.SOLAR_PANEL_DUSTED.get()));
        generator.accept(blockLootKey(RelictBlocks.SOLAR_PANEL_SANDED.get()), selfDrop(RelictBlocks.SOLAR_PANEL_SANDED.get()));
    }

    private static ResourceKey<LootTable> blockLootKey(Block block) {
        return block.getLootTable().orElseThrow(() -> new IllegalStateException("Block " + block + " has no loot table key"));
    }

    private static LootTable.Builder selfDrop(Block block) {
        return LootTable.lootTable().withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1.0F)).add(LootItem.lootTableItem(block)));
    }

    private static LootTable.Builder locatorLoot() {
        return LootTable.lootTable()
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .add(LootItem.lootTableItem(RelictItems.SEISMIC_LOCATOR.get())));
    }

    private static LootTable.Builder knowledgeLoot() {
        return kitLoot().withPool(LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1.0F))
                .add(LootItem.lootTableItem(RelictItems.WEATHERGLASS.get())));
    }

    private static LootTable.Builder kitLoot() {
        return LootTable.lootTable()
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .add(LootItem.lootTableItem(RelictItems.VITAL_VIZARD.get())))
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .add(LootItem.lootTableItem(RelictItems.RANGING_CAISSON.get()))
                        .add(LootItem.lootTableItem(RelictItems.RESTLESS_STRIDERS.get()))
                        .add(LootItem.lootTableItem(RelictItems.GROUNDING_TREADS.get())))
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .add(LootItem.lootTableItem(RelictItems.VITAL_VIZARD.get())
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(2.0F, 3.0F)))))
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .add(LootItem.lootTableItem(RelictItems.SPENT_VIZARD.get())
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(2.0F, 3.0F)))))
                ;
    }

    private static ResourceKey<LootTable> key(String path) {
        return ResourceKey.create(Registries.LOOT_TABLE, Relict.id(path));
    }
}
