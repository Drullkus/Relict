package us.drullk.relict.datagen;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.util.random.WeightedList;
import us.drullk.relict.Relict;
import us.drullk.relict.init.RelictBlocks;
import us.drullk.relict.init.RelictItems;

public class RelictModels extends ModelProvider {

    public RelictModels(PackOutput output) {
        super(output, Relict.MODID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        // FIXME replace nether_portal placeholder for custom
        Identifier portalModel = ModelTemplates.CUBE_ALL.create(RelictBlocks.MARS_PORTAL.get(),
                TextureMapping.cube(new Material(Identifier.withDefaultNamespace("block/nether_portal"))), blockModels.modelOutput);
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(RelictBlocks.MARS_PORTAL.get(),
                new MultiVariant(WeightedList.of(new Variant(portalModel)))));

        itemModels.generateDynamicTrimmableItem(RelictItems.VITAL_VIZARD.get(), itemModels.createFlatItemModel(RelictItems.VITAL_VIZARD.get(), ModelTemplates.FLAT_ITEM), ItemModelGenerators.TRIM_PREFIX_HELMET);

        itemModels.generateFlatItem(RelictItems.RANGING_CAISSON.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(RelictItems.RESTLESS_STRIDERS.get(), ModelTemplates.FLAT_ITEM);

        itemModels.generateDynamicTrimmableItem(RelictItems.GROUNDING_TREADS.get(), itemModels.createFlatItemModel(RelictItems.GROUNDING_TREADS.get(), ModelTemplates.FLAT_ITEM), ItemModelGenerators.TRIM_PREFIX_BOOTS);

        itemModels.generateDynamicTrimmableItem(RelictItems.SPENT_VIZARD.get(), itemModels.createFlatItemModel(RelictItems.SPENT_VIZARD.get(), ModelTemplates.FLAT_ITEM), ItemModelGenerators.TRIM_PREFIX_HELMET);

        itemModels.generateFlatItem(RelictItems.SEISMIC_LOCATOR.get(), ModelTemplates.FLAT_ITEM);
    }

}
