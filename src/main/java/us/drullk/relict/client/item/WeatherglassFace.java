package us.drullk.relict.client.item;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * The eleven sprite states the item model {@code select}s between. Every countdown state wraps a
 * {@link WeatherglassCountdownProperty} range_dispatch for its eight frames; the two clear states are
 * single textures. {@link #id()} is the texture/model path stem under {@code textures/item/weatherglass/}
 * — see the datagen in {@code RelictModels}. The three {@code ATMO_*} states track the Mars atmosphere
 * presence cycle (FILLING/THINNING/VACUUM) after a storm's own countdown windows end; {@code MARS_CLEAR}
 * doubles as the PRESENT face.
 */
public enum WeatherglassFace implements StringRepresentable {
    MARS_CLEAR("mars_clear"),
    CLEAR_DEFAULT("default"),
    RAIN_INTO("rain_into"),
    THUNDER_INTO("thunder_into"),
    MARS_STORM_INTO("storm_into"),
    RAIN_EXIT("rain_exit"),
    THUNDER_EXIT("thunder_exit"),
    MARS_STORM_EXIT("storm_exit"),
    ATMO_THINNING("atmo_thinning"),
    ATMO_VACUUM("atmo_vacuum"),
    ATMO_FILLING("atmo_filling");

    public static final Codec<WeatherglassFace> CODEC = StringRepresentable.fromEnum(WeatherglassFace::values);

    private final String id;

    WeatherglassFace(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    @Override
    public String getSerializedName() {
        return this.id;
    }

}
