package us.drullk.relict.datagen.worldgen.densityfields;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import us.drullk.relict.init.worldgen.RelictNoises;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class RelictCommonFields {

    public static final Map<ResourceKey<NormalNoise.NoiseParameters>, NormalNoise.NoiseParameters> NOISE_PARAMETERS = noiseParameters();

    private static Map<ResourceKey<NormalNoise.NoiseParameters>, NormalNoise.NoiseParameters> noiseParameters() {
        Map<ResourceKey<NormalNoise.NoiseParameters>, NormalNoise.NoiseParameters> parameters = new LinkedHashMap<>();

        parameters.put(RelictNoises.VARIANT_SELECTOR, octaves(1));

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

}
