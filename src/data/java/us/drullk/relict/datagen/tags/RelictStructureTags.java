package us.drullk.relict.datagen.tags;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.StructureTagsProvider;
import us.drullk.relict.RelictTags;
import us.drullk.relict.init.worldgen.RelictStructures;

import java.util.concurrent.CompletableFuture;

public class RelictStructureTags extends StructureTagsProvider {

    public RelictStructureTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        this.tag(RelictTags.SEISMIC_LOCATED).add(RelictStructures.OVERCAST_MOORING);
    }

}
