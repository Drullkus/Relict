package us.drullk.relict.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PotentSulfurBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.PotentSulfurState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.CompositeFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.EnvironmentScanPlacement;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.RandomOffsetPlacement;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import us.drullk.relict.Relict;
import us.drullk.relict.block.AbstractRelictLayerBlock;
import us.drullk.relict.init.RelictBlocks;
import us.drullk.relict.init.worldgen.RelictConfiguredFeatures;
import us.drullk.relict.init.worldgen.RelictPlacedFeatures;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cave content pass gametest pack: the sulfur deep lake's height band and its 3x interior Potent Sulfur
 * density over the ambient rate, plus the ice-cave floor dusting's floor-only/no-floating behavior.
 * Registered through {@link RelictGameTests} the same way {@code BasaltSandGameTests} is.
 *
 * <p>The density and floor tests reach past the registered {@code PlacedFeature}'s own
 * {@code CountPlacement}/{@code InSquarePlacement}/height-band/{@code BiomeFilter} wrapper — those pick
 * where in a real chunk the feature scatters, not what it does once it lands, and a gametest world has no
 * {@code sulfur_caves}/{@code ice_caves} biome for {@code BiomeFilter} to match. Both tests instead invoke
 * the registered {@link ConfiguredFeature} (block selection) chained onto the same scan/offset placement
 * modifiers production uses (floor-finding), so the assertions exercise the real shipped logic at a
 * controlled position.
 */
public final class CaveContentGameTests {

    private static final long SEED = 0xCA5EL;

    // Mirrors RelictPlacedFeatureGenerator.SULFUR_GEYSER_ATTEMPTS (ambient Potent Sulfur, 2..5) — that field
    // is package-private to the data source set's worldgen package and this gametest lives in a different
    // source set, so the reference value is restated here rather than shared.
    private static final UniformInt AMBIENT_POTENT_SULFUR_ATTEMPTS = UniformInt.of(2, 5);
    private static final UniformInt LAKE_INTERIOR_SPREAD = UniformInt.of(-6, 6);
    private static final int LAKE_FLOOR_SCAN_DEPTH = 4;
    private static final int DENSITY_TRIALS = 150;
    private static final double EXPECTED_RATIO = 3.0;
    private static final double RATIO_TOLERANCE_LOW = 1.8;
    private static final double RATIO_TOLERANCE_HIGH = 4.5;

    private static final int FROST_FLOOR_SCAN_DEPTH = 12;
    private static final int FROST_FLOOR_TRIALS = 100;
    private static final int HEIGHT_BAND_SAMPLES = 4000;

    private CaveContentGameTests() {
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        Identifier empty = Identifier.withDefaultNamespace("empty");

        event.registerTest(Relict.id("sulfur_deep_lake_height_band"), new RelictFunctionGameTestInstance(
                CaveContentGameTests::heightBand, Component.literal("Sulfur deep lake: height_range is -32..0"),
                new TestData<>(environment, empty, 20, 0, true)));
        event.registerTest(Relict.id("sulfur_deep_lake_interior_density"), new RelictFunctionGameTestInstance(
                CaveContentGameTests::interiorDensity, Component.literal("Sulfur deep lake: interior Potent Sulfur is ~3x ambient"),
                new TestData<>(environment, empty, 200, 0, true)));
        event.registerTest(Relict.id("ice_cave_snow_floor_only_no_floating"), new RelictFunctionGameTestInstance(
                CaveContentGameTests::snowFloorOnlyNoFloating, Component.literal("Ice cave floor dusting: floor only, layers 1-3, no floating"),
                new TestData<>(environment, empty, 200, 0, true)));
    }

    /**
     * The registered placed feature's own {@code HeightRangePlacement} entry, sampled directly: every
     * returned Y must fall inside [-32, 0], and enough samples must be taken that the observed range
     * actually approaches both ends (rules out a degenerate provider that always returns one height).
     */
    private static void heightBand(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Holder<PlacedFeature> deepLake = level.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE).getOrThrow(RelictPlacedFeatures.SULFUR_DEEP_LAKE);

        PlacementModifier heightRange = deepLake.value().placement().stream()
                .filter(HeightRangePlacement.class::isInstance)
                .findFirst()
                .orElse(null);
        helper.assertTrue(heightRange != null, "sulfur_deep_lake has no HeightRangePlacement modifier");

        RandomSource random = RandomSource.create(SEED);
        BlockPos origin = helper.absolutePos(new BlockPos(8, 5, 8));
        PlacementContext context = new PlacementContext(level, level.getChunkSource().getGenerator(), java.util.Optional.empty());
        int[] minMax = {Integer.MAX_VALUE, Integer.MIN_VALUE};

