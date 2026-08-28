package us.drullk.relict.datagen.models;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.blockstates.ConditionBuilder;
import net.minecraft.client.data.models.blockstates.MultiPartGenerator;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.client.renderer.block.dispatch.VariantMutator;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import us.drullk.relict.Relict;
import us.drullk.relict.init.RelictBlocks;
import us.drullk.relict.init.RelictItems;

public final class RuinPaletteModels {

    private static final VariantMutator UV_LOCK = VariantMutator.UV_LOCK.withValue(true);
    private static final VariantMutator Y_ROT_90 = VariantMutator.Y_ROT.withValue(com.mojang.math.Quadrant.R90);
    private static final VariantMutator Y_ROT_180 = VariantMutator.Y_ROT.withValue(com.mojang.math.Quadrant.R180);
    private static final VariantMutator Y_ROT_270 = VariantMutator.Y_ROT.withValue(com.mojang.math.Quadrant.R270);
    private static final VariantMutator X_ROT_180 = VariantMutator.X_ROT.withValue(com.mojang.math.Quadrant.R180);

    private RuinPaletteModels() {
    }

    public static void register(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        registerFamily(blockModels, itemModels, "ochre",
                RelictBlocks.OCHRE, RelictItems.OCHRE,
                RelictBlocks.OCHRE_SLAB, RelictItems.OCHRE_SLAB,
                RelictBlocks.OCHRE_STAIRS, RelictItems.OCHRE_STAIRS,
                RelictBlocks.OCHRE_WALL, RelictItems.OCHRE_WALL);

        registerFamily(blockModels, itemModels, "polished_ochre",
                RelictBlocks.POLISHED_OCHRE, RelictItems.POLISHED_OCHRE,
                RelictBlocks.POLISHED_OCHRE_SLAB, RelictItems.POLISHED_OCHRE_SLAB,
                RelictBlocks.POLISHED_OCHRE_STAIRS, RelictItems.POLISHED_OCHRE_STAIRS,
                RelictBlocks.POLISHED_OCHRE_WALL, RelictItems.POLISHED_OCHRE_WALL);

        registerFamily(blockModels, itemModels, "serpentine",
                RelictBlocks.SERPENTINE, RelictItems.SERPENTINE,
                RelictBlocks.SERPENTINE_SLAB, RelictItems.SERPENTINE_SLAB,
                RelictBlocks.SERPENTINE_STAIRS, RelictItems.SERPENTINE_STAIRS,
                RelictBlocks.SERPENTINE_WALL, RelictItems.SERPENTINE_WALL);

        registerFamily(blockModels, itemModels, "polished_serpentine",
                RelictBlocks.POLISHED_SERPENTINE, RelictItems.POLISHED_SERPENTINE,
                RelictBlocks.POLISHED_SERPENTINE_SLAB, RelictItems.POLISHED_SERPENTINE_SLAB,
                RelictBlocks.POLISHED_SERPENTINE_STAIRS, RelictItems.POLISHED_SERPENTINE_STAIRS,
                RelictBlocks.POLISHED_SERPENTINE_WALL, RelictItems.POLISHED_SERPENTINE_WALL);
    }

    private static void registerFamily(BlockModelGenerators blockModels, ItemModelGenerators itemModels, String name,
            DeferredBlock<Block> block, DeferredItem<BlockItem> item,
            DeferredBlock<SlabBlock> slab, DeferredItem<BlockItem> slabItem,
            DeferredBlock<StairBlock> stairs, DeferredItem<BlockItem> stairsItem,
            DeferredBlock<WallBlock> wall, DeferredItem<BlockItem> wallItem) {
        TextureMapping textures = TextureMapping.cube(new Material(Relict.id("block/" + name)));

        Identifier blockModel = ModelTemplates.CUBE_ALL.create(block.get(), textures, blockModels.modelOutput);
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(block.get(), variant(blockModel)));
        itemModels.itemModelOutput.accept(item.get(), ItemModelUtils.plainModel(blockModel));

