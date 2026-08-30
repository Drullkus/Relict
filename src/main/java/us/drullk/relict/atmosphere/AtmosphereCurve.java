package us.drullk.relict.atmosphere;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Pure math for the atmosphere/storm clock. No world, level, or registry access, so the server, the client,
 * and the headless {@code AtmosphereCurveSampler} datagen report all evaluate the exact same function — the
 * client never lerps a guess, it recomputes the real curve from a synced anchor.
 * <p>
 * The storm's <em>future</em> is stored ({@link StormSchedule}, one per stay), not its present: phase,
 * {@code tau}, and discharge chance are all derived as pure functions of
 * {@code (marsTotalTicks, geometry, schedule)}, the same treatment seasonal {@code pressure} gets.
 * <p>
 * All constants here are tunable.
 */
public final class AtmosphereCurve {

    private AtmosphereCurve() {
    }

    // ------------------------------------------------------------------------------------------- cycle geometry

    /**
     * Ramp length on each side of the cycle, in ticks. Ramps live <em>inside</em> their half rather than
     * padding it — FILLING + PRESENT sum to exactly half the cycle, same for THINNING + VACUUM — so tuning
     * this never changes the half-cycle length, only how much of it is plateau. Clamped against a
     * pathologically short cycle gamerule in {@link #geometryAt}, so a degenerate cycle becomes pure ramps
     * rather than negative plateaus.
     */
    public static final int RAMP_TICKS = 5 * 60 * 20;

    /** One cycle's geometry, derived once per cycle from the Mars clock and the cycle-length gamerule. */
    public record CycleGeometry(long cycleIndex, long cycleStartTick, long cycleTicks, long rampTicks, long halfTicks) {

        public long presentStart() {
            return this.cycleStartTick + this.rampTicks;
        }

        public long presentEnd() {
            return this.cycleStartTick + this.halfTicks;
        }

        public long thinningEnd() {
            return this.cycleStartTick + this.halfTicks + this.rampTicks;
        }

    }

    /**
     * Cycle position 0 is the start of FILLING — the moment atmosphere begins arriving. That is the cycle's
     * own zero, so a new stay's roll needs no edge detector: it fires whenever {@code cycleIndex} changes.
     * {@code floorDiv}, not {@code /} — the Mars clock is a {@code long} that starts at 0 but commands and
     * {@code /time} can move it, and {@code %} on a negative tick would put the whole model half a cycle out.
     */
    public static CycleGeometry geometryAt(long totalTicks, int solTicks, int cycleTenthSols) {
        long cycleTicks = (long) solTicks * cycleTenthSols / 10;
        long cycleIndex = Math.floorDiv(totalTicks, cycleTicks);
        long cycleStartTick = cycleIndex * cycleTicks;
        long halfTicks = cycleTicks / 2;
        long rampTicks = Math.min(RAMP_TICKS, halfTicks);
        return new CycleGeometry(cycleIndex, cycleStartTick, cycleTicks, rampTicks, halfTicks);
    }

    // ---------------------------------------------------------------------------------------- seasonal pressure

    /**
     * {@code pressure}, 0..1: continuous, shaped (plateau at both extremes, eased ramps), never a bare sine.
     * FILLING and THINNING are the eased ramps; PRESENT and VACUUM are the flat plateaus either side.
     */
    public static float pressureAt(long totalTicks, CycleGeometry geo) {
        long offset = totalTicks - geo.cycleStartTick();
        if (offset < geo.rampTicks()) {
            return (float) smoothstep(offset / (double) geo.rampTicks());
        }
        if (offset < geo.halfTicks()) {
            return 1.0F;
        }
        if (offset < geo.halfTicks() + geo.rampTicks()) {
            double p = (offset - geo.halfTicks()) / (double) geo.rampTicks();
            return 1.0F - (float) smoothstep(p);
        }
        return 0.0F;
    }

