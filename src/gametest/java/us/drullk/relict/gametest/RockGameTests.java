package us.drullk.relict.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import us.drullk.relict.Relict;
import us.drullk.relict.init.worldgen.RelictWorldgenTypes;
import us.drullk.relict.worldgen.DuneCrest;
import us.drullk.relict.worldgen.RockFeature;
import us.drullk.relict.worldgen.RockFeatureConfiguration;
import us.drullk.relict.worldgen.RockFeatureConfiguration.PlacementRule;
import us.drullk.relict.worldgen.RockFeatureConfiguration.RockShape;

import java.util.Optional;

/**
 * {@code relict:rock}'s own test pack: registered through {@link RelictGameTests} the same one-line
 * way {@link BasaltSandGameTests} is.
 *
 * <p>Rather than one near-duplicate test per registered placement (all eight share the same two Java
 * mechanisms), this covers the two axes those eight placements actually vary along: which of the three
 * {@link RockShape}s gets built (the "placed on surface, never floating" law), and which {@link
 * PlacementRule} gates it (the biome-specific bias). Every registered placement is exercised by exactly one
 * shape test and one gate test:
 * <ul>
 *   <li>SINGLE shape -- wrinkle_plains S, rusted_dunes S, fretted_mesas talus S / cap S / floor S
 *   <li>CLAST shape -- wrinkle_plains ridge M, fretted_mesas talus M
 *   <li>BOULDER shape -- wrinkle_plains ejecta L
 *   <li>INTERDUNE_FLOOR gate -- rusted_dunes S
 *   <li>RIDGE_BIAS / TALUS gate (one predicate, two names) -- wrinkle_plains ridge M, fretted_mesas talus S+M
 *   <li>CAP gate -- fretted_mesas cap S
 *   <li>VALLEY_FLOOR gate -- fretted_mesas floor S
 * </ul>
 *
 * <p>Shape tests build real terrain in the shared {@code empty} structure's platform and run {@link
 * RockFeature#place} directly against the live {@link ServerLevel}, reading the test world's own biome
 * back so the feature's biome-membership check has something real to agree with (this pack asserts nothing
 * about biome identity, only about the feature's placement mechanics). Gate tests call {@link
 * RockFeature#passesPlacementRule} directly against synthetic height profiles -- an isotropic dome
 * reads as both "a crest" and "high relief" for any wind axis, a flat profile as neither, which is enough to
 * probe every rule without needing a live world at all.
 */
public final class RockGameTests {

    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");

    private static final RandomSource SEEDED_RANDOM = RandomSource.create(0x1F02_0A19L);

    private RockGameTests() {
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        test(event, environment, "rock_single_on_surface", "Rock: SINGLE places on surface, never floating", RockGameTests::singlePlacesOnSurface);
        test(event, environment, "rock_clast_on_surface", "Rock: CLAST places on surface, never floating", RockGameTests::clastPlacesOnSurface);
        test(event, environment, "rock_boulder_on_surface", "Rock: BOULDER places on surface, never floating", RockGameTests::boulderPlacesOnSurface);
        test(event, environment, "rock_footprint_skips_overstep", "Rock: a footprint column past the step tolerance is skipped, not overhung", RockGameTests::footprintSkipsOverstep);

        test(event, environment, "rock_interdune_floor_gate", "Rock: INTERDUNE_FLOOR excludes crests and rewards flat ground", RockGameTests::interduneFloorGate);
        test(event, environment, "rock_ridge_and_talus_gate", "Rock: RIDGE_BIAS/TALUS reward high local relief", RockGameTests::ridgeAndTalusGate);
        test(event, environment, "rock_cap_gate", "Rock: CAP requires flat ground at or above the height split", RockGameTests::capGate);
        test(event, environment, "rock_valley_floor_gate", "Rock: VALLEY_FLOOR requires flat ground below the height split", RockGameTests::valleyFloorGate);
    }

    private static void test(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment, String id, String description, java.util.function.Consumer<GameTestHelper> function) {
        event.registerTest(Relict.id(id), new RelictFunctionGameTestInstance(
                function, Component.literal(description), new TestData<>(environment, EMPTY_STRUCTURE, 20, 0, true)));
    }

    // -------------------------------------------------------------------------------------------- shape mechanics

