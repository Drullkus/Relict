package us.drullk.relict.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import us.drullk.relict.block.AbstractRelictLayerBlock;
import us.drullk.relict.init.RelictBlocks;

/**
 * {@code relict:dust_layer}'s worldgen baseline, one instance per province with its own
 * {@link DustLayerFeatureConfiguration}. FREEZE_TOP_LAYER-style: a plain 16x16 column walk over the
 * feature's own chunk, run at {@code TOP_LAYER_MODIFICATION} so it reads the terrain the same way vanilla's
 * snow/ice overlay does — after every other decoration, never displacing anything already placed.
 *
 * <p>Coverage patchiness is a coarse {@link LatticeHash} cell mask, not a registered noise field: reusing
 * {@code Noises.SURFACE} the way {@code RelictSurfaceRules}' {@code DUST_CATCH} threshold does would need a
 * {@code RandomState} this feature is never handed (only {@link WorldGenLevel}/{@link RandomSource}/config).
 * That also keeps this feature independent of {@code fretted_mesas}' RED_SAND dust-catch surface patches on
 * purpose — the two are meant to read as independently-placed "dust collects here" reads, not one lensed
 * through the other, and converting the surface patches themselves into layers is its own separate visual
 * change, not something to fold in here. The one cost: patch placement does not vary between world seeds,
 * only between the per-registration {@code coverage_salt}. Flagged as a tuning knob, not hidden.
 */
public class DustLayerFeature extends Feature<DustLayerFeatureConfiguration> {

    /** Decorrelates the coverage mask from any other {@link LatticeHash} consumer that might reuse the same salt. */
    private static final long COVERAGE_HASH_ROLE = 0x445553545F434F56L;

    public DustLayerFeature(Codec<DustLayerFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<DustLayerFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        DustLayerFeatureConfiguration config = context.config();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        BlockPos.MutableBlockPos topPos = new BlockPos.MutableBlockPos();

        boolean placedAny = false;

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = origin.getX() + dx;
                int z = origin.getZ() + dz;

                // Biome decoration is chunk-granular, not column-granular (see class doc): re-check the real
                // biome at this column so a chunk straddling a province border never gets the wrong table.
                if (!level.getBiome(topPos.set(x, level.getMinY(), z)).is(config.province())) {
                    continue;
                }

                if (config.requireDuneCrest() && !isDuneCrest(level, x, z)) {
                    continue;
                }

                if (!coverageHit(config, x, z)) {
                    continue;
                }

                int depth = rollDepth(config, random);
                if (depth <= 0) {
                    continue;
                }

                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                topPos.set(x, y, z);

                if (!level.getBlockState(topPos).isAir()) {
                    continue;
                }

                BlockState dust = RelictBlocks.DUST_LAYER.get().defaultBlockState().setValue(AbstractRelictLayerBlock.LAYERS, depth);
                level.setBlock(topPos, dust, 2);
                placedAny = true;
            }
        }

        return placedAny;
    }

    private static boolean isDuneCrest(WorldGenLevel level, int x, int z) {
        return DuneCrest.isCrest((offsetX, offsetZ) -> level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x + offsetX, z + offsetZ));
    }

    private static boolean coverageHit(DustLayerFeatureConfiguration config, int x, int z) {
        int cellX = Math.floorDiv(x, config.patchCellSize());
        int cellZ = Math.floorDiv(z, config.patchCellSize());
        long hash = LatticeHash.hash(config.coverageSalt(), cellX, cellZ, COVERAGE_HASH_ROLE);
        return LatticeHash.unitInterval(hash) < config.coverageChance();
    }

    private static int rollDepth(DustLayerFeatureConfiguration config, RandomSource random) {
        int min = config.minLayers();
        int max = config.maxLayers();
        return min >= max ? min : min + random.nextInt(max - min + 1);
    }

}
