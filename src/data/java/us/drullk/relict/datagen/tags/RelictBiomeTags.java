package us.drullk.relict.datagen.tags;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.BiomeTagsProvider;
import us.drullk.relict.Relict;

import java.util.concurrent.CompletableFuture;

public class RelictBiomeTags extends BiomeTagsProvider {

    public RelictBiomeTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, Relict.MODID);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
    }

}
