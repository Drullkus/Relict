package us.drullk.relict.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import us.drullk.relict.init.RelictBlocks;

/**
 * Builds a fixed rectangular Nether-Portal-shaped frame (4-wide/5-tall border, 2x3 interior) out of
 * {@code minecraft:polished_sulfur}, at a single heightmap-derived position rather than scanning nearby
 * terrain the way {@code PortalForcer} does.
 */
public final class RelictPortalForcer {

    private static final int FRAME_WIDTH = 4;
    private static final int FRAME_HEIGHT = 5;
    private static final int INTERIOR_WIDTH = 2;
    private static final int INTERIOR_HEIGHT = 3;

    private RelictPortalForcer() {
    }

    public static BlockPos createLandingPortal(ServerLevel level, BlockPos near, Axis axis) {
        level.getChunk(near); // Ensure loaded before getHeight call
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, near.getX(), near.getZ());
        BlockPos base = new BlockPos(near.getX(), surfaceY, near.getZ());

        Direction widthDir = Direction.get(AxisDirection.POSITIVE, axis);
        BlockState frame = Blocks.POLISHED_SULFUR.defaultBlockState();
        BlockState portal = RelictBlocks.MARS_PORTAL.get().defaultBlockState().setValue(RelictPortalBlock.AXIS, axis);

        // Frame first, interior second: an interior cell placed before its whole frame exists would see its
        // own neighbor notify (from a later frame placement) and immediately air itself back out.
        for (int width = -1; width < FRAME_WIDTH - 1; width++) {
            boolean widthEdge = width == -1 || width == INTERIOR_WIDTH;
            for (int height = -1; height < FRAME_HEIGHT - 1; height++) {
                if (widthEdge || height == -1 || height == INTERIOR_HEIGHT) {
                    level.setBlock(base.relative(widthDir, width).above(height), frame, 3);
                }
            }
        }

        for (int width = 0; width < INTERIOR_WIDTH; width++) {
            for (int height = 0; height < INTERIOR_HEIGHT; height++) {
                level.setBlock(base.relative(widthDir, width).above(height), portal, 18);
            }
        }

        return base;
    }

}
