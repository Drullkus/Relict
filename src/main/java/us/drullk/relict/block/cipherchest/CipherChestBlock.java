package us.drullk.relict.block.cipherchest;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import us.drullk.relict.init.RelictBlockEntities;

/**
 * <b>Deliberately NOT a {@code ChestBlock} subclass.</b> That's the double-chest-forbidden proof: vanilla's
 * double-chest merge lives entirely in {@code ChestBlock} (the {@code TYPE} property and its
 * {@code combine()}/{@code updateShape} neighbor-scanning), and this block has neither -- there is no code
 * path here that could ever produce a {@code ChestType.LEFT}/{@code RIGHT} pairing. The block entity is
 * still a real {@link CipherChestBlockEntity} (a {@code ChestBlockEntity} subclass) so the vanilla chest
 * model, lid animation, and 27-slot menu all come along for free; only the merge machinery is absent.
 * <p>
 * While locked, a right-click is routed by exactly which small area the crosshair hit (a dial cell on the
 * lid's engraved grid, or the front latch) -- see {@link CipherChestFaceLayout}, shared with the hover
 * outline in {@code RelictCipherChestRenderers} so the clickable area always matches the highlighted one.
 * Once solved the chest never re-arms: it behaves like a plain chest forever after.
 */
public class CipherChestBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<CipherChestBlock> CODEC = simpleCodec(CipherChestBlock::new);

    private static final int DIAL_STEP = 1;
    private static final int DIAL_STEP_SNEAK = 5;

    // Same footprint as a vanilla single chest (Block.column(14, 0, 14)): a 14x14px column, 14px tall.
    // [VANILLACOPY] ChestBlock.SHAPE, expressed directly in unit fractions to avoid depending on the exact
    // Block.column(...) helper signature.
    private static final VoxelShape SHAPE = Shapes.box(1.0 / 16, 0.0, 1.0 / 16, 15.0 / 16, 14.0 / 16, 15.0 / 16);

    public CipherChestBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends CipherChestBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /**
     * Breakability law (producer ruling): unbreakable like bedrock (zero destroy progress, same mechanism
     * {@code Block.INDESTRUCTIBLE}/bedrock uses -- see the vanilla default this overrides) while locked and
     * not player-placed; breakable forever once unlocked; a player-placed chest is always breakable. This
     * only gates mining progress -- creative-mode instant break and the carried pickaxe/correct-tool law
     * both apply unchanged whenever the chest IS breakable.
     */
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof CipherChestBlockEntity chest && !chest.isBreakable()) {
            return 0.0F;
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Front (latch) faces the placing player, matching vanilla ChestBlock's single-chest placement.
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        if (level.getBlockEntity(pos) instanceof CipherChestBlockEntity chest) {
            // setPlacedBy only ever runs for a BlockItem placement (a player, or a non-player placer like a
            // dispenser); structure/NBT placement never calls it at all. A real Player is what "player-placed"
            // means for the breakability law below.
            chest.randomize(level.getRandom(), placer instanceof Player);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CipherChestBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof CipherChestBlockEntity chest)) {
            return InteractionResult.PASS;
        }

        if (chest.isSolved()) {
            if (level instanceof ServerLevel) {
                player.openMenu(chest);
                player.awardStat(Stats.CUSTOM.get(Stats.OPEN_CHEST));
            }
            return InteractionResult.SUCCESS;
        }

        Direction facing = state.getValue(FACING);
        Direction hitFace = hitResult.getDirection();
        Vec3 local = hitResult.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        long gameTime = level.getGameTime();

        if (CipherChestFaceLayout.isLatchHit(facing, hitFace, local.x, local.y, local.z)) {
            return attemptConfirm(level, pos, chest, player, gameTime);
        }

        int cellIndex = CipherChestFaceLayout.cellIndexFromHit(facing, hitFace, local.x, local.z);
        if (cellIndex < 0) {
            return InteractionResult.PASS;
        }
        return cycleDial(level, pos, chest, player, cellIndex, gameTime);
    }

    // Both attemptConfirm and cycleDial mutate CipherChestBlockEntity state that must stay authoritative on
    // the server: attemptConfirm's wrong-guess scramble draws from Level#getRandom(), and a client-side call
    // to useWithoutItem (real right-clicks run it there too, for prediction) would draw from ClientLevel's
    // own independent random stream -- permanently diverging the client's dial values from the server's the
    // moment a wrong guess is ever scrambled. BlockEntity#setChanged() alone never reaches the client either
    // (it only marks the chunk dirty for saving); ServerLevel#sendBlockUpdated is what actually queues this
    // position's CipherChestBlockEntity#getUpdatePacket for the next tick's broadcast. Both methods below are
    // therefore server-only and end with an explicit resync, so the client's copy (the one useWithoutItem's
    // isSolved()/isLockedOut() branch and the BER read) can never drift from the truth that decides whether
    // player.openMenu ever fires.
    private InteractionResult attemptConfirm(Level level, BlockPos pos, CipherChestBlockEntity chest, Player player, long gameTime) {
        if (chest.isLockedOut(gameTime)) {
            playThud(level, pos);
            return InteractionResult.CONSUME;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        boolean correct = chest.attemptConfirm(serverLevel.getRandom(), gameTime);
        if (correct) {
            serverLevel.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0F, 1.0F);
            serverLevel.gameEvent(null, GameEvent.BLOCK_OPEN, pos);
            player.openMenu(chest);
            player.awardStat(Stats.CUSTOM.get(Stats.OPEN_CHEST));
        } else {
            playThud(serverLevel, pos);
        }
        resync(serverLevel, pos);
        return InteractionResult.SUCCESS;
    }

    private InteractionResult cycleDial(Level level, BlockPos pos, CipherChestBlockEntity chest, Player player, int cellIndex, long gameTime) {
        if (chest.isLockedOut(gameTime)) {
            playThud(level, pos);
            return InteractionResult.CONSUME;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        int amount = player.isShiftKeyDown() ? DIAL_STEP_SNEAK : DIAL_STEP;
        boolean moved = chest.cycleDial(cellIndex, amount, gameTime);
        if (moved) {
            serverLevel.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6F, 1.4F);
            resync(serverLevel, pos);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    private void resync(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
    }

    private void playThud(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.DEEPSLATE_HIT, SoundSource.BLOCKS, 1.0F, 0.6F);
        level.gameEvent(null, GameEvent.BLOCK_CHANGE, pos);
    }

    // Not a BaseEntityBlock, so no createTickerHelper -- the same type-equality-then-unchecked-cast trick
    // that helper does, written out directly. [VANILLACOPY] BaseEntityBlock#createTickerHelper's technique.
    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != RelictBlockEntities.CIPHER_CHEST.get()) {
            return null;
        }
        if (level.isClientSide()) {
            return (BlockEntityTicker<T>) (BlockEntityTicker<CipherChestBlockEntity>) ChestBlockEntity::lidAnimateTick;
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<CipherChestBlockEntity>) (_, _, _, chest) -> chest.recheckOpen();
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    // [VANILLACOPY] BaseEntityBlock#triggerEvent, this class does not extend that
    @Override
    protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int b0, int b1) {
        super.triggerEvent(state, level, pos, b0, b1);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity == null ? false : blockEntity.triggerEvent(b0, b1);
    }
}
