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
        itemModels.generateFlatItem(RelictItems.VITAL_VIZARD.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(RelictItems.RANGING_CAISSON.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(RelictItems.RESTLESS_STRIDERS.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(RelictItems.GROUNDING_TREADS.get(), ModelTemplates.FLAT_ITEM);

        itemModels.generateFlatItem(RelictItems.SPENT_VIZARD.get(), RelictItems.VITAL_VIZARD.get(), ModelTemplates.FLAT_ITEM); // FIXME Create broken sprite
    }

}
