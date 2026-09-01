package us.drullk.relict.datagen.models;

import com.mojang.math.Quadrant;
import com.mojang.math.Transformation;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelTemplate;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.client.renderer.block.dispatch.VariantMutator;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import org.joml.Matrix4f;
import us.drullk.relict.Relict;
import us.drullk.relict.block.wreck.SolarPanelBlock;
import us.drullk.relict.init.RelictBlocks;
import us.drullk.relict.init.RelictItems;

import java.util.Optional;

/**
 * Model/blockstate datagen for the Unmanned Wreck block family: Lab Block and its slab/stairs, Lab Shaft,
 * Lab Mast, Rover Wheel, and the four Solar Panel decay stages. Split out of the monolithic model provider
 * so this feature area owns its own generator class.
 */
public final class WreckModels {

    private static final ModelTemplate LAB_SHAFT_TEMPLATE = new ModelTemplate(Optional.of(Relict.id("block/template_lab_shaft")), Optional.empty(), TextureSlot.ALL);
    private static final ModelTemplate ROVER_WHEEL_TEMPLATE = new ModelTemplate(Optional.of(Relict.id("block/template_rover_wheel")), Optional.empty(), TextureSlot.SIDE, TextureSlot.END);
    private static final ModelTemplate SOLAR_PANEL_TEMPLATE = new ModelTemplate(Optional.of(Relict.id("block/template_solar_panel")), Optional.empty(), TextureSlot.TOP, TextureSlot.SIDE);
    private static final ModelTemplate LAB_MAST_TEMPLATE = new ModelTemplate(Optional.of(Relict.id("block/template_lab_mast")), Optional.empty(), TextureSlot.FRONT, TextureSlot.BACK, TextureSlot.TOP, TextureSlot.BOTTOM, TextureSlot.SIDE);

    // Named to match the naming convention used by vanilla's own stairs/slab family generator (BlockModelGenerators),
    // which this class re-implements by hand: that class's family()/BlockFamilyProvider is not accessible
    // outside its package, and Lab Block already has its own standalone (non-family) registration below.
    private static final VariantMutator UV_LOCK = VariantMutator.UV_LOCK.withValue(true);
    private static final VariantMutator Y_ROT_90 = VariantMutator.Y_ROT.withValue(Quadrant.R90);
    private static final VariantMutator Y_ROT_180 = VariantMutator.Y_ROT.withValue(Quadrant.R180);
    private static final VariantMutator Y_ROT_270 = VariantMutator.Y_ROT.withValue(Quadrant.R270);
    private static final VariantMutator X_ROT_180 = VariantMutator.X_ROT.withValue(Quadrant.R180);

    private WreckModels() {
    }

    public static void register(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        Identifier labBlockModel = registerLabBlock(blockModels, itemModels);
        registerLabSlab(blockModels, itemModels, labBlockModel);
        registerLabStairs(blockModels, itemModels);
        registerLabShaft(blockModels, itemModels);
        registerRoverWheel(blockModels, itemModels);
        registerSolarPanel(blockModels, itemModels, RelictBlocks.SOLAR_PANEL, RelictItems.SOLAR_PANEL, "solar_panel");
        registerSolarPanel(blockModels, itemModels, RelictBlocks.SOLAR_PANEL_SPRINKLED, RelictItems.SOLAR_PANEL_SPRINKLED, "solar_panel_sprinkled");
        registerSolarPanel(blockModels, itemModels, RelictBlocks.SOLAR_PANEL_DUSTED, RelictItems.SOLAR_PANEL_DUSTED, "solar_panel_dusted");
        registerSolarPanel(blockModels, itemModels, RelictBlocks.SOLAR_PANEL_SANDED, RelictItems.SOLAR_PANEL_SANDED, "solar_panel_sanded");
        registerLabMast(blockModels, itemModels);
    }

    private static Material wreckTexture(String name) {
        return new Material(Relict.id("block/unmanned_wreck/" + name));
    }

