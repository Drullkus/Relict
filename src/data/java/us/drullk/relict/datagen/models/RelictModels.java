package us.drullk.relict.datagen.models;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import us.drullk.relict.Relict;
import us.drullk.relict.block.RelictPortalBlock;
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

        RuinPaletteModels.register(blockModels, itemModels);

        DustLayerModels.register(blockModels, itemModels);

        registerBasaltSand(blockModels, itemModels);

        CipherChestModelGenerator.bootstrap(blockModels, itemModels);
    }

    // FIXME replace nether_portal placeholder texture for custom
    private static void registerMarsPortal(BlockModelGenerators blockModels) {
        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(RelictBlocks.MARS_PORTAL.get())
                        .with(PropertyDispatch.initial(RelictPortalBlock.AXIS)
                                .select(Direction.Axis.X, BlockModelGenerators.plainVariant(ModelLocationUtils.getModelLocation(Blocks.NETHER_PORTAL, "_ns")))
                                .select(Direction.Axis.Z, BlockModelGenerators.plainVariant(ModelLocationUtils.getModelLocation(Blocks.NETHER_PORTAL, "_ew")))));
    }

    private static void registerBasaltSand(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        Identifier modelId = ModelTemplates.CUBE_ALL.create(RelictBlocks.BASALT_SAND.get(),
                TextureMapping.cube(new Material(Relict.id("block/basalt_sand"))), blockModels.modelOutput);
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(RelictBlocks.BASALT_SAND.get(),
                BlockModelGenerators.createRotatedVariants(new Variant(modelId))));
        itemModels.itemModelOutput.accept(RelictItems.BASALT_SAND.get(), ItemModelUtils.plainModel(modelId));
    }

}
