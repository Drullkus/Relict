package us.drullk.relict.block.cipherchest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import us.drullk.relict.init.RelictBlockEntities;

/**
 * A real vanilla {@link ChestBlockEntity} (27-slot container, loot-table support, lid animation all
 * inherited unchanged) with the Cipher Chest's lock state bolted on top. Extending the vanilla class rather
 * than reimplementing it is also the block's half of the double-chest-forbidden proof: {@code ChestBlock}'s
 * merge logic keys off {@code state.getBlock() instanceof ChestBlock}, and {@link CipherChestBlock} is
 * deliberately not one -- see its class doc.
 */
public class CipherChestBlockEntity extends ChestBlockEntity {

    public static final int DEFAULT_BLANK_COUNT = 2;
    public static final int MIN_BLANK_COUNT = 1;
    public static final int MAX_BLANK_COUNT = 3;

    /** ~3 real-time seconds at 20 ticks/sol-second, the wrong-guess lockout window. */
    public static final int LOCKOUT_TICKS = 60;

    private long seed;
    private int blankCount = DEFAULT_BLANK_COUNT;
    private int[] blankCells = CipherChestSquare.pickBlankCells(RandomSource.create(seed), blankCount);
    private int[] dialValues = startingDials(blankCells, RandomSource.create(seed));
    private boolean solved;
    private long lockoutUntilGameTime;

    /**
     * True only for a chest placed by a player (via {@link CipherChestBlock#setPlacedBy}); false for one
     * that arrived through structure/NBT placement, which never calls {@code setPlacedBy} at all. Persisted
     * so it survives save/reload -- see {@link #isBreakable()}.
     */
    private boolean playerPlaced;

    /** Opener-count bookkeeping for the chest-sound replica -- see {@link #startOpen}/{@link #stopOpen}. */
    private int lastKnownOpenCount;

    public CipherChestBlockEntity(BlockPos pos, BlockState state) {
        super(RelictBlockEntities.CIPHER_CHEST.get(), pos, state);
    }

    /** Called once, right after placement (producer overrides land afterward via {@code /data merge}). */
    public void randomize(RandomSource placementRandom, boolean playerPlaced) {
        this.seed = placementRandom.nextLong();
        this.blankCount = DEFAULT_BLANK_COUNT;
        rederiveBlanks();
        this.solved = false;
        this.lockoutUntilGameTime = 0;
        this.playerPlaced = playerPlaced;
        this.setChanged();
    }

    private void rederiveBlanks() {
        this.blankCells = CipherChestSquare.pickBlankCells(RandomSource.create(this.seed), this.blankCount);
        this.dialValues = startingDials(this.blankCells, RandomSource.create(this.seed ^ 0x5DEECE66DL));
    }

    private static int[] startingDials(int[] blankCells, RandomSource random) {
        int[] values = new int[blankCells.length];
        for (int i = 0; i < values.length; i++) {
            values[i] = random.nextInt(CipherChestSquare.MAX_VALUE) + CipherChestSquare.MIN_VALUE;
        }
        return values;
    }

    public boolean isSolved() {
        return this.solved;
    }

    public boolean isLockedOut(long gameTime) {
        return !this.solved && gameTime < this.lockoutUntilGameTime;
    }

    /** Game time the current lockout ends at -- the client's blink fade-out envelope counts down to this. */
    public long lockoutUntilGameTime() {
        return this.lockoutUntilGameTime;
    }

    public boolean isPlayerPlaced() {
        return this.playerPlaced;
    }

    /**
     * Breakability law (producer ruling): unbreakable like bedrock while locked and not player-placed
     * (structure-authored chests can't be dug around); breakable forever once unlocked; a player-placed
     * chest is always breakable, locked or not. See {@link CipherChestBlock#getDestroyProgress}.
     */
    public boolean isBreakable() {
        return this.solved || this.playerPlaced;
    }

    public int blankSlotForCell(int cellIndex) {
        for (int i = 0; i < this.blankCells.length; i++) {
            if (this.blankCells[i] == cellIndex) {
                return i;
            }
        }
        return -1;
    }

    /** The value shown at a cell: the canon number for filled cells, or the live dial guess for a blank. */
    public int displayValueAt(int cellIndex) {
        int slot = blankSlotForCell(cellIndex);
        return slot < 0 ? CipherChestSquare.valueAt(cellIndex) : this.dialValues[slot];
    }

    public boolean isBlank(int cellIndex) {
        return blankSlotForCell(cellIndex) >= 0;
    }

    /** Returns true if the dial actually moved (false for a click on a filled cell, or while locked out). */
    public boolean cycleDial(int cellIndex, int amount, long gameTime) {
        if (this.solved || isLockedOut(gameTime)) {
            return false;
        }
        int slot = blankSlotForCell(cellIndex);
        if (slot < 0) {
            return false;
        }
        this.dialValues[slot] = CipherChestSquare.wrapValue(this.dialValues[slot], amount);
        this.setChanged();
        return true;
    }

