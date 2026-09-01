package us.drullk.relict.datagen.worldgen;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.placement.PlacementUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.RarityFilter;
import us.drullk.relict.init.worldgen.RelictBiomes;
import us.drullk.relict.init.worldgen.RelictConfiguredFeatures;
import us.drullk.relict.init.worldgen.RelictPlacedFeatures;
import us.drullk.relict.init.worldgen.RelictWorldgenTypes;
import us.drullk.relict.worldgen.RockFeatureConfiguration;
import us.drullk.relict.worldgen.RockFeatureConfiguration.PlacementRule;
import us.drullk.relict.worldgen.RockFeatureConfiguration.RockShape;

import java.util.List;

/**
 * {@code relict:rock}'s worldgen baseline: one configured/placed pair per size per placed province,
 * for loose surface rock. Split out of {@link
 * RelictConfiguredFeatureGenerator}/{@link RelictPlacedFeatureGenerator} the same way {@link
 * DustLayerFeatureGenerator} is, for the same reason: both call in with one line each.
 *
 * <p>Density knobs below are first-pass starting values meant to be probed and tuned in-game, not derived
 * bars — every one is a named constant so a later pass has somewhere to land, and public so the reports
 * module's coverage plates ({@code RockCoverageSampler}) render exactly these numbers rather than a
 * second copy that could drift from what actually ships (the same reuse {@code DustLayerFeatureGenerator}
 * already does for its own configs). {@link RockFeatureConfiguration.PlacementRule}-gated placements
 * (everything except the wrinkle_plains S rocks) get a higher raw attempt count than their measured
 * coverage, because most attempts miss the gate; that is by design, not slack — see the placement rule
 * javadoc.
 */
public final class RockFeatureGenerator {

    // Wrinkle Plains S: sparse-even coverage ~1 per 6-10 columns radius-8
    public static final UniformInt PLAINS_S_COUNT = UniformInt.of(0, 3);

    // Wrinkle Plains M: ridge-biased
    public static final UniformInt PLAINS_RIDGE_M_COUNT = UniformInt.of(8, 12);

    // Wrinkle Plains L: ~1 per several chunks
    public static final int PLAINS_EJECTA_L_RARITY = 3;
    private static final float PLAINS_EJECTA_L_TUFF_CHANCE = 0.2F;

    // Rusted Dunes S: 50% chance per chunk of ONE block
    public static final int DUNES_S_RARITY = 2;

    // Fretted Mesas talus: TALUS-gated, same over-attempt logic as the plains ridge bias.
    public static final UniformInt MESAS_TALUS_S_COUNT = UniformInt.of(4, 8);
    public static final UniformInt MESAS_TALUS_M_COUNT = UniformInt.of(2, 4);

    // Fretted Mesas cap: very rare, CAP-gated on top of the rarity roll so caps stay clean-topped.
    public static final int MESAS_CAP_S_RARITY = 12;

    // Fretted Mesas floor: medium-sparse, VALLEY_FLOOR-gated, terracotta/tuff mix. VALLEY_FLOOR's gate
    // passes on most of the tableland (broad flat floors dominate the province), so this needs a much lower
    // raw count than a tightly-gated placement to still read as "medium", not "everywhere" -- round 1 at
    // (2, 4) probed to 78.9% coverage, too dense for the intent; halved.
    public static final UniformInt MESAS_FLOOR_S_COUNT = UniformInt.of(1, 2);
    private static final float MESAS_FLOOR_S_TUFF_CHANCE = 0.35F;

