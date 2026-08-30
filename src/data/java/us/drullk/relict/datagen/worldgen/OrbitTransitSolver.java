package us.drullk.relict.datagen.worldgen;

import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.*;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.attribute.LerpFunction;
import net.minecraft.world.attribute.modifier.FloatModifier;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.timeline.Timeline;
import org.jspecify.annotations.Nullable;
import org.joml.Vector3f;
import org.slf4j.Logger;
import us.drullk.relict.init.RelictEnvironmentAttributes;
import us.drullk.relict.init.worldgen.RelictDimension;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

public class OrbitTransitSolver {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Mars and the two orbits, in kilometres; the apparent-size curve is the only thing that reads them. */
    private static final float MARS_RADIUS_KM = 3396.2F;
    private static final float PHOBOS_ORBIT_KM = 9376.0F;
    private static final float DEIMOS_ORBIT_KM = 23458.0F;

    private static final float DEGREES_PER_SCALE_KEYFRAME = 15.0F;
    /** Fine enough to catch the edges of a transit lasting a second or two of real time. */
    private static final int TRANSIT_SCAN_TICKS = 4;
    /**
     * Keyframe spacing within a transit. Coarser than the scan on purpose: the dip is scanned finely so its
     * shoulders land on the right tick, but sampled at a second apiece because {@code getSkyDarken} truncates
     * to an integer, and a third of the sun's light is only five steps of it.
     */
    private static final int TRANSIT_KEYFRAME_TICKS = 20;
    /** Coverage at or above this reads as a total eclipse rather than a partial one, in the schedule report. */
    private static final double TOTALITY_COVERAGE = 0.999;
    private static final float ECLIPSE_LIGHT_FLOOR = 0.67F;

    private final int solTicks;

    public OrbitTransitSolver(int solTicks) {
        this.solTicks = solTicks;
    }

    public void solveTransits(BootstrapContext<Timeline> context, Holder<WorldClock> marsClock, KeyframeTrack<Float> sunAngle) {
        KeyframeTrack<Float> phobosInclination = this.registerPhobosRock(context, marsClock);

        Orbit phobos = this.registerOrbit(context, marsClock, RelictDimension.PHOBOS_ORBIT,
                RelictEnvironmentAttributes.PHOBOS_ANGLE, RelictEnvironmentAttributes.PHOBOS_SCALE,
                RelictDimension.PHOBOS_CROSSINGS_PER_SOL, PHOBOS_ORBIT_KM, null, 0.0F);
        this.registerOrbit(context, marsClock, RelictDimension.DEIMOS_ORBIT,
                RelictEnvironmentAttributes.DEIMOS_ANGLE, RelictEnvironmentAttributes.DEIMOS_SCALE,
                RelictDimension.DEIMOS_CROSSINGS_PER_SOL, DEIMOS_ORBIT_KM,
                RelictEnvironmentAttributes.DEIMOS_INCLINATION, RelictDimension.DEIMOS_INCLINATION);

        this.registerTransit(context, marsClock, sunAngle, phobos, phobosInclination, RelictDimension.PHOBOS_ROCK_SOLS * this.solTicks);
    }

    /**
     * The rocking inclination lives on its own timeline, at its own period, rather than folded into
     * {@code PHOBOS_ORBIT}: the orbit's angle and scale tracks repeat every sol, but the rock takes several
     * sols, and a single {@code Timeline} carries only one period for all its tracks.
     * <p>
     * Returns the same {@link KeyframeTrack} that gets registered, so the transit solver below can bake a
     * sampler from the identical curve instead of recomputing an approximation of it — the position track and
     * the light-dip track then agree exactly rather than merely both being "close to" the intended rock.
     */
    private KeyframeTrack<Float> registerPhobosRock(BootstrapContext<Timeline> context, Holder<WorldClock> clock) {
        int periodTicks = RelictDimension.PHOBOS_ROCK_SOLS * this.solTicks;
        KeyframeTrack<Float> inclination = this.phobosInclinationCurve(periodTicks);

        context.register(RelictDimension.PHOBOS_ROCK, Timeline.builder(clock)
                .setPeriodTicks(periodTicks)
                .addTrack(RelictEnvironmentAttributes.PHOBOS_INCLINATION, track -> replay(inclination, track))
                .build());

        return inclination;
    }

