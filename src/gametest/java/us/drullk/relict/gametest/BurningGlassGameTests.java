package us.drullk.relict.gametest;

import net.minecraft.commands.arguments.EntityAnchorArgument;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.timeline.Timeline;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import us.drullk.relict.Relict;
import us.drullk.relict.atmosphere.RelictAtmosphereServer;
import us.drullk.relict.atmosphere.StormPhase;
import us.drullk.relict.block.wreck.SolarPanelDecay;
import us.drullk.relict.init.RelictItems;
import us.drullk.relict.init.worldgen.RelictDimension;
import us.drullk.relict.item.BurningGlassItem;

import java.util.List;
import java.util.function.Consumer;

/**
 * Burning Glass's own gametest pack: conversions, all three inert gates, use-duration semantics (including
 * Fire Aspect timing), and the dropped-item cook/burn/priority rules.
 * <p>
 * Every conversion/gate test drives the item through its real {@link BurningGlassItem#onUseTick} /
 * {@link BurningGlassItem#finishUsingItem} overrides in a plain loop counting down from
 * {@link BurningGlassItem#getUseDuration} to zero -- "simulated use ticks", not a real 20-second wait --
 * which is exactly the shape {@code LivingEntity#updateUsingItem} drives in a real hold, just without the
 * intervening game ticks. A test that stops the loop partway and never calls {@code finishUsingItem}
 * reproduces an early release without needing {@code Item#releaseUsing} at all: the default no-op inherited
 * from {@link net.minecraft.world.item.Item} is the whole contract.
 * <p>
 * <strong>The Mars storm-dimmed gate is deliberately not exercised end-to-end here.</strong> {@code
 * isSunGateOpen}'s storm branch only runs when {@code level.dimensionTypeRegistration().is(HAS_MARS_ATMOSPHERE)}
 * -- and the GameTest harness always runs inside its own fixed test dimension, never inside the mod's actual
 * Mars level, so that branch is unreachable from any gametest. {@link #stormPhaseGateReused} instead proves
 * the one thing that IS reachable and IS the actual contract: the item classifies a storm phase as dimming
 * through {@link SolarPanelDecay#isStormDepositingDust}, the exact function the solar panels already gate on
 * -- no second definition of "storm-dimmed" exists to test separately.
 * <p>
 * <strong>Known pre-existing flake, not touched here (see the impl report):</strong> {@code
 * sandBecomesGlass}, {@code droppedStackCooksWhole} and {@code droppedStackBurnsWhole} fail intermittently
 * under this same harness on unmodified {@code main} too. Instrumented live: the sun gate itself reads open
 * and the raycast reports a BLOCK hit when it fails, yet the target is still unconverted afterward, and the
 * failure does not clear even given 60+ retried ticks -- so it is not the day/night or light-engine timing
 * this file's other gates already guard against. Root cause unresolved; left as-is rather than shipping an
 * unverified band-aid.
 */
public final class BurningGlassGameTests {

    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");
    private static final BlockPos TARGET = new BlockPos(1, 2, 1);
    private static final BlockPos EYE = new BlockPos(1, 2, 3);

    /**
     * The Mars clock's own total-tick values for one of the datagen-computed total-eclipse events (see
     * {@code OrbitTransitSolver}'s schedule report from {@code runServerData}): totality peaks at 64044,
     * and the transit's tracked coverage has fully cleared by 64248, so 64249 is one tick past egress. Not
     * derived at runtime because nothing here needs to re-scan the schedule -- these two ticks are enough
     * to prove the gate closes for totality and reopens right after.
     */
    private static final long MARS_TOTALITY_TICK = 64044L;
    private static final long MARS_EGRESS_TICK = 64249L;

