package us.drullk.relict.companion;

import com.google.gson.JsonObject;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * {@code shot &lt;pose-spec&gt;}: a single capture -- explicit pos/yaw/pitch, or a {@code frame} target
 * computed the same way {@link FrameVerb} does; gamemode per shot (spectator for world/block, creative
 * for held/PIP), GUI on/off, optional dimension.
 */
final class ShotVerb {

    private ShotVerb() {
    }

    static void plan(List<Step> steps, JsonObject request, JobContext ctx) {
        String name = request.has("name") ? request.get("name").getAsString() : ctx.id;
        String dimension = request.has("dimension") ? request.get("dimension").getAsString() : null;
        String gamemode = request.has("gamemode") ? request.get("gamemode").getAsString() : null;
        String camera = request.has("camera") ? request.get("camera").getAsString() : null;
        boolean hudOff = request.has("hud") && !request.get("hud").getAsBoolean();

        if (gamemode != null) {
            steps.add(CommandExec.run("gamemode " + gamemode));
        }

        double x, y, z;
        float yaw, pitch;
        if (request.has("target")) {
            AABB subject = Framing.targetAabb(request.getAsJsonObject("target"));
            double heading = request.has("heading") ? request.get("heading").getAsDouble() : 200.0;
            double fov = request.has("fov") ? request.get("fov").getAsDouble() : 0.0;
            Framing.Pose pose = Framing.frame(subject, heading, fov);
            x = pose.x();
            y = pose.y();
            z = pose.z();
            yaw = pose.yaw();
            pitch = pose.pitch();
        } else if (request.has("pos")) {
            var pos = request.getAsJsonArray("pos");
            x = pos.get(0).getAsDouble();
            y = pos.get(1).getAsDouble();
            z = pos.get(2).getAsDouble();
            yaw = request.has("yaw") ? request.get("yaw").getAsFloat() : 0f;
            pitch = request.has("pitch") ? request.get("pitch").getAsFloat() : 0f;
        } else {
            throw new CompanionException("shot: needs either \"target\" or \"pos\"");
        }

        steps.add(CommandExec.run(Cmd.in(dimension, Cmd.tp(x, y, z, yaw, pitch))));
        steps.add(Steps.settle(dimension != null ? 60 : 25));
        if (camera != null) {
            steps.add(Steps.once(c -> ClientOps.setCameraType(camera)));
            steps.add(Steps.settle(10));
        }
        if (hudOff) {
            steps.add(Steps.once(c -> ClientOps.setHudHidden(true)));
            steps.add(Steps.settle(10));
        }
        steps.add(ClientOps.capture(ctx.id + "_" + name + ".png"));
        if (hudOff) {
            steps.add(Steps.once(c -> ClientOps.setHudHidden(false)));
        }
    }

}
