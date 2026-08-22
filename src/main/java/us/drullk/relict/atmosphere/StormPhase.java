package us.drullk.relict.atmosphere;

/**
 * The asymmetric storm arc: a rolled lead-in build, sharp entry impact, a wind peak, an electric peak
 * while the wind decays, then a long tail with no match for storm's entry.
 * <p>
 * Does not ride the network: {@link AtmosphereSyncPayload} carries the whole {@link StormSchedule} and
 * both sides derive the current phase from it via {@link AtmosphereCurve#stormAt}, so there is no ordinal
 * to synchronize over packets.
 */
public enum StormPhase {
    CLEAR,
    DISTANT,
    ARRIVAL,
    DUST_ENVELOPE,
    WIND_BUILD,
    ELECTRIC_PEAK,
    TAIL;

    public StormPhase next() {
        return switch (this) {
            case CLEAR -> DISTANT;
            case DISTANT -> ARRIVAL;
            case ARRIVAL -> DUST_ENVELOPE;
            case DUST_ENVELOPE -> WIND_BUILD;
            case WIND_BUILD -> ELECTRIC_PEAK;
            case ELECTRIC_PEAK -> TAIL;
            case TAIL -> CLEAR;
        };
    }
}