    private BurningGlassGameTests() {
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        test(event, environment, "burning_glass_sand_becomes_glass", "Burning Glass: sand smelts to glass in place", BurningGlassGameTests::sandBecomesGlass);
        test(event, environment, "burning_glass_clay_becomes_terracotta", "Burning Glass: clay smelts to terracotta in place", BurningGlassGameTests::clayBecomesTerracotta);
        test(event, environment, "burning_glass_log_becomes_charcoal_drop", "Burning Glass: log converts to air + charcoal drop", BurningGlassGameTests::logBecomesCharcoalDrop);
        test(event, environment, "burning_glass_ore_becomes_ingot_drop", "Burning Glass: iron ore converts to air + iron ingot drop", BurningGlassGameTests::oreBecomesIngotDrop);
        test(event, environment, "burning_glass_unmapped_block_unaffected", "Burning Glass: block outside the conversion map is untouched", BurningGlassGameTests::unmappedBlockUnaffected);
        test(event, environment, "burning_glass_early_release_no_effect", "Burning Glass: releasing before completion leaves the block untouched", BurningGlassGameTests::earlyReleaseHasNoEffect);
        test(event, environment, "burning_glass_fire_aspect_halves_duration", "Burning Glass: Fire Aspect I/II halve the use duration", BurningGlassGameTests::fireAspectHalvesDuration);
        test(event, environment, "burning_glass_dropped_stack_cooks_whole", "Burning Glass: a smeltable dropped stack cooks entirely", BurningGlassGameTests::droppedStackCooksWhole);
        test(event, environment, "burning_glass_dropped_stack_burns_whole", "Burning Glass: a non-smeltable dropped stack is destroyed entirely", BurningGlassGameTests::droppedStackBurnsWhole);
        test(event, environment, "burning_glass_item_beats_block_raycast", "Burning Glass: a dropped item in front of a block wins the raycast", BurningGlassGameTests::droppedItemBeatsBlockInRaycast);
        test(event, environment, "burning_glass_storm_gate_reused", "Burning Glass: storm-dimmed gate reuses the solar panel dust-falling test", BurningGlassGameTests::stormPhaseGateReused);
        // Generous max-ticks headroom: the async light engine's real-time catch-up (see the method doc) can
        // take longer than a few ticks when the batch is busy with other tests' structures.
        event.registerTest(Relict.id("burning_glass_no_sky_blocks_completion"), new RelictFunctionGameTestInstance(BurningGlassGameTests::noSkyAccessBlocksCompletion,
                Component.literal("Burning Glass: roofed target does not convert"), new TestData<>(environment, EMPTY_STRUCTURE, 60, 0, true)));

        // The two tests below briefly set the shared OVERWORLD world clock to night (there is no
        // per-structure day/night in this clock system -- see setTimeOfDay) before resetting it. Each body
        // runs atomically with no intervening tick, so the two cannot corrupt each other, but a batch-mate
        // reading the clock mid-mutation is a real hazard since GameTest environments run their tests
        // concurrently within a batch -- a dedicated environment puts these two in their own batch, run
        // sequential with (never concurrent to) every daylight-only test above.
        Holder<TestEnvironmentDefinition<?>> isolatedClockEnvironment = event.registerEnvironment(Relict.id("burning_glass_time_sensitive"), new TestEnvironmentDefinition.AllOf(List.of()));
        test(event, isolatedClockEnvironment, "burning_glass_night_blocks_completion", "Burning Glass: night target does not convert", BurningGlassGameTests::nightBlocksCompletion);
        test(event, isolatedClockEnvironment, "burning_glass_gated_attempt_no_charge", "Burning Glass: gated use() never starts the hold", BurningGlassGameTests::gatedAttemptDoesNotStartCharge);
        test(event, isolatedClockEnvironment, "burning_glass_eclipse_totality_blocks_then_reopens", "Burning Glass: eclipse totality gates it shut, egress reopens it", BurningGlassGameTests::eclipseGatesBurningGlass);
    }

    private static void test(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment, String id, String description, Consumer<GameTestHelper> function) {
        event.registerTest(Relict.id(id), new RelictFunctionGameTestInstance(function, Component.literal(description),
                new TestData<>(environment, EMPTY_STRUCTURE, 20, 0, true)));
    }

    // -------------------------------------------------------------------------------------------- conversions

    private static void sandBecomesGlass(GameTestHelper helper) {
        ServerLevel level = daylitLevel(helper);
        helper.setBlock(TARGET, Blocks.SAND);

        holdFullDuration(level, sightedPlayer(helper), new ItemStack(RelictItems.BURNING_GLASS.get()));

        helper.assertBlockPresent(Blocks.GLASS, TARGET);
        helper.succeed();
    }

