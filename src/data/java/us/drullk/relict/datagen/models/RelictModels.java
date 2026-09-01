package us.drullk.relict.datagen.models;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelTemplate;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import us.drullk.relict.Relict;
import us.drullk.relict.block.RelictPortalBlock;
import us.drullk.relict.datagen.cipherchest.CipherChestModelGenerator;
import us.drullk.relict.init.RelictBlocks;
import us.drullk.relict.init.RelictItems;

import java.util.Optional;

/**
 * Client-side model/blockstate/item-model datagen entry point. Thin by design: each feature area owns its
 * own generator class (see {@link ServiceArmorModels}, {@link WeatherglassModels}, {@link WreckModels}),
 * composed here from the vanilla {@link BlockModelGenerators}/{@link ItemModelGenerators} entry points this
 * provider is handed.
 */
public class RelictModels extends ModelProvider {

    private static final ModelTemplate MARS_PORTAL_NS_TEMPLATE =
            new ModelTemplate(Optional.of(Relict.id("block/template_mars_portal_ns")), Optional.of("_ns"), TextureSlot.ALL);
    private static final ModelTemplate MARS_PORTAL_EW_TEMPLATE =
            new ModelTemplate(Optional.of(Relict.id("block/template_mars_portal_ew")), Optional.of("_ew"), TextureSlot.ALL);

    public RelictModels(PackOutput output) {
        super(output, Relict.MODID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        registerMarsPortal(blockModels);

        ServiceArmorModels.register(itemModels);
        itemModels.generateStandardCompassItem(RelictItems.SEISMIC_LOCATOR.get());
        itemModels.generateFlatItem(RelictItems.BURNING_GLASS.get(), ModelTemplates.FLAT_ITEM);
        WeatherglassModels.register(itemModels);

        WreckModels.register(blockModels, itemModels);

        RuinPaletteModels.register(blockModels, itemModels);

        DustLayerModels.register(blockModels, itemModels);

        registerBasaltSand(blockModels, itemModels);

        CipherChestModelGenerator.bootstrap(blockModels, itemModels);
    }

    private static void registerMarsPortal(BlockModelGenerators blockModels) {
        TextureMapping textures = TextureMapping.cube(new Material(Relict.id("block/mars_portal")));
        Identifier nsModel = MARS_PORTAL_NS_TEMPLATE.create(RelictBlocks.MARS_PORTAL.get(), textures, blockModels.modelOutput);
        Identifier ewModel = MARS_PORTAL_EW_TEMPLATE.create(RelictBlocks.MARS_PORTAL.get(), textures, blockModels.modelOutput);

        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(RelictBlocks.MARS_PORTAL.get())
                        .with(PropertyDispatch.initial(RelictPortalBlock.AXIS)
                                .select(Direction.Axis.X, BlockModelGenerators.plainVariant(nsModel))
                                .select(Direction.Axis.Z, BlockModelGenerators.plainVariant(ewModel))));
    }

    private static void registerBasaltSand(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        Identifier modelId = ModelTemplates.CUBE_ALL.create(RelictBlocks.BASALT_SAND.get(),
                TextureMapping.cube(new Material(Relict.id("block/basalt_sand"))), blockModels.modelOutput);
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(RelictBlocks.BASALT_SAND.get(),
                BlockModelGenerators.createRotatedVariants(new Variant(modelId))));
        itemModels.itemModelOutput.accept(RelictItems.BASALT_SAND.get(), ItemModelUtils.plainModel(modelId));
    }

}
