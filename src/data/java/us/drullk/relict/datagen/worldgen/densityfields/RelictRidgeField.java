package us.drullk.relict.datagen.worldgen.densityfields;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.CubicSpline;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import us.drullk.relict.init.worldgen.RelictNoises;
import us.drullk.relict.worldgen.NoiseSpread;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The wrinkle-ridge height field, as a density function graph.
 *
 * <h2>What a wrinkle ridge is</h2>
 * Two superposed landforms, which is what the HiRISE reference actually shows: a <em>broad low arch</em>,
 * and a <em>narrow crenulated wrinkle</em> riding on its crest with a steep scarp on one flank. Two terms
 * at two scales, not one bumpy noise.
 * <p>
 * The ridge lines themselves are the <strong>zero contour</strong> of a domain-warped noise, not its peaks.
 * Contours of a smooth 2D field are long wandering curves that pinch and branch at saddle points — which is
 * exactly the anastomosing trace in the orbital imagery, obtained for free rather than constructed.
 * <p>
 * Because the field {@code R} is <em>signed</em> rather than absolute, a cross-section profile that is not
 * symmetric about zero gives a long gentle back-slope on one side and a short steep drop on the other. That
 * asymmetry <em>is</em> the lobate scarp, and it costs no gradients and no finite differences — which is the
 * whole reason this landform can be a density function at all.
 *
 * <h2>Two different noise spaces, which is easy to get backwards</h2>
 * The prototype's gradient noise and {@code minecraft:noise} have different spreads, so the prototype's
 * constants cannot be copied across unchanged. Two of them scale, in <em>opposite</em> directions:
 * <ul>
 *   <li>Anything compared <em>against</em> a noise value — the spline locations in {@code R},
 *       {@link #CREN_AMPLITUDE}, {@link #FLIP_EDGE}, {@link #MASK_GAIN} — lives in the noise's own output
 *       units and scales <em>with</em> {@link #NOISE_SD_RATIO}. (Gain multiplies a noise, so it scales
 *       inversely to keep the product's spread fixed.)</li>
 *   <li>{@link #WARP_STRENGTH} is added to a noise's <em>input</em> coordinate, in lattice units, so what
 *       must be preserved is the absolute shift — and it therefore scales <em>inversely</em>.</li>
 * </ul>
 * Getting this backwards does not crash; it silently produces ridges of the wrong width, which is why the
 * ratio is a named constant with its measurement recorded rather than folded into the numbers.
 */
public final class RelictRidgeField {

    // ---- ridge lines --------------------------------------------------------------------------------

    /** Blocks between ridge lines. The noise wavelength is twice this. */
    private static final double RIDGE_SPACING = 700.0;

    private static final double WARP_SPACING = 900.0;

    /**
     * How far the ridge lines wander off the noise lattice, in lattice units.
     * <p>
     * Worth knowing before spending time on it: in the prototype this does much less than its size
     * suggests. Rendering with it at zero left line position and width essentially unchanged. The reason is
     * that the displacement it produces is comparable to {@link #WARP_SPACING} itself, so the field folds
     * over locally instead of bending coherently — a slow, large-scale wander wants a warp wavelength well
     * above the ridge wavelength, not a bigger strength.
     */
    private static final double WARP_STRENGTH = 0.55;

    // ---- cross-section ------------------------------------------------------------------------------

    /** Share of the ridge height carried by the broad arch; the rest is the narrow wrinkle. */
    private static final double ARCH_SHARE = 0.68;

    /** Crest wander, in units of {@code R}. Perturbs the narrow profile only. */
    private static final double CREN_AMPLITUDE = 0.10;
    private static final double CREN_SPACING = 140.0;

    /** How far either side of zero the steep-flank swap blends over. Blended so the swap is not a cliff. */
    private static final double FLIP_EDGE = 0.25;
    private static final double FLIP_SPACING = 2600.0;

    // ---- coverage -----------------------------------------------------------------------------------

    private static final double MASK_SPACING = 2200.0;
    private static final double MASK_GAIN = 2.2;

    /** Lower makes ridges rarer. Negative-leaning on purpose: most of the plain must stay a plain. */
    private static final double MASK_BIAS = 0.15;

    // ---- the plain ----------------------------------------------------------------------------------

    private static final double PLAIN_SPACING = 90.0;
    static final int PLAIN_OCTAVES = 4;

    // ---- noise scale reconciliation -----------------------------------------------------------------

    /** Both spreads and their ratio live in {@link NoiseSpread}, which every ported primitive reads. */
    private static final double NOISE_SD_RATIO = NoiseSpread.RATIO;

    // ---- cross-section control points ---------------------------------------------------------------

    /**
     * The broad arch, and below it the narrow wrinkle: {@code {location in R, height 0..1, derivative}}.
     * <p>
     * These are not a transcription of the prototype's {@code flank()} — that function is
     * {@code pow(1 - smoothstep(...), sharp)} over an asymmetric pair of half-widths, and there is no
     * {@code pow} and no {@code smoothstep} in the density function primitive set. They are a <em>fit</em>,
     * produced by sampling {@code flank()} at its own landmarks (the two zero crossings, the crest, and two
     * points per flank at the inflection) and taking the analytic derivative at each. Evaluated through
     * Minecraft's own interpolant the fit is within <strong>0.0018</strong> of the prototype on a peak of
     * 1.0 — under a tenth of a block at any amplitude this province will use, so the shape crosses the port
     * without visibly changing.
     * <p>
     * <strong>The outermost derivatives must stay exactly zero.</strong> {@code CubicSpline.Multipoint}
     * does not clamp outside its range — it extends <em>linearly</em> using the endpoint derivative, so any
     * residue there would tilt the entire plain instead of flattening off-ridge.
     */
    private static final double[][] ARCH_PROFILE = {
            {-0.42000, 0.00000, +0.00000},
            {-0.30240, 0.08367, +1.88946},
            {-0.21000, 0.35355, +3.78807},
            {-0.11760, 0.72725, +3.88489},
            {+0.00000, 1.00000, +0.00000},
            {+0.03640, 0.72725, -12.55118},
            {+0.06500, 0.35355, -12.23839},
            {+0.09360, 0.08367, -6.10440},
            {+0.13000, 0.00000, +0.00000},
    };

    private static final double[][] WRINKLE_PROFILE = {
            {-0.13000, 0.00000, +0.00000},
            {-0.09360, 0.04318, +3.99013},
            {-0.06500, 0.26794, +11.74829},
            {-0.03640, 0.66804, +14.60369},
            {+0.00000, 1.00000, +0.00000},
            {+0.00980, 0.66804, -54.24228},
            {+0.01750, 0.26794, -43.63649},
            {+0.02520, 0.04318, -14.82049},
            {+0.03500, 0.00000, +0.00000},
    };

    public static final Map<ResourceKey<NormalNoise.NoiseParameters>, NormalNoise.NoiseParameters> NOISE_PARAMETERS =
            noiseParameters();

    private static Map<ResourceKey<NormalNoise.NoiseParameters>, NormalNoise.NoiseParameters> noiseParameters() {
        Map<ResourceKey<NormalNoise.NoiseParameters>, NormalNoise.NoiseParameters> parameters = new LinkedHashMap<>();
        parameters.put(RelictNoises.RIDGE, new NormalNoise.NoiseParameters(0, 1.0));
        parameters.put(RelictNoises.RIDGE_WARP_X, new NormalNoise.NoiseParameters(0, 1.0));
        parameters.put(RelictNoises.RIDGE_WARP_Z, new NormalNoise.NoiseParameters(0, 1.0));
        parameters.put(RelictNoises.RIDGE_CRENULATION, new NormalNoise.NoiseParameters(0, 1.0));
        parameters.put(RelictNoises.RIDGE_FLIP, new NormalNoise.NoiseParameters(0, 1.0));
        parameters.put(RelictNoises.RIDGE_MASK, new NormalNoise.NoiseParameters(0, 1.0));
        parameters.put(RelictNoises.PLAIN, new NormalNoise.NoiseParameters(-(PLAIN_OCTAVES - 1), 1.0, 1.0, 1.0, 1.0));
        return Map.copyOf(parameters);
    }

    /**
     * How the graph builder reaches a noise. Narrower than {@code HolderGetter} on purpose: datagen passes
     * its registry lookup, and the standalone sampler passes direct holders over
     * {@link #NOISE_PARAMETERS} without needing a registry at all.
     */
    @FunctionalInterface
    public interface NoiseLookup {
        Holder<NormalNoise.NoiseParameters> get(ResourceKey<NormalNoise.NoiseParameters> key);
    }

    private RelictRidgeField() {
    }

    /**
     * The ridge profile, unitless and roughly 0..1, before any per-cell amplitude.
     * <p>
     * Deliberately global rather than per-province. Real compressional ridges are regional features and
     * would look wrong stopping dead at a province border, so what the cell contributes is the
     * <em>amplitude</em> only — see {@link us.drullk.relict.worldgen.ProvinceParameter#RIDGE_AMPLITUDE}.
     * That also makes smoothing across borders later a matter of smoothing one scalar rather than blending
     * two landforms.
     */
    public static DensityFunction shape(NoiseLookup noises) {
        // The ridge field. shifted_noise_2d computes noise(x * xz_scale + shift_x, ...), so the shift lands
        // in noise space, which is what the prototype's gnoise(p / S + w * strength) does too.
        //
        // NOT shift_a/shift_b, though both earlier design notes called for them: those bake in a fixed
        // coordinate scale of 1/4 and an output multiplier of 4, so the warp wavelength would only be
        // settable in powers of two and the strength would land four times hot. A plain noise as the shift
        // keeps WARP_SPACING a free parameter in blocks.
        double warpStrength = WARP_STRENGTH / NOISE_SD_RATIO;
        DensityFunction ridgeField = DensityFunctions.shiftedNoise2d(
                warp(noises, RelictNoises.RIDGE_WARP_X, warpStrength),
                warp(noises, RelictNoises.RIDGE_WARP_Z, warpStrength),
                1.0 / (2.0 * RIDGE_SPACING),
                noises.get(RelictNoises.RIDGE));

        // Which flank carries the scarp, 0..1, swapping in patches along the ridge. A two-point spline with
        // zero derivatives at both ends is exactly smoothstep: the Hermite basis reduces to 3t^2 - 2t^3.
        DensityFunction side = DensityFunctions.spline(CubicSpline.builder(coordinate(flip(noises)))
                .addPoint((float) (-FLIP_EDGE * NOISE_SD_RATIO), 0.0F, 0.0F)
                .addPoint((float) (+FLIP_EDGE * NOISE_SD_RATIO), 1.0F, 0.0F)
                .build());

        DensityFunction arch = flipped(ridgeField, side, ARCH_PROFILE);

        // Crenulation goes into the narrow profile only, so the sharp crest wanders relative to the broad
        // swell underneath it rather than the two moving together.
        DensityFunction crenulated = DensityFunctions.add(ridgeField, DensityFunctions.mul(DensityFunctions.constant(CREN_AMPLITUDE * NOISE_SD_RATIO), planar(noises, RelictNoises.RIDGE_CRENULATION, CREN_SPACING)));
        DensityFunction wrinkle = flipped(crenulated, side, WRINKLE_PROFILE);

        DensityFunction profile = DensityFunctions.add(DensityFunctions.mul(DensityFunctions.constant(ARCH_SHARE), arch), DensityFunctions.mul(DensityFunctions.constant(1.0 - ARCH_SHARE), wrinkle));

        return DensityFunctions.mul(mask(noises), profile);
    }

    /**
     * The plain's own fine relief, before any per-cell amplitude.
     * <p>
     * Unlike every other field here this one is multi-octave, and that changes how the scale is set:
     * {@code PLAIN} is registered with {@link #PLAIN_OCTAVES} amplitudes and therefore a first octave of
     * {@code -(PLAIN_OCTAVES - 1)}, and {@code PerlinNoise} pre-multiplies its input coordinate by
     * {@code 2^firstOctave}. Undoing that here keeps {@link #PLAIN_SPACING} meaning the coarsest octave's
     * wavelength in blocks, which is what it means in the prototype.
     * <p>
     * The amplitudes are all 1: {@code PerlinNoise} already halves its own value factor per octave, so a
     * flat amplitude list <em>is</em> the prototype's halving fbm rather than a change to it.
     */
    public static DensityFunction plain(NoiseLookup noises) {
        double coarsestOctave = 1 << (PLAIN_OCTAVES - 1);
        return DensityFunctions.noise(noises.get(RelictNoises.PLAIN), coarsestOctave / PLAIN_SPACING, 0.0);
    }

    /**
     * Coverage: most of the plain smooth, ridges arriving as events. Clamped rather than smoothly faded so
     * that "no ridge here" is genuinely flat and not a low swell everywhere.
     */
    private static DensityFunction mask(NoiseLookup noises) {
        return DensityFunctions.add(DensityFunctions.mul(DensityFunctions.constant(MASK_GAIN / NOISE_SD_RATIO), planar(noises, RelictNoises.RIDGE_MASK, MASK_SPACING)), DensityFunctions.constant(MASK_BIAS)).clamp(0.0, 1.0);
    }

    /**
     * One cross-section, evaluated in both {@code R} and {@code -R} and blended by {@code side}. Blending
     * the two profiles rather than switching between them is what keeps the scarp swapping sides along a
     * ridge without putting a cliff at the swap.
     */
    private static DensityFunction flipped(DensityFunction ridgeField, DensityFunction side, double[][] profile) {
        return DensityFunctions.lerp(side, crossSection(ridgeField, profile),
                crossSection(DensityFunctions.mul(DensityFunctions.constant(-1.0), ridgeField), profile));
    }

    private static DensityFunction crossSection(DensityFunction ridgeField, double[][] profile) {
        CubicSpline.Builder<DensityFunctions.Spline.Coordinate> builder = CubicSpline.builder(coordinate(ridgeField));
        for (double[] point : profile) {
            // Locations are in R, so they scale with the noise spread; heights are unitless and do not.
            // Derivatives are dValue/dR and so scale inversely with the locations.
            builder.addPoint((float) (point[0] * NOISE_SD_RATIO), (float) point[1],
                    (float) (point[2] / NOISE_SD_RATIO));
        }
        return DensityFunctions.spline(builder.build());
    }

    private static DensityFunction flip(NoiseLookup noises) {
        return planar(noises, RelictNoises.RIDGE_FLIP, FLIP_SPACING);
    }

    private static DensityFunction warp(NoiseLookup noises,
                                        ResourceKey<NormalNoise.NoiseParameters> key, double strength) {
        return DensityFunctions.mul(DensityFunctions.constant(strength), planar(noises, key, WARP_SPACING));
    }

    /** A noise read flat in y, at a wavelength given in blocks. */
    private static DensityFunction planar(NoiseLookup noises,
                                          ResourceKey<NormalNoise.NoiseParameters> key, double spacing) {
        return DensityFunctions.noise(noises.get(key), 1.0 / spacing, 0.0);
    }

    private static DensityFunctions.Spline.Coordinate coordinate(DensityFunction function) {
        return new DensityFunctions.Spline.Coordinate(function);
    }

}
