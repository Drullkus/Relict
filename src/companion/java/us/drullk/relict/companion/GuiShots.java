package us.drullk.relict.companion;

import net.minecraft.client.gui.screens.inventory.InventoryScreen;

import java.util.ArrayList;
import java.util.List;

/**
 * The inventory-GUI-slot shot: opens the real inventory screen client-side (no keypress injection),
 * captures it, then closes it again -- this module is client-only, so it may freely use client classes
 * (Screen, Minecraft) directly.
 */
final class GuiShots {

    private GuiShots() {
    }

    static List<Step> inventory(String fileName) {
        List<Step> steps = new ArrayList<>();
        steps.add(Steps.once(ctx -> ctx.mc().gui.setScreen(new InventoryScreen(ctx.mc().player))));
        steps.add(Steps.settle(10));
        steps.add(ClientOps.capture(fileName + ".png"));
        steps.add(Steps.once(ctx -> ctx.mc().gui.setScreen(null)));
        return steps;
    }

}
