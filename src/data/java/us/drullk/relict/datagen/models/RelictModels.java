package us.drullk.relict.datagen.models;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.util.random.WeightedList;
import us.drullk.relict.Relict;
import us.drullk.relict.datagen.cipherchest.CipherChestModelGenerator;
import us.drullk.relict.init.RelictBlocks;
import us.drullk.relict.init.RelictItems;

/**
 * Client-side model/blockstate/item-model datagen entry point. Thin by design: each feature area owns its
 * own generator class (see {@link ServiceArmorModels}, {@link WeatherglassModels}, {@link WreckModels}),
 * composed here from the vanilla {@link BlockModelGenerators}/{@link ItemModelGenerators} entry points this
 * provider is handed.
 */
public class RelictModels extends ModelProvider {

    public RelictModels(PackOutput output) {
        super(output, Relict.MODID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        registerMarsPortal(blockModels);

        ServiceArmorModels.register(itemModels);
        itemModels.generateFlatItem(RelictItems.SEISMIC_LOCATOR.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(RelictItems.BURNING_GLASS.get(), ModelTemplates.FLAT_ITEM);
        WeatherglassModels.register(itemModels);

        WreckModels.register(blockModels, itemModels);

        DustLayerModels.register(blockModels, itemModels);

        registerBasaltSand(blockModels, itemModels);

        CipherChestModelGenerator.bootstrap(blockModels, itemModels);
    }

    // FIXME replace nether_portal placeholder for custom
    private static void registerMarsPortal(BlockModelGenerators blockModels) {
        Identifier portalModel = ModelTemplates.CUBE_ALL.create(RelictBlocks.MARS_PORTAL.get(),
                TextureMapping.cube(new Material(Identifier.withDefaultNamespace("block/nether_portal"))), blockModels.modelOutput);
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(RelictBlocks.MARS_PORTAL.get(),
                new MultiVariant(WeightedList.of(new Variant(portalModel)))));
    }

    private static void registerBasaltSand(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        Identifier modelId = ModelTemplates.CUBE_ALL.create(RelictBlocks.BASALT_SAND.get(),
                TextureMapping.cube(new Material(Relict.id("block/basalt_sand"))), blockModels.modelOutput);
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(RelictBlocks.BASALT_SAND.get(),
                BlockModelGenerators.createRotatedVariants(new Variant(modelId))));
        itemModels.itemModelOutput.accept(RelictItems.BASALT_SAND.get(), ItemModelUtils.plainModel(modelId));
    }

}
