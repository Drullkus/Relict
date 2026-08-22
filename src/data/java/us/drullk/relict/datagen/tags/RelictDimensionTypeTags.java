package us.drullk.relict.datagen.tags;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.world.level.dimension.DimensionType;
import us.drullk.relict.Relict;
import us.drullk.relict.RelictTags;
import us.drullk.relict.init.worldgen.RelictDimension;

import java.util.concurrent.CompletableFuture;

public class RelictDimensionTypeTags extends TagsProvider<DimensionType> {

    public RelictDimensionTypeTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, Registries.DIMENSION_TYPE, lookupProvider, Relict.MODID);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        this.tag(RelictTags.IS_MARS).add(RelictDimension.MARS_TYPE);
        this.tag(RelictTags.HAS_MARS_ATMOSPHERE).addTag(RelictTags.IS_MARS);
    }

}
