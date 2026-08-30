package us.drullk.relict.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import us.drullk.relict.Relict;
import us.drullk.relict.init.RelictBlocks;
import us.drullk.relict.init.RelictItems;
import us.drullk.relict.init.worldgen.RelictTemplatePools;

/**
 * Overcast Mooring's own GameTest pack: a real jigsaw expansion through both templates plus the
 * two chest tables.
 */
public final class OvercastMooringGameTests {

    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");
    private static final Identifier ARENA = Relict.id("gametest/jigsaw_arena");

    /** Local jigsaw expansion anchor: centered in the 160x140x160 arena, with slack in every direction. */
    private static final BlockPos ANCHOR = new BlockPos(80, 70, 80);

    private static final ResourceKey<LootTable> DECK = ResourceKey.create(Registries.LOOT_TABLE, Relict.id("chests/overcast_mooring/deck"));
    private static final ResourceKey<LootTable> SHUTTLE = ResourceKey.create(Registries.LOOT_TABLE, Relict.id("chests/overcast_mooring/shuttle"));

    private static final long LOOT_SEED = 20260830L;
    private static final BlockPos CHEST_POS = new BlockPos(1, 2, 1);

    /**
     * Direct proof the nested {@code portal_ruin} reference actually resolved, seed-independent: shuttle's
     * own five direct pools can produce at most one guaranteed Vital Vizard plus one 2-3 count roll (4 max),
     * so any total above that is only reachable if the nested reference's own copy of the same pools fired.
     */
    private static final int VITAL_VIZARD_MAX_WITHOUT_REFERENCE = 4;

    private OvercastMooringGameTests() {
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        event.registerTest(id("jigsaw_expands_both_templates"), new RelictFunctionGameTestInstance(
                OvercastMooringGameTests::jigsawExpandsBothTemplates,
                Component.literal("Overcast Mooring: a real jigsaw expansion places both upper and lower"),
                new TestData<>(environment, ARENA, 100, 0, true)));
        event.registerTest(id("deck_rolls_weatherglass"), new RelictFunctionGameTestInstance(
                OvercastMooringGameTests::deckRollsWeatherglass,
                Component.literal("Overcast Mooring: the deck chest rolls a guaranteed Weatherglass and clears its LootTable key"),
                new TestData<>(environment, EMPTY_STRUCTURE, 20, 0, true)));
        event.registerTest(id("shuttle_unpacks_portal_ruin"), new RelictFunctionGameTestInstance(
                OvercastMooringGameTests::shuttleUnpacksPortalRuin,
                Component.literal("Overcast Mooring: the shuttle chest keeps its Burning Glass and unpacks the nested portal_ruin table"),
                new TestData<>(environment, EMPTY_STRUCTURE, 20, 0, true)));
    }

    private static Identifier id(String path) {
        return Relict.id("overcast_mooring/" + path);
    }

    // -------------------------------------------------------------------------------------------- jigsaw

    /**
     * Drives real jigsaw expansion through {@link JigsawPlacement#generateJigsaw} -- the same entry point
     * the vanilla {@code /place jigsaw} command uses -- rather than asserting on the datapack JSON. Both
     * junctions in {@code upper.nbt} share the literal jigsaw name {@code relict:overcast_mooring/upper}
     * (one targets {@code minecraft:structure_start}, the other targets {@code relict:overcast_mooring/lower}
     * -- verified straight off the committed NBT, see the impl report), so alignment can land on either one;
     * that only changes the world-space offset, never which jigsaws get processed for expansion, so the arena
     * margin here is sized for either case.
     * <p>
     * {@code upper.nbt} ships with zero {@code relict:}-namespaced blocks in its palette (all vanilla copper
     * and ice dressing -- see the impl report), so the "counted by relict: id" instruction cannot be honored
     * literally for it. {@code minecraft:diamond_block} stands in as its marker: it cannot occur in this
     * freshly-loaded arena by any means other than this placement.
     */
    private static void jigsawExpandsBothTemplates(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Holder<StructureTemplatePool> startPool = level.registryAccess()
                .lookupOrThrow(Registries.TEMPLATE_POOL)
                .getOrThrow(RelictTemplatePools.OVERCAST_MOORING_START);

        BlockPos position = helper.absolutePos(ANCHOR);
        boolean generated = JigsawPlacement.generateJigsaw(level, startPool, Relict.id("overcast_mooring/upper"), 1, position, false);
        helper.assertTrue(generated, "the start pool should generate a piece at the anchor position");

        boolean[] found = scanForMarkers(level, position, Blocks.DIAMOND_BLOCK, RelictBlocks.BASALT_SAND.get());
        helper.assertTrue(found[0], "expected minecraft:diamond_block (upper's marker -- see class doc) within the expansion radius");
        helper.assertTrue(found[1], "expected relict:basalt_sand (lower's marker) within the expansion radius");
        helper.succeed();
    }