    /**
     * Compares every dial to the canon answer. On success, marks solved (the caller flips the chest open).
     * On failure, scrambles the dials and starts the lockout (the caller layers the sine-wave red blink on
     * top of this -- see {@link CipherChestBlock}).
     */
    public boolean attemptConfirm(RandomSource random, long gameTime) {
        if (this.solved || isLockedOut(gameTime)) {
            return false;
        }

        boolean correct = true;
        for (int i = 0; i < this.blankCells.length; i++) {
            if (this.dialValues[i] != CipherChestSquare.valueAt(this.blankCells[i])) {
                correct = false;
                break;
            }
        }

        if (correct) {
            this.solved = true;
            for (int i = 0; i < this.blankCells.length; i++) {
                this.dialValues[i] = CipherChestSquare.valueAt(this.blankCells[i]);
            }
        } else {
            for (int i = 0; i < this.dialValues.length; i++) {
                this.dialValues[i] = random.nextInt(CipherChestSquare.MAX_VALUE) + CipherChestSquare.MIN_VALUE;
            }
            this.lockoutUntilGameTime = gameTime + LOCKOUT_TICKS;
        }

        this.setChanged();
        return correct;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.seed = input.getLongOr("Seed", 0L);
        this.blankCount = clampBlankCount(input.getIntOr("BlankCount", DEFAULT_BLANK_COUNT));
        rederiveBlanks();
        input.getIntArray("DialValues").ifPresent(saved -> {
            if (saved.length == this.dialValues.length) {
                this.dialValues = saved.clone();
            }
        });
        this.solved = input.getBooleanOr("Solved", false);
        this.lockoutUntilGameTime = input.getLongOr("LockoutUntil", 0L);
        this.playerPlaced = input.getBooleanOr("PlayerPlaced", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putLong("Seed", this.seed);
        output.putInt("BlankCount", this.blankCount);
        output.putIntArray("DialValues", this.dialValues);
        output.putBoolean("Solved", this.solved);
        output.putLong("LockoutUntil", this.lockoutUntilGameTime);
        output.putBoolean("PlayerPlaced", this.playerPlaced);
    }

    // Vanilla ChestBlockEntity's own open/close sounds are gated on `blockState.getBlock() instanceof
    // ChestBlock` (see ContainerOpenersCounter#onOpen/onClose) -- CipherChestBlock deliberately isn't one
    // (that instanceof check is half of the double-chest-forbidden proof), so those sounds never fire for
    // this block. Rather than becoming a ChestBlock (which would resurrect the double-chest merge
    // machinery) or re-copying the private opener counter, this replicates just the sound half directly:
    // super.startOpen/stopOpen/recheckOpen still drive the real opener count (comparator output, the lid
    // animation trigger), and a before/after read of that count via the public ChestBlockEntity.getOpenCount
    // detects the 0<->1 transition to play the same vanilla chest open/close sound vanilla would have.
    @Override
    public void startOpen(ContainerUser containerUser) {
        int before = openCount();
        super.startOpen(containerUser);
        if (before == 0 && openCount() > 0) {
            playChestSound(SoundEvents.CHEST_OPEN);
        }
    }

    @Override
    public void stopOpen(ContainerUser containerUser) {
        int before = openCount();
        super.stopOpen(containerUser);
        if (before > 0 && openCount() == 0) {
            playChestSound(SoundEvents.CHEST_CLOSE);
        }
    }

    @Override
    public void recheckOpen() {
        int before = openCount();
        super.recheckOpen();
        int after = openCount();
        if (before == 0 && after > 0) {
            playChestSound(SoundEvents.CHEST_OPEN);
        } else if (before > 0 && after == 0) {
            playChestSound(SoundEvents.CHEST_CLOSE);
        }
    }

    private int openCount() {
        Level level = this.getLevel();
        return level == null ? this.lastKnownOpenCount : (this.lastKnownOpenCount = ChestBlockEntity.getOpenCount(level, this.getBlockPos()));
    }

    // [VANILLACOPY] ChestBlockEntity's private static playSound, trimmed to the single-chest case (no
    // ChestType.LEFT/RIGHT offset -- this block has no ChestType property at all).
    private void playChestSound(SoundEvent event) {
        Level level = this.getLevel();
        if (level == null) {
            return;
        }
        BlockPos pos = this.getBlockPos();
        level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, event, SoundSource.BLOCKS,
                0.5F, level.getRandom().nextFloat() * 0.1F + 0.9F);
    }

    private static int clampBlankCount(int value) {
        return Math.max(MIN_BLANK_COUNT, Math.min(MAX_BLANK_COUNT, value));
    }

    // Vanilla ChestBlockEntity doesn't sync its NBT to the client (chest contents sync through the open
    // menu's slots instead), but the BER needs the lock fields client-side to draw the live grid, so this
    // opts back in
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

}
