package us.drullk.relict.gametest.cipherchest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import us.drullk.relict.Relict;
import us.drullk.relict.block.cipherchest.CipherChestBlock;
import us.drullk.relict.block.cipherchest.CipherChestBlockEntity;
import us.drullk.relict.block.cipherchest.CipherChestFaceLayout;
import us.drullk.relict.block.cipherchest.CipherChestSquare;
import us.drullk.relict.gametest.RelictFunctionGameTestInstance;
import us.drullk.relict.gametest.RelictGameTests;
import us.drullk.relict.init.RelictBlocks;

/**
 * The Cipher Chest's own GameTest pack -- its own class, one line into {@link RelictGameTests}, per the
 * Model Generators precedent.
 * <p>
 * Every test drives the block through {@link GameTestHelper#useBlock}, the same entry point a real
 * right-click uses (item-on-block, then {@code useWithoutItem}, exactly like
 * {@code CipherChestBlock#useWithoutItem}) -- not by calling block-entity methods directly -- so a passing
 * "menu opens" test is the game itself proving defect #2's fix holds, not a restatement of the code under
 * test. Click positions are computed from {@link CipherChestFaceLayout}'s own hover-shape functions (the
 * same functions the block's hit-test and the hover-outline renderer call), so a test aims exactly where
 * the game itself claims the dial/latch is -- it can't silently drift from the real geometry the way a
 * hand-typed coordinate could.
 * <p>
 * Setup calls {@link CipherChestBlockEntity#randomize} directly with a fixed seed to get a reproducible
 * puzzle instead of a real (non-deterministic) placement -- that's test scaffolding, not the thing under
 * test, and {@code randomize} is the same method {@code CipherChestBlock#setPlacedBy} calls.
 */
public final class CipherChestGameTests {

    private static final long SEED = 20260825L;
    private static final long LOOT_SEED = 20260826L;
    private static final int MAX_TICKS = 200;
    private static final BlockPos POS = new BlockPos(2, 1, 2);
    private static final Direction FACING = Direction.NORTH;

    /** Well past a hopper's 8-tick cooldown, so a blocked hopper has had many chances to prove it, not one. */
    private static final long HOPPER_TICK_BUDGET = 60L;