    /** Single pass over the arena's real footprint, stopping as soon as both markers are seen. */
    private static boolean[] scanForMarkers(ServerLevel level, BlockPos anchor, Block upperMarker, Block lowerMarker) {
        int horizontalRadius = 75;
        int verticalRadius = 68;
        boolean foundUpper = false;
        boolean foundLower = false;

        for (BlockPos pos : BlockPos.betweenClosed(
                anchor.offset(-horizontalRadius, -verticalRadius, -horizontalRadius),
                anchor.offset(horizontalRadius, verticalRadius, horizontalRadius))) {
            if (foundUpper && foundLower) {
                break;
            }
            BlockState state = level.getBlockState(pos);
            foundUpper = foundUpper || state.is(upperMarker);
            foundLower = foundLower || state.is(lowerMarker);
        }

        return new boolean[] {foundUpper, foundLower};
    }

    // --------------------------------------------------------------------------------------------- loot

    private static void deckRollsWeatherglass(GameTestHelper helper) {
        RandomizableContainerBlockEntity chest = placeChest(helper);
        chest.setLootTable(DECK, LOOT_SEED);
        openChest(helper);

        helper.assertContainerContainsSingle(CHEST_POS, RelictItems.WEATHERGLASS.get());
        helper.assertTrue(chest.getLootTable() == null, "opening the chest must unpack the deck table and clear the LootTable key");
        helper.succeed();
    }

    /**
     * See {@link #VITAL_VIZARD_MAX_WITHOUT_REFERENCE}: the shuttle table's own five pools cap Vital Vizard
     * at 4, so seeing more than that is only possible if the nested {@code portal_ruin} reference (itself a
     * full copy of the same pools) actually unpacked.
     */
    private static void shuttleUnpacksPortalRuin(GameTestHelper helper) {
        RandomizableContainerBlockEntity chest = placeChest(helper);
        chest.setLootTable(SHUTTLE, LOOT_SEED);
        openChest(helper);

        helper.assertContainerContains(CHEST_POS, RelictItems.BURNING_GLASS.get());
        int vitalVizardCount = chest.countItem(RelictItems.VITAL_VIZARD.get());
        helper.assertTrue(vitalVizardCount > VITAL_VIZARD_MAX_WITHOUT_REFERENCE,
                "Vital Vizard count " + vitalVizardCount + " should exceed " + VITAL_VIZARD_MAX_WITHOUT_REFERENCE
                        + " only if the nested portal_ruin reference actually unpacked");
        helper.assertTrue(chest.getLootTable() == null, "opening the chest must unpack the shuttle table and clear the LootTable key");
        helper.succeed();
    }

    private static RandomizableContainerBlockEntity placeChest(GameTestHelper helper) {
        helper.setBlock(CHEST_POS, Blocks.CHEST);
        return helper.getBlockEntity(CHEST_POS, RandomizableContainerBlockEntity.class);
    }

    /** Opens the chest through a real click, exactly like a player would -- see {@code menuCapablePlayer}'s
     *  precedent in {@code CipherChestGameTests}: only a loopback-connected mock player survives the menu
     *  packet {@code ServerPlayer#openMenu} sends. */
    private static void openChest(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.useBlock(CHEST_POS, player);
    }

}
