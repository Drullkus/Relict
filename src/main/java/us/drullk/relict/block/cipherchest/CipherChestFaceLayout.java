package us.drullk.relict.block.cipherchest;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Shared geometry for the Cipher Chest's engraved 5x5 grid.
 * <p>
 * "across" runs left-to-right and "along" runs front(latch)-to-back, both as seen by a player standing on
 * the FACING side looking at the chest -- i.e. the same viewer who sees the latch. Row 0 (the square's
 * first CANON row) sits at the back (along = far)
 * <p>
 * Also carries the front-face latch hit box, since the latch is a distinct hover target on a different
 * (vertical) face from the dials.
 */
public final class CipherChestFaceLayout {

    public static final double MARGIN = 0.1;
    public static final double GRID_SIZE = 1.0 - MARGIN * 2.0;

    /** Local Y of the lid's top surface when closed (14/16, matching the vanilla single-chest shape height). */
    public static final double LID_TOP_Y = 14.0 / 16.0;

    private static final double DIAL_HALF_SIZE = (GRID_SIZE / CipherChestSquare.SIZE) * 0.45;
    private static final double DIAL_HALF_HEIGHT = 0.25 / 16.0;

    private static final double LATCH_ACROSS_MIN = (7.0 - 0.25) / 16.0;
    private static final double LATCH_ACROSS_MAX = (9.0 + 0.25) / 16.0;
    private static final double LATCH_Y_MIN = (7.0 - 0.25) / 16.0;
    private static final double LATCH_Y_MAX = (11.0 + 0.25) / 16.0;
    private static final double LATCH_DEPTH = 1.0 / 16.0;

    private CipherChestFaceLayout() {
    }

    /** World-local (x,z) within the block, 0..1 each, to lid-plane (across, along). NaN axis means "not on this face's footprint". */
    public static double[] acrossAlongFromLocal(Direction facing, double localX, double localZ) {
        return switch (facing) {
            case NORTH -> new double[]{1.0 - localX, localZ};
            case SOUTH -> new double[]{localX, 1.0 - localZ};
            case EAST -> new double[]{1.0 - localZ, 1.0 - localX};
            case WEST -> new double[]{localZ, localX};
            default -> new double[]{Double.NaN, Double.NaN};
        };
    }

    /** Inverse of {@link #acrossAlongFromLocal}: lid-plane (across, along) -> world-local (x, z). */
    public static double[] localFromAcrossAlong(Direction facing, double across, double along) {
        return switch (facing) {
            case NORTH -> new double[]{1.0 - across, along};
            case SOUTH -> new double[]{across, 1.0 - along};
            case EAST -> new double[]{1.0 - along, 1.0 - across};
            case WEST -> new double[]{along, across};
            default -> new double[]{0.5, 0.5};
        };
    }

    /**
     * Cell index (0..24) for a hit at the given lid-plane coordinates, or -1 if outside the grid.
     */
    public static int cellIndexAt(double across, double along) {
        double gridAcross = across - MARGIN;
        double gridAlong = along - MARGIN;
        if (gridAcross < 0.0 || gridAcross >= GRID_SIZE || gridAlong < 0.0 || gridAlong >= GRID_SIZE) {
            return -1;
        }
        int col = clampIndex((int) (gridAcross / GRID_SIZE * CipherChestSquare.SIZE));
        int alongIndex = clampIndex((int) (gridAlong / GRID_SIZE * CipherChestSquare.SIZE));
        int row = CipherChestSquare.SIZE - 1 - alongIndex;
        return CipherChestSquare.cellIndex(row, col);
    }

    /** Lid-plane "across" of a cell's center, for drawing/hover-box placement. */
    public static double acrossCenterOf(int col) {
        return MARGIN + (col + 0.5) / CipherChestSquare.SIZE * GRID_SIZE;
    }

    /** Lid-plane "along" of a cell's center, for drawing/hover-box placement. */
    public static double alongCenterOf(int row) {
        int alongIndex = CipherChestSquare.SIZE - 1 - row;
        return MARGIN + (alongIndex + 0.5) / CipherChestSquare.SIZE * GRID_SIZE;
    }

