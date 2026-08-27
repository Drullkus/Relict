package us.drullk.relict.datagen.tags;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.tags.TimelineTags;
import net.minecraft.world.timeline.Timeline;
import us.drullk.relict.Relict;
import us.drullk.relict.init.worldgen.RelictDimension;

import java.util.concurrent.CompletableFuture;

public class RelictTimelineTags extends TagsProvider<Timeline> {

    public RelictTimelineTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, Registries.TIMELINE, lookupProvider, Relict.MODID);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        this.tag(RelictDimension.MARS_TIMELINES)
                .addTag(TimelineTags.UNIVERSAL)
                .add(RelictDimension.MARS_SOL)
                .add(RelictDimension.PHOBOS_ORBIT)
                .add(RelictDimension.DEIMOS_ORBIT)
                .add(RelictDimension.PHOBOS_ROCK)
                .add(RelictDimension.PHOBOS_TRANSIT)
        ;
    }

}
