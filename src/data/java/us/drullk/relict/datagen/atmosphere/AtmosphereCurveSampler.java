package us.drullk.relict.datagen.atmosphere;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.util.RandomSource;
import us.drullk.relict.atmosphere.AtmosphereCurve;
import us.drullk.relict.atmosphere.AtmosphereCurve.CycleGeometry;
import us.drullk.relict.atmosphere.AtmosphereCurve.StormArc;
import us.drullk.relict.atmosphere.AtmospherePhase;
import us.drullk.relict.atmosphere.StormPhase;
import us.drullk.relict.atmosphere.StormSchedule;
import us.drullk.relict.init.worldgen.RelictDimension;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Headless verification for the storm clock — the storm arc's equivalent of
 * {@code RidgeFieldSampler}. {@link AtmosphereCurve} takes no world/registry state, so this samples it
 * directly and asserts the shape rather than trusting the constants by eye. Failing an assertion fails the
 * datagen run, the same discipline the worldgen samplers already use.
 * <p>
 * Four sections: A (half-cycle envelope), B (one-storm-per-stay scheduling bounds), C (lead-in + scaled
 * arc, including the truncation warp), D (axis ranges, including the section-B rolls' independence).
 */
public final class AtmosphereCurveSampler implements DataProvider {

    private static final int DEFAULT_CYCLE_TENTH_SOLS = 25;
    private static final int SHORT_CYCLE_TENTH_SOLS = 5;
    private static final int ROLL_SAMPLE_COUNT = 10_000;

    public AtmosphereCurveSampler(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
    }

    @Override
    public String getName() {
        return "Atmosphere/storm clock report";
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        StringBuilder report = new StringBuilder();

        reportHalfCycleEnvelope(report, DEFAULT_CYCLE_TENTH_SOLS);
        reportHalfCycleEnvelope(report, SHORT_CYCLE_TENTH_SOLS);
        reportScheduling(report);
        reportArc(report);
        reportAxisRanges(report);

        System.out.print(report);
        return CompletableFuture.completedFuture(null);
    }

    // ============================================================================================== section A

    private static void reportHalfCycleEnvelope(StringBuilder report, int cycleTenthSols) {
        report.append(String.format("%n=== A. half-cycle envelope (cycleTenthSols=%d) ===%n%n", cycleTenthSols));

        int solTicks = RelictDimension.SOL_TICKS;
        CycleGeometry geoAtZero = AtmosphereCurve.geometryAt(0L, solTicks, cycleTenthSols);
        long cycleTicks = geoAtZero.cycleTicks();

        require(cycleTicks == (long) solTicks * cycleTenthSols / 10, "cycleTicks must equal solTicks * cycleTenthSols / 10");
        if (cycleTenthSols == DEFAULT_CYCLE_TENTH_SOLS) {
            require(cycleTicks == 66000, "default cycleTicks must be 66000 (55:00)");
            require(cycleTicks == 55L * 1200L, "default cycleTicks must equal 55 minutes at 1200 ticks/min");
        }

        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        Map<AtmospherePhase, Long> phaseTickCounts = new EnumMap<>(AtmospherePhase.class);
        AtmospherePhase previous = null;
        int transitions = 0;
        float previousPressure = -1.0F;
        double maxStepDelta = 0.0;

        for (long tick = 0; tick <= cycleTicks; tick += 1) {
            CycleGeometry geo = AtmosphereCurve.geometryAt(tick, solTicks, cycleTenthSols);
            float pressure = AtmosphereCurve.pressureAt(tick, geo);
            AtmospherePhase phase = AtmosphereCurve.phaseAt(tick, geo);
            min = Math.min(min, pressure);
            max = Math.max(max, pressure);
            phaseTickCounts.merge(phase, 1L, Long::sum);

            if (previous != null) {
                maxStepDelta = Math.max(maxStepDelta, Math.abs(pressure - previousPressure));
                if (previous != phase) {
                    transitions++;
                    AtmospherePhase expectedNext = switch (previous) {
                        case FILLING -> AtmospherePhase.PRESENT;
                        case PRESENT -> AtmospherePhase.THINNING;
                        case THINNING -> AtmospherePhase.VACUUM;
                        case VACUUM -> AtmospherePhase.FILLING;
                    };
                    require(phase == expectedNext, "phase sequence: expected " + previous + " -> " + expectedNext + " but got " + phase);
                }
            }
            previous = phase;
            previousPressure = pressure;
        }

        require(min >= 0.0F && min < 0.001F, "pressure never reaches its VACUUM floor of 0 (min=" + min + ")");
        require(max <= 1.0F && max > 0.999F, "pressure never reaches its PRESENT ceiling of 1 (max=" + max + ")");
        require(transitions == 4, "expected exactly 4 phase transitions per cycle (A3), saw " + transitions);

        long halfTicks = geoAtZero.halfTicks();
        long rampTicks = geoAtZero.rampTicks();
        long fillingPlusPresent = phaseTickCounts.getOrDefault(AtmospherePhase.FILLING, 0L) + phaseTickCounts.getOrDefault(AtmospherePhase.PRESENT, 0L);
        long thinningPlusVacuum = phaseTickCounts.getOrDefault(AtmospherePhase.THINNING, 0L) + phaseTickCounts.getOrDefault(AtmospherePhase.VACUUM, 0L);
        require(Math.abs(fillingPlusPresent - halfTicks) <= 1, "A4: FILLING+PRESENT must equal halfTicks (ramps do not pad the total)");
        require(Math.abs(thinningPlusVacuum - halfTicks) <= 1, "A4: THINNING+VACUUM must equal halfTicks (ramps do not pad the total)");

        require(Math.abs(phaseTickCounts.getOrDefault(AtmospherePhase.FILLING, 0L) - rampTicks) <= 1, "A5: FILLING length must equal rampTicks");
        require(Math.abs(phaseTickCounts.getOrDefault(AtmospherePhase.THINNING, 0L) - rampTicks) <= 1, "A5: THINNING length must equal rampTicks");

        // A6: monotone across the ramps.
        assertMonotone(solTicks, cycleTenthSols, 0, rampTicks, true, "FILLING must be non-decreasing");
        assertMonotone(solTicks, cycleTenthSols, halfTicks, halfTicks + rampTicks, false, "THINNING must be non-increasing");

        // A7: continuity - no step exceeds 2/rampTicks anywhere, and ease is real (endpoints slower than midpoint).
        require(maxStepDelta < 2.0 / rampTicks, "A7: pressure must never step by more than 2/rampTicks in one tick");
        assertEaseIsReal(solTicks, cycleTenthSols, 0, rampTicks, "FILLING");
        assertEaseIsReal(solTicks, cycleTenthSols, halfTicks, halfTicks + rampTicks, "THINNING");

        // A8: geometryAt round-trips, including negative t.
        RandomSource random = RandomSource.create(1234567L);
        for (int i = 0; i < 1000; i++) {
            long t = (random.nextLong() % (cycleTicks * 4)) - cycleTicks * 2;
            CycleGeometry geo = AtmosphereCurve.geometryAt(t, solTicks, cycleTenthSols);
            require(geo.cycleStartTick() <= t && t < geo.cycleStartTick() + geo.cycleTicks(),
                    "A8: t must fall inside its own reported cycle window (t=" + t + ")");
            require(geo.cycleIndex() == Math.floorDiv(t, cycleTicks), "A8: cycleIndex must equal floorDiv(t, cycleTicks) (t=" + t + ")");
        }

        report.append(String.format("cycleTicks       %d ticks (%s)%n", cycleTicks, clock(cycleTicks)));
        report.append(String.format("rampTicks        %d ticks (%s)%n", rampTicks, clock(rampTicks)));
        report.append(String.format("pressure range   %.4f .. %.4f%n", min, max));
        report.append(String.format("phase durations  %s%n", phaseTickCounts));
        report.append("PASS: bounds hit 0/1, 4 clean transitions, ramps live inside their half, ease and continuity hold, geometryAt round-trips\n");
    }

    private static void assertMonotone(int solTicks, int cycleTenthSols, long fromOffset, long toOffset, boolean increasing, String message) {
        float previous = increasing ? -1.0F : 2.0F;
        for (long offset = fromOffset; offset <= toOffset; offset++) {
            CycleGeometry geo = AtmosphereCurve.geometryAt(offset, solTicks, cycleTenthSols);
            float pressure = AtmosphereCurve.pressureAt(offset, geo);
            require(increasing ? pressure >= previous - 1.0e-6F : pressure <= previous + 1.0e-6F, "A6: " + message + " at offset " + offset);
            previous = pressure;
        }
    }

    private static void assertEaseIsReal(int solTicks, int cycleTenthSols, long fromOffset, long toOffset, String label) {
        long span = toOffset - fromOffset;
        if (span < 4) {
            return;
        }

        float p0 = AtmosphereCurve.pressureAt(fromOffset, AtmosphereCurve.geometryAt(fromOffset, solTicks, cycleTenthSols));
        float p1 = AtmosphereCurve.pressureAt(fromOffset + 1, AtmosphereCurve.geometryAt(fromOffset + 1, solTicks, cycleTenthSols));
        float pMidA = AtmosphereCurve.pressureAt(fromOffset + span / 2, AtmosphereCurve.geometryAt(fromOffset + span / 2, solTicks, cycleTenthSols));
        float pMidB = AtmosphereCurve.pressureAt(fromOffset + span / 2 + 1, AtmosphereCurve.geometryAt(fromOffset + span / 2 + 1, solTicks, cycleTenthSols));

        double edgeDelta = Math.abs(p1 - p0);
        double midDelta = Math.abs(pMidB - pMidA);
        require(edgeDelta < midDelta, "A7: " + label + " ease must be real (edge delta " + edgeDelta + " must be smaller than mid-ramp delta " + midDelta + ")");
    }

    // ============================================================================================== section B

    private static void reportScheduling(StringBuilder report) {
        report.append("\n=== B. one-storm-per-stay scheduling bounds ===\n\n");

        int solTicks = RelictDimension.SOL_TICKS;
        CycleGeometry geo = AtmosphereCurve.geometryAt(0L, solTicks, DEFAULT_CYCLE_TENTH_SOLS);

        rollAndAssertOccurrence(geo, 0, "B9: stormFrequencyPercent=0 must never roll a storm");
        rollAndAssertAlwaysStorm(geo, 100, "B9: stormFrequencyPercent=100 must always roll exactly one storm");
        double halfRate = observedOccurrenceRate(geo, 50);
        require(Math.abs(halfRate - 0.5) <= 0.03, "B9: stormFrequencyPercent=50 observed rate must be within +/-0.03 of 0.5, was " + halfRate);

        int minDuration = Integer.MAX_VALUE;
        int maxDuration = Integer.MIN_VALUE;
        int minLeadIn = Integer.MAX_VALUE;
        int maxLeadIn = Integer.MIN_VALUE;

        RandomSource random = RandomSource.create(42L);
        for (int i = 0; i < ROLL_SAMPLE_COUNT; i++) {
            StormSchedule schedule = AtmosphereCurve.roll(geo, geo.cycleStartTick(), 100, random);
            require(schedule.hasStorm(), "B9: stormFrequencyPercent=100 must always produce a storm");

            require(schedule.durationTicks() >= AtmosphereCurve.STORM_MIN_TICKS && schedule.durationTicks() <= AtmosphereCurve.STORM_MAX_TICKS,
                    "B10: durationTicks out of bounds: " + schedule.durationTicks());
            require(schedule.leadInTicks() >= AtmosphereCurve.LEAD_IN_MIN_TICKS && schedule.leadInTicks() <= AtmosphereCurve.LEAD_IN_MAX_TICKS,
                    "B11: leadInTicks out of bounds: " + schedule.leadInTicks());
            require(schedule.stormStartTick() >= geo.presentStart() && schedule.stormStartTick() < geo.presentEnd(),
                    "B12: stormStartTick must fall inside [presentStart, presentEnd)");
            require(schedule.leadInStartTick() >= geo.cycleStartTick(),
                    "B13: leadInStartTick must never reach back into the previous stay's VACUUM");
            require(schedule.stormEndTick() <= geo.presentEnd(), "B14: STORM_MAY_OVERHANG=false must keep stormEndTick <= presentEnd");

            minDuration = Math.min(minDuration, schedule.durationTicks());
            maxDuration = Math.max(maxDuration, schedule.durationTicks());
            minLeadIn = Math.min(minLeadIn, schedule.leadInTicks());
            maxLeadIn = Math.max(maxLeadIn, schedule.leadInTicks());
        }

        int durationSpan = AtmosphereCurve.STORM_MAX_TICKS - AtmosphereCurve.STORM_MIN_TICKS;
        require(minDuration - AtmosphereCurve.STORM_MIN_TICKS <= durationSpan * 0.01, "B10: duration coverage must reach near the minimum bound");
        require(AtmosphereCurve.STORM_MAX_TICKS - maxDuration <= durationSpan * 0.01, "B10: duration coverage must reach near the maximum bound");

        int leadInSpan = AtmosphereCurve.LEAD_IN_MAX_TICKS - AtmosphereCurve.LEAD_IN_MIN_TICKS;
        require(minLeadIn - AtmosphereCurve.LEAD_IN_MIN_TICKS <= leadInSpan * 0.01, "B11: lead-in coverage must reach near the minimum bound");
        require(AtmosphereCurve.LEAD_IN_MAX_TICKS - maxLeadIn <= leadInSpan * 0.01, "B11: lead-in coverage must reach near the maximum bound");

        // B15: idempotency guard is a single field comparison; this asserts the guard's own correctness, not
        // the server tick loop that calls it (exercised separately, in-game).
        StormSchedule first = AtmosphereCurve.roll(geo, geo.cycleStartTick(), 100, random);
        require(first.cycleIndex() == geo.cycleIndex(), "B15: a fresh roll must stamp the current cycleIndex");

        // B16: mid-stay roll with a closed window must stamp a no-storm schedule, not force an impossible fit.
        long midStayNow = geo.presentEnd() - 1000;
        StormSchedule midStay = AtmosphereCurve.roll(geo, midStayNow, 100, random);
        require(!midStay.hasStorm() || midStay.stormEndTick() <= geo.presentEnd(),
                "B16: a mid-stay roll with a closed window must not schedule a storm that cannot fit");
        require(midStay.cycleIndex() == geo.cycleIndex(), "B16: a stamped no-storm schedule must still carry the current cycleIndex");

        report.append(String.format("duration coverage   [%d, %d] against bounds [%d, %d]%n",
                minDuration, maxDuration, AtmosphereCurve.STORM_MIN_TICKS, AtmosphereCurve.STORM_MAX_TICKS));
        report.append(String.format("lead-in coverage    [%d, %d] against bounds [%d, %d]%n",
                minLeadIn, maxLeadIn, AtmosphereCurve.LEAD_IN_MIN_TICKS, AtmosphereCurve.LEAD_IN_MAX_TICKS));
        report.append(String.format("frequency=50 observed occurrence rate: %.4f%n", halfRate));
        report.append("PASS: occurrence gate, duration/lead-in bounds and coverage, PRESENT-window fit, mid-stay closed-window fallback\n");

        report.append("\nfive sampled schedules (offsets from cycle start):\n");
        RandomSource sampleRandom = RandomSource.create(99L);
        for (int i = 0; i < 5; i++) {
            StormSchedule schedule = AtmosphereCurve.roll(geo, geo.cycleStartTick(), 100, sampleRandom);
            report.append(String.format("  lead-in %s | storm start %s | duration %s | end %s | static=%.2f dust=%.2f flux=%.2f%n",
                    clock(schedule.leadInStartTick() - geo.cycleStartTick()), clock(schedule.stormStartTick() - geo.cycleStartTick()),
                    clock(schedule.durationTicks()), clock(schedule.stormEndTick() - geo.cycleStartTick()),
                    schedule.staticAxis(), schedule.dustAxis(), schedule.fluxAxis()));
        }
    }

    private static void rollAndAssertOccurrence(CycleGeometry geo, int percent, String message) {
        RandomSource random = RandomSource.create(7L);
        for (int i = 0; i < 1000; i++) {
            require(!AtmosphereCurve.roll(geo, geo.cycleStartTick(), percent, random).hasStorm(), message);
        }
    }

    private static void rollAndAssertAlwaysStorm(CycleGeometry geo, int percent, String message) {
        RandomSource random = RandomSource.create(8L);
        for (int i = 0; i < 1000; i++) {
            require(AtmosphereCurve.roll(geo, geo.cycleStartTick(), percent, random).hasStorm(), message);
        }
    }

    private static double observedOccurrenceRate(CycleGeometry geo, int percent) {
        RandomSource random = RandomSource.create(9L);
        int occurred = 0;
        for (int i = 0; i < ROLL_SAMPLE_COUNT; i++) {
            if (AtmosphereCurve.roll(geo, geo.cycleStartTick(), percent, random).hasStorm()) {
                occurred++;
            }
        }
        return occurred / (double) ROLL_SAMPLE_COUNT;
    }

    // ============================================================================================== section C

    private static final int[] DURATIONS = {6000, 9000, 12000, 18000};
    private static final int[] LEAD_INS = {2400, 3600};
    private static final float[] DUST_AXES = {0.0F, 0.5F, 1.0F};

    private static void reportArc(StringBuilder report) {
        report.append("\n=== C. lead-in + scaled arc ===\n\n");

        for (int durationTicks : DURATIONS) {
            for (int leadInTicks : LEAD_INS) {
                for (float dustAxis : DUST_AXES) {
                    assertArc(durationTicks, leadInTicks, dustAxis);
                }
            }
            report.append(arcTable(durationTicks, LEAD_INS[0], 0.5F));
        }

        assertTruncation();

        report.append("PASS: phase tiling exact, fixed bookends held, two-peak profile per duration/axis, exactly one tau discontinuity, truncation warp verified\n");
    }

    private static void assertArc(int durationTicks, int leadInTicks, float dustAxis) {
        // The schedule's own lead-in start sits away from t=0 so the sweep below can observe a leading
        // CLEAR tick before LEAD_IN begins, not just the trailing one after TAIL ends.
        long leadInStart = 1000L;
        StormSchedule schedule = new StormSchedule(0, leadInStart, leadInTicks, durationTicks, 0.5F, dustAxis, 0.5F);
        // A geometry whose PRESENT window comfortably contains the whole storm, so this section never truncates.
        CycleGeometry geo = new CycleGeometry(0, 0L, 200_000L, AtmosphereCurve.RAMP_TICKS, 100_000L);

        long body = durationTicks - AtmosphereCurve.ARRIVAL_SILENCE_TICKS - AtmosphereCurve.TAIL_TICKS;
        require(body > 0, "C19: body must be positive at duration=" + durationTicks);

        float ceiling = AtmosphereCurve.tauCeiling(dustAxis);
        float peakTau = -1.0F;
        StormPhase peakTauPhase = null;
        double peakDischargeChance = -1.0;
        StormPhase peakDischargePhase = null;
        StormPhase previousPhase = null;
        int discontinuities = 0;
        float previousTau = 0.0F;
        long observedTotal = 0L;
        java.util.List<StormPhase> sequence = new java.util.ArrayList<>();

        long totalSpan = leadInTicks + durationTicks;
        for (long t = leadInStart - 5; t < leadInStart + totalSpan + 5; t++) {
            StormArc arc = AtmosphereCurve.stormAt(t, geo, schedule);
            if (arc.phase() != StormPhase.CLEAR) {
                observedTotal++;
            }

            require(arc.tau() >= 0.0F && arc.tau() <= ceiling + 1.0e-4F, "C21: tau out of [0, ceiling] at t=" + t + ": " + arc.tau());

            if (arc.phase() == StormPhase.ARRIVAL) {
                require(arc.tau() == 0.0F, "C21: tau must be exactly 0 throughout ARRIVAL_SILENCE");
            }
            if (arc.phase() == StormPhase.DISTANT) {
                require(arc.tau() <= AtmosphereCurve.LEAD_IN_TAU_MAX * ceiling + 1.0e-4F, "C21: LEAD_IN tau must stay under its own ceiling");
            }

            if (arc.tau() > peakTau) {
                peakTau = arc.tau();
                peakTauPhase = arc.phase();
            }

            double dischargeChance = AtmosphereCurve.dischargeChancePerTick(arc.tau(), arc.phase(), 0.5F);
            if (dischargeChance > peakDischargeChance) {
                peakDischargeChance = dischargeChance;
                peakDischargePhase = arc.phase();
            }

            if (previousPhase == null || previousPhase != arc.phase()) {
                sequence.add(arc.phase());
            }
            if (Math.abs(arc.tau() - previousTau) > 0.01F) {
                discontinuities++;
            }
            previousPhase = arc.phase();
            previousTau = arc.tau();
        }

        require(observedTotal == totalSpan, "C17: phase durations must sum exactly to leadInTicks + durationTicks (observed " + observedTotal + " vs " + totalSpan + ")");

        java.util.List<StormPhase> expectedSequence = java.util.List.of(StormPhase.CLEAR, StormPhase.DISTANT, StormPhase.ARRIVAL,
                StormPhase.DUST_ENVELOPE, StormPhase.WIND_BUILD, StormPhase.ELECTRIC_PEAK, StormPhase.TAIL, StormPhase.CLEAR);
        require(sequence.equals(expectedSequence), "C20: phase sequence must be exactly " + expectedSequence + ", was " + sequence);

        // The two curves are built to meet exactly at the WIND_BUILD/ELECTRIC_PEAK boundary (both reach
        // 1.0 x ceiling there), and phases tile the arc half-open (C17/C20), so the single discrete tick
        // carrying that peak value falls on the ELECTRIC_PEAK side of the boundary by construction. The
        // wind peak is still real - it is the boundary itself, one tick either side of the accepted range.
        require(peakTauPhase == StormPhase.WIND_BUILD || (peakTauPhase == StormPhase.ELECTRIC_PEAK && peakTau >= ceiling - 1.0e-4F),
                "C23: the wind peak (tau's maximum) must land in WIND_BUILD (or its shared boundary tick), landed in " + peakTauPhase);
        require(peakDischargePhase == StormPhase.ELECTRIC_PEAK, "C23: the discharge-rate peak must land in ELECTRIC_PEAK, landed in " + peakDischargePhase);
        require(Math.abs(peakTau - ceiling) < 1.0e-4F, "C22: max(tau) must equal tauCeiling(dustAxis), was " + peakTau + " vs " + ceiling);

        double windBuildEndChance = AtmosphereCurve.dischargeChancePerTick(ceiling, StormPhase.WIND_BUILD, 0.5F);
        require(peakDischargeChance > windBuildEndChance * 2.0 || dustAxis == 0.0F,
                "C23: ELECTRIC_PEAK's discharge rate should clearly exceed WIND_BUILD's telegraph rate");

        // C18: fixed bookends hold regardless of duration.
        long arrivalDuration = AtmosphereCurve.phaseStartOffset(schedule, StormPhase.DUST_ENVELOPE) - AtmosphereCurve.phaseStartOffset(schedule, StormPhase.ARRIVAL);
        require(arrivalDuration == AtmosphereCurve.ARRIVAL_SILENCE_TICKS, "C18: ARRIVAL_SILENCE must always be exactly ARRIVAL_SILENCE_TICKS");
        long tailDuration = AtmosphereCurve.phaseStartOffset(schedule, StormPhase.CLEAR) - AtmosphereCurve.phaseStartOffset(schedule, StormPhase.TAIL);
        require(tailDuration == AtmosphereCurve.TAIL_TICKS, "C18: TAIL must always be exactly TAIL_TICKS");

        // C24: exactly one tau discontinuity above 0.01/tick across the whole arc - the LEAD_IN -> ARRIVAL_SILENCE duck.
        require(discontinuities == 1, "C24: exactly one tau jump above 0.01 expected (the arrival duck), counted " + discontinuities);
    }

    private static void assertTruncation() {
        int durationTicks = AtmosphereCurve.STORM_MAX_TICKS;
        int leadInTicks = AtmosphereCurve.LEAD_IN_MIN_TICKS;
        long rampTicks = AtmosphereCurve.RAMP_TICKS;
        long halfTicks = 40000L;
        CycleGeometry geo = new CycleGeometry(0, 0L, halfTicks * 2, rampTicks, halfTicks);

        // Storm starts late enough in PRESENT that it overhangs into THINNING - only reachable via an
        // override in normal play (STORM_MAY_OVERHANG=false keeps the roller from doing this itself), but
        // the warp must still behave correctly whenever it happens (a /relictstorm override, e.g.).
        long stormStart = geo.presentEnd() - 3000;
        long leadInStart = stormStart - leadInTicks;
        StormSchedule schedule = new StormSchedule(0, leadInStart, leadInTicks, durationTicks, 0.5F, 0.5F, 0.5F);
        require(schedule.stormEndTick() > geo.presentEnd(), "C25 precondition: this schedule must actually overhang for the test to mean anything");

        long thinStart = geo.presentEnd();
        long thinEnd = geo.thinningEnd();

        long previousWarped = Long.MIN_VALUE;
        float tauAtThinStart = -1.0F;
        float tauJustBeforeThinStart = -1.0F;
        for (long t = stormStart; t <= thinEnd; t++) {
            long warped = AtmosphereCurve.warpedElapsed(t, geo, schedule);
            require(warped >= previousWarped, "C25: warped storm clock must be monotone non-decreasing (t=" + t + ")");
            previousWarped = warped;

            if (t == thinStart) {
                tauAtThinStart = AtmosphereCurve.stormAt(t, geo, schedule).tau();
            }
            if (t == thinStart - 1) {
                tauJustBeforeThinStart = AtmosphereCurve.stormAt(t, geo, schedule).tau();
            }
        }

        require(AtmosphereCurve.warpedElapsed(thinEnd, geo, schedule) == durationTicks,
                "C25: warped clock must equal durationTicks exactly at thinningEnd");

        long compressionSpan = thinEnd - thinStart;
        long compressedRemaining = durationTicks - AtmosphereCurve.warpedElapsed(thinStart, geo, schedule);
        require(compressedRemaining > compressionSpan, "C25: compression factor (remaining/span) must be > 1, the accelerated tail");

        StormArc atThinEnd = AtmosphereCurve.stormAt(thinEnd, geo, schedule);
        require(atThinEnd.tau() == 0.0F, "C25: tau must be exactly 0 at thinningEnd");
        require(atThinEnd.phase() == StormPhase.CLEAR, "C25: phase must be CLEAR at thinningEnd");
        require(Math.abs(tauAtThinStart - tauJustBeforeThinStart) < 0.05F, "C25: tau must be continuous at thinStart (the warp starts at rate 1)");
    }

    private static String arcTable(int durationTicks, int leadInTicks, float dustAxis) {
        StormSchedule schedule = new StormSchedule(0, 0L, leadInTicks, durationTicks, 0.5F, dustAxis, 0.5F);
        CycleGeometry geo = new CycleGeometry(0, 0L, 200_000L, AtmosphereCurve.RAMP_TICKS, 100_000L);

        StringBuilder table = new StringBuilder();
        table.append(String.format("duration=%s lead-in=%s dust=%.2f:%n", clock(durationTicks), clock(leadInTicks), dustAxis));
        for (StormPhase phase : StormPhase.values()) {
            if (phase == StormPhase.CLEAR) {
                continue;
            }
            long start = AtmosphereCurve.phaseStartOffset(schedule, phase);
            long end = AtmosphereCurve.phaseStartOffset(schedule, phase.next());
            StormArc arcStart = AtmosphereCurve.stormAt(start, geo, schedule);
            table.append(String.format("  %-14s %s .. %s  tau(start)=%.2f%n", phase, clock(start), clock(end), arcStart.tau()));
        }
        return table.toString();
    }

    // ============================================================================================== section D

    private static void reportAxisRanges(StringBuilder report) {
        report.append("\n=== D. axis ranges ===\n\n");

        // D26: dischargeChancePerTick monotone in staticAxis.
        double previous = -1.0;
        for (float staticAxis = 0.0F; staticAxis <= 1.0F; staticAxis += 0.05F) {
            double chance = AtmosphereCurve.dischargeChancePerTick(1.0F, StormPhase.ELECTRIC_PEAK, staticAxis);
            require(chance >= previous, "D26: dischargeChancePerTick must be monotone non-decreasing in staticAxis");
            previous = chance;
        }
        require(AtmosphereCurve.dischargeChancePerTick(1.0F, StormPhase.ELECTRIC_PEAK, 0.0F) > 0.0,
                "D26: discharge chance must be > 0 at staticAxis=0 during ELECTRIC_PEAK");
        require(AtmosphereCurve.dischargeChancePerTick(1.0F, StormPhase.ELECTRIC_PEAK, 1.0F) <= 1.0,
                "D26: discharge chance must stay <= 1.0 at staticAxis=1");

        // D27: tauCeiling.
        require(AtmosphereCurve.tauCeiling(0.0F) == AtmosphereCurve.DUST_TAU_CEILING_MIN, "D27: tauCeiling(0) must equal DUST_TAU_CEILING_MIN");
        require(AtmosphereCurve.tauCeiling(1.0F) == 1.0F, "D27: tauCeiling(1) must equal 1.0");
        float previousCeiling = -1.0F;
        for (float dustAxis = 0.0F; dustAxis <= 1.0F; dustAxis += 0.05F) {
            float ceiling = AtmosphereCurve.tauCeiling(dustAxis);
            require(ceiling >= previousCeiling, "D27: tauCeiling must be monotone in dustAxis");
            previousCeiling = ceiling;
        }

        // D28: dust-devil parameters monotone in fluxAxis.
        int previousMax = -1;
        int previousLifetime = -1;
        double previousChance = -1.0;
        for (float fluxAxis = 0.0F; fluxAxis <= 1.0F; fluxAxis += 0.05F) {
            int maxDevils = AtmosphereCurve.dustDevilMaxCount(fluxAxis);
            int lifetime = AtmosphereCurve.dustDevilLifetimeTicks(fluxAxis);
            double chance = AtmosphereCurve.dustDevilChancePerTick(fluxAxis);
            require(maxDevils >= previousMax, "D28: dustDevilMaxCount must be monotone in fluxAxis");
            require(lifetime >= previousLifetime, "D28: dustDevilLifetimeTicks must be monotone in fluxAxis");
            require(chance >= previousChance, "D28: dustDevilChancePerTick must be monotone in fluxAxis");
            previousMax = maxDevils;
            previousLifetime = lifetime;
            previousChance = chance;
        }
        require(AtmosphereCurve.dustDevilMaxCount(0.0F) >= 1, "D28: maxDevils(0) must be >= 1");
        require(AtmosphereCurve.dustDevilMaxCount(1.0F) == 4, "D28: maxDevils(1) must equal 4");
        require(AtmosphereCurve.dustDevilLifetimeTicks(1.0F) > AtmosphereCurve.dustDevilLifetimeTicks(0.0F), "D28: lifetime(1) must exceed lifetime(0)");

        // D30: over the 10000 rolls of section B, each axis lies in [0,1], mean is 0.5 +/- 0.05, and pairwise
        // correlations stay small — the three axes must roll independently of one another.
        int solTicks = RelictDimension.SOL_TICKS;
        CycleGeometry geo = AtmosphereCurve.geometryAt(0L, solTicks, DEFAULT_CYCLE_TENTH_SOLS);
        RandomSource random = RandomSource.create(2024L);
        double[] staticValues = new double[ROLL_SAMPLE_COUNT];
        double[] dustValues = new double[ROLL_SAMPLE_COUNT];
        double[] fluxValues = new double[ROLL_SAMPLE_COUNT];
        for (int i = 0; i < ROLL_SAMPLE_COUNT; i++) {
            StormSchedule schedule = AtmosphereCurve.roll(geo, geo.cycleStartTick(), 100, random);
            require(schedule.staticAxis() >= 0.0F && schedule.staticAxis() <= 1.0F, "D30: staticAxis out of [0,1]");
            require(schedule.dustAxis() >= 0.0F && schedule.dustAxis() <= 1.0F, "D30: dustAxis out of [0,1]");
            require(schedule.fluxAxis() >= 0.0F && schedule.fluxAxis() <= 1.0F, "D30: fluxAxis out of [0,1]");
            staticValues[i] = schedule.staticAxis();
            dustValues[i] = schedule.dustAxis();
            fluxValues[i] = schedule.fluxAxis();
        }

        double staticMean = mean(staticValues);
        double dustMean = mean(dustValues);
        double fluxMean = mean(fluxValues);
        require(Math.abs(staticMean - 0.5) <= 0.05, "D30: staticAxis mean must be 0.5 +/- 0.05, was " + staticMean);
        require(Math.abs(dustMean - 0.5) <= 0.05, "D30: dustAxis mean must be 0.5 +/- 0.05, was " + dustMean);
        require(Math.abs(fluxMean - 0.5) <= 0.05, "D30: fluxAxis mean must be 0.5 +/- 0.05, was " + fluxMean);

        double staticDustR = correlation(staticValues, dustValues, staticMean, dustMean);
        double staticFluxR = correlation(staticValues, fluxValues, staticMean, fluxMean);
        double dustFluxR = correlation(dustValues, fluxValues, dustMean, fluxMean);
        require(Math.abs(staticDustR) < 0.05, "D30: static/dust correlation must be < 0.05 in magnitude, was " + staticDustR);
        require(Math.abs(staticFluxR) < 0.05, "D30: static/flux correlation must be < 0.05 in magnitude, was " + staticFluxR);
        require(Math.abs(dustFluxR) < 0.05, "D30: dust/flux correlation must be < 0.05 in magnitude, was " + dustFluxR);

        report.append(String.format("axis means (10000 rolls)   static=%.4f dust=%.4f flux=%.4f%n", staticMean, dustMean, fluxMean));
        report.append(String.format("pairwise |r|                static/dust=%.4f static/flux=%.4f dust/flux=%.4f%n",
                Math.abs(staticDustR), Math.abs(staticFluxR), Math.abs(dustFluxR)));
        report.append("PASS: discharge chance and tauCeiling monotone, dust-devil parameters monotone, axes independent and uniform\n");
    }

    private static double mean(double[] values) {
        double sum = 0.0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.length;
    }

    private static double correlation(double[] a, double[] b, double meanA, double meanB) {
        double covariance = 0.0;
        double varianceA = 0.0;
        double varianceB = 0.0;
        for (int i = 0; i < a.length; i++) {
            double da = a[i] - meanA;
            double db = b[i] - meanB;
            covariance += da * db;
            varianceA += da * da;
            varianceB += db * db;
        }
        double denominator = Math.sqrt(varianceA * varianceB);
        return denominator == 0.0 ? 0.0 : covariance / denominator;
    }

    // -------------------------------------------------------------------------------------------------- shared

    private static String clock(long ticks) {
        long seconds = Math.abs(ticks) / 20;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Atmosphere curve sampler assertion failed: " + message);
        }
    }

}
