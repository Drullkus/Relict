package us.drullk.relict.datagen.worldgen;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import us.drullk.relict.datagen.worldgen.densityfields.RelictCommonFields;
import us.drullk.relict.datagen.worldgen.densityfields.RelictDuneField;
import us.drullk.relict.datagen.worldgen.densityfields.RelictRidgeField;
import us.drullk.relict.datagen.worldgen.densityfields.RelictMesaField;
import us.drullk.relict.init.custom.RelictCustomRegistries;
import us.drullk.relict.init.custom.RelictProvinces;
import us.drullk.relict.init.custom.RelictVoronoiSources;
import us.drullk.relict.init.worldgen.RelictBiomes;
import us.drullk.relict.worldgen.ElevationClass;
import us.drullk.relict.worldgen.Province;
import us.drullk.relict.worldgen.VoronoiSource;

public class RelictProvinceGenerator {

    public static final int CELL_SIZE = 512;
    public static final int UNDERGROUND_CELL_SIZE = 384;
    public static final float JITTER = 0.9F;
    public static final float BLEND_WIDTH = 64.0F;

    public static final int EPOCH_SPACING = 8192;

    public static final float EPOCH_RELIEF = 0.42F;

    private static final float RIDGE_AMPLITUDE = 26.0F;

    static final float PLAIN_ROUGHNESS = 3.0F;

    /** Public: read by the reports source set's {@code VoronoiFieldSampler}, which compiles against this output. */
    public static final float DUNE_PLAIN_ROUGHNESS = 2.4F;
    public static final float MESA_PLAIN_ROUGHNESS = 2.6F;

    public static final float DUNE_AMPLITUDE = 24.0F;

    public static final float MESA_AMPLITUDE = 30.0F;

    /**
     * How much of the crater record a province keeps. Craters are placed globally and straddle borders, so
     * this is the only per-province term and it cross-fades over the blend width: an active dune sea buries
     * almost everything it inherits, while old high crust keeps the whole record.
     */
    public static final float PLAINS_CRATER_EXPOSURE = 0.7F;

    public static final float DUNE_CRATER_EXPOSURE = 0.1F;

    public static final float HIGHLAND_CRATER_EXPOSURE = 1.0F;

    public static void bootstrapNoises(BootstrapContext<NormalNoise.NoiseParameters> context) {
        RelictCommonFields.NOISE_PARAMETERS.forEach(context::register);

        // Province fields to create distinct landforms
        RelictDuneField.NOISE_PARAMETERS.forEach(context::register);
        RelictMesaField.NOISE_PARAMETERS.forEach(context::register);
        RelictRidgeField.NOISE_PARAMETERS.forEach(context::register);
    }

    public static void bootstrapProvinces(BootstrapContext<Province> context) {
        HolderGetter<Biome> biomes = context.lookup(Registries.BIOME);

        context.register(RelictProvinces.WRINKLE_PLAINS, new Province(biomes.getOrThrow(RelictBiomes.WRINKLE_PLAINS),
                ElevationClass.MID, 0.0F, RIDGE_AMPLITUDE, PLAIN_ROUGHNESS, 0.0F, 0.0F, PLAINS_CRATER_EXPOSURE));
        context.register(RelictProvinces.RUSTED_DUNES, new Province(biomes.getOrThrow(RelictBiomes.RUSTED_DUNES),
                ElevationClass.LOW, -0.15F, 0.0F, DUNE_PLAIN_ROUGHNESS, DUNE_AMPLITUDE, 0.0F, DUNE_CRATER_EXPOSURE));
        context.register(RelictProvinces.FRETTED_MESAS, new Province(biomes.getOrThrow(RelictBiomes.FRETTED_MESAS),
                ElevationClass.HIGH, 0.18F, 0.0F, MESA_PLAIN_ROUGHNESS, 0.0F, MESA_AMPLITUDE, HIGHLAND_CRATER_EXPOSURE));

        // TODO implement as mountain range-shaped province
        context.register(RelictProvinces.SHATTERED_HIGHLANDS, new Province(biomes.getOrThrow(RelictBiomes.SHATTERED_HIGHLANDS),
                ElevationClass.HIGH, 0.18F, 0.0F, PLAIN_ROUGHNESS, 0.0F, 0.0F, HIGHLAND_CRATER_EXPOSURE));

        // Cave provinces reach the terrain graph through the underground source, which carries no relief, so
        // their crater exposure is never read.
        context.register(RelictProvinces.BASALT_CAVES, new Province(biomes.getOrThrow(RelictBiomes.BASALT_CAVES),
                ElevationClass.NEUTRAL, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        context.register(RelictProvinces.SULFUR_CAVES, new Province(biomes.getOrThrow(RelictBiomes.SULFUR_CAVES),
                ElevationClass.MID, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        context.register(RelictProvinces.ICE_CAVES, new Province(biomes.getOrThrow(RelictBiomes.ICE_CAVES),
                ElevationClass.NEUTRAL, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        context.register(RelictProvinces.CALCITE_CAVES, new Province(biomes.getOrThrow(RelictBiomes.CALCITE_CAVES),
                ElevationClass.HIGH, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
    }

    public static void bootstrapVoronoiSources(BootstrapContext<VoronoiSource> context) {
        HolderGetter<Province> provinces = context.lookup(RelictCustomRegistries.PROVINCE_REGISTRY);

        WeightedList<Holder<Province>> surface = WeightedList.<Holder<Province>>builder()
                .add(provinces.getOrThrow(RelictProvinces.WRINKLE_PLAINS), 1)
                .add(provinces.getOrThrow(RelictProvinces.FRETTED_MESAS), 1)
                .add(provinces.getOrThrow(RelictProvinces.RUSTED_DUNES), 1)
                .build();

        WeightedList<Holder<Province>> underground = WeightedList.<Holder<Province>>builder()
                .add(provinces.getOrThrow(RelictProvinces.BASALT_CAVES), 9)
                .add(provinces.getOrThrow(RelictProvinces.SULFUR_CAVES), 3)
                .add(provinces.getOrThrow(RelictProvinces.ICE_CAVES), 3)
                .add(provinces.getOrThrow(RelictProvinces.CALCITE_CAVES), 3)
                .build();

        context.register(RelictVoronoiSources.MARS, new VoronoiSource(CELL_SIZE, JITTER, BLEND_WIDTH, EPOCH_SPACING, EPOCH_RELIEF, surface));

        context.register(RelictVoronoiSources.MARS_UNDERGROUND, new VoronoiSource(UNDERGROUND_CELL_SIZE, JITTER, BLEND_WIDTH, EPOCH_SPACING, 0.0F, underground));
    }

}
