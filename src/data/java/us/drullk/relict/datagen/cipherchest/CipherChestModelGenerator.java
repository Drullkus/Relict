package us.drullk.relict.datagen.cipherchest;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.client.renderer.special.ChestSpecialRenderer;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.block.state.properties.ChestType;
import us.drullk.relict.Relict;
import us.drullk.relict.init.RelictBlocks;
import us.drullk.relict.init.RelictItems;

public final class CipherChestModelGenerator {

    private static final Identifier EMPTY_MODEL = Relict.id("block/cipher_chest_empty");

    private CipherChestModelGenerator() {
    }

    public static void bootstrap(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(RelictBlocks.CIPHER_CHEST.get(), variant(EMPTY_MODEL)));

        itemModels.itemModelOutput.accept(RelictItems.CIPHER_CHEST.get(), ItemModelUtils.specialModel(
                Identifier.withDefaultNamespace("item/chest"),
                new ChestSpecialRenderer.Unbaked(ChestSpecialRenderer.COPPER.oxidized().single(), ChestType.SINGLE)));

        Identifier rubbingIcon = Relict.id("item/rubbing");
        TextureMapping rubbingIconTexture = new TextureMapping().put(TextureSlot.LAYER0, new Material(rubbingIcon));
        ModelTemplates.FLAT_ITEM.create(rubbingIcon, rubbingIconTexture, itemModels.modelOutput);
        itemModels.itemModelOutput.accept(RelictItems.RUBBING.get(), ItemModelUtils.plainModel(rubbingIcon));
    }

    private static MultiVariant variant(Identifier modelId) {
        return new MultiVariant(WeightedList.of(new Variant(modelId)));
    }

}
