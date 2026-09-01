package us.drullk.relict.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import us.drullk.relict.RelictTags;
import us.drullk.relict.init.RelictBlocks;

/**
 * [VANILLACOPY] {@code PortalShape}'s bottom-left/width/height frame scan, retargeted at
 * {@link RelictTags#MARS_PORTAL_FRAME} and the Mars portal block. Minimum width is 1
 */
final class RelictPortalShape {

    private static final int MIN_WIDTH = 1;
    private static final int MAX_WIDTH = 21;
    private static final int MIN_HEIGHT = 3;
    private static final int MAX_HEIGHT = 21;

    private RelictPortalShape() {
    }

    static boolean isComplete(BlockGetter level, BlockPos pos, Axis axis) {
        Direction rightDir = Direction.get(AxisDirection.POSITIVE, axis);
        BlockPos bottomLeft = calculateBottomLeft(level, rightDir, pos);
        if (bottomLeft == null) {
            return false;
        }

        int width = calculateWidth(level, bottomLeft, rightDir);
        if (width == 0) {
            return false;
        }

        int[] portalBlockCount = new int[1];
        int height = calculateHeight(level, bottomLeft, rightDir, width, portalBlockCount);
        return height != 0 && portalBlockCount[0] == width * height;
    }

    private static BlockPos calculateBottomLeft(BlockGetter level, Direction rightDir, BlockPos pos) {
        int minY = Math.max(level.getMinY(), pos.getY() - MAX_HEIGHT);
        while (pos.getY() > minY && isEmpty(level.getBlockState(pos.below()))) {
            pos = pos.below();
        }

        Direction leftDir = rightDir.getOpposite();
        int edge = getDistanceUntilEdgeAboveFrame(level, pos, leftDir) - 1;
        return edge < 0 ? null : pos.relative(leftDir, edge);
    }

    private static int calculateWidth(BlockGetter level, BlockPos bottomLeft, Direction rightDir) {
        int width = getDistanceUntilEdgeAboveFrame(level, bottomLeft, rightDir);
        return width >= MIN_WIDTH && width <= MAX_WIDTH ? width : 0;
    }

    private static int getDistanceUntilEdgeAboveFrame(BlockGetter level, BlockPos pos, Direction direction) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int width = 0; width <= MAX_WIDTH; width++) {
            mutable.set(pos).move(direction, width);
            BlockState state = level.getBlockState(mutable);
            if (!isEmpty(state)) {
                if (isFrame(state)) {
                    return width;
                }
                break;
            }

            BlockState below = level.getBlockState(mutable.move(Direction.DOWN));
            if (!isFrame(below)) {
                break;
            }
        }

        return 0;
    }

    private static int calculateHeight(BlockGetter level, BlockPos bottomLeft, Direction rightDir, int width, int[] portalBlockCount) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int height = getDistanceUntilTop(level, bottomLeft, rightDir, pos, width, portalBlockCount);
        return height >= MIN_HEIGHT && height <= MAX_HEIGHT && hasTopFrame(level, bottomLeft, rightDir, pos, width, height) ? height : 0;
    }

    private static boolean hasTopFrame(BlockGetter level, BlockPos bottomLeft, Direction rightDir, BlockPos.MutableBlockPos pos, int width, int height) {
        for (int i = 0; i < width; i++) {
            BlockPos.MutableBlockPos framePos = pos.set(bottomLeft).move(Direction.UP, height).move(rightDir, i);
            if (!isFrame(level.getBlockState(framePos))) {
                return false;
            }
        }

        return true;
    }

    private static int getDistanceUntilTop(
            BlockGetter level, BlockPos bottomLeft, Direction rightDir, BlockPos.MutableBlockPos pos, int width, int[] portalBlockCount
    ) {
        for (int height = 0; height < MAX_HEIGHT; height++) {
            pos.set(bottomLeft).move(Direction.UP, height).move(rightDir, -1);
            if (!isFrame(level.getBlockState(pos))) {
                return height;
            }

            pos.set(bottomLeft).move(Direction.UP, height).move(rightDir, width);
            if (!isFrame(level.getBlockState(pos))) {
                return height;
            }

            for (int i = 0; i < width; i++) {
                pos.set(bottomLeft).move(Direction.UP, height).move(rightDir, i);
                BlockState state = level.getBlockState(pos);
                if (!isEmpty(state)) {
                    return height;
                }

                if (isPortal(state)) {
                    portalBlockCount[0]++;
                }
            }
        }

        return MAX_HEIGHT;
    }

    private static boolean isEmpty(BlockState state) {
        return state.isAir() || state.is(BlockTags.FIRE) || isPortal(state);
    }

    private static boolean isFrame(BlockState state) {
        return state.is(RelictTags.MARS_PORTAL_FRAME);
    }

    private static boolean isPortal(BlockState state) {
        return state.is(RelictBlocks.MARS_PORTAL.get());
    }

}
