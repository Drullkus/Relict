package us.drullk.relict.datagen.tags;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import us.drullk.relict.Relict;
import us.drullk.relict.RelictTags;
import us.drullk.relict.init.RelictBlocks;

import java.util.concurrent.CompletableFuture;

public class RelictBlockTags extends BlockTagsProvider {

    public RelictBlockTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, Relict.MODID);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        this.tag(RelictTags.SPELEOTHEM_REPLACEABLE).add(Blocks.SMOOTH_BASALT.builtInRegistryHolder().key());
        this.tag(RelictTags.DRIPSTONE_REPLACEABLE).add(Blocks.SMOOTH_BASALT.builtInRegistryHolder().key(), Blocks.CALCITE.builtInRegistryHolder().key());
        this.tag(RelictTags.BASE_STONE_MARS).add(Blocks.SMOOTH_BASALT.builtInRegistryHolder().key());
        this.tag(RelictTags.MARS_PORTAL_FRAME)
                .add(Blocks.POLISHED_SULFUR.builtInRegistryHolder().key())
                .add(Blocks.SMOOTH_BASALT.builtInRegistryHolder().key());

        this.tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .add(RelictBlocks.CIPHER_CHEST.getKey())
                .add(RelictBlocks.LAB_BLOCK.getKey())
                .add(RelictBlocks.LAB_SLAB.getKey())
                .add(RelictBlocks.LAB_STAIRS.getKey())
                .add(RelictBlocks.LAB_SHAFT.getKey())
                .add(RelictBlocks.LAB_MAST.getKey())
                .add(RelictBlocks.ROVER_WHEEL.getKey())
                .add(RelictBlocks.SOLAR_PANEL.getKey())
                .add(RelictBlocks.SOLAR_PANEL_SPRINKLED.getKey())
                .add(RelictBlocks.SOLAR_PANEL_DUSTED.getKey())
                .add(RelictBlocks.SOLAR_PANEL_SANDED.getKey())
                .add(RelictBlocks.OCHRE.getKey())
                .add(RelictBlocks.OCHRE_SLAB.getKey())
                .add(RelictBlocks.OCHRE_STAIRS.getKey())
                .add(RelictBlocks.OCHRE_WALL.getKey())
                .add(RelictBlocks.POLISHED_OCHRE.getKey())
                .add(RelictBlocks.POLISHED_OCHRE_SLAB.getKey())
                .add(RelictBlocks.POLISHED_OCHRE_STAIRS.getKey())
                .add(RelictBlocks.POLISHED_OCHRE_WALL.getKey())
                .add(RelictBlocks.SERPENTINE.getKey())
                .add(RelictBlocks.SERPENTINE_SLAB.getKey())
                .add(RelictBlocks.SERPENTINE_STAIRS.getKey())
                .add(RelictBlocks.SERPENTINE_WALL.getKey())
                .add(RelictBlocks.POLISHED_SERPENTINE.getKey())
                .add(RelictBlocks.POLISHED_SERPENTINE_SLAB.getKey())
                .add(RelictBlocks.POLISHED_SERPENTINE_STAIRS.getKey())
                .add(RelictBlocks.POLISHED_SERPENTINE_WALL.getKey());

        this.tag(BlockTags.SLABS)
                .add(RelictBlocks.OCHRE_SLAB.getKey())
                .add(RelictBlocks.POLISHED_OCHRE_SLAB.getKey())
                .add(RelictBlocks.SERPENTINE_SLAB.getKey())
                .add(RelictBlocks.POLISHED_SERPENTINE_SLAB.getKey());

        this.tag(BlockTags.STAIRS)
                .add(RelictBlocks.OCHRE_STAIRS.getKey())
                .add(RelictBlocks.POLISHED_OCHRE_STAIRS.getKey())
                .add(RelictBlocks.SERPENTINE_STAIRS.getKey())
                .add(RelictBlocks.POLISHED_SERPENTINE_STAIRS.getKey());

        this.tag(BlockTags.WALLS)
                .add(RelictBlocks.OCHRE_WALL.getKey())
                .add(RelictBlocks.POLISHED_OCHRE_WALL.getKey())
                .add(RelictBlocks.SERPENTINE_WALL.getKey())
                .add(RelictBlocks.POLISHED_SERPENTINE_WALL.getKey());

        this.tag(Tags.Blocks.STONES)
                .add(RelictBlocks.OCHRE.getKey())
                .add(RelictBlocks.POLISHED_OCHRE.getKey())
                .add(RelictBlocks.SERPENTINE.getKey())
                .add(RelictBlocks.POLISHED_SERPENTINE.getKey());

        this.tag(BlockTags.MINEABLE_WITH_SHOVEL)
                .add(RelictBlocks.DUST_LAYER.getKey())
                .add(RelictBlocks.DRY_SNOW.getKey())
                .add(RelictBlocks.DRY_SNOW_LAYER.getKey())
                .add(RelictBlocks.BASALT_SAND.getKey());


        this.tag(RelictTags.SANDS_BASALT).add(RelictBlocks.BASALT_SAND.getKey());
        this.tag(BlockTags.SAND).addTag(RelictTags.SANDS_BASALT);
        this.tag(Tags.Blocks.SANDS).addTag(RelictTags.SANDS_BASALT);
    }

}
