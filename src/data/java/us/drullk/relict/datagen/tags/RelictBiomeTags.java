package us.drullk.relict.datagen.tags;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.BiomeTagsProvider;
import net.minecraft.world.level.biome.Biomes;
import us.drullk.relict.Relict;
import us.drullk.relict.RelictTags;
import us.drullk.relict.init.worldgen.RelictBiomes;

import java.util.concurrent.CompletableFuture;

public class RelictBiomeTags extends BiomeTagsProvider {

    public RelictBiomeTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, Relict.MODID);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        this.tag(RelictTags.HAS_STRUCTURE_PORTAL_RUIN)
                .add(Biomes.DRIPSTONE_CAVES)
                .add(Biomes.SULFUR_CAVES)
                .add(RelictBiomes.BASALT_CAVES)
                .add(RelictBiomes.CALCITE_CAVES)
                .add(RelictBiomes.ICE_CAVES)
                .add(RelictBiomes.SULFUR_CAVES);

        this.tag(RelictTags.HAS_STRUCTURE_UNMANNED_WRECK)
                .add(RelictBiomes.WRINKLE_PLAINS)
                .add(RelictBiomes.RUSTED_DUNES)
                .add(RelictBiomes.FRETTED_MESAS)
                .add(RelictBiomes.SHATTERED_HIGHLANDS);

        this.tag(RelictTags.HAS_STRUCTURE_RUIN_A)
                .add(RelictBiomes.BASALT_CAVES)
                .add(RelictBiomes.CALCITE_CAVES)
                .add(RelictBiomes.ICE_CAVES)
                .add(RelictBiomes.SULFUR_CAVES);
    }

}