    private static void clayBecomesTerracotta(GameTestHelper helper) {
        ServerLevel level = daylitLevel(helper);
        helper.setBlock(TARGET, Blocks.CLAY);

        holdFullDuration(level, sightedPlayer(helper), new ItemStack(RelictItems.BURNING_GLASS.get()));

        helper.assertBlockPresent(Blocks.TERRACOTTA, TARGET);
        helper.succeed();
    }

    private static void logBecomesCharcoalDrop(GameTestHelper helper) {
        ServerLevel level = daylitLevel(helper);
        helper.setBlock(TARGET, Blocks.OAK_LOG);

        holdFullDuration(level, sightedPlayer(helper), new ItemStack(RelictItems.BURNING_GLASS.get()));

        helper.assertBlockPresent(Blocks.AIR, TARGET);
        assertDroppedItem(helper, level, Items.CHARCOAL, "log conversion should drop charcoal");
        helper.succeed();
    }

    private static void oreBecomesIngotDrop(GameTestHelper helper) {
        ServerLevel level = daylitLevel(helper);
        helper.setBlock(TARGET, Blocks.IRON_ORE);

        holdFullDuration(level, sightedPlayer(helper), new ItemStack(RelictItems.BURNING_GLASS.get()));

        helper.assertBlockPresent(Blocks.AIR, TARGET);
        assertDroppedItem(helper, level, Items.IRON_INGOT, "iron ore conversion should drop an iron ingot");
        helper.succeed();
    }

    private static void unmappedBlockUnaffected(GameTestHelper helper) {
        ServerLevel level = daylitLevel(helper);
        helper.setBlock(TARGET, Blocks.STONE);

        holdFullDuration(level, sightedPlayer(helper), new ItemStack(RelictItems.BURNING_GLASS.get()));

        helper.assertBlockPresent(Blocks.STONE, TARGET);
        helper.succeed();
    }

    // -------------------------------------------------------------------------------------------------- gates

    /**
     * {@code canSeeSky} reads live sky light ({@code getBrightness(LightLayer.SKY, pos) >= 15}), and the
     * light engine that value comes from runs on its own thread and refuses to run synchronously (see
     * {@code ThreadedLevelLightEngine#runLightUpdates}) -- so a roof placed this tick is not yet visible to
     * {@code canSeeSky} this tick. {@link GameTestHelper#runAfterDelay} gives the engine a handful of real
     * ticks to catch up before the hold is attempted, the same way a real player's roof would only start
     * gating the tool once the world had actually finished re-lighting under it.
     * <p>
     * The roof sits above {@code TARGET.relative(SOUTH)}, not above {@code TARGET} itself: {@link
     * #sightedPlayer} looks at {@code TARGET} from due south at the same height, so the raycast lands on
     * {@code TARGET}'s south face and {@link BurningGlassItem#targetPos} -- which keys the sun gate off the
     * position the hit face looks out onto, not the block that was hit -- resolves to the south neighbor.
     * A roof placed directly over {@code TARGET} sits one column away from anything the gate ever samples,
     * which is why this test previously passed for the wrong reason: the roof never blocked anything the
     * gate read, but the target still failed to convert, until the position was corrected here.
     */
    private static void noSkyAccessBlocksCompletion(GameTestHelper helper) {
        ServerLevel level = daylitLevel(helper);
        helper.setBlock(TARGET, Blocks.SAND);
        helper.setBlock(TARGET.relative(Direction.SOUTH).above(), Blocks.STONE);

        helper.runAfterDelay(20, () -> {
            holdFullDuration(level, sightedPlayer(helper), new ItemStack(RelictItems.BURNING_GLASS.get()));

            helper.assertBlockPresent(Blocks.SAND, TARGET);
            helper.succeed();
        });
    }

    private static void nightBlocksCompletion(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(TARGET, Blocks.SAND);
        setTimeOfDay(level, 18000);

        holdFullDuration(level, sightedPlayer(helper), new ItemStack(RelictItems.BURNING_GLASS.get()));

        helper.assertBlockPresent(Blocks.SAND, TARGET);
        setTimeOfDay(level, 6000);
        helper.succeed();
    }

