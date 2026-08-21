package us.drullk.relict.datagen;

import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.world.damagesource.DamageScaling;
import net.minecraft.world.damagesource.DamageType;
import us.drullk.relict.init.RelictDamageTypes;

public class RelictDamageTypeGenerator {

    public static void bootstrapDamageTypes(BootstrapContext<DamageType> context) {
        context.register(RelictDamageTypes.UNBREATHABLE, new DamageType("relict.mars_unbreathable", DamageScaling.NEVER, 0.0F));
        context.register(RelictDamageTypes.AIR_DEPLETED, new DamageType("relict.air_depleted", DamageScaling.NEVER, 0.0F));
    }

}
