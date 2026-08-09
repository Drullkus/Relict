package us.drullk.fossilplanet.init;

import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import us.drullk.fossilplanet.TheFossilizedPlanet;

public class TFPItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TheFossilizedPlanet.MODID);

    public static final DeferredItem<Item> EXAMPLE_ITEM = ITEMS.registerSimpleItem("example_item", p -> p.food(new FoodProperties.Builder().alwaysEdible().nutrition(1).saturationModifier(2f).build()));
    public static final DeferredItem<BlockItem> EXAMPLE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("example_block", TFPBlocks.EXAMPLE_BLOCK);

}
