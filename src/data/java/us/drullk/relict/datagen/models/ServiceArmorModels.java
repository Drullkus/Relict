package us.drullk.relict.datagen.models;

import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.model.ModelTemplates;
import us.drullk.relict.init.RelictItems;

/** Item models for the service armor kit: Vital Vizard, Ranging Caisson, Restless Striders, Grounding Treads, Spent Vizard. */
public final class ServiceArmorModels {

    private ServiceArmorModels() {
    }

    public static void register(ItemModelGenerators itemModels) {
        itemModels.generateDynamicTrimmableItem(RelictItems.VITAL_VIZARD.get(),
                itemModels.createFlatItemModel(RelictItems.VITAL_VIZARD.get(), ModelTemplates.FLAT_ITEM), ItemModelGenerators.TRIM_PREFIX_HELMET);

        itemModels.generateFlatItem(RelictItems.RANGING_CAISSON.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(RelictItems.RESTLESS_STRIDERS.get(), ModelTemplates.FLAT_ITEM);

        itemModels.generateDynamicTrimmableItem(RelictItems.GROUNDING_TREADS.get(),
                itemModels.createFlatItemModel(RelictItems.GROUNDING_TREADS.get(), ModelTemplates.FLAT_ITEM), ItemModelGenerators.TRIM_PREFIX_BOOTS);

        itemModels.generateDynamicTrimmableItem(RelictItems.SPENT_VIZARD.get(),
                itemModels.createFlatItemModel(RelictItems.SPENT_VIZARD.get(), ModelTemplates.FLAT_ITEM), ItemModelGenerators.TRIM_PREFIX_HELMET);
    }

}
