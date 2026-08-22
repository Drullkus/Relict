package us.drullk.relict;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Unit;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jspecify.annotations.Nullable;
import us.drullk.relict.init.RelictAttributes;
import us.drullk.relict.init.RelictDamageTypes;
import us.drullk.relict.init.RelictDataComponents;
import us.drullk.relict.init.RelictItems;
import us.drullk.relict.init.RelictSounds;
import us.drullk.relict.item.StoredCharges;

public class RelictEvents {

    // Sole tunable: player gravity is scaled to this fraction of Earth's while in a Mars-life-support
    // dimension. Fall-damage onset and lethal-fall distance are derived from it, not set independently,
    // so a fall of d Mars blocks always lands with the impact energy of an Earth fall of gravityScale*d.
    private static final double MARS_GRAVITY_SCALE = 0.38D;

    private static final AttributeModifier MARS_GRAVITY = new AttributeModifier(Relict.id("mars_gravity"), MARS_GRAVITY_SCALE - 1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    private static final AttributeModifier MARS_FALL_DAMAGE_MULTIPLIER = new AttributeModifier(Relict.id("mars_fall_damage_multiplier"), MARS_GRAVITY_SCALE - 1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    private static final AttributeModifier MARS_SAFE_FALL_DISTANCE = new AttributeModifier(Relict.id("mars_safe_fall_distance"), 1.0D / MARS_GRAVITY_SCALE - 1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

    public void levelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !level.dimensionTypeRegistration().is(RelictTags.HAS_MARS_ATMOSPHERE)
                || Math.floorMod(level.getGameTime(), 10) != 0L) {
            return;
        }

        DamageSource unbreathable = level.damageSources().source(RelictDamageTypes.UNBREATHABLE);
        DamageSource thinAir = level.damageSources().source(RelictDamageTypes.AIR_DEPLETED);

        for (ServerPlayer player : level.players()) {
            applyMarsGravity(player);

            GameType mode = player.gameMode.getGameModeForPlayer();
            if (mode != GameType.SURVIVAL && mode != GameType.ADVENTURE) {
                continue;
            }

            drainVizard(level, player);

            AttributeInstance lifeSupport = player.getAttribute(RelictAttributes.MARS_LIFE_SUPPORT);
            if (lifeSupport == null || lifeSupport.getValue() > 0.0D) {
                continue;
            }

            boolean deadVizard = player.getItemBySlot(EquipmentSlot.HEAD).is(RelictItems.SPENT_VIZARD.get());
            player.hurtServer(level, deadVizard ? thinAir : unbreathable, Math.max(player.getMaxHealth(), 20) * 0.5f);
        }
    }

    private void drainVizard(ServerLevel level, ServerPlayer player) {
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!head.is(RelictItems.VITAL_VIZARD.get())) {
            return;
        }

        int remainingBefore = head.getMaxDamage() - head.getDamageValue();
        if (remainingBefore <= 0) {
            return;
        }

        head.setDamageValue(head.getDamageValue() + 1);
        int remainingAfter = remainingBefore - 1;

        if (remainingAfter <= 0) {
            goInert(level, player, head);
            return;
        }

        int levelBefore = warningLevel(remainingBefore, head.getMaxDamage());
        int levelAfter = warningLevel(remainingAfter, head.getMaxDamage());
        for (int crossing = levelBefore + 1; crossing <= levelAfter; crossing++) {
            warn(level, player, remainingAfter, head.getMaxDamage());
        }
    }

    private static int warningLevel(int remaining, int max) {
        return (int) Math.floor(Math.log((double) max / remaining) / Math.log(2.0));
    }

    private void warn(ServerLevel level, ServerPlayer player, int remaining, int max) {
        int percent = Math.round(100.0F * remaining / max);
        player.sendOverlayMessage(Component.translatable("relict.vizard.warning", percent));
        level.playSound(null, player, RelictSounds.VIZARD_WARNING.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private void goInert(ServerLevel level, ServerPlayer player, ItemStack vitalVizard) {
        ItemStack inert = vitalVizard.transmuteCopy(RelictItems.SPENT_VIZARD.get());
        inert.set(RelictDataComponents.INERT.get(), Unit.INSTANCE);
        player.setItemSlot(EquipmentSlot.HEAD, inert);

        player.sendOverlayMessage(Component.translatable("relict.vizard.inert"));
        level.playSound(null, player, RelictSounds.VIZARD_FAILED.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        syncMarsGravity(event.getEntity());
    }

    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        syncMarsGravity(event.getEntity());
    }

    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        syncMarsGravity(event.getEntity());
    }

    /** Applies or removes the Mars gravity set immediately, keyed off the player's current dimension. */
    private static void syncMarsGravity(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || !(player.level() instanceof ServerLevel level)) {
            return;
        }

        if (level.dimensionTypeRegistration().is(RelictTags.HAS_MARS_ATMOSPHERE)) {
            applyMarsGravity(serverPlayer);
        } else {
            removeMarsGravity(serverPlayer);
        }
    }

    // The three modifiers are one set: always all present or all absent, never partial.
    private static void applyMarsGravity(ServerPlayer player) {
        addIfAbsent(player, Attributes.GRAVITY, MARS_GRAVITY);
        addIfAbsent(player, Attributes.FALL_DAMAGE_MULTIPLIER, MARS_FALL_DAMAGE_MULTIPLIER);
        addIfAbsent(player, Attributes.SAFE_FALL_DISTANCE, MARS_SAFE_FALL_DISTANCE);
    }

    private static void removeMarsGravity(ServerPlayer player) {
        removeIfPresent(player, Attributes.GRAVITY, MARS_GRAVITY.id());
        removeIfPresent(player, Attributes.FALL_DAMAGE_MULTIPLIER, MARS_FALL_DAMAGE_MULTIPLIER.id());
        removeIfPresent(player, Attributes.SAFE_FALL_DISTANCE, MARS_SAFE_FALL_DISTANCE.id());
    }

    private static void addIfAbsent(ServerPlayer player, Holder<Attribute> attribute, AttributeModifier modifier) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null && !instance.hasModifier(modifier.id())) {
            instance.addTransientModifier(modifier);
        }
    }

    private static void removeIfPresent(ServerPlayer player, Holder<Attribute> attribute, Identifier modifierId) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(modifierId);
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
