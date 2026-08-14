package us.drullk.relict.datagen;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.data.PackOutput;
import us.drullk.relict.Relict;
import us.drullk.relict.init.RelictItems;

public class RelictModels extends ModelProvider {

    public RelictModels(PackOutput output) {
        super(output, Relict.MODID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        itemModels.generateDynamicTrimmableItem(RelictItems.VITAL_VIZARD.get(), itemModels.createFlatItemModel(RelictItems.VITAL_VIZARD.get(), ModelTemplates.FLAT_ITEM), ItemModelGenerators.TRIM_PREFIX_HELMET);

        itemModels.generateFlatItem(RelictItems.RANGING_CAISSON.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(RelictItems.RESTLESS_STRIDERS.get(), ModelTemplates.FLAT_ITEM);

        itemModels.generateDynamicTrimmableItem(RelictItems.GROUNDING_TREADS.get(), itemModels.createFlatItemModel(RelictItems.GROUNDING_TREADS.get(), ModelTemplates.FLAT_ITEM), ItemModelGenerators.TRIM_PREFIX_BOOTS);

        itemModels.generateDynamicTrimmableItem(RelictItems.SPENT_VIZARD.get(), itemModels.createFlatItemModel(RelictItems.SPENT_VIZARD.get(), ModelTemplates.FLAT_ITEM), ItemModelGenerators.TRIM_PREFIX_HELMET);
    }

}
