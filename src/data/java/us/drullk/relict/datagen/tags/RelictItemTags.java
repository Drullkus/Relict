package us.drullk.relict.datagen.tags;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.ItemTagsProvider;
import us.drullk.relict.Relict;

import java.util.concurrent.CompletableFuture;

public class RelictItemTags extends ItemTagsProvider {

    public RelictItemTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, Relict.MODID);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
    }

}
