package us.drullk.relict.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Rusted Dunes: a dune sea as a <em>wave</em>, not as noise.
 *
 * <h2>The grammar</h2>
 * <pre>
 * phase(p)  = dot(p, wind)/WAVELENGTH + WARP * fbm(p/WARP_SPACING) + CREN * noise(p/CREN_SPACING)
 * height(p) = AMPLITUDE * am(p) * profile(fract(phase))
 * </pre>
 * The fbm lives <em>inside</em> the phase. It bends crests, wanders their spacing, and where its gradient
 * locally rivals the base {@code 1/WAVELENGTH} gradient the crest trains merge and terminate — the
 * Y-junction mechanism. It never appears as a height term. {@code am} is a slow amplitude field: where it
 * sags, crests lower and break, which is the other half of the junction mechanism and the whole character of
 * the barchanoid variant.
 *
 * <p>{@link #profile} is the asymmetry — interdune floor, long stoss rise whose slope grows toward the
 * brink, short slip face at the angle of repose. The slip face is always downwind; a dune sea has one wind
 * and there is no flip term.
 *
 * <h2>Why this is Java and not a density-function graph</h2>
 * Two reasons, both structural. There is no {@code fract} in the density-function primitive set, so the
 * phase cannot be wrapped; and every knob below is interpolated per column by {@link VariantSelector}, so
 * the profile is not one fixed spline but a family of them.
 *
 * <p>The value returned is unitless: dune height in units of {@link #REFERENCE_AMPLITUDE}, so that
 * {@link ProvinceParameter#DUNE_AMPLITUDE} set to that number reproduces the prototype and any smaller
 * number cools the whole province uniformly.
 */
public record DuneWaveFunction(DensityFunction.NoiseHolder warp, DensityFunction.NoiseHolder crenulation,
                               DensityFunction.NoiseHolder modulation, DensityFunction.NoiseHolder selector)
        implements DensityFunction {

    public static final MapCodec<DuneWaveFunction> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DensityFunction.NoiseHolder.CODEC.fieldOf("warp").forGetter(DuneWaveFunction::warp),
            DensityFunction.NoiseHolder.CODEC.fieldOf("crenulation").forGetter(DuneWaveFunction::crenulation),
            DensityFunction.NoiseHolder.CODEC.fieldOf("modulation").forGetter(DuneWaveFunction::modulation),
            DensityFunction.NoiseHolder.CODEC.fieldOf("selector").forGetter(DuneWaveFunction::selector)
    ).apply(instance, DuneWaveFunction::new));

    private static final KeyDispatchDataCodec<DuneWaveFunction> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    /** Radians from +x. One wind for the whole province, which is what makes it read as a sea. */
    public static final double WIND_AZIMUTH = 0.65;

    /** The blocks that {@link ProvinceParameter#DUNE_AMPLITUDE} is measured in: the tallest variant's height. */
    public static final double REFERENCE_AMPLITUDE = 24.0;

    /**
     * Crest to crest along the wind, and <strong>the same for every variant</strong>.
     *
     * <p>The 0.14c table gives three wavelengths, and the selector cannot interpolate them. Phase is
     * {@code dot(p, wind) / WAVELENGTH}, so a wavelength that varies with position adds a second gradient term
     * {@code -dot(p, wind) * grad(WAVELENGTH) / WAVELENGTH^2} that grows without bound with distance from the
     * origin: measured, it swamped the wave itself past about 30k blocks and shattered the sea into 25-block
     * chatter. The same argument rules out interpolating any noise <em>spacing</em>, which is why the three
     * below are constants too — only quantities that multiply a field value may follow the selector.
     *
     * <p>The variants keep their wavelength <em>spread</em> through {@code warpAmplitude}, which is the term
     * that already makes the local wavelength wander, and is an amplitude rather than a scale.
     */
    private static final double WAVELENGTH = 170.0;

    private static final double WARP_SPACING = 950.0;
    private static final double CRENULATION_SPACING = 240.0;
    private static final double MODULATION_SPACING = 1300.0;

    /** Superposed dunes at {@code WAVELENGTH / SECONDARY_RATIO}; a ratio of the phase, so it may be constant. */
    private static final double SECONDARY_RATIO = 6.0;

    /** Octave count of the phase warp; {@code PerlinNoise} pre-multiplies its input by {@code 2^firstOctave}. */
    public static final int WARP_OCTAVES = 3;

    private static final double COARSEST_WARP_OCTAVE = 1 << (WARP_OCTAVES - 1);

    /** Secondary dunes ride the upper stoss only; lower down the primary slope no longer carries them. */
    private static final double SECONDARY_MASK_IN_START = 0.40;
    private static final double SECONDARY_MASK_IN_END = 0.55;
    private static final double SECONDARY_MASK_OUT_RUN = 0.10;
    private static final double SECONDARY_PHASE_OFFSET = 0.37;

    /** One variant's knob set. Every field here multiplies a value rather than scaling a coordinate. */
    private record Wave(double floorFraction, double slipFraction, double stossPower, double slipBrink,
                        double amplitude, double warpAmplitude, double crenulationAmplitude,
                        double modulationBias, double modulationGain, double modulationFloor,
                        double secondaryAmplitude) {

        static Wave lerp(final double t, final Wave a, final Wave b) {
            return new Wave(
                    Mth.lerp(t, a.floorFraction, b.floorFraction),
                    Mth.lerp(t, a.slipFraction, b.slipFraction),
                    Mth.lerp(t, a.stossPower, b.stossPower),
                    Mth.lerp(t, a.slipBrink, b.slipBrink),
                    Mth.lerp(t, a.amplitude, b.amplitude),
                    Mth.lerp(t, a.warpAmplitude, b.warpAmplitude),
                    Mth.lerp(t, a.crenulationAmplitude, b.crenulationAmplitude),
                    Mth.lerp(t, a.modulationBias, b.modulationBias),
                    Mth.lerp(t, a.modulationGain, b.modulationGain),
                    Mth.lerp(t, a.modulationFloor, b.modulationFloor),
                    Mth.lerp(t, a.secondaryAmplitude, b.secondaryAmplitude));
        }
    }

    /**
     * The three variants, in the order the selector walks them: transverse, barchanoid, compound
     */
    private static final Wave[] VARIANTS = {
            new Wave(0.040, 0.260, 2.0, 1.5, 18.0, 2.2, 0.22, 0.88, 0.45, 0.50, 0.0),
            new Wave(0.465, 0.145, 1.5, 1.2, 12.75, 1.6, 0.18, 0.60, 1.30, 0.00, 0.0),
            new Wave(0.030, 0.260, 2.0, 1.5, 18.5, 1.7, 0.20, 0.90, 0.40, 0.55, 1.4),
    };

    @Override
    public double compute(final FunctionContext context) {
        double x = context.blockX();
        double z = context.blockZ();

        double coordinate = VariantSelector.at(this.selector, x, z, VARIANTS.length);
        int lower = Math.min((int) coordinate, VARIANTS.length - 2);
        Wave wave = Wave.lerp(coordinate - lower, VARIANTS[lower], VARIANTS[lower + 1]);

        double phase = this.phase(wave, x, z);
        double crest = phase - Math.floor(phase);

        double modulation = Math.clamp(
                wave.modulationBias() + wave.modulationGain() / NoiseSpread.RATIO
                        * this.modulation.getValue(x / MODULATION_SPACING, 0.0, z / MODULATION_SPACING),
                wave.modulationFloor(), 1.0);

        double height = wave.amplitude() * modulation * profile(crest, wave);

        double secondaryPhase = phase * SECONDARY_RATIO + SECONDARY_PHASE_OFFSET;
        height += wave.secondaryAmplitude() * secondaryMask(crest, wave)
                * profile(secondaryPhase - Math.floor(secondaryPhase), wave);

        return height / REFERENCE_AMPLITUDE;
    }

    private double phase(final Wave wave, final double x, final double z) {
        double alongWind = x * Math.cos(WIND_AZIMUTH) + z * Math.sin(WIND_AZIMUTH);
        double warpScale = COARSEST_WARP_OCTAVE / WARP_SPACING;

        return alongWind / WAVELENGTH
                + wave.warpAmplitude() * NoiseSpread.PROTOTYPE_FBM_3 / NoiseSpread.MINECRAFT_FBM_3
                * this.warp.getValue(x * warpScale, 0.0, z * warpScale)
                + wave.crenulationAmplitude() / NoiseSpread.RATIO
                * this.crenulation.getValue(x / CRENULATION_SPACING, 0.0, z / CRENULATION_SPACING);
    }

    /** {@code u} runs 0..1 downwind: interdune floor, stoss rise, slip fall. C1 across the wrap. */
    private static double profile(final double u, final Wave wave) {
        double stossEnd = 1.0 - wave.slipFraction();

        if (u < wave.floorFraction()) {
            return 0.0;
        }

        if (u < stossEnd) {
            return Math.pow((u - wave.floorFraction()) / (stossEnd - wave.floorFraction()), wave.stossPower());
        }

        double t = (u - stossEnd) / wave.slipFraction();
        return (2.0 * t - 3.0) * t * t + 1.0 - wave.slipBrink() * t * (1.0 - t) * (1.0 - t);
    }

    private static double secondaryMask(final double u, final Wave wave) {
        double stossEnd = 1.0 - wave.slipFraction();
        return smoothstep(SECONDARY_MASK_IN_START, SECONDARY_MASK_IN_END, u)
                * (1.0 - smoothstep(stossEnd - SECONDARY_MASK_OUT_RUN, stossEnd, u));
    }

    private static double smoothstep(final double from, final double to, final double value) {
        double t = Math.clamp((value - from) / (to - from), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    @Override
    public void fillArray(final double[] array, final ContextProvider contextProvider) {
        contextProvider.fillAllDirectly(array, this);
    }

    @Override
    public DensityFunction mapChildren(final Visitor visitor) {
        return new DuneWaveFunction(visitor.visitNoise(this.warp), visitor.visitNoise(this.crenulation),
                visitor.visitNoise(this.modulation), visitor.visitNoise(this.selector));
    }

    @Override
    public double minValue() {
        return -1.0;
    }

    @Override
    public double maxValue() {
        return 2.0;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }

}