    /**
     * A cosine between {@code PHOBOS_INCLINATION_LOW} and {@code _HIGH}, sampled onto keyframes evenly spaced
     * through the rock period. The rock is slow — a full swing takes several sols — so a couple of dozen
     * keyframes a sol tracks it smoothly without bloating the datapack.
     */
    private KeyframeTrack<Float> phobosInclinationCurve(int periodTicks) {
        int keyframesPerSol = 24;
        int steps = RelictDimension.PHOBOS_ROCK_SOLS * keyframesPerSol;
        int ticksPerStep = this.solTicks / keyframesPerSol;
        float mean = (RelictDimension.PHOBOS_INCLINATION_HIGH + RelictDimension.PHOBOS_INCLINATION_LOW) / 2.0F;
        float amplitude = (RelictDimension.PHOBOS_INCLINATION_HIGH - RelictDimension.PHOBOS_INCLINATION_LOW) / 2.0F;
        double phaseOffset = 2.0 * Math.PI * RelictDimension.PHOBOS_ROCK_PHASE_TICKS / periodTicks;

        List<Keyframe<Float>> keyframes = new ArrayList<>(steps);
        for (int step = 0; step < steps; step++) {
            double phase = 2.0 * Math.PI * step / steps + phaseOffset;
            keyframes.add(new Keyframe<>(step * ticksPerStep, mean + amplitude * (float) Math.cos(phase)));
        }

        return new KeyframeTrack<>(keyframes, EasingType.LINEAR);
    }

    /** What {@link #registerOrbit} learned about a moon, so the transit solver can resample it exactly. */
    private record Orbit(KeyframeTrack<Float> angle, int periodTicks, float discRadius) {
    }

    /**
     * A moon's orbit: where it is and how big it looks, both on one timeline, so the orbit is described in a
     * single place and either can be given a curve later.
     * <p>
     * The period is however many whole sols it takes the moon to return to where it started, so the tracks
     * always close on themselves: one sol for Phobos, which crosses twice within it, and five for Deimos,
     * which needs all five for one crossing.
     * <p>
     * No time markers here — {@code mars_sol} owns every marker on the Mars clock, and the registry rejects
     * a marker defined twice for one clock, so leaving these bare keeps the whole marker namespace free for
     * the seasonal cycle later.
     * <p>
     * No easing either. Vanilla eases its sky angles with a symmetric bezier that makes the sun linger near
     * noon, but that shapes one crossing per period; applied to Phobos it would stretch a whole sol across
     * two crossings and slew them both. These stay linear.
     * <p>
     * {@code inclinationAttribute} is {@code null} for Phobos: its inclination rocks on the separate
     * {@code phobos_rock} timeline instead of holding one value here, so this orbit registers no track for
     * it at all rather than writing a value that {@code phobos_rock} would immediately contend with.
     */
    private Orbit registerOrbit(
            BootstrapContext<Timeline> context,
            Holder<WorldClock> clock,
            ResourceKey<Timeline> key,
            EnvironmentAttribute<Float> angleAttribute,
            EnvironmentAttribute<Float> scaleAttribute,
            float crossingsPerSol,
            float orbitRadiusKm,
            @Nullable EnvironmentAttribute<Float> inclinationAttribute,
            float inclination
    ) {
        float rate = Math.abs(crossingsPerSol);
        int sols = Math.max(1, Math.round(1.0F / rate));
        int periodTicks = this.solTicks * sols;
        float span = 360.0F * rate * sols;

        // Two keyframes on one tick is vanilla's sawtooth idiom: the pair with the reset value first ramps
        // upward over the period, and reversing them ramps downward. Phobos runs backwards, so it reverses.
        KeyframeTrack<Float> angle = new KeyframeTrack<>(crossingsPerSol < 0.0F
                ? List.of(new Keyframe<>(0, 0.0F), new Keyframe<>(0, span))
                : List.of(new Keyframe<>(0, span), new Keyframe<>(0, 0.0F)),
                EasingType.LINEAR);

        Timeline.Builder builder = Timeline.builder(clock)
                .setPeriodTicks(periodTicks)
                .addTrack(angleAttribute, track -> replay(angle, track))
                .addTrack(scaleAttribute, track -> scaleCurve(track, angle, periodTicks, span, orbitRadiusKm));
        if (inclinationAttribute != null) {
            // A lone keyframe bakes to a constant segment, which is how a fixed value rides a timeline.
            builder.addTrack(inclinationAttribute, track -> track.addKeyframe(0, inclination));
        }

        context.register(key, builder.build());

        return new Orbit(angle, periodTicks,
                RelictDimension.angularRadius(quadExtent(key) * RelictDimension.SPRITE_DISC_FRACTION));
    }

