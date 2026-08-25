package us.drullk.relict.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import us.drullk.relict.Relict;

public class RelictCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Relict.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MARS_TAB = CREATIVE_MODE_TABS.register("mars_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.relict")) //The language key for the title of your CreativeModeTab
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> RelictItems.VITAL_VIZARD.value().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(RelictItems.VITAL_VIZARD.value());
                output.accept(RelictItems.RANGING_CAISSON.value());
                output.accept(RelictItems.RESTLESS_STRIDERS.value());
                output.accept(RelictItems.GROUNDING_TREADS.value());

                output.accept(RelictItems.LAB_BLOCK.value());
                output.accept(RelictItems.LAB_SLAB.value());
                output.accept(RelictItems.LAB_STAIRS.value());
                output.accept(RelictItems.LAB_SHAFT.value());
                output.accept(RelictItems.LAB_MAST.value());
                output.accept(RelictItems.ROVER_WHEEL.value());
                output.accept(RelictItems.SOLAR_PANEL.value());
                output.accept(RelictItems.SOLAR_PANEL_SPRINKLED.value());
                output.accept(RelictItems.SOLAR_PANEL_DUSTED.value());
                output.accept(RelictItems.SOLAR_PANEL_SANDED.value());
            }).build());

}