        for (int i = 0; i < HEIGHT_BAND_SAMPLES; i++) {
            heightRange.getPositions(context, random, origin).forEach(pos -> {
                helper.assertTrue(pos.getY() <= 0 && pos.getY() >= -32, "height_range produced y=" + pos.getY() + " outside -32..0");
                minMax[0] = Math.min(minMax[0], pos.getY());
                minMax[1] = Math.max(minMax[1], pos.getY());
            });
        }

        System.out.printf("=== sulfur_deep_lake height band probe === samples=%d observed_range=[%d, %d]%n", HEIGHT_BAND_SAMPLES, minMax[0], minMax[1]);
        helper.assertTrue(minMax[0] <= -28 && minMax[1] >= -4, "band sampled as [" + minMax[0] + ", " + minMax[1] + "], expected to approach both ends of -32..0");
        helper.succeed();
    }

    /**
     * Rebuilds a flat sulfur-floor/water-surface interior DENSITY_TRIALS times, running the registered
     * SULFUR_DEEP_LAKE configured feature's interior Potent Sulfur sub-feature (index 1 of its sequence)
     * against a hand-built reference that uses the ambient rate instead. Both share the exact same
     * scan/offset placement, so the only variable is the attempt count — the resulting block-count ratio is
     * the real, measured effect of the 3x multiplier, not just the configured numbers restated.
     */
    private static void interiorDensity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomSource random = RandomSource.create(SEED);

        BlockPos originRel = new BlockPos(8, 5, 8);
        BlockPos origin = helper.absolutePos(originRel);

        Holder<ConfiguredFeature<?, ?>> deepLakeHolder = level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE).getOrThrow(RelictConfiguredFeatures.SULFUR_DEEP_LAKE);
        CompositeFeatureConfiguration deepLakeConfig = (CompositeFeatureConfiguration) deepLakeHolder.value().config();
        helper.assertTrue(deepLakeConfig.features().size() == 2, "sulfur_deep_lake sequence no longer has exactly 2 entries (lake, interior scatter)");
        PlacedFeature interiorScatter = deepLakeConfig.features().get(1).value();

        PlacedFeature ambientScatter = ambientReferenceScatter();

        long interiorTotal = 0;
        long ambientTotal = 0;

        for (int trial = 0; trial < DENSITY_TRIALS; trial++) {
            buildLakeInterior(helper, originRel);
            interiorScatter.place(level, generator, random, origin);
            interiorTotal += countPotentSulfurWet(helper, originRel);

            buildLakeInterior(helper, originRel);
            ambientScatter.place(level, generator, random, origin);
            ambientTotal += countPotentSulfurWet(helper, originRel);
        }

        helper.assertTrue(ambientTotal > 0, "ambient reference placed zero Potent Sulfur blocks over " + DENSITY_TRIALS + " trials — scan/floor setup is broken");

        double ratio = (double) interiorTotal / (double) ambientTotal;
        System.out.printf("=== sulfur_deep_lake interior density probe === trials=%d interior_blocks=%d (mean %.3f) ambient_blocks=%d (mean %.3f) ratio=%.3f%n",
                DENSITY_TRIALS, interiorTotal, interiorTotal / (double) DENSITY_TRIALS, ambientTotal, ambientTotal / (double) DENSITY_TRIALS, ratio);
        helper.assertTrue(ratio >= RATIO_TOLERANCE_LOW && ratio <= RATIO_TOLERANCE_HIGH,
                "interior/ambient Potent Sulfur ratio was " + ratio + " (interior=" + interiorTotal + ", ambient=" + ambientTotal
                        + "), expected close to " + EXPECTED_RATIO + " within [" + RATIO_TOLERANCE_LOW + ", " + RATIO_TOLERANCE_HIGH + "]");
        helper.succeed();
    }

    private static PlacedFeature ambientReferenceScatter() {
        Holder<ConfiguredFeature<?, ?>> block = Holder.direct(new ConfiguredFeature<>(Feature.SIMPLE_BLOCK, new SimpleBlockConfiguration(
                BlockStateProvider.simple(Blocks.POTENT_SULFUR.defaultBlockState().setValue(PotentSulfurBlock.STATE, PotentSulfurState.WET)))));
        return new PlacedFeature(block, List.of(
                CountPlacement.of(AMBIENT_POTENT_SULFUR_ATTEMPTS),
                RandomOffsetPlacement.of(LAKE_INTERIOR_SPREAD, ConstantInt.of(0)),
                EnvironmentScanPlacement.scanningFor(Direction.DOWN, BlockPredicate.allOf(BlockPredicate.solid(), BlockPredicate.matchesFluids(Direction.UP.getUnitVec3i(), Fluids.WATER)), LAKE_FLOOR_SCAN_DEPTH)));
    }

    /** 13x13 sulfur floor, one water layer above it, air above that — origin sits in the air just over the water. */
    private static void buildLakeInterior(GameTestHelper helper, BlockPos originRel) {
        BlockState water = Blocks.WATER.defaultBlockState();
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                BlockPos floor = originRel.offset(dx, -1, dz);
                helper.setBlock(floor, Blocks.SULFUR);
                helper.setBlock(floor.above(), water);
                helper.setBlock(floor.above(2), Blocks.AIR);
            }
        }
    }

    private static int countPotentSulfurWet(GameTestHelper helper, BlockPos originRel) {
        int count = 0;
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                BlockPos floor = originRel.offset(dx, -1, dz);
                if (helper.getBlockState(floor).is(Blocks.POTENT_SULFUR)) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * A floor and a ceiling three blocks apart; the registered FROST_FLOOR configured feature (block
     * selection) is chained onto the exact scan+offset placement modifiers production uses (Direction.DOWN,
     * max_steps 12, +1 vertical offset) so the down-only scan can never reach the ceiling. Runs
     * FROST_FLOOR_TRIALS times to also sample the 1/2/3-layer weighting.
     */
    private static void snowFloorOnlyNoFloating(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomSource random = RandomSource.create(SEED);

        BlockPos originRel = new BlockPos(8, 7, 8);
        BlockPos floorRel = originRel.offset(0, -2, 0);
        BlockPos ceilingRel = originRel.offset(0, 3, 0);
        BlockPos landingRel = floorRel.above();

        helper.setBlock(floorRel, Blocks.SMOOTH_BASALT);
        helper.setBlock(ceilingRel, Blocks.SMOOTH_BASALT);
        for (int dy = -1; dy <= 2; dy++) {
            helper.setBlock(originRel.offset(0, dy, 0), dy == -2 ? Blocks.SMOOTH_BASALT : Blocks.AIR);
        }

        Holder<ConfiguredFeature<?, ?>> frostFloorHolder = level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE).getOrThrow(RelictConfiguredFeatures.FROST_FLOOR);
        PlacedFeature scanningFrostFloor = new PlacedFeature(frostFloorHolder, List.of(
                EnvironmentScanPlacement.scanningFor(Direction.DOWN, BlockPredicate.solid(), BlockPredicate.ONLY_IN_AIR_PREDICATE, FROST_FLOOR_SCAN_DEPTH),
                RandomOffsetPlacement.vertical(ConstantInt.of(1))));

        BlockPos origin = helper.absolutePos(originRel);
        Map<Integer, Integer> layerCounts = new HashMap<>();
        int landedOnFloor = 0;

        for (int trial = 0; trial < FROST_FLOOR_TRIALS; trial++) {
            helper.setBlock(landingRel, Blocks.AIR);
            scanningFrostFloor.place(level, generator, random, origin);

            BlockState landing = helper.getBlockState(landingRel);
            if (landing.is(RelictBlocks.DRY_SNOW_LAYER.get())) {
                landedOnFloor++;
                int layers = landing.getValue(AbstractRelictLayerBlock.LAYERS);
                helper.assertTrue(layers >= 1 && layers <= 3, "frost_floor placed " + layers + " layers, expected 1-3");
                layerCounts.merge(layers, 1, Integer::sum);
                helper.assertTrue(helper.getBlockState(floorRel).isSolidRender(), "landing floor is missing under a placed layer");
            }
            helper.assertTrue(!helper.getBlockState(ceilingRel.below()).is(RelictBlocks.DRY_SNOW_LAYER.get()), "a snow layer reached the ceiling side");
        }

        System.out.printf("=== frost_floor floor/layer probe === trials=%d landed_on_floor=%d layers1=%d layers2=%d layers3=%d%n",
                FROST_FLOOR_TRIALS, landedOnFloor, layerCounts.getOrDefault(1, 0), layerCounts.getOrDefault(2, 0), layerCounts.getOrDefault(3, 0));
        helper.assertTrue(landedOnFloor > FROST_FLOOR_TRIALS / 2, "frost_floor landed on the prepared floor only " + landedOnFloor + "/" + FROST_FLOOR_TRIALS + " times");
        helper.assertTrue(layerCounts.getOrDefault(1, 0) >= layerCounts.getOrDefault(3, 0), "layers=1 was not at least as common as layers=3 — weighting looks inverted");

        // No-floating law, checked independently of the feature: an unsupported layer must self-remove.
        helper.setBlock(landingRel, RelictBlocks.DRY_SNOW_LAYER.get().defaultBlockState());
        helper.setBlock(floorRel, Blocks.AIR);
        helper.assertTrue(helper.getBlockState(landingRel).isAir(), "a dry_snow_layer block survived with its support removed (detached solid)");

        helper.succeed();
    }

}
