package us.drullk.relict.worldgen;

import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * One low-frequency field that moves a landform grammar continuously through its prototyped variants.
 *
 * <p>{@link #SPACING} is kept as a visible tunable: it is the distance over which a
 * province changes character, and nothing else depends on it. {@link #HALF_RANGE} is in the noise's own
 * output units, so the tails outside it clamp and each end variant is reached in its pure form.
 */
public final class VariantSelector {

    public static final double SPACING = 3000.0;

    private static final double HALF_RANGE = 0.5;

    private VariantSelector() {
    }

    public static double at(final DensityFunction.NoiseHolder selector, final double x, final double z, final int variants) {
        double value = selector.getValue(x / SPACING, 0.0, z / SPACING);
        return Math.clamp(0.5 + value / (2.0 * HALF_RANGE), 0.0, 1.0) * (variants - 1);
    }

}