    /**
     * The eight registered configs, public for the same reason the counts above are: {@code
     * RockCoverageSampler} and the gametest pack both render/exercise exactly these objects rather than
     * a second copy that could drift.
     */
    public static final RockFeatureConfiguration WRINKLE_PLAINS_S_CONFIG = new RockFeatureConfiguration(
            RelictBiomes.WRINKLE_PLAINS, RockShape.SINGLE, Blocks.SMOOTH_BASALT, Blocks.BASALT, 0.05F, PlacementRule.ANY);
    public static final RockFeatureConfiguration WRINKLE_PLAINS_RIDGE_M_CONFIG = new RockFeatureConfiguration(
            RelictBiomes.WRINKLE_PLAINS, RockShape.CLAST, Blocks.SMOOTH_BASALT, Blocks.BASALT, 0.05F, PlacementRule.RIDGE_BIAS);
    public static final RockFeatureConfiguration WRINKLE_PLAINS_EJECTA_L_CONFIG = new RockFeatureConfiguration(
            RelictBiomes.WRINKLE_PLAINS, RockShape.BOULDER, Blocks.SMOOTH_BASALT, Blocks.TUFF, PLAINS_EJECTA_L_TUFF_CHANCE, PlacementRule.ANY);

    public static final RockFeatureConfiguration RUSTED_DUNES_S_CONFIG = new RockFeatureConfiguration(
            RelictBiomes.RUSTED_DUNES, RockShape.SINGLE, Blocks.SMOOTH_BASALT, Blocks.ANDESITE, 0.3F, PlacementRule.INTERDUNE_FLOOR);

    public static final RockFeatureConfiguration FRETTED_MESAS_TALUS_S_CONFIG = new RockFeatureConfiguration(
            RelictBiomes.FRETTED_MESAS, RockShape.SINGLE, Blocks.TUFF, Blocks.TUFF, 0.0F, PlacementRule.TALUS);
    public static final RockFeatureConfiguration FRETTED_MESAS_TALUS_M_CONFIG = new RockFeatureConfiguration(
            RelictBiomes.FRETTED_MESAS, RockShape.CLAST, Blocks.TUFF, Blocks.TUFF, 0.0F, PlacementRule.TALUS);
    public static final RockFeatureConfiguration FRETTED_MESAS_CAP_S_CONFIG = new RockFeatureConfiguration(
            RelictBiomes.FRETTED_MESAS, RockShape.SINGLE, Blocks.SMOOTH_BASALT, Blocks.SMOOTH_BASALT, 0.0F, PlacementRule.CAP);
    public static final RockFeatureConfiguration FRETTED_MESAS_FLOOR_S_CONFIG = new RockFeatureConfiguration(
            RelictBiomes.FRETTED_MESAS, RockShape.SINGLE, Blocks.GRANITE, Blocks.TUFF, MESAS_FLOOR_S_TUFF_CHANCE, PlacementRule.VALLEY_FLOOR);

    private RockFeatureGenerator() {
    }

    public static void bootstrapConfiguredFeatures(BootstrapContext<ConfiguredFeature<?, ?>> context) {
        register(context, RelictConfiguredFeatures.ROCK_WRINKLE_PLAINS_S, WRINKLE_PLAINS_S_CONFIG);
        register(context, RelictConfiguredFeatures.ROCK_WRINKLE_PLAINS_RIDGE_M, WRINKLE_PLAINS_RIDGE_M_CONFIG);
        register(context, RelictConfiguredFeatures.ROCK_WRINKLE_PLAINS_EJECTA_L, WRINKLE_PLAINS_EJECTA_L_CONFIG);

        register(context, RelictConfiguredFeatures.ROCK_RUSTED_DUNES_S, RUSTED_DUNES_S_CONFIG);

        register(context, RelictConfiguredFeatures.ROCK_FRETTED_MESAS_TALUS_S, FRETTED_MESAS_TALUS_S_CONFIG);
        register(context, RelictConfiguredFeatures.ROCK_FRETTED_MESAS_TALUS_M, FRETTED_MESAS_TALUS_M_CONFIG);
        register(context, RelictConfiguredFeatures.ROCK_FRETTED_MESAS_CAP_S, FRETTED_MESAS_CAP_S_CONFIG);
        register(context, RelictConfiguredFeatures.ROCK_FRETTED_MESAS_FLOOR_S, FRETTED_MESAS_FLOOR_S_CONFIG);
    }

