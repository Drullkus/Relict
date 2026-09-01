package us.drullk.relict.gametest;

import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import us.drullk.relict.Relict;
import us.drullk.relict.init.RelictCreativeTabs;
import us.drullk.relict.init.RelictItems;

import java.util.HashSet;
import java.util.Set;

/**
 * Cross-references every item {@link RelictItems#ITEMS} registers against {@link RelictCreativeTabs#MARS_TAB}'s
 * built contents, so an item registered without a matching {@code output.accept(...)} call fails a test
 * instead of silently missing from the tab.
 */
public final class CreativeTabGameTests {

    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");

    // SPENT_VIZARD is reached only by RelictEvents transmuting a worn-out VITAL_VIZARD in place; a copy
    // picked from the tab would be indistinguishable from one earned in play, so it stays tab-exempt.
    private static final Set<Item> TAB_EXEMPT = Set.of(RelictItems.SPENT_VIZARD.get());

    private CreativeTabGameTests() {
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        event.registerTest(Relict.id("creative_tab_has_every_item"), new RelictFunctionGameTestInstance(
                CreativeTabGameTests::everyItemInTab, Component.literal("Mars tab: every registered item is reachable (or exempted)"),
                new TestData<>(environment, EMPTY_STRUCTURE, 20, 0, true)));
    }

    private static void everyItemInTab(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CreativeModeTab tab = RelictCreativeTabs.MARS_TAB.value();
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(), true, level.registryAccess()));

        Set<Item> tabItems = new HashSet<>();
        for (ItemStack stack : tab.getDisplayItems()) {
            tabItems.add(stack.getItem());
        }

        for (DeferredHolder<Item, ? extends Item> entry : RelictItems.ITEMS.getEntries()) {
            Item item = entry.get();
            if (TAB_EXEMPT.contains(item)) {
                continue;
            }
            helper.assertTrue(tabItems.contains(item), "missing from the Mars creative tab: " + entry.getId());
        }
        helper.succeed();
    }

}
