package us.drullk.relict.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.SingleItemRecipeBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredBlock;
import us.drullk.relict.Relict;
import us.drullk.relict.init.RelictBlocks;

import java.util.concurrent.CompletableFuture;

/**
 * Recipe datagen for the four ruin-palette stone families (Ochre, Polished Ochre, Serpentine, Polished
 * Serpentine): shape crafting (slab/stairs/wall) and stonecutter conversions for each, plus the
 * base-to-polished 2x2 crafting recipe. Unlock advancements are the vanilla-recipe-provider default
 * ({@code unlockedBy(getHasName(base), has(base))}, auto-saved alongside each recipe) -- there is no
 * repo-specific recipe provider to mirror yet, so this follows vanilla's own convention directly.
 */
public class RelictRecipes extends RecipeProvider {

    public RelictRecipes(HolderLookup.Provider registries, RecipeOutput output) {
        super(registries, output);
    }

    @Override
    protected void buildRecipes() {
        ruinPaletteFamily(RelictBlocks.OCHRE, RelictBlocks.OCHRE_SLAB, RelictBlocks.OCHRE_STAIRS, RelictBlocks.OCHRE_WALL);
        ruinPaletteFamily(RelictBlocks.POLISHED_OCHRE, RelictBlocks.POLISHED_OCHRE_SLAB, RelictBlocks.POLISHED_OCHRE_STAIRS, RelictBlocks.POLISHED_OCHRE_WALL);
        ruinPaletteFamily(RelictBlocks.SERPENTINE, RelictBlocks.SERPENTINE_SLAB, RelictBlocks.SERPENTINE_STAIRS, RelictBlocks.SERPENTINE_WALL);
        ruinPaletteFamily(RelictBlocks.POLISHED_SERPENTINE, RelictBlocks.POLISHED_SERPENTINE_SLAB, RelictBlocks.POLISHED_SERPENTINE_STAIRS, RelictBlocks.POLISHED_SERPENTINE_WALL);

        polishedFromBase(RelictBlocks.OCHRE, RelictBlocks.POLISHED_OCHRE);
        polishedFromBase(RelictBlocks.SERPENTINE, RelictBlocks.POLISHED_SERPENTINE);
    }

    /**
     * Slab (6 base -> 6 slabs -- doubled from vanilla's usual 3 -> 6 shape, a deliberately chosen ratio
     * for this family), stairs (6 -> 4, vanilla's own ratio/pattern via {@link #stairBuilder}), and wall (6 -> 6,
     * vanilla's own ratio/pattern via {@link #wall}) crafting, plus a stonecutter conversion for every shape
     * at vanilla's usual per-shape multiplier (2 for slabs, 1 for stairs/walls).
     */
    private void ruinPaletteFamily(DeferredBlock<Block> base, DeferredBlock<? extends Block> slab, DeferredBlock<? extends Block> stairs, DeferredBlock<? extends Block> wall) {
        Block baseBlock = base.get();

        slabSixToSix(RecipeCategory.BUILDING_BLOCKS, slab.get(), baseBlock);
        this.stairBuilder(stairs.get(), Ingredient.of(baseBlock))
                .unlockedBy(getHasName(baseBlock), this.has(baseBlock))
                .save(this.output);
        this.wall(RecipeCategory.DECORATIONS, wall.get(), baseBlock);

        ruinStonecutter(RecipeCategory.BUILDING_BLOCKS, slab.get(), baseBlock, 2);
        ruinStonecutter(RecipeCategory.BUILDING_BLOCKS, stairs.get(), baseBlock, 1);
        ruinStonecutter(RecipeCategory.DECORATIONS, wall.get(), baseBlock, 1);
    }

    /** Polished variant: 4 base -> 4 polished (2x2 full grid, vanilla's own ratio via {@link #polished}), plus its own 1 -> 1 stonecutter conversion. */
    private void polishedFromBase(DeferredBlock<Block> base, DeferredBlock<Block> polished) {
        this.polished(RecipeCategory.BUILDING_BLOCKS, polished.get(), base.get());
        ruinStonecutter(RecipeCategory.BUILDING_BLOCKS, polished.get(), base.get(), 1);
    }

    /**
     * Same recipe {@link #stonecutterResultFromBase} builds, but saved under an explicit {@code relict:} id.
     * The inherited helper's {@code save(output, String)} parses its id with {@link net.minecraft.resources.Identifier#parse},
     * which defaults to the {@code minecraft:} namespace for a bare (colon-free) string -- fine for vanilla's
     * own recipe datagen, wrong here, so this saves through the {@code ResourceKey} overload instead.
     */
    private void ruinStonecutter(RecipeCategory category, ItemLike result, ItemLike base, int count) {
        ResourceKey<Recipe<?>> id = ResourceKey.create(Registries.RECIPE, Relict.id(getConversionRecipeName(result, base) + "_stonecutting"));
        SingleItemRecipeBuilder.stonecutting(Ingredient.of(base), category, result, count)
                .unlockedBy(getHasName(base), this.has(base))
                .save(this.output, id);
    }

    /** Deliberate ratio for this family: a full 3-wide/2-tall grid of base (6 items) yields 6 slabs. */
    private void slabSixToSix(RecipeCategory category, ItemLike result, ItemLike base) {
        this.shaped(category, result, 6)
                .define('#', base)
                .pattern("###")
                .pattern("###")
                .unlockedBy(getHasName(base), this.has(base))
                .save(this.output);
    }

    public static class Runner extends RecipeProvider.Runner {

        public Runner(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries);
        }

        @Override
        protected RecipeProvider createRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
            return new RelictRecipes(registries, output);
        }

        @Override
        public String getName() {
            return "Relict Recipes";
        }

    }

}
