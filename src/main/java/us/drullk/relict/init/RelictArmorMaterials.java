package us.drullk.relict.init;

import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import us.drullk.relict.Relict;
import us.drullk.relict.RelictTags;

import java.util.EnumMap;

public class RelictArmorMaterials {

    public static final ResourceKey<EquipmentAsset> SERVICE_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, Relict.id("service"));

    public static final ResourceKey<EquipmentAsset> VITAL_VIZARD_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, Relict.id("vital_vizard"));
    public static final ResourceKey<EquipmentAsset> SPENT_VIZARD_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, Relict.id("spent_vizard"));

    public static final ArmorMaterial SERVICE = new ArmorMaterial(
            7,
            Util.make(new EnumMap<>(ArmorType.class), defenseMap -> {
                defenseMap.put(ArmorType.HELMET, 2);
                defenseMap.put(ArmorType.CHESTPLATE, 5);
                defenseMap.put(ArmorType.LEGGINGS, 3);
                defenseMap.put(ArmorType.BOOTS, 1);
            }),
            25,
            SoundEvents.ARMOR_EQUIP_GOLD,
            0.0F,
            0.0F,
            RelictTags.REPAIRS_SERVICE_ARMOR,
            SERVICE_ASSET);

}
