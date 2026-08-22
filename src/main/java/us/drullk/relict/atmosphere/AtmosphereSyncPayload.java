package us.drullk.relict.atmosphere;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import us.drullk.relict.Relict;

/**
 * The only packet this system sends: the stay's {@link StormSchedule} plus the two gamerule-shaped values
 * the client cannot read for itself. Sparse by construction — sent on a schedule change or a heartbeat,
 * plus once immediately on join/dimension-change (see {@code RelictAtmosphereServer}), never per tick.
 * <p>
 * Seasonal {@code pressure} rides for nearly free on top of it: both sides read the same Mars
 * {@code WorldClock} (already kept in sync by vanilla's own clock packets) and run the same pure curve, so
 * only {@code cycleTenthSols} — a gamerule, and gamerule values are not broadcast to clients in general —
 * needs to travel at all.
 * <p>
 * {@code StormPhase} no longer rides the wire: it is derived from the schedule via
 * {@link AtmosphereCurve#stormAt}, so there is no ordinal wire mapping to keep stable.
 * <p>
 * {@code rampTicks} deliberately does not ride: it stays a compile-time constant in {@link AtmosphereCurve},
 * shared by both sides. If it ever becomes a gamerule, it joins this payload too.
 */
public record AtmosphereSyncPayload(int cycleTenthSols, StormSchedule schedule) implements CustomPacketPayload {

    public static final Type<AtmosphereSyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Relict.MODID, "atmosphere_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AtmosphereSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AtmosphereSyncPayload::cycleTenthSols,
            StormSchedule.STREAM_CODEC, AtmosphereSyncPayload::schedule,
            AtmosphereSyncPayload::new);

    @Override
    public Type<AtmosphereSyncPayload> type() {
        return TYPE;
    }

}
