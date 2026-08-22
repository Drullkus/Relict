package us.drullk.relict.atmosphere;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The whole stay's storm, rolled once and stored. Stores the storm's <em>future</em> rather than stepping a
 * present-tense phase forward every tick, so phase and {@code tau} become pure functions of
 * {@code (marsTotalTicks, geometry, schedule)} — the same treatment seasonal {@code pressure} already gets.
 * <p>
 * {@code cycleIndex} is stamped even when the stay rolled no storm ({@code stormFrequencyPercent} below
 * 100), so "has this stay's cycle already been rolled" stays a single field comparison against
 * {@link AtmosphereCurve.CycleGeometry#cycleIndex()} rather than a second flag.
 */
public record StormSchedule(long cycleIndex, long leadInStartTick, int leadInTicks, int durationTicks, float staticAxis, float dustAxis, float fluxAxis) {

    /** Never stored as-is under a real cycle — see the class doc. Only meaningful before any roll has run. */
    public static final StormSchedule NONE = new StormSchedule(-1, 0, 0, 0, 0, 0, 0);

    public static final Codec<StormSchedule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.LONG.optionalFieldOf("cycle_index", -1L).forGetter(StormSchedule::cycleIndex),
                    Codec.LONG.optionalFieldOf("lead_in_start_tick", 0L).forGetter(StormSchedule::leadInStartTick),
                    Codec.INT.optionalFieldOf("lead_in_ticks", 0).forGetter(StormSchedule::leadInTicks),
                    Codec.INT.optionalFieldOf("duration_ticks", 0).forGetter(StormSchedule::durationTicks),
                    Codec.FLOAT.optionalFieldOf("static_axis", 0.0F).forGetter(StormSchedule::staticAxis),
                    Codec.FLOAT.optionalFieldOf("dust_axis", 0.0F).forGetter(StormSchedule::dustAxis),
                    Codec.FLOAT.optionalFieldOf("flux_axis", 0.0F).forGetter(StormSchedule::fluxAxis))
            .apply(instance, StormSchedule::new));

    public static final StreamCodec<ByteBuf, StormSchedule> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, StormSchedule::cycleIndex,
            ByteBufCodecs.VAR_LONG, StormSchedule::leadInStartTick,
            ByteBufCodecs.VAR_INT, StormSchedule::leadInTicks,
            ByteBufCodecs.VAR_INT, StormSchedule::durationTicks,
            ByteBufCodecs.FLOAT, StormSchedule::staticAxis,
            ByteBufCodecs.FLOAT, StormSchedule::dustAxis,
            ByteBufCodecs.FLOAT, StormSchedule::fluxAxis,
            StormSchedule::new);

    /** A stamped-but-empty schedule for a cycle that rolled no storm; distinct from {@link #NONE}'s sentinel index. */
    public static StormSchedule none(long cycleIndex) {
        return new StormSchedule(cycleIndex, 0, 0, 0, 0, 0, 0);
    }

    public boolean hasStorm() {
        return this.durationTicks > 0;
    }

    public long stormStartTick() {
        return this.leadInStartTick + this.leadInTicks;
    }

    public long stormEndTick() {
        return this.stormStartTick() + this.durationTicks;
    }

}