    /** The seasonal phase, derived from the same split as {@link #pressureAt}, never stored on its own. */
    public static AtmospherePhase phaseAt(long totalTicks, CycleGeometry geo) {
        long offset = totalTicks - geo.cycleStartTick();
        if (offset < geo.rampTicks()) {
            return AtmospherePhase.FILLING;
        }
        if (offset < geo.halfTicks()) {
            return AtmospherePhase.PRESENT;
        }
        if (offset < geo.halfTicks() + geo.rampTicks()) {
            return AtmospherePhase.THINNING;
        }
        return AtmospherePhase.VACUUM;
    }

    // ------------------------------------------------------------------------------------ one-storm-per-stay roll

    /** Storm duration bounds, rolled once per stay. */
    public static final int STORM_MIN_TICKS = 6000;
    public static final int STORM_MAX_TICKS = 18000;

    /** Lead-in bounds, rolled once per stay independent of storm duration. */
    public static final int LEAD_IN_MIN_TICKS = 2400;
    public static final int LEAD_IN_MAX_TICKS = 3600;

    /**
     * The roller never deliberately lets a storm overhang into THINNING. A fitting start always exists at
     * default settings, so overhang can only happen from a {@code /relictstorm} override, a shortened cycle
     * gamerule, or a roll made mid-stay — truncation ({@link #stormAt}) stays implemented and
     * sampler-tested for those cases regardless.
     */
    public static final boolean STORM_MAY_OVERHANG = false;

    /**
     * Rolls this stay's storm. Called once per cycle, the instant {@code data.schedule().cycleIndex() !=
     * geo.cycleIndex()} is observed — see {@link us.drullk.relict.atmosphere.RelictAtmosphereServer}.
     *
     * @param nowTicks the Mars total-tick at the moment of the roll (not necessarily {@code geo.cycleStartTick()}:
     *                 the roll can happen mid-stay if nobody was on Mars when the cycle turned over)
     */
    public static StormSchedule roll(CycleGeometry geo, long nowTicks, int stormFrequencyPercent, RandomSource random) {
        if (true) { // FIXME remove this disable, once storm is polished and sounds are added
            return StormSchedule.none(geo.cycleIndex());
        }

        double chance = Math.min(1.0, stormFrequencyPercent / 100.0);
        if (random.nextDouble() >= chance) {
            return StormSchedule.none(geo.cycleIndex());
        }

        int durationTicks = STORM_MIN_TICKS + random.nextInt(STORM_MAX_TICKS - STORM_MIN_TICKS + 1);
        int leadInTicks = LEAD_IN_MIN_TICKS + random.nextInt(LEAD_IN_MAX_TICKS - LEAD_IN_MIN_TICKS + 1);

        long earliestStart = Math.max(geo.presentStart(), nowTicks + leadInTicks);
        long latestStart = geo.presentEnd() - (STORM_MAY_OVERHANG ? 1 : durationTicks);
        if (earliestStart > latestStart) {
            // The window closed — only possible on a mid-stay roll. Honest answer: no storm this stay.
            return StormSchedule.none(geo.cycleIndex());
        }

        long stormStart = earliestStart + (long) (random.nextDouble() * (latestStart - earliestStart + 1));
        stormStart = Math.min(stormStart, latestStart);
        long leadInStartTick = stormStart - leadInTicks;

        float staticAxis = random.nextFloat();
        float dustAxis = random.nextFloat();
        float fluxAxis = random.nextFloat();

        return new StormSchedule(geo.cycleIndex(), leadInStartTick, leadInTicks, durationTicks, staticAxis, dustAxis, fluxAxis);
    }

    // -------------------------------------------------------------------------------------------------- the arc

    /** Fixed bookend (audio shape, not severity): a single baked stinger, absurd stretched to storm length. */
    public static final int ARRIVAL_SILENCE_TICKS = 60;

    /** Fixed bookend: a bed length, not a fraction of the storm. */
    public static final int TAIL_TICKS = 1200;

    /** Ceiling on {@code LEAD_IN}'s own {@code tau}: a faint haze, the audio cue's visual twin. */
    public static final float LEAD_IN_TAU_MAX = 0.08F;

    /** {@code tau} ceiling floor at {@code dustAxis == 0}; a low-dust storm never fully whites out the sky. */
    public static final float DUST_TAU_CEILING_MIN = 0.45F;

