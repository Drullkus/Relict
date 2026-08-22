package us.drullk.relict.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import us.drullk.relict.Relict;

public class RelictDamageTypes {

    public static final ResourceKey<DamageType> UNBREATHABLE = ResourceKey.create(Registries.DAMAGE_TYPE, Relict.id("mars_unbreathable"));
    public static final ResourceKey<DamageType> AIR_DEPLETED = ResourceKey.create(Registries.DAMAGE_TYPE, Relict.id("air_depleted"));
    public static final ResourceKey<DamageType> STORM_DISCHARGE = ResourceKey.create(Registries.DAMAGE_TYPE, Relict.id("storm_discharge"));

}
