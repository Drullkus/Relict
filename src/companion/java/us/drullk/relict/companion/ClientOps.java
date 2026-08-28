package us.drullk.relict.companion;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Direct, in-code client operations that stand in for what a human would press a key or click a button
 * for: toggling the HUD off for a clean shot, and switching camera type per shot.
 */
final class ClientOps {

    private ClientOps() {
    }

    /** F1 equivalent: {@code Hud.toggle()}/{@code isHidden()} are the real public API F1's keybinding
     * itself calls (confirmed by reading {@code Hud}'s source: {@code toggle()} flips the same
     * {@code isHidden} field {@code extractRenderState} gates the crosshair/hotbar/effects/demo/
     * scoreboard/title/chat/tab-list extraction on) -- only flips state when it doesn't already match, so
     * this is idempotent to call repeatedly. IMPORTANT: HUD extraction happens once per rendered frame,
     * not per poll of this step, so callers must settle at least one tick after this before capturing --
     * a screenshot taken in the very same tick call would still read the framebuffer from before this
     * flip landed (see the callers in ShotVerb/ItemChecklistVerb/BlockChecklistVerb). */
    static void setHudHidden(boolean hidden) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null) {
            return;
        }
        if (mc.gui.hud.isHidden() != hidden) {
            mc.gui.hud.toggle();
        }
    }

    static void setCameraType(String name) {
        if (name == null) {
            return;
        }
        CameraType type = switch (name) {
            case "first_person" -> CameraType.FIRST_PERSON;
            case "third_person_back" -> CameraType.THIRD_PERSON_BACK;
            case "third_person_front" -> CameraType.THIRD_PERSON_FRONT;
            default -> throw new CompanionException("unknown camera type \"" + name + "\" "
                    + "(expected first_person, third_person_back, or third_person_front)");
        };
        Minecraft.getInstance().options.setCameraType(type);
    }

    /** A step that writes {@code fileName} under {@code run/screenshots/} via the game's own native
     * screenshot path (no keypress injection, no X involvement) and records it in the job's shot list
     * once the write actually lands on disk. */
    static Step capture(String fileName) {
        AtomicBoolean submitted = new AtomicBoolean(false);
        AtomicBoolean done = new AtomicBoolean(false);
        return ctx -> {
            Minecraft mc = ctx.mc();
            if (submitted.compareAndSet(false, true)) {
                Screenshot.grab(mc.gameDirectory, fileName, mc.gameRenderer.mainRenderTarget(), 1, message -> done.set(true));
            }
            if (done.get()) {
                ctx.shots.add("screenshots/" + fileName);
            }
            return done.get();
        };
    }

}
