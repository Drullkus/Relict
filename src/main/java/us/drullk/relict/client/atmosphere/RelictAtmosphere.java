package us.drullk.relict.client.atmosphere;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.clock.WorldClock;
import us.drullk.relict.atmosphere.AtmosphereCurve;
import us.drullk.relict.atmosphere.AtmosphereCurve.CycleGeometry;
import us.drullk.relict.atmosphere.AtmosphereCurve.StormArc;
import us.drullk.relict.atmosphere.AtmospherePhase;
import us.drullk.relict.atmosphere.AtmosphereSyncPayload;
import us.drullk.relict.atmosphere.StormPhase;
import us.drullk.relict.atmosphere.StormSchedule;
import us.drullk.relict.init.worldgen.RelictDimension;
import org.jspecify.annotations.Nullable;

/**
 * Client-side reflection of the storm clock. Never lerps: the server publishes a sparse
 * {@link AtmosphereSyncPayload} anchor (the whole {@link StormSchedule}, not a present-tense phase) and
 * this class re-runs the exact same {@link AtmosphereCurve} the server used, reading the Mars
 * {@code WorldClock}'s own tick count — which vanilla already keeps in sync — so every frame is exact, not
 * interpolated toward a guess.
 * <p>
 * {@code cycleTenthSols} rides in the payload rather than being read from a synced gamerule: game rule
 * values are not broadcast to clients in general in 26.2
 * <p>
 * Consumed by {@code MarsSkyboxRenderer} and the storm-visual hooks in this package; a future audio
 * controller is the other intended reader, hence the accessor names.
 */
public final class RelictAtmosphere {

    /**
     * Matches {@link us.drullk.relict.init.RelictGameRules#ATMOSPHERE_CYCLE_TENTH_SOLS}'s own default,
     * until the first sync arrives — see {@link #isSynced()} for the renderer-side hazard this guards
     * against (up to ~2 s of a guessed cycle right after joining or changing dimension).
     */
    private static volatile int cycleTenthSols = 25;
    private static volatile StormSchedule schedule = StormSchedule.NONE;
    private static volatile boolean synced = false;

    private RelictAtmosphere() {
    }

    public static void handleSync(AtmosphereSyncPayload payload) {
        cycleTenthSols = payload.cycleTenthSols();
        schedule = payload.schedule();
        synced = true;
    }

    /**
     * Whether at least one sync payload has arrived since the current level was loaded. Before the first
     * sync (up to the ~2 s join/dimension-change window), every accessor below reads a guessed default
     * rather than the real schedule — {@code MarsSkyboxRenderer} tolerates that (it already handles
     * {@code pressure = 0} / {@code tau = 0}), but a client-side effect that reacts to the guess directly
     * should check this first.
     */
    public static boolean isSynced() {
        return synced;
    }

    /** Seasonal pressure, 0..1, or 0 if no level is loaded yet. The Mars clock is server-global, so this
     * reads correctly even from another dimension — vanilla broadcasts its updates to every player. */
    public static float clientPressure() {
        Long totalTicks = marsTotalTicks();
        return totalTicks == null ? 0.0F : AtmosphereCurve.pressureAt(totalTicks, geometry(totalTicks));
    }

    /** Dust opacity, 0..1 (already {@code arcShape(phase) x tauCeiling(dustAxis)}), or 0 if no level yet. */
    public static float clientTau() {
        Long totalTicks = marsTotalTicks();
        if (totalTicks == null) {
            return 0.0F;
        }

        return AtmosphereCurve.stormAt(totalTicks, geometry(totalTicks), schedule).tau();
    }

    public static AtmospherePhase clientAtmosPhase() {
        Long totalTicks = marsTotalTicks();
        return totalTicks == null ? AtmospherePhase.VACUUM : AtmosphereCurve.phaseAt(totalTicks, geometry(totalTicks));
    }

    public static StormPhase clientStormPhase() {
        Long totalTicks = marsTotalTicks();
        return totalTicks == null ? StormPhase.CLEAR : AtmosphereCurve.stormAt(totalTicks, geometry(totalTicks), schedule).phase();
    }

    /** The full live arc — phase, tau, and progress into the phase — for client effects that need more
     * than just tau (storm visuals: ambient dust density, fog contraction). */
    public static StormArc clientArc() {
        Long totalTicks = marsTotalTicks();
        if (totalTicks == null) {
            return new StormArc(StormPhase.CLEAR, 0.0F, 0.0, 0L, 0L);
        }

        return AtmosphereCurve.stormAt(totalTicks, geometry(totalTicks), schedule);
    }

    private static CycleGeometry geometry(long totalTicks) {
        return AtmosphereCurve.geometryAt(totalTicks, RelictDimension.SOL_TICKS, cycleTenthSols);
    }

    @Nullable
    private static Long marsTotalTicks() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }

        Holder<WorldClock> marsClock = level.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK).getOrThrow(RelictDimension.MARS_CLOCK);
        return level.clockManager().getTotalTicks(marsClock);
    }

}
