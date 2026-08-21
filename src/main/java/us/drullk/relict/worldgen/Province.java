package us.drullk.relict.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

public record Province(Holder<Biome> biome, ElevationClass elevationClass, float elevationOffset, float ridgeAmplitude, float plainRoughness, float duneAmplitude, float mesaAmplitude, float craterExposure) {

    public static final float MAX_RIDGE_AMPLITUDE = 64.0F;
    public static final float MAX_PLAIN_ROUGHNESS = 16.0F;
    public static final float MAX_DUNE_AMPLITUDE = 48.0F;
    public static final float MAX_MESA_AMPLITUDE = 64.0F;

    /** Craters are placed globally and only their amplitude is per-province, so this is a share, not a size. */
    public static final float MAX_CRATER_EXPOSURE = 1.0F;

    public static final Codec<Province> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Biome.CODEC.fieldOf("biome").forGetter(Province::biome),
            ElevationClass.CODEC.optionalFieldOf("elevation_class", ElevationClass.MID).forGetter(Province::elevationClass),
            Codec.floatRange(-1.0F, 1.0F).optionalFieldOf("elevation_offset", 0.0F).forGetter(Province::elevationOffset),
            Codec.floatRange(0.0F, MAX_RIDGE_AMPLITUDE).optionalFieldOf("ridge_amplitude", 0.0F).forGetter(Province::ridgeAmplitude),
            Codec.floatRange(0.0F, MAX_PLAIN_ROUGHNESS).optionalFieldOf("plain_roughness", 2.5F).forGetter(Province::plainRoughness),
            Codec.floatRange(0.0F, MAX_DUNE_AMPLITUDE).optionalFieldOf("dune_amplitude", 0.0F).forGetter(Province::duneAmplitude),
            Codec.floatRange(0.0F, MAX_MESA_AMPLITUDE).optionalFieldOf("mesa_amplitude", 0.0F).forGetter(Province::mesaAmplitude),
            Codec.floatRange(0.0F, MAX_CRATER_EXPOSURE).optionalFieldOf("crater_exposure", MAX_CRATER_EXPOSURE).forGetter(Province::craterExposure)
    ).apply(instance, Province::new));

}
