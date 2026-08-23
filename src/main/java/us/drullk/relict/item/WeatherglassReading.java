package us.drullk.relict.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

public record WeatherglassReading(Kind kind, long checkedGameTime, long targetGameTime) {

    public static final Codec<WeatherglassReading> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Kind.CODEC.fieldOf("kind").forGetter(WeatherglassReading::kind),
                    Codec.LONG.fieldOf("checked_game_time").forGetter(WeatherglassReading::checkedGameTime),
                    Codec.LONG.fieldOf("target_game_time").forGetter(WeatherglassReading::targetGameTime))
            .apply(instance, WeatherglassReading::new));

    public static final StreamCodec<ByteBuf, WeatherglassReading> STREAM_CODEC = StreamCodec.composite(
            Kind.STREAM_CODEC, WeatherglassReading::kind,
            ByteBufCodecs.VAR_LONG, WeatherglassReading::checkedGameTime,
            ByteBufCodecs.VAR_LONG, WeatherglassReading::targetGameTime,
            WeatherglassReading::new);

    public float fraction(long currentGameTime) {
        if (this.kind == Kind.CLEAR) {
            return 0.0F;
        }

        long denominator = this.targetGameTime - this.checkedGameTime;
        if (denominator <= 0) {
            return 1.0F;
        }

        return Mth.clamp((currentGameTime - this.checkedGameTime) / (float) denominator, 0.0F, 1.0F);
    }

    public enum Kind {
        CLEAR, RAIN_INTO, RAIN_EXIT, THUNDER_INTO, THUNDER_EXIT;

        public static final Codec<Kind> CODEC = Codec.STRING.xmap(Kind::valueOf, Enum::name);
        public static final StreamCodec<ByteBuf, Kind> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(i -> Kind.values()[i], Kind::ordinal);
    }

}
