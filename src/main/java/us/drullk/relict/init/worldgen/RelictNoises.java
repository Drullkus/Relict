package us.drullk.relict.init.worldgen;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import us.drullk.relict.Relict;

public class RelictNoises {

    public static final ResourceKey<NormalNoise.NoiseParameters> RIDGE = ResourceKey.create(Registries.NOISE, Relict.id("ridge"));

    public static final ResourceKey<NormalNoise.NoiseParameters> RIDGE_WARP_X = ResourceKey.create(Registries.NOISE, Relict.id("ridge_warp_x"));
    public static final ResourceKey<NormalNoise.NoiseParameters> RIDGE_WARP_Z = ResourceKey.create(Registries.NOISE, Relict.id("ridge_warp_z"));
    public static final ResourceKey<NormalNoise.NoiseParameters> RIDGE_CRENULATION = ResourceKey.create(Registries.NOISE, Relict.id("ridge_crenulation"));
    public static final ResourceKey<NormalNoise.NoiseParameters> RIDGE_FLIP = ResourceKey.create(Registries.NOISE, Relict.id("ridge_flip"));
    public static final ResourceKey<NormalNoise.NoiseParameters> RIDGE_MASK = ResourceKey.create(Registries.NOISE, Relict.id("ridge_mask"));

    public static final ResourceKey<NormalNoise.NoiseParameters> PLAIN = ResourceKey.create(Registries.NOISE, Relict.id("plain"));

}
