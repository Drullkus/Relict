package us.drullk.relict.datagen.tags;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.ItemTags;
import net.neoforged.neoforge.common.data.ItemTagsProvider;
import us.drullk.relict.Relict;
import us.drullk.relict.RelictTags;
import us.drullk.relict.init.RelictItems;

import java.util.concurrent.CompletableFuture;

public class RelictItemTags extends ItemTagsProvider {

    public RelictItemTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, Relict.MODID);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        this.tag(RelictTags.REPAIRS_SERVICE_ARMOR);

        this.tag(ItemTags.HEAD_ARMOR).add(RelictItems.VITAL_VIZARD.getKey());
        this.tag(ItemTags.HEAD_ARMOR).add(RelictItems.SPENT_VIZARD.getKey());
        this.tag(ItemTags.CHEST_ARMOR).add(RelictItems.RANGING_CAISSON.getKey());
        this.tag(ItemTags.LEG_ARMOR).add(RelictItems.RESTLESS_STRIDERS.getKey());
        this.tag(ItemTags.FOOT_ARMOR).add(RelictItems.GROUNDING_TREADS.getKey());
    }

}