    private static void singlePlacesOnSurface(GameTestHelper helper) {
        buildFlatPlatform(helper, 5, 0);
        RockFeatureConfiguration config = configWithLiveBiome(helper, RockShape.SINGLE, Blocks.SMOOTH_BASALT, Blocks.SMOOTH_BASALT, 0.0F, PlacementRule.ANY);

        boolean placed = place(helper, config, new BlockPos(2, 1, 2));
        helper.assertTrue(placed, "SINGLE placement reported failure over open flat ground");
        assertNoFloatingRocks(helper, new BlockPos(0, 0, 0), new BlockPos(4, 4, 4), config);
        helper.succeed();
    }

    private static void clastPlacesOnSurface(GameTestHelper helper) {
        buildFlatPlatform(helper, 6, 0);
        RockFeatureConfiguration config = configWithLiveBiome(helper, RockShape.CLAST, Blocks.SMOOTH_BASALT, Blocks.SMOOTH_BASALT, 0.0F, PlacementRule.ANY);

        boolean placed = placeUntilSuccess(helper, config, new BlockPos(2, 1, 2));
        helper.assertTrue(placed, "CLAST placement never succeeded over open flat ground across several rolls");
        assertNoFloatingRocks(helper, new BlockPos(0, 0, 0), new BlockPos(5, 4, 5), config);
        helper.succeed();
    }

    private static void boulderPlacesOnSurface(GameTestHelper helper) {
        buildFlatPlatform(helper, 7, 0);
        RockFeatureConfiguration config = configWithLiveBiome(helper, RockShape.BOULDER, Blocks.SMOOTH_BASALT, Blocks.TUFF, 0.2F, PlacementRule.ANY);

        boolean placed = place(helper, config, new BlockPos(3, 1, 3));
        helper.assertTrue(placed, "BOULDER placement reported failure over open flat ground");
        assertNoFloatingRocks(helper, new BlockPos(0, 0, 0), new BlockPos(6, 4, 6), config);
        helper.succeed();
    }

    /**
     * A cliff runs through the platform: one side sits {@code STEP} blocks lower than the other, well past
     * {@code FOOTPRINT_MAX_STEP}. A BOULDER placed on the high side must still pass "never floating" -- the
     * footprint columns that would land on the low side get skipped, not built as an overhang.
     */
    private static void footprintSkipsOverstep(GameTestHelper helper) {
        int cliffAtX = 3;
        buildSteppedPlatform(helper, 7, cliffAtX, 0, 5);
        RockFeatureConfiguration config = configWithLiveBiome(helper, RockShape.BOULDER, Blocks.SMOOTH_BASALT, Blocks.SMOOTH_BASALT, 0.0F, PlacementRule.ANY);

        boolean placed = place(helper, config, new BlockPos(4, 6, 3));
        helper.assertTrue(placed, "BOULDER placement reported failure straddling a cliff edge");
        assertNoFloatingRocks(helper, new BlockPos(0, 0, 0), new BlockPos(6, 8, 6), config);
        helper.succeed();
    }

    // -------------------------------------------------------------------------------------------- placement rule gates

    private static void interduneFloorGate(GameTestHelper helper) {
        helper.assertTrue(RockFeature.passesPlacementRule(PlacementRule.INTERDUNE_FLOOR, flat(100)),
                "INTERDUNE_FLOOR should pass on flat, non-crest ground");
        helper.assertTrue(!RockFeature.passesPlacementRule(PlacementRule.INTERDUNE_FLOOR, dome(100)),
                "INTERDUNE_FLOOR should fail on a crest");
        helper.succeed();
    }

    private static void ridgeAndTalusGate(GameTestHelper helper) {
        helper.assertTrue(!RockFeature.passesPlacementRule(PlacementRule.RIDGE_BIAS, flat(100)),
                "RIDGE_BIAS should fail on flat ground");
        helper.assertTrue(RockFeature.passesPlacementRule(PlacementRule.RIDGE_BIAS, dome(100)),
                "RIDGE_BIAS should pass on steep local relief");
        helper.assertTrue(!RockFeature.passesPlacementRule(PlacementRule.TALUS, flat(100)),
                "TALUS should fail on flat ground");
        helper.assertTrue(RockFeature.passesPlacementRule(PlacementRule.TALUS, dome(100)),
                "TALUS should pass on steep local relief");
        helper.succeed();
    }

