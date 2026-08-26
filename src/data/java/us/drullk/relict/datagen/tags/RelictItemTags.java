package us.drullk.relict.datagen.tags;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.data.BlockTagCopyingItemTagProvider;
import us.drullk.relict.Relict;
import us.drullk.relict.RelictTags;
import us.drullk.relict.init.RelictItems;

import java.util.concurrent.CompletableFuture;

public class RelictItemTags extends BlockTagCopyingItemTagProvider {

    private static final TagKey<Item> SANDS_BASALT = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "sands/basalt"));

    public RelictItemTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, CompletableFuture<TagLookup<Block>> blockTags) {
        super(output, lookupProvider, blockTags, Relict.MODID);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        this.tag(RelictTags.REPAIRS_SERVICE_ARMOR);

        this.tag(ItemTags.HEAD_ARMOR).add(RelictItems.VITAL_VIZARD.getKey());
        this.tag(ItemTags.HEAD_ARMOR).add(RelictItems.SPENT_VIZARD.getKey());
        this.tag(ItemTags.CHEST_ARMOR).add(RelictItems.RANGING_CAISSON.getKey());
        this.tag(ItemTags.LEG_ARMOR).add(RelictItems.RESTLESS_STRIDERS.getKey());
        this.tag(ItemTags.FOOT_ARMOR).add(RelictItems.GROUNDING_TREADS.getKey());

        this.tag(ItemTags.TRIMMABLE_ARMOR)
                .remove(RelictItems.RANGING_CAISSON.getKey())
                .remove(RelictItems.RESTLESS_STRIDERS.getKey());

        this.tag(SANDS_BASALT).add(RelictItems.BASALT_SAND.getKey());
        this.tag(ItemTags.SMELTS_TO_GLASS).addTag(SANDS_BASALT);
        this.copy(BlockTags.SAND, ItemTags.SAND);
        this.copy(Tags.Blocks.SANDS, Tags.Items.SANDS);

    }

}
