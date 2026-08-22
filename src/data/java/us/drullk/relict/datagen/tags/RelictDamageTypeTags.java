package us.drullk.relict.datagen.tags;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.DamageTypeTagsProvider;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import us.drullk.relict.Relict;
import us.drullk.relict.RelictTags;
import us.drullk.relict.init.RelictDamageTypes;

import java.util.concurrent.CompletableFuture;

public class RelictDamageTypeTags extends DamageTypeTagsProvider {

    public RelictDamageTypeTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, Relict.MODID);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        this.tag(RelictTags.IS_ELECTRIC).add(DamageTypes.LIGHTNING_BOLT, RelictDamageTypes.STORM_DISCHARGE);

        this.tag(DamageTypeTags.BYPASSES_ARMOR).add(RelictDamageTypes.UNBREATHABLE, RelictDamageTypes.AIR_DEPLETED, RelictDamageTypes.STORM_DISCHARGE);
        this.tag(DamageTypeTags.BYPASSES_EFFECTS).add(RelictDamageTypes.UNBREATHABLE, RelictDamageTypes.AIR_DEPLETED);
        this.tag(DamageTypeTags.BYPASSES_ENCHANTMENTS).add(RelictDamageTypes.UNBREATHABLE, RelictDamageTypes.AIR_DEPLETED);
        this.tag(DamageTypeTags.BYPASSES_INVULNERABILITY).add(RelictDamageTypes.UNBREATHABLE, RelictDamageTypes.AIR_DEPLETED);
        this.tag(DamageTypeTags.BYPASSES_RESISTANCE).add(RelictDamageTypes.UNBREATHABLE, RelictDamageTypes.AIR_DEPLETED);
        this.tag(DamageTypeTags.NO_KNOCKBACK).add(RelictDamageTypes.UNBREATHABLE, RelictDamageTypes.AIR_DEPLETED, RelictDamageTypes.STORM_DISCHARGE);
    }

}
