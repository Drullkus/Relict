package us.drullk.relict.datagen.worldgen.densityfields;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import us.drullk.relict.init.worldgen.RelictNoises;
import us.drullk.relict.worldgen.DuneWaveFunction;
import us.drullk.relict.worldgen.MesaFieldFunction;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The noise channels that shape dunes' surface primitive, and the graph nodes that wire them up.
 *
 * <p>Primitives interpolate their knob sets per column, so the sampling scales are not fixed and cannot
 * be baked into {@code DensityFunctions.noise} nodes. Each primitive therefore holds its noises directly and
 * scales them itself; what lives here is only the registration and the wiring.
 */
public final class RelictDuneField {

    public static final Map<ResourceKey<NormalNoise.NoiseParameters>, NormalNoise.NoiseParameters> NOISE_PARAMETERS = noiseParameters();

    private RelictDuneField() {
    }

    private static Map<ResourceKey<NormalNoise.NoiseParameters>, NormalNoise.NoiseParameters> noiseParameters() {
        Map<ResourceKey<NormalNoise.NoiseParameters>, NormalNoise.NoiseParameters> parameters = new LinkedHashMap<>();

        // The phase warp is the one multi-octave channel: the higher octaves are what let the warp gradient
        // locally rival the wave's own and merge crest trains into Y junctions.
        parameters.put(RelictNoises.DUNE_WARP, octaves(DuneWaveFunction.WARP_OCTAVES));
        parameters.put(RelictNoises.DUNE_CRENULATION, octaves(1));
        parameters.put(RelictNoises.DUNE_MODULATION, octaves(1));

        return Map.copyOf(parameters);
    }

    /**
     * A flat amplitude list is the prototype's halving fbm rather than a change to it: {@code PerlinNoise}
     * already halves its own value factor per octave. The first octave is set so the caller's spacing keeps
     * meaning the coarsest octave's wavelength in blocks.
     */
    private static NormalNoise.NoiseParameters octaves(final int count) {
        double[] finer = new double[count - 1];
        Arrays.fill(finer, 1.0);
        return new NormalNoise.NoiseParameters(-(count - 1), 1.0, finer);
    }

    public static DensityFunction duneShape(final RelictRidgeField.NoiseLookup noises) {
        return new DuneWaveFunction(noise(noises, RelictNoises.DUNE_WARP), noise(noises, RelictNoises.DUNE_CRENULATION),
                noise(noises, RelictNoises.DUNE_MODULATION), noise(noises, RelictNoises.VARIANT_SELECTOR));
    }

    public static DensityFunction mesaShape(final RelictRidgeField.NoiseLookup noises) {
        return new MesaFieldFunction(noise(noises, RelictNoises.MESA_WARP_X), noise(noises, RelictNoises.MESA_WARP_Z),
                noise(noises, RelictNoises.MESA_SERRATION_X), noise(noises, RelictNoises.MESA_SERRATION_Z),
                noise(noises, RelictNoises.MESA_UNDULATION), noise(noises, RelictNoises.VARIANT_SELECTOR));
    }

    private static DensityFunction.NoiseHolder noise(final RelictRidgeField.NoiseLookup noises,
                                                     final ResourceKey<NormalNoise.NoiseParameters> key) {
        return new DensityFunction.NoiseHolder(noises.get(key));
    }

}
