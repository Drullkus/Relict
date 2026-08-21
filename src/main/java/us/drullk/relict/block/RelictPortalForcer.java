package us.drullk.relict.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import us.drullk.relict.init.RelictBlocks;

public final class RelictPortalForcer {

    private static final int FRAME_HALF_WIDTH = 1;

    private RelictPortalForcer() {
    }

    public static BlockPos createLandingPortal(ServerLevel level, BlockPos near) {
        level.getChunk(near); // Ensure loaded before getHeight call
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, near.getX(), near.getZ());
        BlockPos base = new BlockPos(near.getX(), surfaceY, near.getZ());

        BlockState frame = Blocks.SMOOTH_BASALT.defaultBlockState();
        for (Direction direction : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            level.setBlockAndUpdate(base.relative(direction, FRAME_HALF_WIDTH), frame);
        }
        level.setBlockAndUpdate(base.below(), frame);

        BlockState portal = RelictBlocks.MARS_PORTAL.get().defaultBlockState();
        level.setBlockAndUpdate(base, portal);
        level.setBlockAndUpdate(base.above(), portal);

        return base;
    }

}
