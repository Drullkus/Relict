package us.drullk.relict.companion;

import com.google.gson.JsonObject;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * {@code frame &lt;target&gt;}: computes the target's AABB and positions the camera so the subject fills
 * ~2/3 of frame (see {@link Framing}) -- distance from AABB size + FOV, never a fixed offset. Moves the
 * player/camera only; capturing is a separate {@code shot} call (or is folded into the checklist verbs,
 * which use the same math directly).
 */
final class FrameVerb {

    private FrameVerb() {
    }

    static void plan(List<Step> steps, JsonObject request, JobContext ctx) {
        if (!request.has("target")) {
            throw new CompanionException("frame: missing \"target\"");
        }
        AABB subject = Framing.targetAabb(request.getAsJsonObject("target"));
        double heading = request.has("heading") ? request.get("heading").getAsDouble() : 200.0;
        double fov = request.has("fov") ? request.get("fov").getAsDouble() : 0.0;
        String dimension = request.has("dimension") ? request.get("dimension").getAsString() : null;

        Framing.Pose pose = Framing.frame(subject, heading, fov);
        steps.add(CommandExec.run(Cmd.in(dimension, Cmd.tp(pose))));
        steps.add(Steps.settle(dimension != null ? 60 : 25));
        steps.add(Steps.once(c -> {
            c.extra.addProperty("x", pose.x());
            c.extra.addProperty("y", pose.y());
            c.extra.addProperty("z", pose.z());
            c.extra.addProperty("yaw", pose.yaw());
            c.extra.addProperty("pitch", pose.pitch());
        }));
    }

}
