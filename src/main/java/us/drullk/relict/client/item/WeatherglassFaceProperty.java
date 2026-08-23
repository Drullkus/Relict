package us.drullk.relict.client.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import us.drullk.relict.RelictTags;
import us.drullk.relict.atmosphere.StormPhase;
import us.drullk.relict.client.atmosphere.RelictAtmosphere;
import us.drullk.relict.init.RelictDataComponents;
import us.drullk.relict.item.WeatherglassReading;

/**
 * The custom {@code select} property driving the Patient Weatherglass's face. Mars reads {@link
 * RelictAtmosphere}'s already-synced schedule live; every other dimension reads the {@link
 * WeatherglassReading} data component written on the item's last check (stale until rechecked — see
 * that class). A no-weather dimension (Nether, End) always shows the clear-default face, live, regardless
 * of any stale reading — {@code canHaveWeather()} is a per-dimension-type property, not a countdown, so
 * there is nothing to be stale about there.
 * <p>
 * Mars precedence: a storm's own into/exit countdown window (any {@link StormPhase} but {@code CLEAR})
 * always owns the face; only once the storm arc reports {@code CLEAR} does the face fall through to the
 * atmosphere presence cycle ({@code FILLING}/{@code THINNING}/{@code VACUUM}, or {@code PRESENT} =
 * {@code MARS_CLEAR}). This falls out of the storm roll's own bounds — a storm's warped tail already lands
 * on {@code CLEAR} at the same tick THINNING ends — so no extra tie-breaking is needed here.
 */
public record WeatherglassFaceProperty() implements SelectItemModelProperty<WeatherglassFace> {

    public static final SelectItemModelProperty.Type<WeatherglassFaceProperty, WeatherglassFace> TYPE =
            SelectItemModelProperty.Type.create(MapCodec.unit(new WeatherglassFaceProperty()), WeatherglassFace.CODEC);

    @Override
    public @Nullable WeatherglassFace get(ItemStack itemStack, @Nullable ClientLevel level, @Nullable LivingEntity owner, int seed, ItemDisplayContext displayContext) {
        if (level == null) {
            return WeatherglassFace.CLEAR_DEFAULT;
        }

        if (level.dimensionTypeRegistration().is(RelictTags.HAS_MARS_ATMOSPHERE)) {
            return marsFace();
        }

        if (!level.canHaveWeather()) {
            return WeatherglassFace.CLEAR_DEFAULT;
        }

        WeatherglassReading reading = itemStack.get(RelictDataComponents.WEATHERGLASS_READING.get());
        return reading == null ? WeatherglassFace.CLEAR_DEFAULT : vanillaFace(reading.kind());
    }

    private static WeatherglassFace marsFace() {
        StormPhase stormPhase = RelictAtmosphere.clientStormPhase();
        if (stormPhase == StormPhase.DISTANT) {
            return WeatherglassFace.MARS_STORM_INTO;
        }
        if (stormPhase != StormPhase.CLEAR) {
            return WeatherglassFace.MARS_STORM_EXIT;
        }

        return switch (RelictAtmosphere.clientAtmosPhase()) {
            case PRESENT -> WeatherglassFace.MARS_CLEAR;
            case FILLING -> WeatherglassFace.ATMO_FILLING;
            case THINNING -> WeatherglassFace.ATMO_THINNING;
            case VACUUM -> WeatherglassFace.ATMO_VACUUM;
        };
    }

    private static WeatherglassFace vanillaFace(WeatherglassReading.Kind kind) {
        return switch (kind) {
            case CLEAR -> WeatherglassFace.CLEAR_DEFAULT;
            case RAIN_INTO -> WeatherglassFace.RAIN_INTO;
            case RAIN_EXIT -> WeatherglassFace.RAIN_EXIT;
            case THUNDER_INTO -> WeatherglassFace.THUNDER_INTO;
            case THUNDER_EXIT -> WeatherglassFace.THUNDER_EXIT;
        };
    }

    @Override
    public SelectItemModelProperty.Type<WeatherglassFaceProperty, WeatherglassFace> type() {
        return TYPE;
    }

    @Override
    public Codec<WeatherglassFace> valueCodec() {
        return WeatherglassFace.CODEC;
    }

}
