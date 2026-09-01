package us.drullk.relict.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import us.drullk.relict.Relict;
import us.drullk.relict.block.RelictPortalBlock;
import us.drullk.relict.block.RelictPortalForcer;
import us.drullk.relict.block.RelictPortalNetwork;
import us.drullk.relict.init.RelictBlocks;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Mars Portal's own gametest pack: stand-delay gating, generated-frame shape, and frame-break collapse.
 * Particle color and model/selection-box geometry are visual and are not exercised here.
 */
public final class MarsPortalGameTests {

    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");

    private MarsPortalGameTests() {
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        test(event, environment, "mars_portal_survival_delay_is_gated", "Mars Portal: survival stand delay matches the vanilla gamerule", MarsPortalGameTests::survivalDelayIsGated);
        test(event, environment, "mars_portal_creative_delay_is_gated", "Mars Portal: creative stand delay matches the vanilla gamerule", MarsPortalGameTests::creativeDelayIsGated);
        test(event, environment, "mars_portal_generated_frame_is_rectangular", "Mars Portal: generated destination frame is a proper rectangle", MarsPortalGameTests::generatedFrameIsRectangular);
        test(event, environment, "mars_portal_breaking_frame_collapses_portal", "Mars Portal: breaking a frame block collapses the whole portal", MarsPortalGameTests::breakingFrameCollapsesPortal);
        test(event, environment, "mars_portal_stale_cache_entry_is_pruned", "Mars Portal: a destroyed cached portal is pruned and skipped on lookup", MarsPortalGameTests::staleCacheEntryIsPrunedOnLookup);
    }

    private static void test(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment, String id, String description, Consumer<GameTestHelper> function) {
        event.registerTest(Relict.id(id), new RelictFunctionGameTestInstance(function, Component.literal(description),
                new TestData<>(environment, EMPTY_STRUCTURE, 20, 0, true)));
    }

    /**
     * {@code getPortalTransitionTime} is the gate against instant teleport: the interface default is 0
     * (immediate), so a survival player must read a positive delay equal to the same gamerule vanilla
     * Nether portals use.
     */
    private static void survivalDelayIsGated(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RelictPortalBlock portal = RelictBlocks.MARS_PORTAL.get();
        Player survivalPlayer = helper.makeMockServerPlayer(GameType.SURVIVAL);

        int delay = portal.getPortalTransitionTime(level, survivalPlayer);

        helper.assertTrue(delay > 0, "a survival player must not teleport instantly");
        helper.assertValueEqual(delay, level.getGameRules().get(GameRules.PLAYERS_NETHER_PORTAL_DEFAULT_DELAY),
                "survival delay should match players_nether_portal_default_delay");
        helper.succeed();
    }

    private static void creativeDelayIsGated(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RelictPortalBlock portal = RelictBlocks.MARS_PORTAL.get();
        Player creativePlayer = helper.makeMockServerPlayer(GameType.CREATIVE);
        Player survivalPlayer = helper.makeMockServerPlayer(GameType.SURVIVAL);

        int creativeDelay = portal.getPortalTransitionTime(level, creativePlayer);
        int survivalDelay = portal.getPortalTransitionTime(level, survivalPlayer);

        helper.assertValueEqual(creativeDelay, level.getGameRules().get(GameRules.PLAYERS_NETHER_PORTAL_CREATIVE_DELAY),
                "creative delay should match players_nether_portal_creative_delay");
        helper.assertTrue(creativeDelay <= survivalDelay, "creative delay should be no longer than survival delay");
        helper.succeed();
    }

    /**
     * Census over a fresh {@code createLandingPortal} arena: 14 frame cells (4-wide/5-tall border) of
     * {@code polished_sulfur} and 6 portal cells (2x3 interior)
     */
    private static void generatedFrameIsRectangular(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 1), new BlockPos(3, 1, 1))) {
            helper.setBlock(pos, Blocks.STONE);
        }
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 2, 1), new BlockPos(3, 8, 1))) {
            helper.setBlock(pos, Blocks.AIR);
        }
        BlockPos near = helper.absolutePos(new BlockPos(1, 1, 1));

        BlockPos base = RelictPortalForcer.createLandingPortal(level, near, Direction.Axis.X);

        int frameCount = 0;
        int portalCount = 0;
        for (BlockPos pos : BlockPos.betweenClosed(base.offset(-2, -2, -2), base.offset(3, 4, 2))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.POLISHED_SULFUR)) {
                frameCount++;
            } else if (state.is(RelictBlocks.MARS_PORTAL.get())) {
                portalCount++;
            }
        }

        helper.assertValueEqual(frameCount, 14, "polished_sulfur frame cell count");
        helper.assertValueEqual(portalCount, 6, "mars_portal interior cell count");
        helper.succeed();
    }

    /** Breaking one frame block must collapse every portal cell it framed, like a Nether Portal. */
    private static void breakingFrameCollapsesPortal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 1), new BlockPos(3, 1, 1))) {
            helper.setBlock(pos, Blocks.STONE);
        }
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 2, 1), new BlockPos(3, 8, 1))) {
            helper.setBlock(pos, Blocks.AIR);
        }
        BlockPos near = helper.absolutePos(new BlockPos(1, 1, 1));

        BlockPos base = RelictPortalForcer.createLandingPortal(level, near, Direction.Axis.X);
        helper.assertTrue(level.getBlockState(base).is(RelictBlocks.MARS_PORTAL.get()), "portal should exist before the break");

        level.destroyBlock(base.above(3), false); // top frame row, directly over the portal interior

        boolean anyPortalRemains = false;
        for (BlockPos pos : BlockPos.betweenClosed(base.offset(-2, -2, -2), base.offset(3, 4, 2))) {
            if (level.getBlockState(pos).is(RelictBlocks.MARS_PORTAL.get())) {
                anyPortalRemains = true;
            }
        }
        helper.assertTrue(!anyPortalRemains, "breaking a frame block should collapse every portal cell it framed");
        helper.succeed();
    }

    /**
     * A cache entry whose portal block is gone must be skipped (and pruned) in favor of a further-but-still-
     * valid entry, not returned as a reusable exit
     */
    private static void staleCacheEntryIsPrunedOnLookup(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ResourceKey<Level> dimension = level.dimension();
        RelictPortalNetwork network = RelictPortalNetwork.get(level);

        BlockPos stale = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos valid = helper.absolutePos(new BlockPos(1, 1, 3));
        helper.setBlock(new BlockPos(1, 1, 3), RelictBlocks.MARS_PORTAL.get());

        GlobalPos staleGlobal = GlobalPos.of(dimension, stale);
        GlobalPos validGlobal = GlobalPos.of(dimension, valid);
        network.remember(staleGlobal);
        network.remember(validGlobal);

        try {
            Optional<GlobalPos> found = network.findNearest(dimension, stale, 16,
                    candidate -> level.getBlockState(candidate.pos()).is(RelictBlocks.MARS_PORTAL.get()));

            helper.assertTrue(found.isPresent(), "the still-valid entry should be found");
            helper.assertTrue(found.get().pos().equals(valid), "the stale entry should be skipped, not returned");
        } finally {
            network.forget(staleGlobal);
            network.forget(validGlobal);
            helper.setBlock(new BlockPos(1, 1, 3), Blocks.AIR);
        }

        helper.succeed();
    }

}
