package us.drullk.relict.datagen;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.advancements.AdvancementProvider;
import net.minecraft.data.advancements.AdvancementSubProvider;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class RelictAdvancements extends AdvancementProvider {

    public RelictAdvancements(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries, List.of(new RelictAdvancementGenerator()));
    }

    private static class RelictAdvancementGenerator implements AdvancementSubProvider {

        @Override
        public void generate(HolderLookup.Provider provider, Consumer<AdvancementHolder> consumer) {

        }

    }

}
