package us.drullk.relict.companion;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The {@code frame} verb's deterministic replacement for "think about distance": given a subject's AABB,
 * compute a camera position/yaw/pitch that fills roughly {@link #TARGET_COVERAGE} of frame, from
 * bounding-box size and FOV alone. This is what stops a flat subject (the solar-panel lesson: a
 * fixed 1-block offset shows only an edge) from ever being under-framed again.
 */
final class Framing {

    /** Target frame coverage for a framed subject: roughly two-thirds of the frame. */
    static final double TARGET_COVERAGE = 2.0 / 3.0;
    private static final double DEFAULT_FOV_DEGREES = 70.0;
    private static final double MIN_DISTANCE = 1.75;
    /** A floor on the camera's elevation angle, applied regardless of subject height. Making the vertical
     * offset purely proportional to the subject's own height (verticalSize * 0.15) is fine for a cube-ish
     * subject but degenerates for a thin one: a solar panel's true shape is ~1/16 block tall, so that
     * offset alone puts the camera almost exactly level with it -- looking at a flat surface edge-on, at
     * the right distance but the wrong angle to actually see it. */
    private static final double MIN_ELEVATION_RADIANS = Math.toRadians(20.0);

    record Pose(double x, double y, double z, float yaw, float pitch) {
    }

    private Framing() {
    }

    /** The block's own real shape (moved into world space), not a generic unit cube -- a flat block like
     * a solar panel is ~1/16 as tall as a full block, and framing off the wrong height means a
     * fixed-offset shot only grazes the edge of a flat subject. Falls back to a unit cube for a block
     * with an empty collision shape (e.g. a layer at height 0, or a non-solid decorative shape some
     * future block might use). */
    static AABB blockAabb(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        VoxelShape shape = state.getShape(level, pos);
        if (shape.isEmpty()) {
            return new AABB(pos);
        }
        return shape.bounds().move(pos.getX(), pos.getY(), pos.getZ());
    }

    /** @param headingDegrees approach direction in MC yaw convention -- purely which side the camera
     *                        retreats to, not the framing math. Verified against a live client, not
     *                        assumed: this build's {@code /tp} yaw has forward = (sin(yaw), -cos(yaw)) in
     *                        (x, z) -- yaw 0 faces north (-Z), yaw 90 faces east (+X), the opposite of a
     *                        commonly assumed convention. */
    static Pose frame(AABB subject, double headingDegrees, double fovDegrees) {
        Vec3 center = subject.getCenter();
        double horizontalSize = Math.max(subject.getXsize(), subject.getZsize());
        double verticalSize = subject.getYsize();
        double size = Math.max(Math.max(horizontalSize, verticalSize), 0.25);

        double fovRadians = Math.toRadians(fovDegrees <= 0 ? DEFAULT_FOV_DEGREES : fovDegrees);
        double distance = size / (2 * Math.tan(fovRadians * TARGET_COVERAGE / 2));
        distance = Math.max(distance, MIN_DISTANCE + size / 2);

        double headingRadians = Math.toRadians(headingDegrees);
        double forwardX = Math.sin(headingRadians);
        double forwardZ = -Math.cos(headingRadians);

        double camX = center.x - forwardX * distance;
        double camY = center.y + Math.max(verticalSize * 0.15, distance * Math.tan(MIN_ELEVATION_RADIANS));
        double camZ = center.z - forwardZ * distance;

        double dx = center.x - camX;
        double dy = center.y - camY;
        double dz = center.z - camZ;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontalDistance));

        return new Pose(camX, camY, camZ, (float) headingDegrees, pitch);
    }

    /** Reads a {@code target} object out of a request: {@code {"type":"block","pos":[x,y,z]}},
     * {@code {"type":"region","from":[...],"to":[...]}}, or {@code {"type":"blocks","positions":[[...],...]}}
     * (the bounding box spanning every listed position -- e.g. a whole staged row for a wide shot). */
    static AABB targetAabb(JsonObject target) {
        String type = target.has("type") ? target.get("type").getAsString() : "block";
        return switch (type) {
            case "block" -> new AABB(Cmd.readPos(target.getAsJsonArray("pos")));
            case "region" -> Cmd.boxOf(Cmd.readPos(target.getAsJsonArray("from")), Cmd.readPos(target.getAsJsonArray("to")));
            case "blocks" -> {
                JsonArray positions = target.getAsJsonArray("positions");
                if (positions.isEmpty()) {
                    throw new CompanionException("frame: \"blocks\" target has no positions");
                }
                AABB union = new AABB(Cmd.readPos(positions.get(0).getAsJsonArray()));
                for (int i = 1; i < positions.size(); i++) {
                    union = union.minmax(new AABB(Cmd.readPos(positions.get(i).getAsJsonArray())));
                }
                yield union;
            }
            default -> throw new CompanionException("frame: unknown target type \"" + type + "\"");
        };
    }

}