    private CipherChestGameTests() {
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(environment, RelictGameTests.PLATFORM, MAX_TICKS, 0, true);

        event.registerTest(id("menu_opens_on_correct_code"), new RelictFunctionGameTestInstance(
                CipherChestGameTests::menuOpensOnCorrectCode,
                Component.literal("Cipher Chest: entering the correct code opens the chest menu"), data));
        event.registerTest(id("dial_cycling_steps_and_wraps"), new RelictFunctionGameTestInstance(
                CipherChestGameTests::dialCyclingStepsAndWraps,
                Component.literal("Cipher Chest: a click cycles a dial +1, a sneak-click +5, both wrapping"), data));
        event.registerTest(id("wrong_code_scrambles_and_locks_out"), new RelictFunctionGameTestInstance(
                CipherChestGameTests::wrongCodeScramblesAndLocksOut,
                Component.literal("Cipher Chest: a wrong code scrambles the dials and starts the lockout"), data));
        event.registerTest(id("correct_code_persists_after_reload"), new RelictFunctionGameTestInstance(
                CipherChestGameTests::correctCodePersistsAfterReload,
                Component.literal("Cipher Chest: the solved state survives a save/reload round trip"), data));
        event.registerTest(id("side_filter_ignores_other_faces"), new RelictFunctionGameTestInstance(
                CipherChestGameTests::sideFilterIgnoresOtherFaces,
                Component.literal("Cipher Chest: a click on a non-interactive face never selects a dial or the latch"), data));
        event.registerTest(id("breakability_locked_structure_placed_is_unbreakable"), new RelictFunctionGameTestInstance(
                CipherChestGameTests::breakabilityLockedStructurePlacedIsUnbreakable,
                Component.literal("Cipher Chest: locked and structure-placed resists all destroy progress"), data));
        event.registerTest(id("breakability_player_placed_is_breakable"), new RelictFunctionGameTestInstance(
                CipherChestGameTests::breakabilityPlayerPlacedIsBreakable,
                Component.literal("Cipher Chest: a player-placed chest is breakable even while locked"), data));
        event.registerTest(id("breakability_unlocked_is_breakable"), new RelictFunctionGameTestInstance(
                CipherChestGameTests::breakabilityUnlockedIsBreakable,
                Component.literal("Cipher Chest: an unlocked chest is breakable forever"), data));
        event.registerTest(id("breakability_flags_survive_reload"), new RelictFunctionGameTestInstance(
                CipherChestGameTests::breakabilityFlagsSurviveReload,
                Component.literal("Cipher Chest: the player-placed flag survives a save/reload round trip"), data));
        event.registerTest(id("loot_table_unpacks_on_unlock_and_open"), new RelictFunctionGameTestInstance(
                CipherChestGameTests::lootTableUnpacksOnUnlockAndOpen,
                Component.literal("Cipher Chest: a LootTable key rolls into real items on first open, then clears"), data));
        event.registerTest(id("container_api_denies_access_while_locked"), new RelictFunctionGameTestInstance(
                CipherChestGameTests::containerApiDeniesAccessWhileLocked,
                Component.literal("Cipher Chest: a locked chest's Container methods refuse access and never unpack loot"), data));
        event.registerTest(id("hopper_cannot_extract_from_locked_chest"), new RelictFunctionGameTestInstance(
                CipherChestGameTests::hopperCannotExtractFromLockedChest,
                Component.literal("Cipher Chest: a real hopper below a locked chest extracts nothing over time"), data));
        event.registerTest(id("hopper_cannot_insert_into_locked_chest"), new RelictFunctionGameTestInstance(
                CipherChestGameTests::hopperCannotInsertIntoLockedChest,
                Component.literal("Cipher Chest: a real hopper above a locked chest never manages to push its item in"), data));
        event.registerTest(id("hopper_cannot_extract_during_lockout_window"), new RelictFunctionGameTestInstance(
                CipherChestGameTests::hopperCannotExtractDuringLockoutWindow,
                Component.literal("Cipher Chest: the wrong-guess lockout window rejects a hopper the same as never-solved"), data));
        event.registerTest(id("hopper_routes_resume_once_unlocked"), new RelictFunctionGameTestInstance(
                CipherChestGameTests::hopperRoutesResumeOnceUnlocked,
                Component.literal("Cipher Chest: once solved, hopper extraction and insertion both behave vanilla-normal"), data));
    }

    private static Identifier id(String path) {
        return Relict.id("cipher_chest/" + path);
    }

    // --------------------------------------------------------------------------------------------- tests

    private static void menuOpensOnCorrectCode(GameTestHelper helper) {
        place(helper, false);
        CipherChestBlockEntity chest = chestAt(helper);
        ServerPlayer player = menuCapablePlayer(helper);

        solve(helper, chest, player);

        helper.assertTrue(chest.isSolved(), "the chest should be solved after entering the canon code");
        helper.assertTrue(player.containerMenu instanceof ChestMenu,
                "the correct code should open the chest menu through a real useBlock click (defect #2)");
        helper.succeed();
    }

    private static void dialCyclingStepsAndWraps(GameTestHelper helper) {
        place(helper, false);
        CipherChestBlockEntity chest = chestAt(helper);
        Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);

        int cell = firstBlankCell(chest);
        int start = chest.displayValueAt(cell);
        BlockHitResult dialHit = dialHit(helper, cell);

        helper.useBlock(POS, player, dialHit);
        int afterClick = chest.displayValueAt(cell);
        helper.assertTrue(afterClick == CipherChestSquare.wrapValue(start, 1), "a plain click should advance the dial by +1, wrapping");

        player.setShiftKeyDown(true);
        helper.useBlock(POS, player, dialHit);
        int afterSneakClick = chest.displayValueAt(cell);
        helper.assertTrue(afterSneakClick == CipherChestSquare.wrapValue(afterClick, 5), "a sneak-click should advance the dial by +5, wrapping");