    public static void bootstrapPlacedFeatures(BootstrapContext<PlacedFeature> context, HolderGetter<ConfiguredFeature<?, ?>> features) {
        register(context, RelictPlacedFeatures.ROCK_WRINKLE_PLAINS_S, features.getOrThrow(RelictConfiguredFeatures.ROCK_WRINKLE_PLAINS_S),
                CountPlacement.of(PLAINS_S_COUNT), InSquarePlacement.spread(), PlacementUtils.HEIGHTMAP, BiomeFilter.biome());
        register(context, RelictPlacedFeatures.ROCK_WRINKLE_PLAINS_RIDGE_M, features.getOrThrow(RelictConfiguredFeatures.ROCK_WRINKLE_PLAINS_RIDGE_M),
                CountPlacement.of(PLAINS_RIDGE_M_COUNT), InSquarePlacement.spread(), PlacementUtils.HEIGHTMAP, BiomeFilter.biome());
        register(context, RelictPlacedFeatures.ROCK_WRINKLE_PLAINS_EJECTA_L, features.getOrThrow(RelictConfiguredFeatures.ROCK_WRINKLE_PLAINS_EJECTA_L),
                RarityFilter.onAverageOnceEvery(PLAINS_EJECTA_L_RARITY), InSquarePlacement.spread(), PlacementUtils.HEIGHTMAP, BiomeFilter.biome());

        register(context, RelictPlacedFeatures.ROCK_RUSTED_DUNES_S, features.getOrThrow(RelictConfiguredFeatures.ROCK_RUSTED_DUNES_S),
                RarityFilter.onAverageOnceEvery(DUNES_S_RARITY), InSquarePlacement.spread(), PlacementUtils.HEIGHTMAP, BiomeFilter.biome());

        register(context, RelictPlacedFeatures.ROCK_FRETTED_MESAS_TALUS_S, features.getOrThrow(RelictConfiguredFeatures.ROCK_FRETTED_MESAS_TALUS_S),
                CountPlacement.of(MESAS_TALUS_S_COUNT), InSquarePlacement.spread(), PlacementUtils.HEIGHTMAP, BiomeFilter.biome());
        register(context, RelictPlacedFeatures.ROCK_FRETTED_MESAS_TALUS_M, features.getOrThrow(RelictConfiguredFeatures.ROCK_FRETTED_MESAS_TALUS_M),
                CountPlacement.of(MESAS_TALUS_M_COUNT), InSquarePlacement.spread(), PlacementUtils.HEIGHTMAP, BiomeFilter.biome());
        register(context, RelictPlacedFeatures.ROCK_FRETTED_MESAS_CAP_S, features.getOrThrow(RelictConfiguredFeatures.ROCK_FRETTED_MESAS_CAP_S),
                RarityFilter.onAverageOnceEvery(MESAS_CAP_S_RARITY), InSquarePlacement.spread(), PlacementUtils.HEIGHTMAP, BiomeFilter.biome());
        register(context, RelictPlacedFeatures.ROCK_FRETTED_MESAS_FLOOR_S, features.getOrThrow(RelictConfiguredFeatures.ROCK_FRETTED_MESAS_FLOOR_S),
                CountPlacement.of(MESAS_FLOOR_S_COUNT), InSquarePlacement.spread(), PlacementUtils.HEIGHTMAP, BiomeFilter.biome());
    }

    private static void register(BootstrapContext<ConfiguredFeature<?, ?>> context, ResourceKey<ConfiguredFeature<?, ?>> key, RockFeatureConfiguration config) {
        context.register(key, new ConfiguredFeature<>(RelictWorldgenTypes.ROCK_FEATURE.get(), config));
    }

    private static void register(BootstrapContext<PlacedFeature> context, ResourceKey<PlacedFeature> key, Holder<ConfiguredFeature<?, ?>> feature, PlacementModifier... modifiers) {
        context.register(key, new PlacedFeature(feature, List.of(modifiers)));
    }

}
