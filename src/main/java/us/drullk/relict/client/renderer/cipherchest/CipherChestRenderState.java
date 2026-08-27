package us.drullk.relict.client.renderer.cipherchest;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;
import us.drullk.relict.block.cipherchest.CipherChestSquare;

/**
 * Everything the BER needs to draw a frame, extracted from {@link us.drullk.relict.block.cipherchest.CipherChestBlockEntity}
 * on the client each frame.
 */
public class CipherChestRenderState extends BlockEntityRenderState {

    public final int[] values = new int[CipherChestSquare.CELL_COUNT];
    public final boolean[] blank = new boolean[CipherChestSquare.CELL_COUNT];
    public Direction facing = Direction.NORTH;
    public float open;
    public boolean solved;

    /** True while a wrong guess's lockout is still running -- the sine-wave red blink plays for exactly this window. */
    public boolean blinking;
    /** Game time (ticks, plus partial tick) fed to the blink's sine wave so the phase is continuous across frames. */
    public float blinkPhase;
    /** Game time the current lockout ends at -- the blink's fade-out envelope counts down to this. */
    public long lockoutUntil;

}
