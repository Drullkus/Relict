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

    public static final ResourceKey<NormalNoise.NoiseParameters> DUNE_WARP = ResourceKey.create(Registries.NOISE, Relict.id("dune_warp"));
    public static final ResourceKey<NormalNoise.NoiseParameters> DUNE_CRENULATION = ResourceKey.create(Registries.NOISE, Relict.id("dune_crenulation"));
    public static final ResourceKey<NormalNoise.NoiseParameters> DUNE_MODULATION = ResourceKey.create(Registries.NOISE, Relict.id("dune_modulation"));

    public static final ResourceKey<NormalNoise.NoiseParameters> MESA_WARP_X = ResourceKey.create(Registries.NOISE, Relict.id("mesa_warp_x"));
    public static final ResourceKey<NormalNoise.NoiseParameters> MESA_WARP_Z = ResourceKey.create(Registries.NOISE, Relict.id("mesa_warp_z"));
    public static final ResourceKey<NormalNoise.NoiseParameters> MESA_SERRATION_X = ResourceKey.create(Registries.NOISE, Relict.id("mesa_serration_x"));
    public static final ResourceKey<NormalNoise.NoiseParameters> MESA_SERRATION_Z = ResourceKey.create(Registries.NOISE, Relict.id("mesa_serration_z"));
    public static final ResourceKey<NormalNoise.NoiseParameters> MESA_UNDULATION = ResourceKey.create(Registries.NOISE, Relict.id("mesa_undulation"));

    /** One field to select which variant of a shape primitive inside provinces. */
    public static final ResourceKey<NormalNoise.NoiseParameters> VARIANT_SELECTOR = ResourceKey.create(Registries.NOISE, Relict.id("variant_selector"));

}