    private static void capGate(GameTestHelper helper) {
        helper.assertTrue(RockFeature.passesPlacementRule(PlacementRule.CAP, flat(300)),
                "CAP should pass on flat ground at a high elevation");
        helper.assertTrue(!RockFeature.passesPlacementRule(PlacementRule.CAP, flat(10)),
                "CAP should fail on flat ground at a low elevation");
        helper.assertTrue(!RockFeature.passesPlacementRule(PlacementRule.CAP, dome(300)),
                "CAP should fail on steep ground even at a high elevation");
        helper.succeed();
    }

    private static void valleyFloorGate(GameTestHelper helper) {
        helper.assertTrue(RockFeature.passesPlacementRule(PlacementRule.VALLEY_FLOOR, flat(10)),
                "VALLEY_FLOOR should pass on flat ground at a low elevation");
        helper.assertTrue(!RockFeature.passesPlacementRule(PlacementRule.VALLEY_FLOOR, flat(300)),
                "VALLEY_FLOOR should fail on flat ground at a high elevation");
        helper.succeed();
    }

    /** Constant height in every direction: no crest, no relief. */
    private static DuneCrest.RelativeHeight flat(int base) {
        return (offsetX, offsetZ) -> base;
    }

    /** An isotropic dome peaking at the origin: reads as a crest and as high relief along any axis, so this needs no knowledge of the real wind azimuth. */
    private static DuneCrest.RelativeHeight dome(int base) {
        return (offsetX, offsetZ) -> base - (offsetX * offsetX + offsetZ * offsetZ);
    }

    // -------------------------------------------------------------------------------------------- terrain + invocation helpers

    private static void buildFlatPlatform(GameTestHelper helper, int size, int topY) {
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                for (int y = 0; y <= topY; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
    }

    private static void buildSteppedPlatform(GameTestHelper helper, int size, int cliffAtX, int lowTopY, int highTopY) {
        for (int x = 0; x < size; x++) {
            int topY = x < cliffAtX ? lowTopY : highTopY;
            for (int z = 0; z < size; z++) {
                for (int y = 0; y <= topY; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
    }

    private static boolean place(GameTestHelper helper, RockFeatureConfiguration config, BlockPos relativeOrigin) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(relativeOrigin);
        FeaturePlaceContext<RockFeatureConfiguration> context = new FeaturePlaceContext<>(
                Optional.empty(), level, level.getChunkSource().getGenerator(), SEEDED_RANDOM, origin, config);
        return RelictWorldgenTypes.ROCK_FEATURE.get().place(context);
    }

    /** CLAST's per-cell roll can occasionally miss every slot; retry a few times rather than flake on that. */
    private static boolean placeUntilSuccess(GameTestHelper helper, RockFeatureConfiguration config, BlockPos relativeOrigin) {
        for (int attempt = 0; attempt < 8; attempt++) {
            if (place(helper, config, relativeOrigin)) {
                return true;
            }
        }
        return false;
    }

    /** Every solid block of the placement's own material must have non-air directly below it. */
    private static void assertNoFloatingRocks(GameTestHelper helper, BlockPos min, BlockPos max, RockFeatureConfiguration config) {
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = helper.getBlockState(pos);
                    if (state.is(config.primaryBlock()) || state.is(config.secondaryBlock())) {
                        BlockState below = helper.getBlockState(pos.below());
                        helper.assertTrue(!below.isAir(), "a placed rock at relative " + pos + " is floating (air below)");
                    }
                }
            }
        }
    }

    /** Reads the test world's own biome back so the feature's biome-membership check has a real key to agree with. */
    private static RockFeatureConfiguration configWithLiveBiome(GameTestHelper helper, RockShape shape,
                                                                net.minecraft.world.level.block.Block primary, net.minecraft.world.level.block.Block secondary, float secondaryChance, PlacementRule rule) {
        BlockPos probe = helper.absolutePos(BlockPos.ZERO);
        Holder<Biome> biome = helper.getLevel().getBiome(probe);
        ResourceKey<Biome> province = biome.unwrapKey().orElseThrow(() -> new IllegalStateException("Rock gametest: test world's biome has no resource key"));
        return new RockFeatureConfiguration(province, shape, primary, secondary, secondaryChance, rule);
    }

}
