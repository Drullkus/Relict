package us.drullk.relict.init.worldgen;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.timeline.Timeline;
import org.joml.Vector3f;
import us.drullk.relict.Relict;

public class RelictDimension {

    // Mars world
    public static final ResourceKey<DimensionType> MARS_TYPE = ResourceKey.create(Registries.DIMENSION_TYPE, Relict.id("mars"));
    public static final ResourceKey<Level> MARS_LEVEL = ResourceKey.create(Registries.DIMENSION, Relict.id("mars"));
    public static final ResourceKey<LevelStem> MARS_LEVELSTEM = ResourceKey.create(Registries.LEVEL_STEM, Relict.id("mars"));
    public static final ResourceKey<NoiseGeneratorSettings> MARS_NOISE_SETTINGS = ResourceKey.create(Registries.NOISE_SETTINGS, Relict.id("mars"));

    // Mars time
    public static final ResourceKey<WorldClock> MARS_CLOCK = ResourceKey.create(Registries.WORLD_CLOCK, Relict.id("mars"));
    public static final ResourceKey<Timeline> MARS_SOL = ResourceKey.create(Registries.TIMELINE, Relict.id("mars_sol"));
    public static final ResourceKey<Timeline> PHOBOS_ORBIT = ResourceKey.create(Registries.TIMELINE, Relict.id("phobos_orbit"));
    public static final ResourceKey<Timeline> DEIMOS_ORBIT = ResourceKey.create(Registries.TIMELINE, Relict.id("deimos_orbit"));
    public static final ResourceKey<Timeline> PHOBOS_TRANSIT = ResourceKey.create(Registries.TIMELINE, Relict.id("phobos_transit"));

    public static final TagKey<Timeline> MARS_TIMELINES = TagKey.create(Registries.TIMELINE, Relict.id("in_mars"));

    // Mars moons
    /**
     * Phase frames each moon is drawn with: one number driving the sprites datagen writes, the frame the
     * sky renderer picks, and the filmstrip the config tool shows. Keep them divisible by 4 so that full,
     * both quarters, and new land exactly on a frame.
     */
    public static final int PHOBOS_PHASES = 16;
    public static final int DEIMOS_PHASES = 8;

    /**
     * Sky crossings per sol, signed against the sun. The sun's own sky angle advances once per sol, so a
     * negative rate is a body that rises in the west and sets in the east.
     * <p>
     * Phobos orbits Mars faster than Mars turns, so it overtakes the observer and runs backwards across
     * the sky — really 2.22 crossings per sol, rounded to two so one sol holds a whole number of them.
     * Deimos orbits a little slower than Mars turns and so drifts the ordinary way, once per 5.4 sols,
     * rounded to five.
     * <p>
     * A consequence worth knowing: elongation from the sun advances at the difference of the two rates,
     * so Phobos runs a full set of phases three times per sol and Deimos once per 1.25 sols.
     */
    public static final float PHOBOS_CROSSINGS_PER_SOL = -2.0F;
    public static final float DEIMOS_CROSSINGS_PER_SOL = 0.2F;

    /**
     * How far each moon's orbital plane is rolled away from the sun's, in degrees.
     * <p>
     * Vanilla hangs every celestial body on the same great circle — after its own setup a body sits in the
     * world XY plane — so its sun and moon can only ever coincide or oppose. Rolling that plane about the
     * <em>world Y axis</em> keeps every orbit a great circle through the zenith and the nadir, which is the
     * axis all the planes then share. Bodies still meet, at the zenith, which is what makes an eclipse
     * possible; inclination only decides how near a conjunction has to happen for the discs to touch.
     * <p>
     * The separation at a conjunction occurring at sky angle {@code a} satisfies
     * {@code cos s = 1 - (1 - cos i) * sin^2 a}, so the discs overlap only for conjunctions within some
     * distance of the zenith, and a larger inclination narrows that window. See
     * {@code RelictTimelineGenerator}, which solves for the actual transit ticks and reports whether the
     * current values produce any.
     */
    public static final float PHOBOS_INCLINATION = 15.0F;
    public static final float DEIMOS_INCLINATION = -25.0F;