    private static Identifier registerLabBlock(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        Identifier modelId = ModelTemplates.CUBE_ALL.create(RelictBlocks.LAB_BLOCK.get(),
                TextureMapping.cube(wreckTexture("lab_block")), blockModels.modelOutput);
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(RelictBlocks.LAB_BLOCK.get(), variant(modelId)));
        itemModels.itemModelOutput.accept(RelictItems.LAB_BLOCK.get(), ItemModelUtils.plainModel(modelId));
        return modelId;
    }

    /** Standard vanilla slab shape/placement (SlabBlock), same texture as Lab Block on all faces. */
    private static void registerLabSlab(BlockModelGenerators blockModels, ItemModelGenerators itemModels, Identifier labBlockModel) {
        TextureMapping textures = TextureMapping.cube(wreckTexture("lab_block"));
        Identifier bottom = ModelTemplates.SLAB_BOTTOM.create(RelictBlocks.LAB_SLAB.get(), textures, blockModels.modelOutput);
        Identifier top = ModelTemplates.SLAB_TOP.create(RelictBlocks.LAB_SLAB.get(), textures, blockModels.modelOutput);

        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(RelictBlocks.LAB_SLAB.get())
                .with(PropertyDispatch.initial(BlockStateProperties.SLAB_TYPE)
                        .select(SlabType.BOTTOM, variant(bottom))
                        .select(SlabType.TOP, variant(top))
                        .select(SlabType.DOUBLE, variant(labBlockModel))));
        itemModels.itemModelOutput.accept(RelictItems.LAB_SLAB.get(), ItemModelUtils.plainModel(bottom));
    }

    /** Standard vanilla stairs shape/placement (StairBlock), same texture as Lab Block on all faces. */
    private static void registerLabStairs(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        TextureMapping textures = TextureMapping.cube(wreckTexture("lab_block"));
        MultiVariant inner = variant(ModelTemplates.STAIRS_INNER.create(RelictBlocks.LAB_STAIRS.get(), textures, blockModels.modelOutput));
        Identifier straightModel = ModelTemplates.STAIRS_STRAIGHT.create(RelictBlocks.LAB_STAIRS.get(), textures, blockModels.modelOutput);
        MultiVariant straight = variant(straightModel);
        MultiVariant outer = variant(ModelTemplates.STAIRS_OUTER.create(RelictBlocks.LAB_STAIRS.get(), textures, blockModels.modelOutput));

        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(RelictBlocks.LAB_STAIRS.get())
                .with(PropertyDispatch.initial(BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.HALF, BlockStateProperties.STAIRS_SHAPE)
                        .select(Direction.EAST, Half.BOTTOM, StairsShape.STRAIGHT, straight)
                        .select(Direction.WEST, Half.BOTTOM, StairsShape.STRAIGHT, straight.with(Y_ROT_180).with(UV_LOCK))
                        .select(Direction.SOUTH, Half.BOTTOM, StairsShape.STRAIGHT, straight.with(Y_ROT_90).with(UV_LOCK))
                        .select(Direction.NORTH, Half.BOTTOM, StairsShape.STRAIGHT, straight.with(Y_ROT_270).with(UV_LOCK))
                        .select(Direction.EAST, Half.BOTTOM, StairsShape.OUTER_RIGHT, outer)
                        .select(Direction.WEST, Half.BOTTOM, StairsShape.OUTER_RIGHT, outer.with(Y_ROT_180).with(UV_LOCK))
                        .select(Direction.SOUTH, Half.BOTTOM, StairsShape.OUTER_RIGHT, outer.with(Y_ROT_90).with(UV_LOCK))
                        .select(Direction.NORTH, Half.BOTTOM, StairsShape.OUTER_RIGHT, outer.with(Y_ROT_270).with(UV_LOCK))
                        .select(Direction.EAST, Half.BOTTOM, StairsShape.OUTER_LEFT, outer.with(Y_ROT_270).with(UV_LOCK))
                        .select(Direction.WEST, Half.BOTTOM, StairsShape.OUTER_LEFT, outer.with(Y_ROT_90).with(UV_LOCK))
                        .select(Direction.SOUTH, Half.BOTTOM, StairsShape.OUTER_LEFT, outer)
                        .select(Direction.NORTH, Half.BOTTOM, StairsShape.OUTER_LEFT, outer.with(Y_ROT_180).with(UV_LOCK))
                        .select(Direction.EAST, Half.BOTTOM, StairsShape.INNER_RIGHT, inner)
                        .select(Direction.WEST, Half.BOTTOM, StairsShape.INNER_RIGHT, inner.with(Y_ROT_180).with(UV_LOCK))
                        .select(Direction.SOUTH, Half.BOTTOM, StairsShape.INNER_RIGHT, inner.with(Y_ROT_90).with(UV_LOCK))
                        .select(Direction.NORTH, Half.BOTTOM, StairsShape.INNER_RIGHT, inner.with(Y_ROT_270).with(UV_LOCK))
                        .select(Direction.EAST, Half.BOTTOM, StairsShape.INNER_LEFT, inner.with(Y_ROT_270).with(UV_LOCK))
                        .select(Direction.WEST, Half.BOTTOM, StairsShape.INNER_LEFT, inner.with(Y_ROT_90).with(UV_LOCK))
                        .select(Direction.SOUTH, Half.BOTTOM, StairsShape.INNER_LEFT, inner)
                        .select(Direction.NORTH, Half.BOTTOM, StairsShape.INNER_LEFT, inner.with(Y_ROT_180).with(UV_LOCK))
                        .select(Direction.EAST, Half.TOP, StairsShape.STRAIGHT, straight.with(X_ROT_180).with(UV_LOCK))
                        .select(Direction.WEST, Half.TOP, StairsShape.STRAIGHT, straight.with(X_ROT_180).with(Y_ROT_180).with(UV_LOCK))
                        .select(Direction.SOUTH, Half.TOP, StairsShape.STRAIGHT, straight.with(X_ROT_180).with(Y_ROT_90).with(UV_LOCK))
                        .select(Direction.NORTH, Half.TOP, StairsShape.STRAIGHT, straight.with(X_ROT_180).with(Y_ROT_270).with(UV_LOCK))
                        .select(Direction.EAST, Half.TOP, StairsShape.OUTER_RIGHT, outer.with(X_ROT_180).with(Y_ROT_90).with(UV_LOCK))
                        .select(Direction.WEST, Half.TOP, StairsShape.OUTER_RIGHT, outer.with(X_ROT_180).with(Y_ROT_270).with(UV_LOCK))
                        .select(Direction.SOUTH, Half.TOP, StairsShape.OUTER_RIGHT, outer.with(X_ROT_180).with(Y_ROT_180).with(UV_LOCK))
                        .select(Direction.NORTH, Half.TOP, StairsShape.OUTER_RIGHT, outer.with(X_ROT_180).with(UV_LOCK))
                        .select(Direction.EAST, Half.TOP, StairsShape.OUTER_LEFT, outer.with(X_ROT_180).with(UV_LOCK))
                        .select(Direction.WEST, Half.TOP, StairsShape.OUTER_LEFT, outer.with(X_ROT_180).with(Y_ROT_180).with(UV_LOCK))
                        .select(Direction.SOUTH, Half.TOP, StairsShape.OUTER_LEFT, outer.with(X_ROT_180).with(Y_ROT_90).with(UV_LOCK))
                        .select(Direction.NORTH, Half.TOP, StairsShape.OUTER_LEFT, outer.with(X_ROT_180).with(Y_ROT_270).with(UV_LOCK))
                        .select(Direction.EAST, Half.TOP, StairsShape.INNER_RIGHT, inner.with(X_ROT_180).with(Y_ROT_90).with(UV_LOCK))
                        .select(Direction.WEST, Half.TOP, StairsShape.INNER_RIGHT, inner.with(X_ROT_180).with(Y_ROT_270).with(UV_LOCK))
                        .select(Direction.SOUTH, Half.TOP, StairsShape.INNER_RIGHT, inner.with(X_ROT_180).with(Y_ROT_180).with(UV_LOCK))
                        .select(Direction.NORTH, Half.TOP, StairsShape.INNER_RIGHT, inner.with(X_ROT_180).with(UV_LOCK))
                        .select(Direction.EAST, Half.TOP, StairsShape.INNER_LEFT, inner.with(X_ROT_180).with(UV_LOCK))
                        .select(Direction.WEST, Half.TOP, StairsShape.INNER_LEFT, inner.with(X_ROT_180).with(Y_ROT_180).with(UV_LOCK))
                        .select(Direction.SOUTH, Half.TOP, StairsShape.INNER_LEFT, inner.with(X_ROT_180).with(Y_ROT_90).with(UV_LOCK))
                        .select(Direction.NORTH, Half.TOP, StairsShape.INNER_LEFT, inner.with(X_ROT_180).with(Y_ROT_270).with(UV_LOCK))));
        itemModels.itemModelOutput.accept(RelictItems.LAB_STAIRS.get(), ItemModelUtils.plainModel(straightModel));
    }

    private static void registerLabShaft(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        TextureMapping textures = TextureMapping.cube(wreckTexture("lab_block"));
        Identifier modelId = LAB_SHAFT_TEMPLATE.create(RelictBlocks.LAB_SHAFT.get(), textures, blockModels.modelOutput);

        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(RelictBlocks.LAB_SHAFT.get())
                .with(PropertyDispatch.initial(BlockStateProperties.AXIS)
                        .select(Direction.Axis.Y, variant(modelId))
                        .select(Direction.Axis.Z, variant(modelId).with(VariantMutator.X_ROT.withValue(Quadrant.R90)))
                        .select(Direction.Axis.X, variant(modelId)
                                .with(VariantMutator.X_ROT.withValue(Quadrant.R90))
                                .with(VariantMutator.Y_ROT.withValue(Quadrant.R90)))));
        itemModels.itemModelOutput.accept(RelictItems.LAB_SHAFT.get(), ItemModelUtils.plainModel(modelId));
    }

    private static final Transformation ROVER_WHEEL_ITEM_TRANSFORM = new Transformation(new Matrix4f()
            .translate(0.5f, 0.5f, 0.5f)
            .mul(BlockModelRotation.get(Quadrant.fromXYAngles(Quadrant.R90, Quadrant.R0)).transformation().getMatrix())
            .translate(-0.5f, -0.5f, -0.5f));

    private static void registerRoverWheel(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        TextureMapping textures = new TextureMapping()
                .put(TextureSlot.END, wreckTexture("rover_wheel_hub"))
                .put(TextureSlot.SIDE, wreckTexture("rover_wheel_treads"));
        Identifier modelId = ROVER_WHEEL_TEMPLATE.create(RelictBlocks.ROVER_WHEEL.get(), textures, blockModels.modelOutput);

        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(RelictBlocks.ROVER_WHEEL.get())
                .with(PropertyDispatch.initial(BlockStateProperties.AXIS)
                        .select(Direction.Axis.Y, variant(modelId))
                        .select(Direction.Axis.Z, variant(modelId).with(VariantMutator.X_ROT.withValue(Quadrant.R90)))
                        .select(Direction.Axis.X, variant(modelId)
                                .with(VariantMutator.X_ROT.withValue(Quadrant.R90))
                                .with(VariantMutator.Y_ROT.withValue(Quadrant.R90)))));
        itemModels.itemModelOutput.accept(RelictItems.ROVER_WHEEL.get(), ItemModelUtils.plainModel(modelId, ROVER_WHEEL_ITEM_TRANSFORM));
    }

    private static void registerSolarPanel(BlockModelGenerators blockModels, ItemModelGenerators itemModels,
            DeferredBlock<SolarPanelBlock> stage, DeferredItem<net.minecraft.world.item.BlockItem> item, String topTextureName) {
        TextureMapping textures = new TextureMapping()
                .put(TextureSlot.TOP, wreckTexture(topTextureName))
                .put(TextureSlot.SIDE, wreckTexture("solar_panel_side"));
        Identifier modelId = SOLAR_PANEL_TEMPLATE.create(stage.get(), textures, blockModels.modelOutput);
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(stage.get(), variant(modelId)));
        itemModels.itemModelOutput.accept(item.get(), ItemModelUtils.plainModel(modelId));
    }

    private static void registerLabMast(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        TextureMapping textures = new TextureMapping()
                .put(TextureSlot.FRONT, wreckTexture("lab_mast_front"))
                .put(TextureSlot.BACK, wreckTexture("lab_mast_back"))
                .put(TextureSlot.TOP, wreckTexture("lab_block"))
                .put(TextureSlot.SIDE, wreckTexture("lab_mast_side"))
                .put(TextureSlot.BOTTOM, wreckTexture("lab_mast_back"));
        Identifier modelId = LAB_MAST_TEMPLATE.create(RelictBlocks.LAB_MAST.get(), textures, blockModels.modelOutput);

        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(RelictBlocks.LAB_MAST.get())
                .with(PropertyDispatch.initial(BlockStateProperties.HORIZONTAL_FACING)
                        .select(Direction.NORTH, variant(modelId))
                        .select(Direction.EAST, variant(modelId).with(VariantMutator.Y_ROT.withValue(Quadrant.R90)))
                        .select(Direction.SOUTH, variant(modelId).with(VariantMutator.Y_ROT.withValue(Quadrant.R180)))
                        .select(Direction.WEST, variant(modelId).with(VariantMutator.Y_ROT.withValue(Quadrant.R270)))));
        itemModels.itemModelOutput.accept(RelictItems.LAB_MAST.get(), ItemModelUtils.plainModel(modelId));
    }

    private static MultiVariant variant(Identifier modelId) {
        return new MultiVariant(WeightedList.of(new Variant(modelId)));
    }

}