        helper.succeed();
    }

    private static void wrongCodeScramblesAndLocksOut(GameTestHelper helper) {
        place(helper, false);
        CipherChestBlockEntity chest = chestAt(helper);
        Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);

        int cell = firstBlankCell(chest);
        if (chest.displayValueAt(cell) == CipherChestSquare.valueAt(cell)) {
            // Guarantee at least one wrong dial regardless of what the fixed seed happened to start it at.
            helper.useBlock(POS, player, dialHit(helper, cell));
        }
        helper.useBlock(POS, player, latchHit(helper));

        long gameTime = helper.getLevel().getGameTime();
        helper.assertTrue(!chest.isSolved(), "a wrong code must not solve the chest");
        helper.assertTrue(chest.isLockedOut(gameTime), "a wrong code must start the lockout the blink rides on");
        helper.succeed();
    }

    private static void correctCodePersistsAfterReload(GameTestHelper helper) {
        place(helper, false);
        CipherChestBlockEntity chest = chestAt(helper);
        ServerPlayer player = menuCapablePlayer(helper);
        solve(helper, chest, player);

        CipherChestBlockEntity reloaded = reload(helper, chest);
        helper.assertTrue(reloaded.isSolved(), "the solved state must survive a save/reload round trip");
        helper.succeed();
    }

    private static void sideFilterIgnoresOtherFaces(GameTestHelper helper) {
        place(helper, false);
        CipherChestBlockEntity chest = chestAt(helper);
        Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);

        int cell = firstBlankCell(chest);
        int before = chest.displayValueAt(cell);
        boolean lockedBefore = chest.isLockedOut(helper.getLevel().getGameTime());

        // The back face (opposite the latch): neither isLatchHit (wrong face) nor cellIndexFromHit (wrong
        // face, not UP) can accept a hit here, so a click must be a complete no-op. Catches mirror-click problems
        helper.useBlock(POS, player, hitAt(helper, FACING.getOpposite(), 0.5, 0.5, 0.5));

        helper.assertTrue(chest.displayValueAt(cell) == before, "a click on a non-interactive face must not move a dial");
        helper.assertTrue(chest.isLockedOut(helper.getLevel().getGameTime()) == lockedBefore,
                "a click on a non-interactive face must not trigger the latch");
        helper.assertTrue(!chest.isSolved(), "a click on a non-interactive face must not solve the chest");
        helper.succeed();
    }

    private static void breakabilityLockedStructurePlacedIsUnbreakable(GameTestHelper helper) {
        place(helper, false); // fresh block entity, never randomize()'d through setPlacedBy -> playerPlaced stays false
        Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);

        float progress = destroyProgress(helper, player);
        helper.assertTrue(progress <= 0.0F, "a locked, structure-placed chest must be unbreakable (zero destroy progress)");
        helper.succeed();
    }

    private static void breakabilityPlayerPlacedIsBreakable(GameTestHelper helper) {
        place(helper, true);
        Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);

        float progress = destroyProgress(helper, player);
        helper.assertTrue(progress > 0.0F, "a player-placed chest must be breakable even while locked");
        helper.succeed();
    }

    private static void breakabilityUnlockedIsBreakable(GameTestHelper helper) {
        place(helper, false);
        CipherChestBlockEntity chest = chestAt(helper);
        ServerPlayer player = menuCapablePlayer(helper);
        solve(helper, chest, player);

        float progress = destroyProgress(helper, player);
        helper.assertTrue(progress > 0.0F, "an unlocked chest must be breakable forever, structure-placed or not");
        helper.succeed();
    }

    private static void breakabilityFlagsSurviveReload(GameTestHelper helper) {
        place(helper, true);
        CipherChestBlockEntity chest = chestAt(helper);

        CipherChestBlockEntity reloaded = reload(helper, chest);
        helper.assertTrue(reloaded.isPlayerPlaced(), "the player-placed flag must survive a save/reload round trip");
        helper.assertTrue(reloaded.isBreakable(), "a reloaded player-placed chest must still read as breakable");
        helper.succeed();
    }

    private static void lootTableUnpacksOnUnlockAndOpen(GameTestHelper helper) {
        place(helper, false);
        CipherChestBlockEntity chest = chestAt(helper);
        chest.setLootTable(BuiltInLootTables.SIMPLE_DUNGEON, LOOT_SEED);
        ServerPlayer player = menuCapablePlayer(helper);

        helper.assertTrue(chest.getLootTable() != null, "the loot table key should still be set before the chest is ever opened");

        solve(helper, chest, player);

        helper.assertTrue(chest.isSolved(), "the chest should be solved after entering the canon code");
        helper.assertTrue(player.containerMenu instanceof ChestMenu, "the correct code should open the chest menu");
        helper.assertTrue(chest.getLootTable() == null,
                "opening the menu must unpack the loot table and clear the key (RandomizableContainer semantics)");
        helper.assertTrue(anySlotFilled(chest), "the unpacked loot table should have placed at least one item in the chest");
        helper.succeed();
    }

    /**
     * Direct, per-method proof that every {@link net.minecraft.world.Container} entry point -- not just the
     * emergent hopper behavior the tests below drive -- refuses access while locked: {@code getItem} and
     * {@code removeItem} return empty without unpacking the loot table, {@code setItem} is a no-op, and
     * {@code canPlaceItem}/{@code canTakeItem} both refuse. This is what a hopper, dropper, or hopper
     * minecart ultimately bottoms out in (see {@code HopperBlockEntity#getBlockContainer}, which resolves a
     * target purely via {@code instanceof Container} -- no capability lookup involved), so gating it here
     * gates all of them at once.
     */
    private static void containerApiDeniesAccessWhileLocked(GameTestHelper helper) {
        place(helper, false);
        CipherChestBlockEntity chest = chestAt(helper);
        chest.setLootTable(BuiltInLootTables.SIMPLE_DUNGEON, LOOT_SEED);
        ItemStack probe = new ItemStack(Items.DIAMOND);

        helper.assertTrue(!chest.isSolved(), "sanity: the chest must still be locked for this test to mean anything");
        helper.assertTrue(chest.getItem(0).isEmpty(), "a locked chest must present every slot as empty to a direct Container#getItem call");
        helper.assertTrue(chest.removeItem(0, 1).isEmpty(), "a locked chest must have nothing for Container#removeItem to take");
        helper.assertTrue(!chest.canTakeItem(chest, 0, probe), "a locked chest must refuse extraction through Container#canTakeItem");
        helper.assertTrue(!chest.canPlaceItem(0, probe), "a locked chest must refuse insertion through Container#canPlaceItem");

        chest.setItem(0, probe);
        helper.assertTrue(chest.getItem(0).isEmpty(), "a direct Container#setItem call must not be able to plant an item in a locked chest");
        helper.assertTrue(chest.getLootTable() != null, "a locked chest's loot table must not unpack via any direct Container-interface call");
        helper.succeed();
    }

    private static void hopperCannotExtractFromLockedChest(GameTestHelper helper) {
        place(helper, false);
        CipherChestBlockEntity chest = chestAt(helper);
        chest.setLootTable(BuiltInLootTables.SIMPLE_DUNGEON, LOOT_SEED);
        BlockPos hopperPos = POS.below();
        helper.setBlock(hopperPos, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));

        helper.runAtTickTime(HOPPER_TICK_BUDGET, () -> {
            helper.assertTrue(chest.getLootTable() != null,
                    "a locked chest's loot table must survive a real hopper ticking beneath it for a generous window");
            helper.assertTrue(hopperIsEmpty(helper, hopperPos), "a hopper below a locked chest must never extract anything");
            helper.succeed();
        });
    }

    private static void hopperCannotInsertIntoLockedChest(GameTestHelper helper) {
        place(helper, false);
        BlockPos hopperPos = POS.above();
        helper.setBlock(hopperPos, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        HopperBlockEntity hopper = helper.getBlockEntity(hopperPos, HopperBlockEntity.class);
        hopper.setItem(0, new ItemStack(Items.DIAMOND));

        helper.runAtTickTime(HOPPER_TICK_BUDGET, () -> {
            ItemStack stillHeld = hopper.getItem(0);
            helper.assertTrue(!stillHeld.isEmpty() && stillHeld.getCount() == 1,
                    "a hopper feeding a locked chest must still hold its own item -- a successful push would have consumed it");
            helper.succeed();
        });
    }

    private static void hopperCannotExtractDuringLockoutWindow(GameTestHelper helper) {
        place(helper, false);
        CipherChestBlockEntity chest = chestAt(helper);
        Player dialer = helper.makeMockServerPlayer(GameType.SURVIVAL);
        int cell = firstBlankCell(chest);
        if (chest.displayValueAt(cell) == CipherChestSquare.valueAt(cell)) {
            helper.useBlock(POS, dialer, dialHit(helper, cell));
        }
        helper.useBlock(POS, dialer, latchHit(helper)); // a wrong guess: scrambles the dials, starts the lockout

        long gameTime = helper.getLevel().getGameTime();
        helper.assertTrue(!chest.isSolved() && chest.isLockedOut(gameTime), "sanity: the chest must be in its wrong-guess lockout window");

        chest.setLootTable(BuiltInLootTables.SIMPLE_DUNGEON, LOOT_SEED);
        BlockPos hopperPos = POS.below();
        helper.setBlock(hopperPos, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));

        // Strictly inside CipherChestBlockEntity.LOCKOUT_TICKS (60), not just "still unsolved" some time
        // after it lapsed -- generous enough for several hopper cooldown cycles either way.
        long checkTick = CipherChestBlockEntity.LOCKOUT_TICKS / 2;
        helper.runAtTickTime(checkTick, () -> {
            helper.assertTrue(chest.isLockedOut(helper.getLevel().getGameTime()), "sanity: the check must still land inside the lockout window");
            helper.assertTrue(chest.getLootTable() != null,
                    "the lockout window is still an unsolved chest -- a hopper must not unpack or extract during it either");
            helper.assertTrue(hopperIsEmpty(helper, hopperPos), "a hopper below a lockout-window chest must never extract anything");
            helper.succeed();
        });
    }

    private static void hopperRoutesResumeOnceUnlocked(GameTestHelper helper) {
        place(helper, false);
        CipherChestBlockEntity chest = chestAt(helper);
        chest.setLootTable(BuiltInLootTables.SIMPLE_DUNGEON, LOOT_SEED);
        ServerPlayer player = menuCapablePlayer(helper);
        solve(helper, chest, player);
        helper.assertTrue(chest.isSolved() && anySlotFilled(chest), "sanity: the chest should be solved and loot-filled before the hopper checks");

        BlockPos extractHopperPos = POS.below();
        helper.setBlock(extractHopperPos, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));

        BlockPos insertHopperPos = POS.east();
        helper.setBlock(insertHopperPos, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.WEST));
        HopperBlockEntity insertHopper = helper.getBlockEntity(insertHopperPos, HopperBlockEntity.class);
        insertHopper.setItem(0, new ItemStack(Items.EMERALD));

        helper.runAtTickTime(HOPPER_TICK_BUDGET, () -> {
            helper.assertTrue(!hopperIsEmpty(helper, extractHopperPos), "an unlocked chest must feed a hopper below it exactly like a vanilla chest");
            helper.assertTrue(hopperIsEmpty(helper, insertHopperPos),
                    "an unlocked chest must accept a hopper's push exactly like a vanilla chest (the emerald should have moved in)");
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------------------------------- helpers

    private static void place(GameTestHelper helper, boolean playerPlaced) {
        helper.setBlock(POS, RelictBlocks.CIPHER_CHEST.get().defaultBlockState().setValue(CipherChestBlock.FACING, FACING));
        chestAt(helper).randomize(RandomSource.create(SEED), playerPlaced);
    }

    /**
     * {@link GameTestHelper#makeMockServerPlayer} leaves {@code ServerPlayer.connection} null, which is
     * fine for most tests but crashes {@code ServerPlayer#openMenu} (it sends a packet over that
     * connection) -- exactly the call this test must exercise to prove that crash doesn't happen. Only
     * {@code makeMockServerPlayerInLevel} wires a real (loopback) connection via
     * {@code PlayerList#placeNewPlayer}, so any test that needs the chest menu to actually open uses it
     * instead. It is {@code @Deprecated(forRemoval = true)} in the decompiled 26.2.0.64 sources with no
     * non-deprecated replacement that provides a working connection; flagged here as a fragile dependency
     * for a future MC version to watch.
     */
    private static ServerPlayer menuCapablePlayer(GameTestHelper helper) {
        return helper.makeMockServerPlayerInLevel();
    }

    private static CipherChestBlockEntity chestAt(GameTestHelper helper) {
        if (helper.getBlockEntity(POS, CipherChestBlockEntity.class) instanceof CipherChestBlockEntity chest) {
            return chest;
        }
        throw new IllegalStateException("No CipherChestBlockEntity at " + POS);
    }

    private static int firstBlankCell(CipherChestBlockEntity chest) {
        for (int cell = 0; cell < CipherChestSquare.CELL_COUNT; cell++) {
            if (chest.isBlank(cell)) {
                return cell;
            }
        }
        throw new IllegalStateException("No blank cell -- CipherChestBlockEntity.DEFAULT_BLANK_COUNT should be >= 1");
    }

    /** Dials every blank cell to its canon value via real clicks, then clicks the latch. */
    private static void solve(GameTestHelper helper, CipherChestBlockEntity chest, Player player) {
        for (int cell = 0; cell < CipherChestSquare.CELL_COUNT; cell++) {
            if (!chest.isBlank(cell)) {
                continue;
            }
            int target = CipherChestSquare.valueAt(cell);
            BlockHitResult dialHit = dialHit(helper, cell);
            int guard = 0;
            while (chest.displayValueAt(cell) != target && guard++ <= CipherChestSquare.MAX_VALUE) {
                helper.useBlock(POS, player, dialHit);
            }
            helper.assertTrue(chest.displayValueAt(cell) == target, "dial at cell " + cell + " should reach its canon value");
        }
        helper.useBlock(POS, player, latchHit(helper));
    }

    private static boolean hopperIsEmpty(GameTestHelper helper, BlockPos hopperPos) {
        HopperBlockEntity hopper = helper.getBlockEntity(hopperPos, HopperBlockEntity.class);
        return hopper.isEmpty();
    }

    private static boolean anySlotFilled(CipherChestBlockEntity chest) {
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            if (!chest.getItem(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static float destroyProgress(GameTestHelper helper, Player player) {
        BlockState state = helper.getBlockState(POS);
        return state.getDestroyProgress(player, helper.getLevel(), helper.absolutePos(POS));
    }

    private static CipherChestBlockEntity reload(GameTestHelper helper, CipherChestBlockEntity chest) {
        Provider registries = helper.getLevel().registryAccess();
        // loadStatic needs the "id" field to know which BlockEntityType to construct -- saveWithoutMetadata
        // (what CipherChestBlockEntity#getUpdateTag uses for client sync) deliberately omits it, since the
        // client already knows the type from context. saveWithFullMetadata writes it (plus x/y/z, unused
        // here) for exactly this kind of standalone round trip.
        CompoundTag tag = chest.saveWithFullMetadata(registries);
        BlockPos absolutePos = helper.absolutePos(POS);
        BlockState state = helper.getLevel().getBlockState(absolutePos);
        BlockEntity reloaded = BlockEntity.loadStatic(absolutePos, state, tag, registries);
        if (reloaded instanceof CipherChestBlockEntity reloadedChest) {
            return reloadedChest;
        }
        throw helper.assertionException("reload round-trip did not produce a CipherChestBlockEntity");
    }

    private static BlockHitResult dialHit(GameTestHelper helper, int cellIndex) {
        AABB box = CipherChestFaceLayout.dialHoverShape(FACING, cellIndex).bounds();
        return hitAt(helper, Direction.UP, center(box.minX, box.maxX), center(box.minY, box.maxY), center(box.minZ, box.maxZ));
    }

    private static BlockHitResult latchHit(GameTestHelper helper) {
        AABB box = CipherChestFaceLayout.latchHoverShape(FACING).bounds();
        return hitAt(helper, FACING, center(box.minX, box.maxX), center(box.minY, box.maxY), center(box.minZ, box.maxZ));
    }

    private static BlockHitResult hitAt(GameTestHelper helper, Direction face, double localX, double localY, double localZ) {
        BlockPos absolutePos = helper.absolutePos(POS);
        Vec3 location = Vec3.atLowerCornerOf(absolutePos).add(localX, localY, localZ);
        return new BlockHitResult(location, face, absolutePos, false);
    }

    private static double center(double min, double max) {
        return (min + max) / 2.0;
    }

}