    /**
     * Half-width of each body's drawn quad at the celestial distance of 100.
     * <p>
     * Shared rather than kept in the renderer because the transit solver needs the same angular sizes to
     * work out when discs overlap, and the two disagreeing would put the light dip somewhere the eclipse
     * is not.
     * <p>
     * These cover the whole sprite, a quarter of which is the body — see {@link #SPRITE_DISC_FRACTION}. The
     * discs that result are 3.6 degrees across for the sun, 8.3 for Phobos and 2.9 for Deimos, so
     * <strong>Phobos is 2.3 times the sun's width and can cover it completely.</strong> From the ground it
     * is really 0.57 times, which makes Mars's real transits annular; this is a deliberate exaggeration in
     * the same spirit as the sol length, and it is what makes a total eclipse possible at all.
     */
    public static final float SUN_QUAD_EXTENT = 12.67F;
    public static final float PHOBOS_QUAD_EXTENT = 29.0F;
    public static final float DEIMOS_QUAD_EXTENT = 10.0F;

    /**
     * Fraction of a celestial quad its disc covers, the rest being glow.
     * <p>
     * Applies to the sun as much as the moons: vanilla's {@code sun.png} is also an eight-pixel disc centred
     * in a thirty-two-pixel canvas, with everything outside it a broad faint halo, which is the same
     * arrangement the moon gen-configs use. Measure the sprite before assuming otherwise — the halo reaches
     * the canvas edge and reads as the body if you go by eye.
     * <p>
     * Must match {@code body / canvas} in the moon gen-configs.
     */
    public static final float SPRITE_DISC_FRACTION = 8.0F / 32.0F;

    /** Angular radius in degrees of a body drawn at the given quad half-width. */
    public static float angularRadius(float quadExtent) {
        return (float) Math.toDegrees(Math.atan(quadExtent / 100.0));
    }

    /**
     * Unit direction of a body at the given sky angle on a plane rolled by the given inclination, in the
     * same frame the sky renderer builds its transforms in: {@code +Y} is the zenith, and an angle of zero
     * puts the body there.
     * <p>
     * Shared between the renderer, which needs it to work out phases, and the transit solver, which needs it
     * to work out when discs overlap. The two must agree exactly or the light dip lands away from the
     * eclipse, so this is deliberately the only place the geometry is written down.
     */
    public static Vector3f skyDirection(float inclinationDegrees, float angleDegrees) {
        double angle = Math.toRadians(angleDegrees);
        double inclination = Math.toRadians(inclinationDegrees);
        return new Vector3f(
                (float) (-Math.sin(angle) * Math.cos(inclination)),
                (float) Math.cos(angle),
                (float) (Math.sin(angle) * Math.sin(inclination)));
    }

    /** Unit normal of an orbital plane rolled by the given inclination; already normalized. */
    public static Vector3f orbitNormal(float inclinationDegrees) {
        double inclination = Math.toRadians(inclinationDegrees);
        return new Vector3f((float) Math.sin(inclination), 0.0F, (float) Math.cos(inclination));
    }

    /**
     * Fraction of the sun's disc a moon hides, from their angular separation and radii.
     * <p>
     * Phobos is the wider of the two discs, so close enough in it reaches a clean 1.0 — a total eclipse.
     * Deimos is narrower than the sun and tops out at the square of the ratio, a few percent, which is about
     * what it manages in reality too.
     */
    public static double eclipseCoverage(double separation, double sunRadius, double moonRadius) {
        if (separation >= sunRadius + moonRadius) {
            return 0.0;
        }

        if (separation <= sunRadius - moonRadius) {
            return moonRadius * moonRadius / (sunRadius * sunRadius);
        }

        if (separation <= moonRadius - sunRadius) {
            return 1.0;
        }

        double lens = moonRadius * moonRadius
                * Math.acos(Math.clamp((separation * separation + moonRadius * moonRadius - sunRadius * sunRadius)
                        / (2.0 * separation * moonRadius), -1.0, 1.0))
                + sunRadius * sunRadius
                * Math.acos(Math.clamp((separation * separation + sunRadius * sunRadius - moonRadius * moonRadius)
                        / (2.0 * separation * sunRadius), -1.0, 1.0))
                - 0.5 * Math.sqrt(Math.max(0.0, (moonRadius + sunRadius - separation) * (separation + moonRadius - sunRadius)
                        * (separation - moonRadius + sunRadius) * (separation + moonRadius + sunRadius)));

        return lens / (Math.PI * sunRadius * sunRadius);
    }

}
