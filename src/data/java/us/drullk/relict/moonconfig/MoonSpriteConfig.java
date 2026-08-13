package us.drullk.relict.moonconfig;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Every visual knob for one moon's sprite set, in pipeline order: orient the model, light it, normalize
 * its albedo, posterize that, map it through a gradient, snap the result to a palette, then lay a glow
 * behind it.
 * <p>
 * The order is fixed because it is forced by the pipeline — nothing can be gradient mapped before it is
 * rendered — so the stages are named blocks rather than a rearrangeable graph. Each block round-trips
 * through its own codec, which is what {@code runMoonConfig} edits.
 * <p>
 * Frame count is deliberately absent: it lives in {@code RelictDimension} so that the renderer, datagen,
 * and the tool all read one number, and is passed in at render time.
 *
 * @param model       classpath resource of the glTF binary to rasterize
 * @param spriteDir   directory under {@code textures/environment/celestial}, and so the sprite id prefix
 * @param canvas      sprite width in pixels
 * @param body        width the moon occupies within the canvas; the rest is glow
 * @param supersample samples per body pixel on each axis; only decides where the hard edge falls
 * @param levels      luminance window for the albedo; measured from the visible hemisphere when absent
 * @param posterize   value steps before the gradient map; empty leaves the lighting continuous
 * @param palette     colours the shaded body is snapped to; empty keeps whatever the gradient produced
 */
