package us.drullk.relict.companion;

import com.google.gson.JsonArray;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.Locale;

/**
 * Small command-string builders -- the companion's verbs are mostly a thin, in-process layer over plain
 * Minecraft commands, so this keeps that vocabulary in one place instead of scattering string formatting
 * across every verb.
 */
final class Cmd {

    private Cmd() {
    }

    /** Wraps {@code command} in an {@code execute in <dimension> run} prefix when {@code dimension} is
     * given and non-blank; otherwise runs it in whatever dimension the player is already in. */
    static String in(String dimension, String command) {
        if (dimension == null || dimension.isBlank()) {
            return command;
        }
        return "execute in " + dimension + " run " + command;
    }

    static String tp(double x, double y, double z, float yaw, float pitch) {
        return "tp @s %s %s %s %s %s".formatted(fmt(x), fmt(y), fmt(z), fmt(yaw), fmt(pitch));
    }

    static String tp(Framing.Pose pose) {
        return tp(pose.x(), pose.y(), pose.z(), pose.yaw(), pose.pitch());
    }

    static String fmt(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }

    static BlockPos readPos(JsonArray array) {
        if (array.size() != 3) {
            throw new CompanionException("expected a [x, y, z] position array");
        }
        return new BlockPos(array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt());
    }

    /** An AABB spanning two block-space corners, inclusive of both (the far corner's block gets its full
     * 1x1x1 volume counted, not just its near face) -- {@code AABB} itself only has a two-{@code Vec3}
     * constructor, not a two-{@code BlockPos} one. */
    static AABB boxOf(BlockPos a, BlockPos b) {
        int minX = Math.min(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxX = Math.max(a.getX(), b.getX()) + 1;
        int maxY = Math.max(a.getY(), b.getY()) + 1;
        int maxZ = Math.max(a.getZ(), b.getZ()) + 1;
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

}