    /** {@code tau} ceiling for this storm, from the rolled dust axis. Multiplies into every phase's {@code tau}. */
    public static float tauCeiling(float dustAxis) {
        return Mth.lerp(dustAxis, DUST_TAU_CEILING_MIN, 1.0F);
    }

    public record StormArc(StormPhase phase, float tau, double phaseProgress, long ticksIntoPhase, long phaseDurationTicks) {

        public static final StormArc CLEAR = new StormArc(StormPhase.CLEAR, 0.0F, 0.0, 0L, 0L);

    }

    /**
     * The public arc entry point. Server, client, {@code /relictstorm status}, and the sampler all call this
     * one method — nothing else computes a phase.
     * <p>
     * If the atmosphere leaves (THINNING) while the storm is running, truncation is a
     * time warp on the storm's own clock once {@code totalTicks} crosses into THINNING: the remaining body
     * ticks are compressed to land exactly on {@link CycleGeometry#thinningEnd()}, so {@code tau} reaches 0
     * and the phase becomes {@link StormPhase#CLEAR} at exactly the tick seasonal pressure does too. The
     * lead-in is never warped.
     */
    public static StormArc stormAt(long totalTicks, CycleGeometry geo, StormSchedule schedule) {
        if (!schedule.hasStorm()) {
            return StormArc.CLEAR;
        }

        long leadInStart = schedule.leadInStartTick();
        long stormStart = schedule.stormStartTick();

        if (totalTicks < leadInStart) {
            return StormArc.CLEAR;
        }

        if (totalTicks < stormStart) {
            long ticksIntoPhase = totalTicks - leadInStart;
            double progress = ticksIntoPhase / (double) schedule.leadInTicks();
            float tau = (float) (smoothstep(progress) * LEAD_IN_TAU_MAX) * tauCeiling(schedule.dustAxis());
            return new StormArc(StormPhase.DISTANT, tau, progress, ticksIntoPhase, schedule.leadInTicks());
        }

        long warped = warpedElapsed(totalTicks, geo, schedule);
        if (warped >= schedule.durationTicks()) {
            return StormArc.CLEAR;
        }

        return bodyArcAt(warped, schedule);
    }

    /**
     * The storm-body clock, after the truncation warp. Equal to the raw elapsed time
     * {@code totalTicks - stormStartTick()} everywhere except inside THINNING, where the remaining body
     * ticks are compressed onto the shrinking window between THINNING's start and its end — the
     * "accelerated tail". Monotone non-decreasing by construction; sampler-asserted.
     * Public so {@code AtmosphereCurveSampler} can assert its monotonicity directly rather than inferring
     * it from phase transitions.
     */
    public static long warpedElapsed(long totalTicks, CycleGeometry geo, StormSchedule schedule) {
        long elapsed = totalTicks - schedule.stormStartTick();
        long thinStart = geo.presentEnd();
        long thinEnd = geo.thinningEnd();

        if (schedule.stormEndTick() <= thinStart || totalTicks <= thinStart) {
            return elapsed;
        }

        long eAtThin = thinStart - schedule.stormStartTick();
        long remaining = schedule.durationTicks() - eAtThin;
        long span = thinEnd - thinStart;
        if (span <= 0) {
            return schedule.durationTicks();
        }

        long warped = eAtThin + remaining * (totalTicks - thinStart) / span;
        return Math.min(warped, schedule.durationTicks());
    }

