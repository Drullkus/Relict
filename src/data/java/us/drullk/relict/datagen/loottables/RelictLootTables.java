package us.drullk.relict.datagen.loottables;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import us.drullk.relict.Relict;
import us.drullk.relict.init.RelictItems;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class RelictLootTables extends LootTableProvider {

    public static final ResourceKey<LootTable> PORTAL_RUIN = key("chests/portal_ruin");

    public static final ResourceKey<LootTable> RUIN_A_KNOWLEDGE = key("ruin_a/knowledge");
    public static final ResourceKey<LootTable> RUIN_A_MATERIAL = key("ruin_a/material");

    public static final ResourceKey<LootTable> UNMANNED_WRECK = key("chests/unmanned_wreck");

    public RelictLootTables(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, Set.of(), List.of(
                new SubProviderEntry(_ -> generator -> {
                    generator.accept(PORTAL_RUIN, kitLoot());
                    generator.accept(RUIN_A_KNOWLEDGE, kitLoot());
                    generator.accept(RUIN_A_MATERIAL, kitLoot());
                    generator.accept(UNMANNED_WRECK, locatorLoot());
                }, LootContextParamSets.CHEST)
        ), registries);
    }

    private static LootTable.Builder locatorLoot() {
        return LootTable.lootTable()
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .add(LootItem.lootTableItem(RelictItems.SEISMIC_LOCATOR.get())));
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