    private static float quadExtent(ResourceKey<Timeline> key) {
        return key == RelictDimension.PHOBOS_ORBIT ? RelictDimension.PHOBOS_QUAD_EXTENT : RelictDimension.DEIMOS_QUAD_EXTENT;
    }

    /**
     * Apparent size across a crossing.
     * <p>
     * Phobos orbits so close that the observer's distance to it changes materially as it crosses: straight
     * up they are separated by the orbit less Mars's radius, but at the horizon by the leg of a right
     * triangle, which for Phobos is half again as far. Deimos, four times higher, barely varies.
     * <p>
     * Sampled rather than solved because the value has to follow whatever the angle track does. Fifteen
     * degrees of arc per keyframe holds the linear interpolation within about a percent of the curve, which
     * on a body a few pixels wide is nothing.
     */
    private static void scaleCurve(
            KeyframeTrack.Builder<Float> track, KeyframeTrack<Float> angle, int periodTicks, float span, float orbitRadiusKm
    ) {
        KeyframeTrackSampler<Float> sampler = angle.bakeSampler(Optional.of(periodTicks), LerpFunction.ofFloat());
        int steps = Math.max(4, Math.round(span / DEGREES_PER_SCALE_KEYFRAME));
        float zenithDistance = orbitRadiusKm - MARS_RADIUS_KM;

        for (int step = 0; step < steps; step++) {
            int ticks = Math.round((float) step * periodTicks / steps);
            double zenithAngle = Math.toRadians(sampler.sample(ticks));
            double sine = Math.sin(zenithAngle);
            double distance = -MARS_RADIUS_KM * Math.cos(zenithAngle)
                    + Math.sqrt(orbitRadiusKm * orbitRadiusKm - MARS_RADIUS_KM * MARS_RADIUS_KM * sine * sine);
            track.addKeyframe(ticks, (float) (zenithDistance / distance));
        }
    }

    public static void replay(KeyframeTrack<Float> source, KeyframeTrack.Builder<Float> target) {
        target.setEasing(source.easingType());
        source.keyframes().forEach(keyframe -> target.addKeyframe(keyframe.ticks(), keyframe.value()));
    }

