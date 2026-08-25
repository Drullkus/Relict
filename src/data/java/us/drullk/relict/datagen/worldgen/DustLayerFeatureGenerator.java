package us.drullk.relict.datagen.worldgen;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import us.drullk.relict.init.worldgen.RelictBiomes;
import us.drullk.relict.init.worldgen.RelictConfiguredFeatures;
import us.drullk.relict.init.worldgen.RelictPlacedFeatures;
import us.drullk.relict.init.worldgen.RelictWorldgenTypes;
import us.drullk.relict.worldgen.DustLayerFeatureConfiguration;

import java.util.List;

/**
 * {@code relict:dust_layer}'s worldgen baseline, one configured/placed pair per province. Split out of
 * {@link RelictConfiguredFeatureGenerator}/{@link RelictPlacedFeatureGenerator} into its own single-purpose
 * class — both call in with one line each — so it doesn't collide with unrelated concurrent work refactoring
 * the rest of datagen into per-feature classes.
 *
 * <p>Numbers below are a first-pass baseline (patchy plains, mostly-clean dune crests, mesa dust-catches),
 * roughly right and meant to be tuned in-game — every one is a named constant so a later pass has somewhere
 * to land.
 */
public final class DustLayerFeatureGenerator {

    private DustLayerFeatureGenerator() {
    }

    // Wrinkle Plains: "thin, patchy (0-1 layers, mask-gated)". Cell size 28 (round 1 was 6 — eyes-on plate
    // read as fine speckle noise at that size, not legible patches; widened until it read as patches).
    private static final int PLAINS_MIN_LAYERS = 0;
    private static final int PLAINS_MAX_LAYERS = 1;
    private static final double PLAINS_COVERAGE_CHANCE = 0.15;
    private static final int PLAINS_PATCH_CELL_SIZE = 28;

    // Rusted Dunes: "body mostly clean; 0-1 on crests at most" — coverage is high but require_dune_crest
    // confines every hit to DuneCrest.isCrest, so the dune body itself never gets a roll at all. Cell size
    // widened from round 1's 4 for the same reason as the plains.
    private static final int DUNES_MIN_LAYERS = 0;
    private static final int DUNES_MAX_LAYERS = 1;
    private static final double DUNES_CREST_COVERAGE_CHANCE = 0.5;
    private static final int DUNES_PATCH_CELL_SIZE = 16;

    // Fretted Mesas: "the dust-catch story lives here: 1-3 layers where dust collects" — reads as
    // catch-basin-sized patches, not speckle, so the widest cell of the three. Coverage chance trimmed from
    // round 1's 0.22 alongside the widening so the wider cells don't inflate total area covered.
    private static final int MESAS_MIN_LAYERS = 1;
    private static final int MESAS_MAX_LAYERS = 3;
    private static final double MESAS_COVERAGE_CHANCE = 0.18;
    private static final int MESAS_PATCH_CELL_SIZE = 48;

    // Distinct per-province salts so the three coverage masks don't share a phase.
    private static final long PLAINS_SALT = 0x0AC1_D057_5A17L;
    private static final long DUNES_SALT = 0x0AC2_D057_5A17L;
    private static final long MESAS_SALT = 0x0AC3_D057_5A17L;

    /**
     * Public so the reports source set's coverage plates ({@code DustLayerCoverageSampler}) render exactly
     * these configs rather than a second copy of the numbers — {@code reports} carries a compile dependency
     * on {@code data} for exactly this kind of reuse (per {@code Relict/CLAUDE.md}'s source-set rule).
     */
    public static final DustLayerFeatureConfiguration WRINKLE_PLAINS_CONFIG = new DustLayerFeatureConfiguration(
            RelictBiomes.WRINKLE_PLAINS, PLAINS_MIN_LAYERS, PLAINS_MAX_LAYERS, PLAINS_COVERAGE_CHANCE, PLAINS_PATCH_CELL_SIZE, false, PLAINS_SALT);
    public static final DustLayerFeatureConfiguration RUSTED_DUNES_CONFIG = new DustLayerFeatureConfiguration(
            RelictBiomes.RUSTED_DUNES, DUNES_MIN_LAYERS, DUNES_MAX_LAYERS, DUNES_CREST_COVERAGE_CHANCE, DUNES_PATCH_CELL_SIZE, true, DUNES_SALT);
    public static final DustLayerFeatureConfiguration FRETTED_MESAS_CONFIG = new DustLayerFeatureConfiguration(
            RelictBiomes.FRETTED_MESAS, MESAS_MIN_LAYERS, MESAS_MAX_LAYERS, MESAS_COVERAGE_CHANCE, MESAS_PATCH_CELL_SIZE, false, MESAS_SALT);

    public static void bootstrapConfiguredFeatures(BootstrapContext<ConfiguredFeature<?, ?>> context) {
        register(context, RelictConfiguredFeatures.DUST_LAYER_WRINKLE_PLAINS, WRINKLE_PLAINS_CONFIG);
        register(context, RelictConfiguredFeatures.DUST_LAYER_RUSTED_DUNES, RUSTED_DUNES_CONFIG);
        register(context, RelictConfiguredFeatures.DUST_LAYER_FRETTED_MESAS, FRETTED_MESAS_CONFIG);
    }

    public static void bootstrapPlacedFeatures(BootstrapContext<PlacedFeature> context, HolderGetter<ConfiguredFeature<?, ?>> features) {
        // FREEZE_TOP_LAYER-style column walk, plus BiomeFilter.biome() (skips the whole chunk if its
        // representative biome sample doesn't even match — the feature's own per-column province check,
        // added after the datapack validator flagged this placement's first draft, covers the rest).
        register(context, RelictPlacedFeatures.DUST_LAYER_WRINKLE_PLAINS, features.getOrThrow(RelictConfiguredFeatures.DUST_LAYER_WRINKLE_PLAINS));
        register(context, RelictPlacedFeatures.DUST_LAYER_RUSTED_DUNES, features.getOrThrow(RelictConfiguredFeatures.DUST_LAYER_RUSTED_DUNES));
        register(context, RelictPlacedFeatures.DUST_LAYER_FRETTED_MESAS, features.getOrThrow(RelictConfiguredFeatures.DUST_LAYER_FRETTED_MESAS));
    }

    private static void register(BootstrapContext<ConfiguredFeature<?, ?>> context, ResourceKey<ConfiguredFeature<?, ?>> key, DustLayerFeatureConfiguration config) {
        context.register(key, new ConfiguredFeature<>(RelictWorldgenTypes.DUST_LAYER_FEATURE.get(), config));
    }

    private static void register(BootstrapContext<PlacedFeature> context, ResourceKey<PlacedFeature> key, Holder<ConfiguredFeature<?, ?>> feature) {
        context.register(key, new PlacedFeature(feature, List.of(BiomeFilter.biome())));
    }

}
