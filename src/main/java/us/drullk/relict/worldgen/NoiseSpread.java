package us.drullk.relict.worldgen;

/**
 * Measured spreads of the two noise implementations this mod's terrain crosses between.
 *
 * <p>Every shape in {@code prototypes/} is written against a GLSL gradient noise whose spread is not the
 * spread of {@code minecraft:noise}, so a constant copied across unchanged lands at the wrong size. Which
 * direction a constant scales depends on where it sits: an amplitude that <em>multiplies</em> a noise scales
 * inversely with the target spread, and a threshold <em>compared against</em> a noise value scales with it.
 *
 * <p>The numbers are measurements, not choices. {@code RidgeFieldSampler} re-measures the single-octave
 * value on every datagen run and prints what this constant should read; the multi-octave values come from
 * the same report's noise-spread section.
 */
public final class NoiseSpread {

    /** Spread of the prototype's {@code gnoise}, over 160k off-lattice samples. */
    public static final double PROTOTYPE = 0.3104;

    /** Spread of a single-octave {@code minecraft:noise}, on the same off-lattice grid. */
    public static final double MINECRAFT = 0.3150;

    public static final double RATIO = MINECRAFT / PROTOTYPE;

    /**
     * Spread of the prototype's three-octave {@code fbm}: three independent octaves at amplitudes 1/2, 1/4,
     * 1/8, so {@code sqrt(1/4 + 1/16 + 1/64) * PROTOTYPE}.
     */
    public static final double PROTOTYPE_FBM_3 = 0.1778;

    /**
     * Spread of a three-octave {@code minecraft:noise}. Not derivable from {@link #MINECRAFT}:
     * {@code NormalNoise} renormalizes the whole octave stack, so a three-octave field is nowhere near the
     * single-octave value times the amplitude sum.
     */
    public static final double MINECRAFT_FBM_3 = 0.2833;

    private NoiseSpread() {
    }

}
