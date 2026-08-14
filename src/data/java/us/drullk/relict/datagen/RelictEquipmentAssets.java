package us.drullk.relict.datagen;

import net.minecraft.client.data.models.EquipmentAssetProvider;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.equipment.EquipmentAsset;
import us.drullk.relict.Relict;
import us.drullk.relict.init.RelictArmorMaterials;

import java.util.function.BiConsumer;

public class RelictEquipmentAssets extends EquipmentAssetProvider {

    public RelictEquipmentAssets(PackOutput output) {
        super(output);
    }

    @Override
    protected void registerModels(BiConsumer<ResourceKey<EquipmentAsset>, EquipmentClientInfo> output) {
        output.accept(RelictArmorMaterials.SERVICE_ASSET, EquipmentClientInfo.builder().addHumanoidLayers(Relict.id("service")).build());
    }

}