    /**
     * Splits the storm body into its six phases: two fixed bookends ({@link #ARRIVAL_SILENCE_TICKS},
     * {@link #TAIL_TICKS}) and three phases scaled 2:4:3 ({@code DUST : WIND_BUILD : ELECTRIC_PEAK}).
     * {@code ELECTRIC_PEAK} absorbs the integer remainder so the phases always tile
     * {@code leadInTicks + durationTicks} exactly.
     */
    private static StormArc bodyArcAt(long warpedElapsed, StormSchedule schedule) {
        long body = schedule.durationTicks() - ARRIVAL_SILENCE_TICKS - TAIL_TICKS;
        long dustLen = body * 2L / 9L;
        long windLen = body * 4L / 9L;
        long peakLen = body - dustLen - windLen;

        long offset = warpedElapsed;
        if (offset < ARRIVAL_SILENCE_TICKS) {
            return phaseArc(StormPhase.ARRIVAL, offset, ARRIVAL_SILENCE_TICKS, schedule);
        }
        offset -= ARRIVAL_SILENCE_TICKS;

        if (offset < dustLen) {
            return phaseArc(StormPhase.DUST_ENVELOPE, offset, dustLen, schedule);
        }
        offset -= dustLen;

        if (offset < windLen) {
            return phaseArc(StormPhase.WIND_BUILD, offset, windLen, schedule);
        }
        offset -= windLen;

        if (offset < peakLen) {
            return phaseArc(StormPhase.ELECTRIC_PEAK, offset, peakLen, schedule);
        }
        offset -= peakLen;

        return phaseArc(StormPhase.TAIL, offset, TAIL_TICKS, schedule);
    }

    /**
     * {@code tau} per phase, multiplied by this storm's dust ceiling. Two peaks: {@code tau} peaks at the
     * end of WIND_BUILD, discharge <em>rate</em> (see {@link #dischargeChancePerTick}) peaks later inside
     * ELECTRIC_PEAK.
     */
    private static StormArc phaseArc(StormPhase phase, long ticksIntoPhase, long phaseDurationTicks, StormSchedule schedule) {
        double progress = phaseDurationTicks <= 0 ? 1.0 : Mth.clamp(ticksIntoPhase / (double) phaseDurationTicks, 0.0, 1.0);
        float ceiling = tauCeiling(schedule.dustAxis());
        float tau = switch (phase) {
            // The one deliberate discontinuity: the loaded-gun duck. tau snaps to zero on entry.
            case ARRIVAL -> 0.0F;
            case DUST_ENVELOPE -> (float) (smoothstep(progress) * 0.35) * ceiling;
            case WIND_BUILD -> lerp(0.35F, 1.0F, (float) smoothstep(progress)) * ceiling;
            case ELECTRIC_PEAK -> lerp(1.0F, 0.5F, (float) smoothstep(progress)) * ceiling;
            case TAIL -> lerp(0.5F, 0.0F, (float) smoothstep(progress)) * ceiling;
            default -> 0.0F;
        };
        return new StormArc(phase, tau, progress, ticksIntoPhase, phaseDurationTicks);
    }

    /**
     * The offset, in ticks from {@code schedule.leadInStartTick()}, of the given phase's start — the same
     * weight table {@link #bodyArcAt} uses, exposed once for {@code /relictstorm force} so there is one
     * table, not two. {@link StormPhase#CLEAR} returns the offset of the storm's own end.
     */
    public static long phaseStartOffset(StormSchedule schedule, StormPhase phase) {
        if (phase == StormPhase.DISTANT) {
            return 0L;
        }

        long body = schedule.durationTicks() - ARRIVAL_SILENCE_TICKS - TAIL_TICKS;
        long dustLen = body * 2L / 9L;
        long windLen = body * 4L / 9L;
        long peakLen = body - dustLen - windLen;

        long offset = schedule.leadInTicks();
        if (phase == StormPhase.ARRIVAL) {
            return offset;
        }
        offset += ARRIVAL_SILENCE_TICKS;
        if (phase == StormPhase.DUST_ENVELOPE) {
            return offset;
        }
        offset += dustLen;
        if (phase == StormPhase.WIND_BUILD) {
            return offset;
        }
        offset += windLen;
        if (phase == StormPhase.ELECTRIC_PEAK) {
            return offset;
        }
        offset += peakLen;
        if (phase == StormPhase.TAIL) {
            return offset;
        }

        // CLEAR: the storm's own end.
        return schedule.leadInTicks() + schedule.durationTicks();
    }

    // ---------------------------------------------------------------------------------------------- discharges

