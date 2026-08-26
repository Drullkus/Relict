package us.drullk.relict.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import us.drullk.relict.Relict;
import us.drullk.relict.RelictTags;
import us.drullk.relict.init.RelictBlocks;
import us.drullk.relict.init.RelictItems;

/**
 * Basalt sand's own gametest pack: falling behavior, the glass smelt, and the {@code c:sands} tag
 * membership. Registered through {@link RelictGameTests} into the shared {@code relict:default}
 * environment and wrapped in the shared {@link RelictFunctionGameTestInstance} -- the same framework
 * {@code CipherChestGameTests} uses -- rather than each feature carrying its own environment and its own
 * code-registered {@code GameTestInstance} subclass.
 */
public final class BasaltSandGameTests {

    private static final Identifier FALL_STRUCTURE = Relict.id("gametest/basalt_sand_fall");
    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");

    private BasaltSandGameTests() {
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        event.registerTest(Relict.id("basalt_sand_falls_like_sand"), new RelictFunctionGameTestInstance(
                BasaltSandGameTests::fallsLikeSand, Component.literal("Basalt Sand: falls like sand and settles"),
                new TestData<>(environment, FALL_STRUCTURE, 100, 0, true)));
        event.registerTest(Relict.id("basalt_sand_smelts_into_glass"), new RelictFunctionGameTestInstance(
                BasaltSandGameTests::smeltsIntoGlass, Component.literal("Basalt Sand: smelts into glass"),
                new TestData<>(environment, EMPTY_STRUCTURE, 20, 0, true)));
        event.registerTest(Relict.id("basalt_sand_sand_tags"), new RelictFunctionGameTestInstance(
                BasaltSandGameTests::tagMembership, Component.literal("Basalt Sand: c:sands tag membership"),
                new TestData<>(environment, EMPTY_STRUCTURE, 20, 0, true)));
    }

    /** Places basalt_sand over air above a floor and waits for it to fall and settle as the block. */
    private static void fallsLikeSand(GameTestHelper helper) {
        BlockPos floor = new BlockPos(1, 0, 1);
        BlockPos start = new BlockPos(1, 3, 1);

        helper.setBlock(floor, Blocks.STONE);
        helper.setBlock(start, RelictBlocks.BASALT_SAND.get());

        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.AIR, start);
            helper.assertBlockPresent(RelictBlocks.BASALT_SAND.get(), floor.above());
        });
    }

    /**
     * Recipe-manager lookup rather than an actual furnace burn: driving a real furnace needs fuel, a
     * 200-tick cook, and a structure to hold it, all to re-prove what a direct recipe query answers in one
     * tick — basalt_sand is accepted by vanilla's own {@code glass} smelting recipe (it dispatches off the
     * {@code minecraft:smelts_to_glass} item tag, which the item tags provider adds basalt_sand to) and
     * that recipe assembles to glass.
     */
    private static void smeltsIntoGlass(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SingleRecipeInput input = new SingleRecipeInput(new ItemStack(RelictItems.BASALT_SAND.get()));

        boolean smeltsToGlass = level.getServer().getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, input, level)
                .map(recipe -> recipe.value().assemble(input).is(Items.GLASS))
                .orElse(false);

        helper.assertTrue(smeltsToGlass, "basalt_sand has no smelting recipe that assembles to glass");
        helper.succeed();
    }

    /** Runtime tag membership: basalt_sand is in c:sands/basalt, and c:sands/basalt is referenced into c:sands. */
    private static void tagMembership(GameTestHelper helper) {
        Holder.Reference<Block> block = RelictBlocks.BASALT_SAND.get().builtInRegistryHolder();

        helper.assertTrue(block.is(RelictTags.SANDS_BASALT), "basalt_sand is missing from c:sands/basalt");
        helper.assertTrue(block.is(Tags.Blocks.SANDS), "basalt_sand is missing from c:sands (via the c:sands/basalt reference)");
        helper.succeed();
    }

}