    /**
     * The ground going dim when a moon crosses the sun.
     * <p>
     * This is a datapack track rather than code because the orbits are commensurate: Phobos returns to the
     * same place relative to the sun every sol, and its rocking inclination returns to the same value every
     * {@code PHOBOS_ROCK_SOLS} sols, so the combined pattern repeats at a fixed tick forever, which makes it
     * keyframeable. Nothing else would work — {@code EnvironmentAttributeSystem} is where a per-tick
     * contribution would belong, and vanilla builds it inline in the level constructors with no hook for a
     * mod to add a layer. The tradeoff is that this only holds while the periods stay commensurate; a
     * drifting Phobos, or a rock at a non-integer sol count, would need that hook.
     * <p>
     * {@code SKY_LIGHT_LEVEL} feeds {@code Level#getSkyDarken}, and through it
     * {@code LevelReader#getEffectiveSkyBrightness} and mob spawning — so the dip is felt, not just seen. It
     * multiplies, and rides its own timeline so that it composes with the day-night track on the sol rather
     * than colliding with it.
     * <p>
     * Ticks where the sun is already below the horizon are skipped: the light is at its night value there
     * anyway, and dimming it further for an eclipse nobody can see would darken the night.
     */
    private void registerTransit(
            BootstrapContext<Timeline> context,
            Holder<WorldClock> clock,
            KeyframeTrack<Float> sunAngle,
            Orbit moon,
            KeyframeTrack<Float> moonInclination,
            int inclinationPeriodTicks
    ) {
        int periodTicks = lcm(lcm(this.solTicks, moon.periodTicks()), inclinationPeriodTicks);
        KeyframeTrackSampler<Float> sun = sunAngle.bakeSampler(Optional.of(this.solTicks), LerpFunction.ofFloat());
        KeyframeTrackSampler<Float> arc = moon.angle().bakeSampler(Optional.of(moon.periodTicks()), LerpFunction.ofFloat());
        // Baked from the exact track registered as phobos_rock, not recomputed, so the light dip can never
        // land at a different inclination than the one the renderer is drawing that tick.
        KeyframeTrackSampler<Float> inclination = moonInclination.bakeSampler(Optional.of(inclinationPeriodTicks), LerpFunction.ofFloat());
        float sunRadius = RelictDimension.angularRadius(RelictDimension.SUN_QUAD_EXTENT * RelictDimension.SPRITE_DISC_FRACTION);

        double[] coverage = new double[periodTicks / TRANSIT_SCAN_TICKS + 1];

        for (int sample = 0; sample < coverage.length; sample++) {
            int ticks = sample * TRANSIT_SCAN_TICKS;
            float sunSkyAngle = sun.sample(ticks);
            Vector3f sunDirection = RelictDimension.skyDirection(0.0F, sunSkyAngle);
            if (sunDirection.y() <= 0.0F) {
                continue;
            }

            Vector3f moonDirection = RelictDimension.skyDirection(inclination.sample(ticks), arc.sample(ticks));
            double separation = Math.toDegrees(Math.acos(Math.clamp(sunDirection.dot(moonDirection), -1.0F, 1.0F)));
            coverage[sample] = RelictDimension.eclipseCoverage(separation, sunRadius, moon.discRadius());
        }

        // Anchored at full brightness so that stretches between transits interpolate flat, and so the track
        // is never empty when no transit is found at all.
        NavigableMap<Integer, Float> dips = new TreeMap<>();
        dips.put(0, 1.0F - (float) coverage[0]);

        int samplesPerKeyframe = Math.max(1, TRANSIT_KEYFRAME_TICKS / TRANSIT_SCAN_TICKS);
        for (int sample = 1; sample < coverage.length; sample++) {
            boolean covered = coverage[sample] > 0.0;
            boolean edge = covered != coverage[sample - 1] > 0.0;
            if (edge) {
                // One clear tick either side, so the shoulder of the dip is a ramp rather than a step.
                dips.merge(Math.clamp((long) (covered ? sample - 1 : sample) * TRANSIT_SCAN_TICKS, 0, periodTicks - 1), 1.0F, Math::min);
            }

            if (covered && (edge || sample % samplesPerKeyframe == 0)) {
                dips.merge(sample * TRANSIT_SCAN_TICKS, 1.0F - (float) coverage[sample], Math::min);
            }
        }

        List<TransitEvent> events = this.findEvents(coverage, sun);
        reportSchedule(events, periodTicks, moon, sunRadius);

        context.register(RelictDimension.PHOBOS_TRANSIT, Timeline.builder(clock)
                .setPeriodTicks(periodTicks)
                .addModifierTrack(EnvironmentAttributes.SKY_LIGHT_LEVEL, FloatModifier.MULTIPLY,
                        track -> dips.forEach(track::addKeyframe))
                .addModifierTrack(EnvironmentAttributes.SKY_LIGHT_FACTOR, FloatModifier.MULTIPLY,
                        track -> dips.forEach((ticks, value) -> track.addKeyframe(ticks, Mth.lerp(value, ECLIPSE_LIGHT_FLOOR, 1.0f))))
                .addModifierTrack(RelictEnvironmentAttributes.ECLIPSE_DARKEN, FloatModifier.MULTIPLY,
                        track -> dips.forEach((ticks, value) -> track.addKeyframe(ticks, Mth.lerp(value, ECLIPSE_LIGHT_FLOOR, 1.0f))))
                .build());
    }

    /** One contiguous stretch of the sun's disc being at least partly covered. */
    private record TransitEvent(int startTick, int peakTick, int endTick, float sunAngleAtPeak, double peakCoverage, int totalityTicks) {
    }

