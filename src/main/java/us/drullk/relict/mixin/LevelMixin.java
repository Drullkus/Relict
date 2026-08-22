package us.drullk.relict.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import us.drullk.relict.RelictTags;

/**
 * Mars is structurally weatherless: forces {@link Level#canHaveWeather()} false for any dimension type
 * tagged {@link RelictTags#HAS_MARS_ATMOSPHERE}. {@code Level} is common code, so this one mixin covers
 * both logical sides, and every dependent (weather ramping, sync packets, {@code isRaining}/
 * {@code isThundering}) inherits the false from there.
 */
@Mixin(Level.class)
public abstract class LevelMixin {

    @Shadow
    public abstract Holder<DimensionType> dimensionTypeRegistration();

    @Inject(method = "canHaveWeather", at = @At("HEAD"), cancellable = true)
    private void relict$noWeatherOnMars(CallbackInfoReturnable<Boolean> cir) {
        if (this.dimensionTypeRegistration().is(RelictTags.HAS_MARS_ATMOSPHERE)) {
            cir.setReturnValue(false);
        }
    }

}
