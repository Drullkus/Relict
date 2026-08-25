package us.drullk.relict.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Shared 1-8 layer geometry for {@code relict:dust_layer} and {@code relict:dry_snow_layer}: the same
 * {@code LAYERS} property vanilla's own {@link net.minecraft.world.level.block.SnowLayerBlock} uses, the same
 * partial-collision boxes, the same support-below/stacking/replacement rules.
 *
 * <p>[VANILLACOPY] {@code net.minecraft.world.level.block.SnowLayerBlock} (26.2), with one deliberate
 * omission: vanilla's {@code randomTick} melts the layer once block light exceeds 11. Neither Mars dust nor
 * Mars dry-CO2-snow melts (both are dry-world veneers with no light-driven decay), so that method is not
 * copied here at all — there is no light-based tick to suppress in either subclass, which is the simplest
 * possible proof that neither one can melt. {@link DustLayerBlock} adds its own random tick for storm
 * deposition and calm erosion; {@link DrySnowLayerBlock} adds none yet.
 */
public abstract class AbstractRelictLayerBlock extends Block {

    public static final IntegerProperty LAYERS = BlockStateProperties.LAYERS;

    private static final VoxelShape[] SHAPES = Block.boxes(8, height -> Block.column(16.0, 0.0, height * 2));

    protected AbstractRelictLayerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LAYERS, 1));
    }

    @Override
    public abstract MapCodec<? extends AbstractRelictLayerBlock> codec();

    // [VANILLACOPY] SnowLayerBlock.isPathfindable
    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return type == PathComputationType.LAND && state.getValue(LAYERS) < 5;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(LAYERS)];
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(LAYERS) - 1];
    }

    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return SHAPES[state.getValue(LAYERS)];
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(LAYERS)];
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    /**
     * [VANILLACOPY] {@code SnowLayerBlock.canSurvive}, unchanged, including its two vanilla tags: this stays
     * exactly the support-below requirement, not a new canSurvive rule — reusing vanilla's own
     * {@code CANNOT_SUPPORT_SNOW_LAYER}/{@code SUPPORT_OVERRIDE_SNOW_LAYER} tags is that requirement, not an
     * addition to it; Mars terrain is solid stone/sand and never populates either tag, so in practice this
     * degrades to the plain face-full check.
     */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState belowState = level.getBlockState(pos.below());
        if (belowState.is(net.minecraft.tags.BlockTags.CANNOT_SUPPORT_SNOW_LAYER)) {
            return false;
        }

        return belowState.is(net.minecraft.tags.BlockTags.SUPPORT_OVERRIDE_SNOW_LAYER)
                || Block.isFaceFull(belowState.getCollisionShape(level, pos.below()), Direction.UP)
                || belowState.is(this) && belowState.getValue(LAYERS) == 8;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return !state.canSurvive(level, pos)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    // [VANILLACOPY] SnowLayerBlock.canBeReplaced
    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        int layers = state.getValue(LAYERS);
        if (!context.getItemInHand().is(this.asItem()) || layers >= 8) {
            return layers == 1;
        }

        return !context.replacingClickedOnBlock() || context.getClickedFace() == Direction.UP;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = context.getLevel().getBlockState(context.getClickedPos());
        if (state.is(this)) {
            int layers = state.getValue(LAYERS);
            return this.stack(state, Math.min(8, layers + 1));
        }

        return super.getStateForPlacement(context);
    }

    /**
     * Applies a new layer count on top of an existing state, for both player placement and the storm's own
     * deposition tick. A subclass with extra properties that a fresh layer should reset (dust's
     * {@code trodden}) overrides this instead of duplicating the increment logic at each call site.
     */
    protected BlockState stack(BlockState existing, int newLayers) {
        return existing.setValue(LAYERS, newLayers);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LAYERS);
    }

}