    /**
     * Walks the scanned coverage into discrete transits, so the report below can list each one instead of
     * just the single closest approach a fixed inclination used to produce.
     */
    private List<TransitEvent> findEvents(double[] coverage, KeyframeTrackSampler<Float> sun) {
        List<TransitEvent> events = new ArrayList<>();
        boolean inTransit = false;
        int start = 0;
        int totalityStart = -1;
        int totalityTicks = 0;
        double peak = 0.0;
        int peakTick = 0;

        for (int sample = 0; sample < coverage.length; sample++) {
            int ticks = sample * TRANSIT_SCAN_TICKS;
            boolean covered = coverage[sample] > 0.0;
            boolean total = coverage[sample] >= TOTALITY_COVERAGE;

            if (covered && !inTransit) {
                inTransit = true;
                start = ticks;
                peak = 0.0;
                totalityStart = -1;
                totalityTicks = 0;
            }

            if (inTransit) {
                if (coverage[sample] > peak) {
                    peak = coverage[sample];
                    peakTick = ticks;
                }

                if (total && totalityStart < 0) {
                    totalityStart = ticks;
                } else if (!total && totalityStart >= 0) {
                    totalityTicks += ticks - totalityStart;
                    totalityStart = -1;
                }
            }

            if ((!covered || sample == coverage.length - 1) && inTransit) {
                inTransit = false;
                if (totalityStart >= 0) {
                    totalityTicks += ticks - totalityStart;
                }

                events.add(new TransitEvent(start, peakTick, ticks, sun.sample(peakTick), peak, totalityTicks));
            }
        }

        return events;
    }

    /**
     * The eclipse schedule over one full repeat — every transit the rock produces, not just whether one
     * exists. Worth reporting in full rather than leaving silent: the geometry can be perfectly correct and
     * still produce nothing, or produce partials that never reach totality, and a track of small dips reads
     * the same as a broken one otherwise.
     */
    private static void reportSchedule(List<TransitEvent> events, int periodTicks, Orbit moon, float sunRadius) {
        double repeatMinutes = periodTicks / 20.0 / 60.0;
        LOGGER.info("phobos rock: repeat is {} ticks ({} sols, {} real min)",
                periodTicks, RelictDimension.PHOBOS_ROCK_SOLS, round(repeatMinutes));

        if (events.isEmpty()) {
            double touching = sunRadius + moon.discRadius();
            LOGGER.warn("transit: none across the whole rock — even at {} degrees, the shallowest point, the "
                            + "discs never touch ({} degrees needed). Widen the LOW/HIGH gap or check the phase.",
                    RelictDimension.PHOBOS_INCLINATION_LOW, round(touching));
            return;
        }

        LOGGER.info("phobos eclipse schedule: tick / sol+offset / sun angle / peak coverage / totality");
        long totalEclipses = 0;
        for (TransitEvent event : events) {
            int sol = event.peakTick() / (periodTicks / RelictDimension.PHOBOS_ROCK_SOLS);
            boolean total = event.peakCoverage() >= TOTALITY_COVERAGE;
            totalEclipses += total ? 1 : 0;
            LOGGER.info("  tick {} / sol {} +{} / {} deg / {}% / {} s{}",
                    event.peakTick(), sol, event.peakTick() % (periodTicks / RelictDimension.PHOBOS_ROCK_SOLS),
                    round(event.sunAngleAtPeak()), Math.round(event.peakCoverage() * 100.0),
                    round(event.totalityTicks() / 20.0), total ? " TOTAL" : "");
        }

        if (totalEclipses == 0) {
            LOGGER.warn("transit: {} partial event(s) this repeat, none reaching totality — the trough doesn't "
                    + "line up with a daylight conjunction. Nudge PHOBOS_ROCK_PHASE_TICKS.", events.size());
        } else {
            LOGGER.info("phobos eclipse cadence: {} total eclipse(s) per repeat -> one every {} real min",
                    totalEclipses, round(repeatMinutes / totalEclipses));
        }
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static int lcm(int first, int second) {
        int a = first;
        int b = second;
        while (b != 0) {
            int carry = b;
            b = a % b;
            a = carry;
        }

        return first / a * second;
    }

}