        registerSlab(blockModels, itemModels, slab, slabItem, textures, blockModel);
        registerStairs(blockModels, itemModels, stairs, stairsItem, textures);
        registerWall(blockModels, itemModels, wall, wallItem, textures);
    }

    /** Standard vanilla slab shape/placement (SlabBlock): bottom/top halves plus the double-slab = full block. */
    private static void registerSlab(BlockModelGenerators blockModels, ItemModelGenerators itemModels,
            DeferredBlock<SlabBlock> slab, DeferredItem<BlockItem> slabItem, TextureMapping textures, Identifier fullBlockModel) {
        Identifier bottom = ModelTemplates.SLAB_BOTTOM.create(slab.get(), textures, blockModels.modelOutput);
        Identifier top = ModelTemplates.SLAB_TOP.create(slab.get(), textures, blockModels.modelOutput);

        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(slab.get())
                .with(PropertyDispatch.initial(BlockStateProperties.SLAB_TYPE)
                        .select(SlabType.BOTTOM, variant(bottom))
                        .select(SlabType.TOP, variant(top))
                        .select(SlabType.DOUBLE, variant(fullBlockModel))));
        itemModels.itemModelOutput.accept(slabItem.get(), ItemModelUtils.plainModel(bottom));
    }

    /** Standard vanilla stairs shape/placement (StairBlock): straight, inner corner, outer corner, both halves. */
    private static void registerStairs(BlockModelGenerators blockModels, ItemModelGenerators itemModels,
            DeferredBlock<StairBlock> stairs, DeferredItem<BlockItem> stairsItem, TextureMapping textures) {
        MultiVariant inner = variant(ModelTemplates.STAIRS_INNER.create(stairs.get(), textures, blockModels.modelOutput));
        Identifier straightModel = ModelTemplates.STAIRS_STRAIGHT.create(stairs.get(), textures, blockModels.modelOutput);
        MultiVariant straight = variant(straightModel);
        MultiVariant outer = variant(ModelTemplates.STAIRS_OUTER.create(stairs.get(), textures, blockModels.modelOutput));

        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(stairs.get())
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
        itemModels.itemModelOutput.accept(stairsItem.get(), ItemModelUtils.plainModel(straightModel));
    }

    private static void registerWall(BlockModelGenerators blockModels, ItemModelGenerators itemModels,
            DeferredBlock<WallBlock> wall, DeferredItem<BlockItem> wallItem, TextureMapping textures) {
        MultiVariant post = variant(ModelTemplates.WALL_POST.create(wall.get(), textures, blockModels.modelOutput));
        MultiVariant lowSide = variant(ModelTemplates.WALL_LOW_SIDE.create(wall.get(), textures, blockModels.modelOutput));
        MultiVariant tallSide = variant(ModelTemplates.WALL_TALL_SIDE.create(wall.get(), textures, blockModels.modelOutput));

        blockModels.blockStateOutput.accept(MultiPartGenerator.multiPart(wall.get())
                .with(new ConditionBuilder().term(BlockStateProperties.UP, true), post)
                .with(new ConditionBuilder().term(BlockStateProperties.NORTH_WALL, WallSide.LOW), lowSide.with(UV_LOCK))
                .with(new ConditionBuilder().term(BlockStateProperties.EAST_WALL, WallSide.LOW), lowSide.with(Y_ROT_90).with(UV_LOCK))
                .with(new ConditionBuilder().term(BlockStateProperties.SOUTH_WALL, WallSide.LOW), lowSide.with(Y_ROT_180).with(UV_LOCK))
                .with(new ConditionBuilder().term(BlockStateProperties.WEST_WALL, WallSide.LOW), lowSide.with(Y_ROT_270).with(UV_LOCK))
                .with(new ConditionBuilder().term(BlockStateProperties.NORTH_WALL, WallSide.TALL), tallSide.with(UV_LOCK))
                .with(new ConditionBuilder().term(BlockStateProperties.EAST_WALL, WallSide.TALL), tallSide.with(Y_ROT_90).with(UV_LOCK))
                .with(new ConditionBuilder().term(BlockStateProperties.SOUTH_WALL, WallSide.TALL), tallSide.with(Y_ROT_180).with(UV_LOCK))
                .with(new ConditionBuilder().term(BlockStateProperties.WEST_WALL, WallSide.TALL), tallSide.with(Y_ROT_270).with(UV_LOCK)));

        Identifier inventoryModel = ModelTemplates.WALL_INVENTORY.create(wall.get(), textures, blockModels.modelOutput);
        itemModels.itemModelOutput.accept(wallItem.get(), ItemModelUtils.plainModel(inventoryModel));
    }

    private static MultiVariant variant(Identifier modelId) {
        return new MultiVariant(WeightedList.of(new Variant(modelId)));
    }

}
