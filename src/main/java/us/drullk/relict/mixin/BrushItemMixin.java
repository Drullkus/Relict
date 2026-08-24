package us.drullk.relict.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BrushItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import us.drullk.relict.block.wreck.SolarPanelDecay;

/**
 * Vanilla's brush interaction is hardwired to {@link net.minecraft.world.level.block.entity.BrushableBlockEntity}
 * (see {@code BrushItem.onUseTick}), so the solar panel — which carries no block entity — hooks the same
 * per-stroke cadence here instead. The original method still runs after this injection and no-ops for our
 * blocks, since they are not a BrushableBlockEntity.
 */
@Mixin(BrushItem.class)
public abstract class BrushItemMixin {

    @Shadow
    public abstract int getUseDuration(ItemStack itemStack, LivingEntity user);

    @Invoker("calculateHitResult")
    abstract HitResult relict$calculateHitResult(Player player);

    @Inject(method = "onUseTick", at = @At("HEAD"))
    private void relict$brushSolarPanel(Level level, LivingEntity livingEntity, ItemStack itemStack, int ticksRemaining, CallbackInfo ci) {
        if (ticksRemaining < 0 || !(livingEntity instanceof Player player) || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        if (!(this.relict$calculateHitResult(player) instanceof BlockHitResult blockHit) || blockHit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        int timeElapsed = this.getUseDuration(itemStack, livingEntity) - ticksRemaining + 1;
        if (timeElapsed % 10 != 5) {
            return;
        }

        BlockPos pos = blockHit.getBlockPos();
        BlockState state = serverLevel.getBlockState(pos);
        SolarPanelDecay.brush(serverLevel, pos, state, player, itemStack);
    }

}
