package us.drullk.relict.client.item;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import us.drullk.relict.RelictTags;
import us.drullk.relict.client.atmosphere.RelictAtmosphere;
import us.drullk.relict.init.RelictDataComponents;
import us.drullk.relict.item.WeatherglassReading;

/**
 * The custom {@code range_dispatch} property behind every countdown face's 5-frame animation (see {@link
 * WeatherglassFaceProperty}). Mars reads the live storm arc; vanilla-weather dimensions read the stored
 * {@link WeatherglassReading} snapshot against the current overworld clock tick, mirroring the same clock
 * the server stamped the snapshot with (see {@code WeatherglassItem}).
 */
public record WeatherglassCountdownProperty() implements RangeSelectItemModelProperty {

    public static final MapCodec<WeatherglassCountdownProperty> MAP_CODEC = MapCodec.unit(new WeatherglassCountdownProperty());

    @Override
    public float get(ItemStack itemStack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        if (level == null) {
            return 0.0F;
        }

        if (level.dimensionTypeRegistration().is(RelictTags.HAS_MARS_ATMOSPHERE)) {
            return marsFraction();
        }

        WeatherglassReading reading = itemStack.get(RelictDataComponents.WEATHERGLASS_READING.get());
        return reading == null ? 0.0F : reading.fraction(overworldGameTime(level));
    }

    /** Storm windows keep reading the storm's own arc; once the arc is {@code CLEAR} the meter falls
     * through to the atmosphere presence cycle (same precedence as {@link WeatherglassFaceProperty}). */
    private static float marsFraction() {
        return switch (RelictAtmosphere.clientStormPhase()) {
            case DISTANT -> (float) RelictAtmosphere.clientArc().phaseProgress();
            case ARRIVAL, DUST_ENVELOPE, WIND_BUILD, ELECTRIC_PEAK, TAIL -> RelictAtmosphere.clientStormExitFraction();
            case CLEAR -> RelictAtmosphere.clientAtmosphereFraction();
        };
    }

    private static long overworldGameTime(ClientLevel level) {
        Holder<WorldClock> overworldClock = level.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK).getOrThrow(WorldClocks.OVERWORLD);
        return level.clockManager().getTotalTicks(overworldClock);
    }

    @Override
    public MapCodec<WeatherglassCountdownProperty> type() {
        return MAP_CODEC;
    }

}