    /** Per-phase multiplier on discharge rate: the second peak, offset from {@code tau}'s. */
    private static float dischargePhaseMultiplier(StormPhase phase) {
        return switch (phase) {
            case CLEAR, DISTANT, ARRIVAL, DUST_ENVELOPE -> 0.0F;
            // Rare telegraph zaps while the wind is still climbing.
            case WIND_BUILD -> 0.1F;
            case ELECTRIC_PEAK -> 1.0F;
            // Lingering danger in the "boring" tail: quieter is not safer.
            case TAIL -> 0.2F;
        };
    }

    /** Chance, per player per tick, of a corona (discharge lead-in) at the given {@code tau} and phase. */
    public static final double BASE_DISCHARGE_CHANCE_PER_TICK = 0.01;

    /** Static-axis scale range on the discharge roll rate. */
    public static final float STATIC_SCALE_MIN = 0.4F;
    public static final float STATIC_SCALE_MAX = 2.5F;

    /**
     * The static axis's single multiplication onto the discharge rate.
     * {@link #DISCHARGE_PLAYER_COOLDOWN_TICKS} and {@link #DISCHARGE_GLOBAL_COOLDOWN_TICKS} are
     * <em>not</em> scaled by this axis, so a {@code staticAxis = 1.0} storm still cannot machine-gun a
     * player — the axis moves the roll rate, the cooldowns keep the hard ceiling.
     */
    public static double dischargeChancePerTick(float tau, StormPhase phase, float staticAxis) {
        return BASE_DISCHARGE_CHANCE_PER_TICK * tau * dischargePhaseMultiplier(phase) * Mth.lerp(staticAxis, STATIC_SCALE_MIN, STATIC_SCALE_MAX);
    }

    /** Lead time between a corona warning and its snap. */
    public static final int DISCHARGE_CORONA_LEAD_TICKS = 8;

    /** Per-player minimum gap between snaps, so a lucky streak of rolls cannot machine-gun one player. */
    public static final int DISCHARGE_PLAYER_COOLDOWN_TICKS = 60;

    /** Server-wide minimum gap between snaps, independent of player count. */
    public static final int DISCHARGE_GLOBAL_COOLDOWN_TICKS = 10;

    /** Flat, roughly one-HP-class hit. */
    public static final float DISCHARGE_DAMAGE = 1.0F;

    // ---------------------------------------------------------------------------------------------- dust devils

    /** Base per-tick roll chance at {@code fluxAxis == 0}; scaled up to {@code x3} at {@code fluxAxis == 1}. */
    public static final double DUST_DEVIL_CHANCE_PER_TICK_BASE = 1.0 / 12000.0;

    /** Devils appear once pressure clears this floor, on either seasonal ramp too, not just the plateau. */
    public static final float DUST_DEVIL_PRESSURE_FLOOR = 0.35F;

    public static double dustDevilChancePerTick(float fluxAxis) {
        return DUST_DEVIL_CHANCE_PER_TICK_BASE * Mth.lerp(fluxAxis, 0.5F, 3.0F);
    }

    public static int dustDevilMaxCount(float fluxAxis) {
        return Math.round(Mth.lerp(fluxAxis, 1.0F, 4.0F));
    }

    public static int dustDevilLifetimeTicks(float fluxAxis) {
        return Math.round(Mth.lerp(fluxAxis, 120.0F, 300.0F));
    }

    public static float dustDevilColumnHeightScale(float fluxAxis) {
        return Mth.lerp(fluxAxis, 2.5F, 6.0F);
    }

    public static int dustDevilParticleCount(float fluxAxis) {
        return Math.round(Mth.lerp(fluxAxis, 2.0F, 6.0F));
    }

    public static float dustDevilHorizontalSpreadScale(float fluxAxis) {
        return Mth.lerp(fluxAxis, 1.0F, 1.8F);
    }

    public static float dustDevilSoundVolume(float fluxAxis) {
        return Mth.lerp(fluxAxis, 0.5F, 1.0F);
    }

    // -------------------------------------------------------------------------------------------------- shared

    private static double smoothstep(double x) {
        double t = Mth.clamp(x, 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    private static float lerp(float from, float to, double t) {
        return (float) (from + (to - from) * t);
    }

}