    /**
     * Exercises {@link BurningGlassItem#use} directly (not the hold loop): a gated attempt must refuse to
     * start the charge at all, which is the whole point of checking the gate up front rather than only at
     * completion -- a doomed 20-second hold is never started.
     */
    private static void gatedAttemptDoesNotStartCharge(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(TARGET, Blocks.SAND);
        setTimeOfDay(level, 18000);

        Player player = sightedPlayer(helper);
        Item item = RelictItems.BURNING_GLASS.get();
        ItemStack stack = new ItemStack(item);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);

        InteractionResult result = item.use(level, player, InteractionHand.MAIN_HAND);

        helper.assertTrue(!player.isUsingItem(), "a gated attempt must not start the hold");
        helper.assertTrue(result != InteractionResult.CONSUME, "a gated attempt must not return CONSUME");
        helper.assertBlockPresent(Blocks.SAND, TARGET);

        setTimeOfDay(level, 6000);
        helper.succeed();
    }

    private static void earlyReleaseHasNoEffect(GameTestHelper helper) {
        ServerLevel level = daylitLevel(helper);
        helper.setBlock(TARGET, Blocks.SAND);

        Player player = sightedPlayer(helper);
        Item item = RelictItems.BURNING_GLASS.get();
        ItemStack stack = new ItemStack(item);
        int duration = item.getUseDuration(stack, player);

        // Ticks all the way down to the last one, then stops -- exactly what a release one tick before
        // completion looks like. finishUsingItem is deliberately never called: that is the release.
        for (int remaining = duration; remaining > 1; remaining--) {
            item.onUseTick(level, player, stack, remaining);
        }

        helper.assertBlockPresent(Blocks.SAND, TARGET);
        helper.succeed();
    }

    /**
     * Reproduces the real Mars transit end to end rather than testing a reused boundary: {@code mars_sol}
     * and {@code phobos_transit} -- the actual registered timelines {@code OrbitTransitSolver} writes --
     * are layered onto this gametest level's own attribute system on top of its default layers, so
     * {@link BurningGlassItem#isSunGateOpen} runs its real {@code isBrightOutside} check against the real
     * eclipse dip. {@code ServerLevel#setEnvironmentAttributes} is the same swap-and-restore the vanilla
     * {@code TestEnvironmentDefinition.Timelines} environment uses; it is done here directly instead of
     * through that declarative environment because the timeline holders it needs are only resolvable once
     * the level's registries are up, which is after environment registration runs.
     */
    private static void eclipseGatesBurningGlass(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(TARGET, Blocks.SAND);
        setTimeOfDay(level, 6000);

        Holder<WorldClock> marsClock = level.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK).getOrThrow(RelictDimension.MARS_CLOCK);
        Holder<Timeline> marsSol = level.registryAccess().lookupOrThrow(Registries.TIMELINE).getOrThrow(RelictDimension.MARS_SOL);
        Holder<Timeline> phobosTransit = level.registryAccess().lookupOrThrow(Registries.TIMELINE).getOrThrow(RelictDimension.PHOBOS_TRANSIT);
        long originalMarsTicks = level.getServer().clockManager().getTotalTicks(marsClock);

        EnvironmentAttributeSystem original = level.setEnvironmentAttributes(EnvironmentAttributeSystem.builder()
                .addDefaultLayers(level)
                .addTimelineLayer(marsSol, level.clockManager())
                .addTimelineLayer(phobosTransit, level.clockManager())
                .build());

        try {
            Player player = sightedPlayer(helper);
            Item item = RelictItems.BURNING_GLASS.get();

            level.getServer().clockManager().setTotalTicks(marsClock, MARS_TOTALITY_TICK);
            level.updateSkyBrightness();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
            InteractionResult duringTotality = item.use(level, player, InteractionHand.MAIN_HAND);
            helper.assertTrue(!player.isUsingItem(), "totality must not let the hold start");
            helper.assertTrue(duringTotality != InteractionResult.CONSUME, "a totality attempt must not return CONSUME");
            helper.assertBlockPresent(Blocks.SAND, TARGET);

            level.getServer().clockManager().setTotalTicks(marsClock, MARS_EGRESS_TICK);
            level.updateSkyBrightness();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
            InteractionResult afterEgress = item.use(level, player, InteractionHand.MAIN_HAND);
            helper.assertTrue(player.isUsingItem(), "one tick past egress the gate should let the hold start");
            helper.assertTrue(afterEgress == InteractionResult.CONSUME, "one tick past egress use() should return CONSUME");
            player.stopUsingItem();
        } finally {
            level.setEnvironmentAttributes(original);
            level.getServer().clockManager().setTotalTicks(marsClock, originalMarsTicks);
        }

        setTimeOfDay(level, 6000);
        helper.succeed();
    }

    // --------------------------------------------------------------------------------------- use duration

    private static void fireAspectHalvesDuration(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = sightedPlayer(helper);
        Item item = RelictItems.BURNING_GLASS.get();
        Holder<Enchantment> fireAspect = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FIRE_ASPECT);

        ItemStack plain = new ItemStack(item);
        ItemStack levelOne = new ItemStack(item);
        levelOne.enchant(fireAspect, 1);
        ItemStack levelTwo = new ItemStack(item);
        levelTwo.enchant(fireAspect, 2);

        helper.assertValueEqual(item.getUseDuration(plain, player), 20 * 20, "base use duration");
        helper.assertValueEqual(item.getUseDuration(levelOne, player), 10 * 20, "Fire Aspect I use duration");
        helper.assertValueEqual(item.getUseDuration(levelTwo, player), 5 * 20, "Fire Aspect II use duration");
        helper.succeed();
    }

    // ------------------------------------------------------------------------------------------ dropped items

    /**
     * Every dropped-item test spawns via {@code Vec3.atCenterOf(pos)}, not the {@code BlockPos} overload of
     * {@link GameTestHelper#spawnItem} -- that overload places the entity at the block's minimum corner, and
     * an {@link ItemEntity}'s small hitbox sitting in one corner of the block is an easy miss for a ray aimed
     * at the block's center, which is exactly where {@link #sightedPlayer} aims.
     */
    private static void droppedStackCooksWhole(GameTestHelper helper) {
        ServerLevel level = daylitLevel(helper);
        ItemEntity dropped = helper.spawnItem(Items.SAND, Vec3.atCenterOf(TARGET));
        dropped.setItem(new ItemStack(Items.SAND, 5));

        holdFullDuration(level, sightedPlayer(helper), new ItemStack(RelictItems.BURNING_GLASS.get()));

        helper.assertTrue(dropped.isAlive(), "the entity itself should survive a cook, only its stack changes");
        ItemStack result = dropped.getItem();
        helper.assertTrue(result.is(Items.GLASS) && result.getCount() == 5, "the whole 5-sand stack should cook to 5 glass, got " + result);
        helper.succeed();
    }

    private static void droppedStackBurnsWhole(GameTestHelper helper) {
        ServerLevel level = daylitLevel(helper);
        ItemEntity dropped = helper.spawnItem(Items.DIRT, Vec3.atCenterOf(TARGET));
        dropped.setItem(new ItemStack(Items.DIRT, 5));

        holdFullDuration(level, sightedPlayer(helper), new ItemStack(RelictItems.BURNING_GLASS.get()));

        helper.assertTrue(!dropped.isAlive(), "a non-smeltable dropped stack should be destroyed entirely");
        helper.succeed();
    }

    /**
     * A convertible block sits behind a smeltable dropped item on the same ray; only the item should react.
     * The item is placed at the literal midpoint between the player's real eye position and the block's
     * center -- a fixed block-grid offset is not reliable here, because {@link #sightedPlayer}'s eye sits
     * above the feet position it sets, giving the aim a slight downward pitch that a naive horizontal offset
     * does not lie on.
     */
    private static void droppedItemBeatsBlockInRaycast(GameTestHelper helper) {
        ServerLevel level = daylitLevel(helper);
        helper.setBlock(TARGET, Blocks.SAND);

        Player player = sightedPlayer(helper);
        Vec3 eye = player.getEyePosition();
        Vec3 targetCenter = helper.absoluteVec(Vec3.atCenterOf(TARGET));
        Vec3 midpoint = eye.add(targetCenter.subtract(eye).scale(0.5));

        ItemEntity dropped = new ItemEntity(level, midpoint.x, midpoint.y, midpoint.z, new ItemStack(Items.SAND, 1));
        dropped.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(dropped);

        holdFullDuration(level, player, new ItemStack(RelictItems.BURNING_GLASS.get()));

        helper.assertBlockPresent(Blocks.SAND, TARGET);
        helper.assertTrue(dropped.getItem().is(Items.GLASS), "the nearer dropped item should have been cooked instead of the farther block");
        helper.succeed();
    }

    // ------------------------------------------------------------------------------------------------ storm gate

    /**
     * See the class doc: the full Mars-dimension branch of {@code isSunGateOpen} cannot run inside the
     * GameTest harness, so this proves the reused boundary directly instead.
     */
    private static void stormPhaseGateReused(GameTestHelper helper) {
        helper.assertTrue(SolarPanelDecay.isStormDepositingDust(StormPhase.DUST_ENVELOPE), "DUST_ENVELOPE should count as storm-dimmed");
        helper.assertTrue(SolarPanelDecay.isStormDepositingDust(StormPhase.WIND_BUILD), "WIND_BUILD should count as storm-dimmed");
        helper.assertTrue(SolarPanelDecay.isStormDepositingDust(StormPhase.ELECTRIC_PEAK), "ELECTRIC_PEAK should count as storm-dimmed");
        helper.assertTrue(SolarPanelDecay.isStormDepositingDust(StormPhase.TAIL), "TAIL should count as storm-dimmed");
        helper.assertTrue(!SolarPanelDecay.isStormDepositingDust(StormPhase.CLEAR), "CLEAR should not count as storm-dimmed");
        helper.assertTrue(!SolarPanelDecay.isStormDepositingDust(StormPhase.DISTANT), "DISTANT (lead-in haze) should not count as storm-dimmed");
        helper.assertTrue(!SolarPanelDecay.isStormDepositingDust(StormPhase.ARRIVAL), "ARRIVAL (silent impact) should not count as storm-dimmed");
        helper.succeed();
    }

    // ----------------------------------------------------------------------------------------------- helpers

    private static ServerLevel daylitLevel(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        setTimeOfDay(level, 6000);
        return level;
    }

    /**
     * The classic per-level day-time field is gone in favor of {@code EnvironmentAttributes.SKY_LIGHT_LEVEL},
     * a data-driven attribute computed off a shared {@link WorldClock} rather than a level-local counter --
     * see {@link us.drullk.relict.item.WeatherglassItem} and {@link RelictAtmosphereServer} for the same
     * clock-holder-lookup pattern already used elsewhere in the mod (there, for the Mars clock; here, for the
     * vanilla {@link WorldClocks#OVERWORLD} clock the GameTest dimension itself runs on). Setting the clock's
     * total ticks invalidates every level's attribute cache immediately (see
     * {@code ServerClockManager#modifyClock}), so {@link Level#isBrightOutside} reflects the new time with no
     * extra tick needed.
     */
    private static void setTimeOfDay(ServerLevel level, long ticks) {
        Holder<WorldClock> overworldClock = level.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK).getOrThrow(WorldClocks.OVERWORLD);
        level.getServer().clockManager().setTotalTicks(overworldClock, ticks);
        // The clock invalidates the attribute system's cache, but skyDarken itself (what isBrightOutside
        // reads) is a separately cached int that only recomputes on its own -- normally once per level tick.
        // Force that recompute now so the gate sees the new time immediately.
        level.updateSkyBrightness();
    }

    private static Player sightedPlayer(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 eye = helper.absoluteVec(Vec3.atCenterOf(EYE));
        Vec3 look = helper.absoluteVec(Vec3.atCenterOf(TARGET));
        player.setPos(eye.x, eye.y, eye.z);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, look);
        return player;
    }

    private static void holdFullDuration(ServerLevel level, Player player, ItemStack stack) {
        Item item = stack.getItem();
        for (int remaining = item.getUseDuration(stack, player); remaining > 0; remaining--) {
            item.onUseTick(level, player, stack, remaining);
        }
        item.finishUsingItem(stack, level, player);
    }

    private static void assertDroppedItem(GameTestHelper helper, ServerLevel level, Item expected, String message) {
        AABB area = new AABB(helper.absolutePos(TARGET)).inflate(1.5);
        boolean found = level.getEntitiesOfClass(ItemEntity.class, area).stream().anyMatch(entity -> entity.getItem().is(expected));
        helper.assertTrue(found, message);
    }

}
