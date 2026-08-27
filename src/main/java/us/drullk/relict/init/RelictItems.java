package us.drullk.relict.init;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import us.drullk.relict.Relict;
import us.drullk.relict.item.RubbingItem;
import us.drullk.relict.item.SeismicLocatorItem;
import us.drullk.relict.item.StoredCharges;
import us.drullk.relict.item.VizardItem;
import us.drullk.relict.item.WeatherglassItem;

import java.util.List;
import java.util.function.UnaryOperator;

public class RelictItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Relict.MODID);

    // 1 durability per 10-tick scan (RelictEvents); 3600s * 20t/s / 10 = the helmet lasts one Mars hour
    public static final int VITAL_VIZARD_DURABILITY = 7_200;

    public static final DeferredItem<Item> VITAL_VIZARD = ITEMS.registerItem("vital_vizard", VizardItem::new, properties -> serviceArmor(properties, ArmorType.HELMET, modifiers -> modifiers
            .withModifierAdded(RelictAttributes.MARS_LIFE_SUPPORT, RelictAttributes.enable(RelictAttributes.MARS_LIFE_SUPPORT.getId().withPrefix("vizard_")), EquipmentSlotGroup.HEAD)
            .withModifierAdded(RelictAttributes.NAUSEA_IMMUNITY, RelictAttributes.enable(RelictAttributes.NAUSEA_IMMUNITY.getId().withPrefix("vizard_")), EquipmentSlotGroup.HEAD)
            .withModifierAdded(Attributes.OXYGEN_BONUS, add(Relict.id("vizard_oxygen_bonus"), 4.0), EquipmentSlotGroup.HEAD)
    ).component(DataComponents.EQUIPPABLE, vizardEquippable(RelictArmorMaterials.VITAL_VIZARD_ASSET)).durability(VITAL_VIZARD_DURABILITY));
    public static final DeferredItem<Item> SPENT_VIZARD = ITEMS.registerItem("spent_vizard", Item::new, properties -> properties.stacksTo(1).attributes(RelictArmorMaterials.SERVICE.createAttributes(ArmorType.HELMET)).component(DataComponents.EQUIPPABLE, vizardEquippable(RelictArmorMaterials.SPENT_VIZARD_ASSET)));
    public static final DeferredItem<Item> RANGING_CAISSON = ITEMS.registerItem("ranging_caisson", Item::new, properties -> serviceArmor(properties, ArmorType.CHESTPLATE, UnaryOperator.identity()).component(RelictDataComponents.STORED_CHARGE.get(), StoredCharges.EMPTY));
    public static final DeferredItem<Item> RESTLESS_STRIDERS = ITEMS.registerItem("restless_striders", Item::new, properties -> serviceArmor(properties, ArmorType.LEGGINGS, modifiers -> modifiers
            .withModifierAdded(Attributes.MOVEMENT_SPEED, new AttributeModifier(Relict.id("striders_movement_speed"), 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), EquipmentSlotGroup.LEGS)
            .withModifierAdded(Attributes.STEP_HEIGHT, new AttributeModifier(Relict.id("striders_step_height"), 0.5, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.LEGS)
            .withModifierAdded(Attributes.WATER_MOVEMENT_EFFICIENCY, new AttributeModifier(Relict.id("striders_water_movement"), 1.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.LEGS)
    ));
    public static final DeferredItem<Item> GROUNDING_TREADS = ITEMS.registerItem("grounding_treads", Item::new, properties -> serviceArmor(properties, ArmorType.BOOTS, modifiers -> modifiers
            .withModifierAdded(Attributes.SAFE_FALL_DISTANCE, add(Relict.id("treads_safe_fall_distance"), 10.0), EquipmentSlotGroup.FEET)
            .withModifierAdded(RelictAttributes.ELECTRIC_DAMAGE, add(RelictAttributes.ELECTRIC_DAMAGE.getId().withPrefix("treads_"), -0.1), EquipmentSlotGroup.FEET)
    ));

    public static final DeferredItem<Item> SEISMIC_LOCATOR = ITEMS.registerItem("seismic_locator", SeismicLocatorItem::new, properties -> properties.stacksTo(1));
    public static final DeferredItem<Item> WEATHERGLASS = ITEMS.registerItem("weatherglass", WeatherglassItem::new, properties -> properties.stacksTo(1));

    public static final DeferredItem<net.minecraft.world.item.BlockItem> LAB_BLOCK = ITEMS.registerSimpleBlockItem(RelictBlocks.LAB_BLOCK);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> LAB_SHAFT = ITEMS.registerSimpleBlockItem(RelictBlocks.LAB_SHAFT);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> LAB_MAST = ITEMS.registerSimpleBlockItem(RelictBlocks.LAB_MAST);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> LAB_SLAB = ITEMS.registerSimpleBlockItem(RelictBlocks.LAB_SLAB);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> LAB_STAIRS = ITEMS.registerSimpleBlockItem(RelictBlocks.LAB_STAIRS);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> ROVER_WHEEL = ITEMS.registerSimpleBlockItem(RelictBlocks.ROVER_WHEEL);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> SOLAR_PANEL = ITEMS.registerSimpleBlockItem(RelictBlocks.SOLAR_PANEL);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> SOLAR_PANEL_SPRINKLED = ITEMS.registerSimpleBlockItem(RelictBlocks.SOLAR_PANEL_SPRINKLED);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> SOLAR_PANEL_DUSTED = ITEMS.registerSimpleBlockItem(RelictBlocks.SOLAR_PANEL_DUSTED);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> SOLAR_PANEL_SANDED = ITEMS.registerSimpleBlockItem(RelictBlocks.SOLAR_PANEL_SANDED);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> CIPHER_CHEST = ITEMS.registerSimpleBlockItem(RelictBlocks.CIPHER_CHEST);

    public static final DeferredItem<RubbingItem> RUBBING = ITEMS.registerItem("rubbing", RubbingItem::new, properties -> properties
            .stacksTo(1)
            .component(DataComponents.MAP_ID, new MapId(0))
            .component(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.MAP_ID, true))
            .component(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.relict.rubbing.tooltip")))));

    public static final DeferredItem<net.minecraft.world.item.BlockItem> DUST_LAYER = ITEMS.registerSimpleBlockItem(RelictBlocks.DUST_LAYER);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> DRY_SNOW = ITEMS.registerSimpleBlockItem(RelictBlocks.DRY_SNOW);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> DRY_SNOW_LAYER = ITEMS.registerSimpleBlockItem(RelictBlocks.DRY_SNOW_LAYER);

    public static final DeferredItem<net.minecraft.world.item.BlockItem> BASALT_SAND = ITEMS.registerSimpleBlockItem(RelictBlocks.BASALT_SAND);

    private static Item.Properties serviceArmor(Item.Properties properties, ArmorType type, UnaryOperator<ItemAttributeModifiers> extras) {
        return properties.humanoidArmor(RelictArmorMaterials.SERVICE, type).attributes(extras.apply(RelictArmorMaterials.SERVICE.createAttributes(type)));
    }

    private static Equippable vizardEquippable(ResourceKey<EquipmentAsset> asset) {
        return Equippable.builder(EquipmentSlot.HEAD).setEquipSound(RelictArmorMaterials.SERVICE.equipSound()).setAsset(asset).build();
    }

    private static AttributeModifier add(Identifier id, double amount) {
        return new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE);
    }

}
