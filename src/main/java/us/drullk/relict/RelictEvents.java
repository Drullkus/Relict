package us.drullk.relict;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jspecify.annotations.Nullable;
import us.drullk.relict.init.RelictAttributes;
import us.drullk.relict.init.RelictDamageTypes;
import us.drullk.relict.item.StoredCharges;

public class RelictEvents {

    public void levelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !level.dimensionTypeRegistration().is(RelictTags.REQUIRES_MARS_LIFE_SUPPORT)
                || Math.floorMod(level.getGameTime(), 10) != 0L) {
            return;
        }

        DamageSource unbreathable = level.damageSources().source(RelictDamageTypes.UNBREATHABLE);

        for (ServerPlayer player : level.players()) {
            GameType mode = player.gameMode.getGameModeForPlayer();
            if (mode != GameType.SURVIVAL && mode != GameType.ADVENTURE) {
                continue;
            }

            AttributeInstance lifeSupport = player.getAttribute(RelictAttributes.MARS_LIFE_SUPPORT);
            if (lifeSupport == null || lifeSupport.getValue() > 0.0D) {
                continue;
            }

            player.hurtServer(level, unbreathable, Math.max(player.getMaxHealth(), 20) * 0.5f);
        }
    }

    public void incomingElectricDamage(LivingIncomingDamageEvent event) {
        if (!event.getSource().is(RelictTags.IS_ELECTRIC)) {
            return;
        }

        LivingEntity entity = event.getEntity();
        AttributeInstance electricDamage = entity.getAttribute(RelictAttributes.ELECTRIC_DAMAGE);
        if (electricDamage == null) {
            return;
        }

        float multiplier = (float) electricDamage.getValue();
        if (multiplier >= 1.0F) {
            return;
        }

        float damageAmount = event.getAmount();
        float damageApplied = damageAmount * multiplier;
        event.setAmount(damageApplied);

        StoredCharges.addCharge(entity.getItemBySlot(EquipmentSlot.CHEST), Math.round((damageAmount - damageApplied) * 10));
    }

    public void mobEffectApplicable(MobEffectEvent.Applicable event) {
        if (event.getEffectInstance().is(MobEffects.NAUSEA) && isEnabled(event.getEntity().getAttribute(RelictAttributes.NAUSEA_IMMUNITY))) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    private static boolean isEnabled(@Nullable AttributeInstance attribute) {
        return attribute != null && attribute.getValue() > 0.0D;
    }

}