    private static int clampIndex(int index) {
        return Math.max(0, Math.min(CipherChestSquare.SIZE - 1, index));
    }

    /** The Y-axis block rotation (degrees) that turns a NORTH-authored overlay to face the given direction. */
    public static float yRotationDegreesFor(Direction facing) {
        return switch (facing) {
            case NORTH -> 0.0F;
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };
    }

    /** Cell index under a hit on the lid's UP face, or -1 if the hit face isn't UP or missed the grid. */
    public static int cellIndexFromHit(Direction facing, Direction hitFace, double localX, double localZ) {
        if (hitFace != Direction.UP) {
            return -1;
        }
        double[] acrossAlong = acrossAlongFromLocal(facing, localX, localZ);
        return cellIndexAt(acrossAlong[0], acrossAlong[1]);
    }

    /** Whether a hit on the block's outward FACING face landed inside the latch's small hover box. */
    public static boolean isLatchHit(Direction facing, Direction hitFace, double localX, double localY, double localZ) {
        if (hitFace != facing) {
            return false;
        }
        double[] acrossAlong = acrossAlongFromLocal(facing, localX, localZ);
        double across = acrossAlong[0];
        return across >= LATCH_ACROSS_MIN && across <= LATCH_ACROSS_MAX
                && localY >= LATCH_Y_MIN && localY <= LATCH_Y_MAX;
    }

    /** Small hover-box VoxelShape for one dial cell, in block-local coordinates. */
    public static VoxelShape dialHoverShape(Direction facing, int cellIndex) {
        int row = CipherChestSquare.rowOf(cellIndex);
        int col = CipherChestSquare.colOf(cellIndex);
        double[] local = localFromAcrossAlong(facing, acrossCenterOf(col), alongCenterOf(row));
        double x = local[0];
        double z = local[1];
        return Shapes.box(
                clamp01(x - DIAL_HALF_SIZE), LID_TOP_Y - DIAL_HALF_HEIGHT, clamp01(z - DIAL_HALF_SIZE),
                clamp01(x + DIAL_HALF_SIZE), Math.min(1.0, LID_TOP_Y + DIAL_HALF_HEIGHT), clamp01(z + DIAL_HALF_SIZE));
    }

    /** Small hover-box VoxelShape for the latch, in block-local coordinates. */
    public static VoxelShape latchHoverShape(Direction facing) {
        // The latch sits on the outward FACING face; its box is thin along that face's own normal axis and
        // spans LATCH_ACROSS_*/LATCH_Y_* across the other two.
        double faceCoord = faceCoordinate(facing);
        double faceMin = Math.max(0.0, faceCoord - LATCH_DEPTH);
        double faceMax = Math.min(1.0, faceCoord + LATCH_DEPTH);

        double[] nearCorner = localFromAcrossAlong(facing, LATCH_ACROSS_MIN, alongAtFace(facing));
        double[] farCorner = localFromAcrossAlong(facing, LATCH_ACROSS_MAX, alongAtFace(facing));
        double xMin = Math.min(nearCorner[0], farCorner[0]);
        double xMax = Math.max(nearCorner[0], farCorner[0]);
        double zMin = Math.min(nearCorner[1], farCorner[1]);
        double zMax = Math.max(nearCorner[1], farCorner[1]);

        return switch (facing.getAxis()) {
            case X -> Shapes.box(faceMin, LATCH_Y_MIN, clamp01(zMin), faceMax, LATCH_Y_MAX, clamp01(zMax));
            case Z -> Shapes.box(clamp01(xMin), LATCH_Y_MIN, faceMin, clamp01(xMax), LATCH_Y_MAX, faceMax);
            default -> Shapes.box(0.375, LATCH_Y_MIN, faceMin, 0.625, LATCH_Y_MAX, faceMax);
        };
    }

    /** "along" value of the plane containing the FACING face itself (the face's own coordinate, 0 or 1). */
    private static double alongAtFace(Direction facing) {
        return 0.0;
    }

    private static double faceCoordinate(Direction facing) {
        return switch (facing) {
            case NORTH, WEST -> 0.0;
            case SOUTH, EAST -> 1.0;
            default -> 0.0;
        };
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
