package us.drullk.relict.datagen.tags;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.DamageTypeTagsProvider;
import us.drullk.relict.Relict;

import java.util.concurrent.CompletableFuture;

public class RelictDamageTypeTags extends DamageTypeTagsProvider {

    public RelictDamageTypeTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, Relict.MODID);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
    }

}
