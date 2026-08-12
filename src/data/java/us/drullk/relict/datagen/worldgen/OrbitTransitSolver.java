package us.drullk.relict.datagen.worldgen;

import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.EasingType;
import net.minecraft.util.Keyframe;
import net.minecraft.util.KeyframeTrack;
import net.minecraft.util.KeyframeTrackSampler;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.attribute.LerpFunction;
import net.minecraft.world.attribute.modifier.FloatModifier;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.timeline.Timeline;
import org.joml.Vector3f;
import org.slf4j.Logger;
import us.drullk.relict.init.RelictEnvironmentAttributes;
import us.drullk.relict.init.worldgen.RelictDimension;

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

    private final int solTicks;

    public OrbitTransitSolver(int solTicks) {
        this.solTicks = solTicks;
    }

    public void solveTransits(BootstrapContext<Timeline> context, Holder<WorldClock> marsClock, KeyframeTrack<Float> sunAngle) {
        Orbit phobos = this.registerOrbit(context, marsClock, RelictDimension.PHOBOS_ORBIT,
                RelictEnvironmentAttributes.PHOBOS_ANGLE, RelictEnvironmentAttributes.PHOBOS_INCLINATION,
                RelictEnvironmentAttributes.PHOBOS_SCALE, RelictDimension.PHOBOS_CROSSINGS_PER_SOL,
                RelictDimension.PHOBOS_INCLINATION, PHOBOS_ORBIT_KM);
        this.registerOrbit(context, marsClock, RelictDimension.DEIMOS_ORBIT,
                RelictEnvironmentAttributes.DEIMOS_ANGLE, RelictEnvironmentAttributes.DEIMOS_INCLINATION,
                RelictEnvironmentAttributes.DEIMOS_SCALE, RelictDimension.DEIMOS_CROSSINGS_PER_SOL,
                RelictDimension.DEIMOS_INCLINATION, DEIMOS_ORBIT_KM);

        this.registerTransit(context, marsClock, sunAngle, phobos);
    }

    /** What {@link #registerOrbit} learned about a moon, so the transit solver can resample it exactly. */
    private record Orbit(KeyframeTrack<Float> angle, int periodTicks, float inclination, float discRadius) {
    }

    /**
     * A moon's orbit: where it is, how its plane is rolled, and how big it looks — all three on one timeline,
     * so the orbit is described in a single place and any of them can be given a curve later.
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
     */
    private Orbit registerOrbit(
            BootstrapContext<Timeline> context,
            Holder<WorldClock> clock,
            ResourceKey<Timeline> key,
            EnvironmentAttribute<Float> angleAttribute,
            EnvironmentAttribute<Float> inclinationAttribute,
            EnvironmentAttribute<Float> scaleAttribute,
            float crossingsPerSol,
            float inclination,
            float orbitRadiusKm
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

        context.register(key, Timeline.builder(clock)
                .setPeriodTicks(periodTicks)
                .addTrack(angleAttribute, track -> replay(angle, track))
                // A lone keyframe bakes to a constant segment, which is how a fixed value rides a timeline.
                .addTrack(inclinationAttribute, track -> track.addKeyframe(0, inclination))
                .addTrack(scaleAttribute, track -> scaleCurve(track, angle, periodTicks, span, orbitRadiusKm))
                .build());

        return new Orbit(angle, periodTicks, inclination,
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
     * same place relative to the sun every sol, so a transit happens at the same tick of every sol forever,
     * which makes it keyframeable. Nothing else would work — {@code EnvironmentAttributeSystem} is where a
     * per-tick contribution would belong, and vanilla builds it inline in the level constructors with no hook
     * for a mod to add a layer. The tradeoff is that this only holds while the periods stay commensurate; a
     * drifting Phobos would need that hook.
     * <p>
     * {@code SKY_LIGHT_LEVEL} feeds {@code Level#getSkyDarken}, and through it
     * {@code LevelReader#getEffectiveSkyBrightness} and mob spawning — so the dip is felt, not just seen. It
     * multiplies, and rides its own timeline so that it composes with the day-night track on the sol rather
     * than colliding with it.
     * <p>
     * Ticks where the sun is already below the horizon are skipped: the light is at its night value there
     * anyway, and dimming it further for an eclipse nobody can see would darken the night.
     */
    private void registerTransit(BootstrapContext<Timeline> context, Holder<WorldClock> clock, KeyframeTrack<Float> sunAngle, Orbit moon) {
        int periodTicks = lcm(this.solTicks, moon.periodTicks());
        KeyframeTrackSampler<Float> sun = sunAngle.bakeSampler(Optional.of(this.solTicks), LerpFunction.ofFloat());
        KeyframeTrackSampler<Float> arc = moon.angle().bakeSampler(Optional.of(moon.periodTicks()), LerpFunction.ofFloat());
        float sunRadius = RelictDimension.angularRadius(RelictDimension.SUN_QUAD_EXTENT * RelictDimension.SPRITE_DISC_FRACTION);

        double[] coverage = new double[periodTicks / TRANSIT_SCAN_TICKS + 1];
        double closest = 180.0;
        float closestSunAngle = 0.0F;

        for (int sample = 0; sample < coverage.length; sample++) {
            int ticks = sample * TRANSIT_SCAN_TICKS;
            float sunSkyAngle = sun.sample(ticks);
            Vector3f sunDirection = RelictDimension.skyDirection(0.0F, sunSkyAngle);
            if (sunDirection.y() <= 0.0F) {
                continue;
            }

            Vector3f moonDirection = RelictDimension.skyDirection(moon.inclination(), arc.sample(ticks));
            double separation = Math.toDegrees(Math.acos(Math.clamp(sunDirection.dot(moonDirection), -1.0F, 1.0F)));
            if (separation < closest) {
                closest = separation;
                closestSunAngle = sunSkyAngle;
            }

            coverage[sample] = RelictDimension.eclipseCoverage(separation, sunRadius, moon.discRadius());
        }

        // Anchored at full brightness so that stretches between transits interpolate flat, and so the track
        // is never empty when no transit is found at all.
        NavigableMap<Integer, Float> dips = new TreeMap<>();
        dips.put(0, 1.0F - (float) coverage[0]);
        int transits = 0;
        double peak = 0.0;

        int samplesPerKeyframe = Math.max(1, TRANSIT_KEYFRAME_TICKS / TRANSIT_SCAN_TICKS);
        for (int sample = 1; sample < coverage.length; sample++) {
            boolean covered = coverage[sample] > 0.0;
            boolean edge = covered != coverage[sample - 1] > 0.0;
            if (edge) {
                if (covered) {
                    transits++;
                }

                // One clear tick either side, so the shoulder of the dip is a ramp rather than a step.
                dips.merge(Math.clamp((long) (covered ? sample - 1 : sample) * TRANSIT_SCAN_TICKS, 0, periodTicks - 1), 1.0F, Math::min);
            }

            if (covered && (edge || sample % samplesPerKeyframe == 0)) {
                dips.merge(sample * TRANSIT_SCAN_TICKS, 1.0F - (float) coverage[sample], Math::min);
            }

            peak = Math.max(peak, coverage[sample]);
        }

        report(transits, peak, closest, closestSunAngle, sunRadius, moon);
        context.register(RelictDimension.PHOBOS_TRANSIT, Timeline.builder(clock)
                .setPeriodTicks(periodTicks)
                .addModifierTrack(EnvironmentAttributes.SKY_LIGHT_LEVEL, FloatModifier.MULTIPLY,
                        track -> dips.forEach(track::addKeyframe))
                .build());
    }

    /**
     * Whether the current inclination actually lets a transit happen, and if not, what would.
     * <p>
     * Worth reporting rather than leaving silent: the geometry can be perfectly correct and still produce
     * nothing, because conjunctions land at fixed sky angles and the discs only touch near the zenith. A
     * track of pure ones is indistinguishable from a broken one otherwise.
     */
    private static void report(int transits, double peak, double closest, float closestSunAngle, float sunRadius, Orbit moon) {
        double touching = sunRadius + moon.discRadius();
        if (transits > 0) {
            LOGGER.info("transit: {} per sol, closest approach {} degrees against {} to touch, dimming the sun by up to {}%",
                    transits, round(closest), round(touching), Math.round(peak * 100.0));
            return;
        }

        // Conjunctions sit at fixed sky angles, so the nearest one is as good as it will ever get. Inverting
        // cos s = 1 - (1 - cos i) sin^2 a at that angle gives the inclination that would just bring the discs
        // together there.
        double sine = Math.sin(Math.toRadians(closestSunAngle));
        double required = Math.toDegrees(Math.acos(Math.clamp(
                1.0 - (1.0 - Math.cos(Math.toRadians(touching))) / (sine * sine), -1.0, 1.0)));
        LOGGER.warn("transit: none — closest approach is {} degrees and the discs need {} to touch. Conjunctions land "
                        + "at fixed sky angles, the nearest {} degrees from the zenith, so no phase offset helps; "
                        + "an inclination at or under {} degrees would reach it.",
                round(closest), round(touching), round(closestSunAngle), round(required));
    }

    private static double round(double degrees) {
        return Math.round(degrees * 10.0) / 10.0;
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
