package us.drullk.relict.datagen.models;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelTemplate;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import us.drullk.relict.Relict;
import us.drullk.relict.block.DustLayerBlock;
import us.drullk.relict.init.RelictBlocks;
import us.drullk.relict.init.RelictItems;

import java.util.Optional;

/**
 * Models for the dust layer + dry snow pair: 8 shared box-height templates (checked in as
 * {@code block/template_layer_height2..16.json}, one geometry family reused across every layer block this
 * mod ever adds) times a texture per block/state. Kept out of {@link RelictModels} entirely — one call from
 * {@link RelictModels#registerModels} is the whole seam — a deliberately small, single-purpose class so it
 * doesn't collide with unrelated concurrent work on the rest of datagen.
 */
public final class DustLayerModels {

    private DustLayerModels() {
    }

    private static final int[] HEIGHTS = {2, 4, 6, 8, 10, 12, 14, 16};

    public static void register(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        ModelTemplate[] layerTemplates = layerTemplates();

        registerDustLayer(blockModels, itemModels, layerTemplates);
        registerDrySnowLayer(blockModels, itemModels, layerTemplates);
        registerDrySnowBlock(blockModels, itemModels);
    }

    private static ModelTemplate[] layerTemplates() {
        ModelTemplate[] templates = new ModelTemplate[HEIGHTS.length];
        for (int i = 0; i < HEIGHTS.length; i++) {
            templates[i] = new ModelTemplate(Optional.of(Relict.id("block/template_layer_height" + HEIGHTS[i])), Optional.empty(), TextureSlot.ALL);
        }
        return templates;
    }

    private static void registerDustLayer(BlockModelGenerators blockModels, ItemModelGenerators itemModels, ModelTemplate[] layerTemplates) {
        Material base = new Material(Identifier.withDefaultNamespace("block/red_sand"));
        Material trodden = new Material(Relict.id("block/trodden_red_sand"));

        Identifier[] baseModels = new Identifier[HEIGHTS.length];
        Identifier[] troddenModels = new Identifier[HEIGHTS.length];
        for (int i = 0; i < HEIGHTS.length; i++) {
            baseModels[i] = layerTemplates[i].create(Relict.id("block/dust_layer_height" + HEIGHTS[i]), TextureMapping.cube(base), blockModels.modelOutput);
            troddenModels[i] = layerTemplates[i].create(Relict.id("block/dust_layer_trodden_height" + HEIGHTS[i]), TextureMapping.cube(trodden), blockModels.modelOutput);
        }

        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(RelictBlocks.DUST_LAYER.get())
                .with(PropertyDispatch.initial(BlockStateProperties.LAYERS, DustLayerBlock.TRODDEN)
                        .generate((layers, isTrodden) -> variant(isTrodden ? troddenModels[layers - 1] : baseModels[layers - 1]))));

        itemModels.itemModelOutput.accept(RelictItems.DUST_LAYER.get(), ItemModelUtils.plainModel(baseModels[0]));
    }

    private static void registerDrySnowLayer(BlockModelGenerators blockModels, ItemModelGenerators itemModels, ModelTemplate[] layerTemplates) {
        Material texture = new Material(Relict.id("block/dry_snow"));

        Identifier[] models = new Identifier[HEIGHTS.length];
        for (int i = 0; i < HEIGHTS.length; i++) {
            models[i] = layerTemplates[i].create(Relict.id("block/dry_snow_layer_height" + HEIGHTS[i]), TextureMapping.cube(texture), blockModels.modelOutput);
        }

        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(RelictBlocks.DRY_SNOW_LAYER.get())
                .with(PropertyDispatch.initial(BlockStateProperties.LAYERS).generate(layers -> variant(models[layers - 1]))));

        itemModels.itemModelOutput.accept(RelictItems.DRY_SNOW_LAYER.get(), ItemModelUtils.plainModel(models[0]));
    }

    private static void registerDrySnowBlock(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        Material texture = new Material(Relict.id("block/dry_snow"));
        Identifier modelId = ModelTemplates.CUBE_ALL.create(RelictBlocks.DRY_SNOW.get(), TextureMapping.cube(texture), blockModels.modelOutput);
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(RelictBlocks.DRY_SNOW.get(), variant(modelId)));
        itemModels.itemModelOutput.accept(RelictItems.DRY_SNOW.get(), ItemModelUtils.plainModel(modelId));
    }

    private static MultiVariant variant(Identifier modelId) {
        return new MultiVariant(WeightedList.of(new Variant(modelId)));
    }

}
