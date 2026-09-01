package us.drullk.relict.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

/**
 * {@code relict:rock} — the one configurable "loose rock on the surface" feature every biome's S/M/L
 * placements share, rather than a bespoke class per size per province.
 *
 * <p>Every voxel this places snaps to its own column's heightmap and only writes into air over solid ground
 * (see {@link #placeVoxel}) — the "sits on the heightmap, never floating" law holds column-by-column, not
 * just at the origin, which is what keeps an L boulder from overhanging into a detached solid where the
 * ground dips under one corner of its footprint.
 *
 * <p>Placement bias (ridge scarps, cliff talus, dune interdune floors, mesa caps vs. floors) reads {@link
 * RockRelief} and {@link DuneCrest} off the heightmap rather than the raw density-function graph — see
 * {@link RockFeatureConfiguration.PlacementRule} for why: a {@link Feature} is handed no {@code
 * RandomState} to sample noise fields with.
 */
public class RockFeature extends Feature<RockFeatureConfiguration> {

    /** How far out {@link RockRelief} looks; same order of magnitude as the wind-axis sample in {@link DuneCrest}. */
    private static final int RELIEF_RADIUS = 5;

    /** Relief at or below this reads as "locally flat": interdune floors, mesa caps, mesa valley floors. */
    private static final double FLAT_RELIEF_MAX = 2.0;

    /** Relief at or above this reads as "locally steep": wrinkle-ridge scarps, mesa cliff talus. */
    private static final double STEEP_RELIEF_MIN = 4.0;

    /**
     * Absolute-Y split between fretted_mesas caps and floors. A knob, not a derivation: the elevation ladder
     * this was picked against put the province's floors near 121-140 and its caps above that;
     * re-probe if {@code MESA_AMPLITUDE} ever moves.
     */
    private static final int MESA_CAP_HEIGHT = 145;

    /**
     * A footprint column may snap to a surface at most this many blocks away from the origin's own surface
     * before it is skipped outright, rather than built as an overhang — the L-boulder half of the
     * never-floating law.
     */
    private static final int FOOTPRINT_MAX_STEP = 2;

    /** Per-cell odds a CLAST footprint slot actually gets a block, so a 2x2 slot reads as 2-4 blocks, not always 4. */
    private static final float CLAST_CELL_FILL_CHANCE = 0.85F;

    /** 2x2, one block tall: the 2-4 block clast footprint. */
    private static final int[][] CLAST_OFFSETS = {{0, 0, 0}, {1, 0, 0}, {0, 1, 0}, {1, 1, 0}};

    /** A rounded plus-shape core (two tall) with four corners (one tall) — reads as a squat 3x3x2 boulder without being a cube. */
    private static final int[][] BOULDER_OFFSETS = {
            {0, 0, 1}, {1, 0, 1}, {-1, 0, 1}, {0, 1, 1}, {0, -1, 1},
            {1, 1, 0}, {1, -1, 0}, {-1, 1, 0}, {-1, -1, 0}
    };

    public RockFeature(Codec<RockFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<RockFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RockFeatureConfiguration config = context.config();
        RandomSource random = context.random();
        BlockPos origin = context.origin();

        if (!passesPlacementRule(config.placementRule(), heightLookup(level, origin.getX(), origin.getZ()))) {
            return false;
        }

        return switch (config.shape()) {
            case SINGLE -> placeVoxel(level, config, random, origin.getX(), origin.getY(), origin.getZ());
            case CLAST -> placeFootprint(level, config, random, origin, CLAST_OFFSETS, true);
            case BOULDER -> placeFootprint(level, config, random, origin, BOULDER_OFFSETS, false);
        };
    }

    private static boolean placeFootprint(WorldGenLevel level, RockFeatureConfiguration config, RandomSource random,
                                          BlockPos origin, int[][] offsets, boolean probabilistic) {
        boolean placedAny = false;

        for (int[] offset : offsets) {
            if (probabilistic && random.nextFloat() >= CLAST_CELL_FILL_CHANCE) {
                continue;
            }

            int x = origin.getX() + offset[0];
            int z = origin.getZ() + offset[1];

            int localY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            if (Math.abs(localY - origin.getY()) > FOOTPRINT_MAX_STEP) {
                continue; // would overhang past the ground below it -- skip the column instead of floating
            }

            if (placeVoxel(level, config, random, x, localY, z)) {
                placedAny = true;
                if (offset[2] > 0) {
                    placedAny |= placeVoxel(level, config, random, x, localY + 1, z);
                }
            }
        }

        return placedAny;
    }

    /** One block: air over non-air ground only, so nothing this places is ever floating. */
    private static boolean placeVoxel(WorldGenLevel level, RockFeatureConfiguration config, RandomSource random, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (!level.getBlockState(pos).isAir() || level.getBlockState(pos.below()).isAir()) {
            return false;
        }

        Block block = random.nextFloat() < config.secondaryChance() ? config.secondaryBlock() : config.primaryBlock();
        level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_CLIENTS);
        return true;
    }

    /**
     * The gate every placement attempt must clear, on top of the biome check. Takes a plain height lookup
     * rather than a {@link WorldGenLevel} so the reports module's coverage plates can evaluate the exact
     * same predicate off a density-function proxy for the heightmap, instead of duplicating the thresholds
     * and risking drift (the way {@code DustLayerCoverageSampler} duplicates {@code DustLayerFeature}'s
     * private roll math, because that math has no clean non-private seam to share).
     */
    public static boolean passesPlacementRule(RockFeatureConfiguration.PlacementRule rule, DuneCrest.RelativeHeight height) {
        return switch (rule) {
            case ANY -> true;
            case INTERDUNE_FLOOR -> !DuneCrest.isCrest(height) && RockRelief.localRelief(height, RELIEF_RADIUS) <= FLAT_RELIEF_MAX;
            case RIDGE_BIAS, TALUS -> RockRelief.localRelief(height, RELIEF_RADIUS) >= STEEP_RELIEF_MIN;
            case CAP -> RockRelief.localRelief(height, RELIEF_RADIUS) <= FLAT_RELIEF_MAX && height.at(0, 0) >= MESA_CAP_HEIGHT;
            case VALLEY_FLOOR -> RockRelief.localRelief(height, RELIEF_RADIUS) <= FLAT_RELIEF_MAX && height.at(0, 0) < MESA_CAP_HEIGHT;
        };
    }

    private static DuneCrest.RelativeHeight heightLookup(WorldGenLevel level, int x, int z) {
        return (offsetX, offsetZ) -> surfaceHeight(level, x + offsetX, z + offsetZ);
    }

    private static int surfaceHeight(WorldGenLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
    }

}