public record MoonSpriteConfig(
        String model,
        String spriteDir,
        int canvas,
        int body,
        int supersample,
        Orientation orientation,
        Lighting lighting,
        RimLight rimLight,
        Optional<Levels> levels,
        Optional<Integer> posterize,
        Ramp gradient,
        Optional<List<Integer>> palette,
        Halo halo
) {

    public static final Codec<MoonSpriteConfig> CODEC = RecordCodecBuilder.<MoonSpriteConfig>create(i -> i.group(
            Codec.STRING.fieldOf("model").forGetter(MoonSpriteConfig::model),
            Codec.STRING.fieldOf("sprite_dir").forGetter(MoonSpriteConfig::spriteDir),
            Codec.intRange(1, 256).fieldOf("canvas").forGetter(MoonSpriteConfig::canvas),
            Codec.intRange(1, 256).fieldOf("body").forGetter(MoonSpriteConfig::body),
            Codec.intRange(1, 64).optionalFieldOf("supersample", 16).forGetter(MoonSpriteConfig::supersample),
            Orientation.CODEC.fieldOf("orientation").forGetter(MoonSpriteConfig::orientation),
            Lighting.CODEC.optionalFieldOf("lighting", Lighting.DEFAULT).forGetter(MoonSpriteConfig::lighting),
            RimLight.CODEC.optionalFieldOf("rim_light", RimLight.NONE).forGetter(MoonSpriteConfig::rimLight),
            Levels.CODEC.optionalFieldOf("levels").forGetter(MoonSpriteConfig::levels),
            Codec.intRange(2, 64).optionalFieldOf("posterize").forGetter(MoonSpriteConfig::posterize),
            Ramp.CODEC.fieldOf("gradient").forGetter(MoonSpriteConfig::gradient),
            HexColor.CODEC.listOf().optionalFieldOf("palette").forGetter(MoonSpriteConfig::palette),
            Halo.CODEC.fieldOf("halo").forGetter(MoonSpriteConfig::halo)
    ).apply(i, MoonSpriteConfig::new)).validate(MoonSpriteConfig::validate);

    private static DataResult<MoonSpriteConfig> validate(MoonSpriteConfig config) {
        if (config.body > config.canvas) {
            return DataResult.error(() -> "body (" + config.body + ") cannot be wider than canvas (" + config.canvas + ")");
        }

        if (config.palette.isPresent() && config.palette.get().isEmpty()) {
            return DataResult.error(() -> "palette must have at least one colour, or be omitted entirely");
        }

        return DataResult.success(config);
    }

    /** Pixel resolution the model is rasterized at before being reduced to {@link #body()}. */
    public int renderSize() {
        return this.body * this.supersample;
    }

    /** Quantizes a 0..1 value to {@link #posterize()} steps, or passes it through when unset. */
    public float posterizeValue(float value) {
        if (this.posterize.isEmpty()) {
            return value;
        }

        int steps = this.posterize.get();
        return Math.round(value * (steps - 1)) / (float) (steps - 1);
    }

    /**
     * Snaps a shaded colour to the nearest palette entry, weighting the channels by roughly how much each
     * contributes to perceived brightness so the snap does not swap a dark warm for a dark cool.
     */
    public int snapToPalette(int rgb) {
        if (this.palette.isEmpty()) {
            return rgb;
        }

        int red = rgb >> 16 & 0xFF;
        int green = rgb >> 8 & 0xFF;
        int blue = rgb & 0xFF;
        int best = rgb;
        long bestDistance = Long.MAX_VALUE;

        for (int candidate : this.palette.get()) {
            long deltaRed = red - (candidate >> 16 & 0xFF);
            long deltaGreen = green - (candidate >> 8 & 0xFF);
            long deltaBlue = blue - (candidate & 0xFF);
            long distance = 2 * deltaRed * deltaRed + 4 * deltaGreen * deltaGreen + 3 * deltaBlue * deltaBlue;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }

        return best;
    }

    /**
     * Which way the model faces. Both moons are tidally locked, so the face pointed at Mars is the only
     * one ever seen from the ground: aim {@code view} down the model's longest axis and {@code up} along
     * its poles, then nudge with the angles.
     *
     * @param view  model axis pointing at the viewer
     * @param up    model axis that becomes sprite-up, which is the moon's spin axis
     * @param yaw   degrees about {@code up}
     * @param pitch degrees about the resulting right vector
     * @param roll  degrees about {@code view}
     */
    public record Orientation(Axis view, Axis up, float yaw, float pitch, float roll) {

        public static final Codec<Orientation> CODEC = RecordCodecBuilder.create(i -> i.group(
                Axis.CODEC.fieldOf("view").forGetter(Orientation::view),
                Axis.CODEC.fieldOf("up").forGetter(Orientation::up),
                Codec.FLOAT.optionalFieldOf("yaw", 0.0F).forGetter(Orientation::yaw),
                Codec.FLOAT.optionalFieldOf("pitch", 0.0F).forGetter(Orientation::pitch),
                Codec.FLOAT.optionalFieldOf("roll", 0.0F).forGetter(Orientation::roll)
        ).apply(i, Orientation::new));

    }

    public enum Axis {
        X_POS("+x", 1.0F, 0.0F, 0.0F),
        X_NEG("-x", -1.0F, 0.0F, 0.0F),
        Y_POS("+y", 0.0F, 1.0F, 0.0F),
        Y_NEG("-y", 0.0F, -1.0F, 0.0F),
        Z_POS("+z", 0.0F, 0.0F, 1.0F),
        Z_NEG("-z", 0.0F, 0.0F, -1.0F);

        public static final Codec<Axis> CODEC = Codec.STRING.comapFlatMap(
                name -> {
                    for (Axis axis : values()) {
                        if (axis.name.equalsIgnoreCase(name)) {
                            return DataResult.success(axis);
                        }
                    }

                    return DataResult.error(() -> "Unknown axis: " + name + " (expected one of +x -x +y -y +z -z)");
                },
                axis -> axis.name);

        private final String name;
        private final float[] vector;

        Axis(String name, float x, float y, float z) {
            this.name = name;
            this.vector = new float[]{x, y, z};
        }

        public float[] vector() {
            return this.vector.clone();
        }
    }

    /**
     * @param wrap        pushes light around the terminator; 0 is a hard Lambert edge
     * @param phaseOffset degrees added to every frame's light angle, where 0 lights the moon head-on
     * @param phaseAxis   which way the terminator sweeps, in degrees counterclockwise from sprite-right, so
     *                    0 draws a vertical terminator crossing left to right and 90 a horizontal one lit
     *                    from the top. 180 mirrors the sweep, swapping waxing for waning.
     *                    <p>
     *                    Needed because a sprite's axes are not the sky's. The moon quad is posed by its
     *                    orbit, which leaves sprite-right along the orbital normal and sprite-down along the
     *                    direction of travel — and the sun, sitting in the orbit, is therefore displaced
     *                    from the moon <em>vertically</em> in the sprite. Both moons want 90.
     *                    <p>
     *                    Only the sun's in-plane offset is modelled. Its height above the moon's orbital
     *                    plane would tilt the terminator off this axis over the cycle, and nothing computes
     *                    that; it would have to come from the renderer as a second, per-frame angle.
     * @param gain        scales lit values before posterizing. Albedo times Lambert peaks well below 1, so
     *                    without gain the bright end of the gradient is unreachable and the moon renders
     *                    muddy. The generator logs the peak it saw; {@code 1 / peak} fills the ramp.
     */
    public record Lighting(float wrap, float phaseOffset, float phaseAxis, float gain) {

        public static final Lighting DEFAULT = new Lighting(0.0F, 0.0F, 0.0F, 1.0F);

        public static final Codec<Lighting> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0.0F, 1.0F).optionalFieldOf("wrap", 0.0F).forGetter(Lighting::wrap),
                Codec.FLOAT.optionalFieldOf("phase_offset", 0.0F).forGetter(Lighting::phaseOffset),
                Codec.FLOAT.optionalFieldOf("phase_axis", 0.0F).forGetter(Lighting::phaseAxis),
                Codec.floatRange(0.01F, 16.0F).optionalFieldOf("gain", 1.0F).forGetter(Lighting::gain)
        ).apply(i, Lighting::new));

    }

    /**
     * A faint outline on every body pixel that touches empty space, added regardless of where the sun is.
     * <p>
     * Vanilla's {@code new_moon} is not blank either: without this the new phase would vanish completely,
     * and a moon on timekeeping duty has to stay findable all the way through.
     * <p>
     * Keyed on the silhouette rather than on the surface normal, so the outline is unbroken and the moon's
     * shape stays readable at every phase. A normal-based limb term leaves gaps wherever the geometry turns
     * away gently, which at eight pixels across reads as a chewed edge rather than a rock.
     *
     * @param strength value added to silhouette pixels, before posterizing
     */
    public record RimLight(float strength) {

        public static final RimLight NONE = new RimLight(0.0F);

        public static final Codec<RimLight> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0.0F, 1.0F).optionalFieldOf("strength", 0.0F).forGetter(RimLight::strength)
        ).apply(i, RimLight::new));

    }

    /** Albedo luminance window, stretched to 0..1 before the gradient map. */
    public record Levels(float min, float max) {

        public static final Codec<Levels> CODEC = RecordCodecBuilder.<Levels>create(i -> i.group(
                Codec.FLOAT.fieldOf("min").forGetter(Levels::min),
                Codec.FLOAT.fieldOf("max").forGetter(Levels::max)
        ).apply(i, Levels::new)).validate(levels -> levels.max - levels.min < 1.0E-4F
                ? DataResult.error(() -> "levels max must exceed min")
                : DataResult.success(levels));

        public float normalize(float luminance) {
            return Math.clamp((luminance - this.min) / (this.max - this.min), 0.0F, 1.0F);
        }

    }

    /**
     * A gradient map. {@link Interpolation#CONSTANT} makes every stop a hard band, which is how the
     * output stays on a fixed palette instead of drifting into smooth ramps.
     */
    public record Ramp(Interpolation interpolation, List<Stop> stops) {

        public static final Codec<Ramp> CODEC = RecordCodecBuilder.<Ramp>create(i -> i.group(
                Interpolation.CODEC.optionalFieldOf("interpolation", Interpolation.LINEAR).forGetter(Ramp::interpolation),
                Stop.CODEC.listOf().fieldOf("stops").forGetter(Ramp::stops)
        ).apply(i, Ramp::new)).validate(Ramp::validate);

        private static DataResult<Ramp> validate(Ramp ramp) {
            if (ramp.stops.isEmpty()) {
                return DataResult.error(() -> "ramp needs at least one stop");
            }

            for (int index = 1; index < ramp.stops.size(); index++) {
                if (ramp.stops.get(index).position() < ramp.stops.get(index - 1).position()) {
                    return DataResult.error(() -> "ramp stops must be ordered by position");
                }
            }

            return DataResult.success(ramp);
        }

        /** Samples the ramp, returning a packed 0xRRGGBB value. */
        public int sample(float position) {
            float clamped = Math.clamp(position, 0.0F, 1.0F);
            Stop previous = this.stops.getFirst();

            for (Stop stop : this.stops) {
                if (stop.position() >= clamped) {
                    if (this.interpolation == Interpolation.CONSTANT || stop.position() - previous.position() < 1.0E-6F) {
                        return previous.rgb();
                    }

                    float mix = (clamped - previous.position()) / (stop.position() - previous.position());
                    return lerpRgb(previous.rgb(), stop.rgb(), mix);
                }

                previous = stop;
            }

            return this.stops.getLast().rgb();
        }

        private static int lerpRgb(int from, int to, float mix) {
            int red = Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * mix);
            int green = Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * mix);
            int blue = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * mix);
            return red << 16 | green << 8 | blue;
        }

    }

    public record Stop(float position, int rgb) {

        public static final Codec<Stop> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0.0F, 1.0F).fieldOf("pos").forGetter(Stop::position),
                HexColor.CODEC.fieldOf("color").forGetter(Stop::rgb)
        ).apply(i, Stop::new));

    }

    public enum Interpolation {
        LINEAR,
        CONSTANT;

        public static final Codec<Interpolation> CODEC = Codec.STRING.comapFlatMap(
                name -> {
                    for (Interpolation interpolation : values()) {
                        if (interpolation.name().equalsIgnoreCase(name)) {
                            return DataResult.success(interpolation);
                        }
                    }

                    return DataResult.error(() -> "Unknown interpolation: " + name);
                },
                interpolation -> interpolation.name().toLowerCase(Locale.ROOT));
    }

    /**
     * The glow plate, identical in every frame so the moon stays identifiable when it is nearly new.
     * Radii are fractions of the canvas half-width; the ramp is sampled from the inner radius outwards.
     */
    public record Halo(float innerRadius, float outerRadius, Ramp ramp) {

        public static final Codec<Halo> CODEC = RecordCodecBuilder.<Halo>create(i -> i.group(
                Codec.floatRange(0.0F, 2.0F).fieldOf("inner_radius").forGetter(Halo::innerRadius),
                Codec.floatRange(0.0F, 2.0F).fieldOf("outer_radius").forGetter(Halo::outerRadius),
                Ramp.CODEC.fieldOf("ramp").forGetter(Halo::ramp)
        ).apply(i, Halo::new)).validate(halo -> halo.outerRadius <= halo.innerRadius
                ? DataResult.error(() -> "halo outer_radius must exceed inner_radius")
                : DataResult.success(halo));

    }

}
