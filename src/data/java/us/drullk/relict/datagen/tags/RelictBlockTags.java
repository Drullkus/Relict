package us.drullk.relict.datagen.tags;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import us.drullk.relict.Relict;
import us.drullk.relict.RelictTags;

import java.util.concurrent.CompletableFuture;

public class RelictBlockTags extends BlockTagsProvider {

    public RelictBlockTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, Relict.MODID);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        this.tag(RelictTags.SPELEOTHEM_REPLACEABLE).add(Blocks.SMOOTH_BASALT.builtInRegistryHolder().key());
    }

}
