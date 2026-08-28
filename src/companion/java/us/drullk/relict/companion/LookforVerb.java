package us.drullk.relict.companion;

import com.google.gson.JsonObject;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * {@code lookfor &lt;block-id&gt; [radius]}: faces the nearest instance within the search volume --
 * position is left alone, only rotation changes. A self-correction verb: point the camera at a known
 * subject without having to compute its exact coordinates first.
 */
final class LookforVerb {

    private LookforVerb() {
    }

    static void plan(List<Step> steps, JsonObject request, JobContext ctx) {
        if (!request.has("block")) {
            throw new CompanionException("lookfor: missing \"block\"");
        }
        String blockId = request.get("block").getAsString();
        int radius = request.has("radius") ? request.get("radius").getAsInt() : 16;

        steps.add(Steps.once(c -> {
            ClientLevel level = c.level();
            LocalPlayer player = c.mc().player;
            if (player == null) {
                throw new CompanionException("lookfor: no player");
            }
            BlockPos origin = player.blockPosition();
            BlockPos nearest = null;
            long bestDistSq = Long.MAX_VALUE;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        BlockPos pos = origin.offset(dx, dy, dz);
                        BlockState state = level.getBlockState(pos);
                        if (state.isAir()) {
                            continue;
                        }
                        String key = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                        if (!key.equals(blockId)) {
                            continue;
                        }
                        long distSq = (long) dx * dx + (long) dy * dy + (long) dz * dz;
                        if (distSq < bestDistSq) {
                            bestDistSq = distSq;
                            nearest = pos;
                        }
                    }
                }
            }
            if (nearest == null) {
                throw new CompanionException("lookfor: no \"" + blockId + "\" found within " + radius + " blocks");
            }
            Vec3 eye = player.getEyePosition();
            Vec3 target = Vec3.atCenterOf(nearest);
            float[] yawPitch = lookAt(eye, target);
            c.extra.addProperty("foundX", nearest.getX());
            c.extra.addProperty("foundY", nearest.getY());
            c.extra.addProperty("foundZ", nearest.getZ());
            c.extra.addProperty("yaw", yawPitch[0]);
            c.extra.addProperty("pitch", yawPitch[1]);
        }));
        // `~ ~ ~` keeps position; only yaw/pitch (filled in by the search step above) change -- built
        // lazily since the values aren't known until that step has actually run.
        steps.add(Steps.lazy(c -> CommandExec.run("tp @s ~ ~ ~ %s %s".formatted(
                c.extra.get("yaw").getAsString(), c.extra.get("pitch").getAsString()))));
    }

    /** Same live-verified yaw convention as {@link Framing#frame} -- forward = (sin(yaw), -cos(yaw)) in
     * (x, z), so the inverse is {@code atan2(dx, -dz)}, not the more commonly assumed {@code atan2(dz, dx) - 90}. */
    private static float[] lookAt(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dx, -dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        return new float[]{yaw, pitch};
    }

}
