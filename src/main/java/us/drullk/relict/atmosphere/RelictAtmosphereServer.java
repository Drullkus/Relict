package us.drullk.relict.atmosphere;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.clock.WorldClock;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import us.drullk.relict.RelictTags;
import us.drullk.relict.atmosphere.AtmosphereCurve.CycleGeometry;
import us.drullk.relict.atmosphere.AtmosphereCurve.StormArc;
import us.drullk.relict.init.RelictDamageTypes;
import us.drullk.relict.init.RelictGameRules;
import us.drullk.relict.init.RelictItems;
import us.drullk.relict.init.RelictSounds;
import us.drullk.relict.init.worldgen.RelictDimension;
import us.drullk.relict.item.StoredCharges;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side tick loop for the atmosphere/storm clock: rolls one {@link StormSchedule} per stay, derives
 * the live arc from it, applies triboelectric discharges, spawns dust devils, and broadcasts the sparse
 * sync payload. Neither seasonal pressure nor the storm arc need ticking here in the state-machine sense —
 * both are pure functions of the Mars clock, read fresh every call — but the roll itself is state, fixed
 * the instant it is made, so this class is where a new stay's cycle turnover is noticed and rolled.
 */
public class RelictAtmosphereServer {

    private static final int HEARTBEAT_INTERVAL_TICKS = 40;

    private final RandomSource random = RandomSource.create();

    private final Map<UUID, Long> playerDischargeCooldown = new HashMap<>();
    private final Map<UUID, Long> pendingSnapTick = new HashMap<>();
    private long globalDischargeCooldownUntil = Long.MIN_VALUE;
    private long lastBroadcastTick = Long.MIN_VALUE;
    private final List<DustDevil> dustDevils = new ArrayList<>();

    /**
     * Phase-entry sound detection without a state machine: the phase is derived, not stored, so "did we
     * just enter this phase" is read off a transient field instead. Not saved, not synced — a server
     * restarted mid-storm does not replay the arrival/lead-in stinger, which is the one place this derived
     * model is lossy.
     */
    private StormPhase lastPhase = null;

    public void levelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !level.dimensionTypeRegistration().is(RelictTags.REQUIRES_MARS_LIFE_SUPPORT)) {
            return;
        }

        MinecraftServer server = level.getServer();
        long totalTicks = marsTotalTicks(server);
        GameRules rules = server.getGlobalGameRules();
        int cycleTenthSols = rules.get(RelictGameRules.ATMOSPHERE_CYCLE_TENTH_SOLS.get());
        int stormFrequencyPercent = rules.get(RelictGameRules.STORM_FREQUENCY_PERCENT.get());
        boolean stormDamageEnabled = rules.get(RelictGameRules.STORM_DAMAGE.get());

        CycleGeometry geo = AtmosphereCurve.geometryAt(totalTicks, RelictDimension.SOL_TICKS, cycleTenthSols);
        RelictAtmosphereData data = server.getDataStorage().computeIfAbsent(RelictAtmosphereData.TYPE);

        boolean rolled = false;
        if (data.schedule().cycleIndex() != geo.cycleIndex()) {
            data.setSchedule(AtmosphereCurve.roll(geo, totalTicks, stormFrequencyPercent, this.random));
            rolled = true;
        }

        float pressure = AtmosphereCurve.pressureAt(totalTicks, geo);
        StormArc arc = AtmosphereCurve.stormAt(totalTicks, geo, data.schedule());

        firePhaseEntrySounds(level, arc.phase());

        tickDischarges(level, totalTicks, arc.phase(), arc.tau(), data.schedule().staticAxis(), stormDamageEnabled);
        tickDustDevils(level, totalTicks, pressure, data.schedule().fluxAxis());

        if (rolled || totalTicks - this.lastBroadcastTick >= HEARTBEAT_INTERVAL_TICKS) {
            this.lastBroadcastTick = totalTicks;
            PacketDistributor.sendToPlayersInDimension(level, new AtmosphereSyncPayload(cycleTenthSols, data.schedule()));
        }
    }

    /**
     * Sends the current payload to one player immediately, closing the join/dimension-change gap: without
     * this a joining player renders from a guessed cycle for up to the heartbeat interval.
     */
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        sendImmediateSync(event.getEntity());
    }

    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        sendImmediateSync(event.getEntity());
    }

    private void sendImmediateSync(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || !(player.level() instanceof ServerLevel level)
                || !level.dimensionTypeRegistration().is(RelictTags.REQUIRES_MARS_LIFE_SUPPORT)) {
            return;
        }

        MinecraftServer server = level.getServer();
        GameRules rules = server.getGlobalGameRules();
        int cycleTenthSols = rules.get(RelictGameRules.ATMOSPHERE_CYCLE_TENTH_SOLS.get());
        RelictAtmosphereData data = server.getDataStorage().computeIfAbsent(RelictAtmosphereData.TYPE);

        PacketDistributor.sendToPlayer(serverPlayer, new AtmosphereSyncPayload(cycleTenthSols, data.schedule()));
    }

    public static long marsTotalTicks(MinecraftServer server) {
        Holder<WorldClock> marsClock = server.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK).getOrThrow(RelictDimension.MARS_CLOCK);
        return server.clockManager().getTotalTicks(marsClock);
    }

    // ------------------------------------------------------------------------------------ phase-entry sounds

    private void firePhaseEntrySounds(ServerLevel level, StormPhase phase) {
        StormPhase previous = this.lastPhase;
        this.lastPhase = phase;

        if (previous == null || previous == phase) {
            return;
        }

        if (phase == StormPhase.DISTANT) {
            for (ServerPlayer player : level.players()) {
                RelictSounds.fire(RelictSounds.STORM_LEAD_IN, level, player.blockPosition(), SoundSource.WEATHER, 1.0F, 1.0F);
            }
        } else if (phase == StormPhase.ARRIVAL) {
            for (ServerPlayer player : level.players()) {
                RelictSounds.fire(RelictSounds.STORM_ARRIVAL, level, player.blockPosition(), SoundSource.WEATHER, 1.0F, 1.0F);
            }
        }
    }

    // --------------------------------------------------------------------------------------- discharges (kept)

    private void tickDischarges(ServerLevel level, long totalTicks, StormPhase stormPhase, float tau, float staticAxis, boolean stormDamageEnabled) {
        this.applyDueSnaps(level, totalTicks, stormDamageEnabled);

        double chancePerTick = AtmosphereCurve.dischargeChancePerTick(tau, stormPhase, staticAxis);
        if (chancePerTick <= 0.0 || totalTicks < this.globalDischargeCooldownUntil) {
            return;
        }

        for (ServerPlayer player : level.players()) {
            if (!isExposed(level, player)) {
                continue;
            }

            UUID id = player.getUUID();
            long cooldownUntil = this.playerDischargeCooldown.getOrDefault(id, Long.MIN_VALUE);
            if (totalTicks < cooldownUntil || this.random.nextDouble() >= chancePerTick) {
                continue;
            }

            long snapTick = totalTicks + AtmosphereCurve.DISCHARGE_CORONA_LEAD_TICKS;
            this.pendingSnapTick.put(id, snapTick);
            this.playerDischargeCooldown.put(id, snapTick + AtmosphereCurve.DISCHARGE_PLAYER_COOLDOWN_TICKS);
            this.globalDischargeCooldownUntil = totalTicks + AtmosphereCurve.DISCHARGE_GLOBAL_COOLDOWN_TICKS;

            RelictSounds.fire(RelictSounds.DISCHARGE_CORONA, level, player.blockPosition(), SoundSource.WEATHER, 0.6F, 1.0F);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(), 3, 0.4, 0.4, 0.4, 0.01);
        }
    }

    private void applyDueSnaps(ServerLevel level, long totalTicks, boolean stormDamageEnabled) {
        this.pendingSnapTick.entrySet().removeIf(entry -> {
            if (totalTicks < entry.getValue()) {
                return false;
            }

            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player != null && player.level() == level) {
                this.applySnap(level, player, stormDamageEnabled);
            }

            return true;
        });
    }

    private void applySnap(ServerLevel level, ServerPlayer player, boolean stormDamageEnabled) {
        RelictSounds.fire(RelictSounds.DISCHARGE_SNAP, level, player.blockPosition(), SoundSource.WEATHER, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(), 12, 0.5, 0.6, 0.5, 0.05);

        boolean grounded = player.getItemBySlot(EquipmentSlot.FEET).is(RelictItems.GROUNDING_TREADS);
        // Grounding Treads negate the hit outright and bank the shed charge to the chest instead, independent
        // of the player's generic ELECTRIC_DAMAGE attribute so a bare storm zap is never merely softened.
        if (grounded) {
            StoredCharges.addCharge(player.getItemBySlot(EquipmentSlot.CHEST), Math.round(AtmosphereCurve.DISCHARGE_DAMAGE * 10.0F));
            return;
        }

        if (!stormDamageEnabled) {
            return;
        }

        DamageSource source = level.damageSources().source(RelictDamageTypes.STORM_DISCHARGE);
        player.hurtServer(level, source, AtmosphereCurve.DISCHARGE_DAMAGE);
    }

    private static boolean isExposed(ServerLevel level, ServerPlayer player) {
        GameType mode = player.gameMode.getGameModeForPlayer();
        return (mode == GameType.SURVIVAL || mode == GameType.ADVENTURE) && level.canSeeSky(player.blockPosition());
    }

    // -------------------------------------------------------------------------------------------- dust devils

    /**
     * The gate is pressure alone — devils appear from partway up FILLING through partway down THINNING,
     * foreshadowing the storm's character, and stay active during the storm arc itself too (not suppressed
     * by an active storm). Devils remain entity-free, non-damaging, and particle-only regardless.
     */
    private void tickDustDevils(ServerLevel level, long totalTicks, float pressure, float fluxAxis) {
        this.dustDevils.removeIf(devil -> devil.tick(level, this.random));

        boolean eligible = pressure >= AtmosphereCurve.DUST_DEVIL_PRESSURE_FLOOR;
        int maxDevils = AtmosphereCurve.dustDevilMaxCount(fluxAxis);
        double chancePerTick = AtmosphereCurve.dustDevilChancePerTick(fluxAxis);
        if (!eligible || this.dustDevils.size() >= maxDevils || this.random.nextDouble() >= chancePerTick) {
            return;
        }

        List<ServerPlayer> candidates = level.players().stream().filter(p -> isExposed(level, p)).toList();
        if (candidates.isEmpty()) {
            return;
        }

        ServerPlayer near = candidates.get(this.random.nextInt(candidates.size()));
        double angle = this.random.nextDouble() * Math.PI * 2.0;
        double distance = 6.0 + this.random.nextDouble() * 6.0;
        DustDevil devil = new DustDevil(
                near.getX() + Math.cos(angle) * distance,
                near.getY(),
                near.getZ() + Math.sin(angle) * distance,
                (this.random.nextDouble() - 0.5) * 0.15,
                (this.random.nextDouble() - 0.5) * 0.15,
                AtmosphereCurve.dustDevilLifetimeTicks(fluxAxis),
                fluxAxis);
        this.dustDevils.add(devil);
        RelictSounds.fire(RelictSounds.DUST_DEVIL, level, BlockPos.containing(devil.x, devil.y, devil.z), SoundSource.WEATHER,
                AtmosphereCurve.dustDevilSoundVolume(fluxAxis), 1.0F);
    }

    /** A cheap, entity-free wandering particle column: no collision, no damage, despawns after a short walk. */
    private static final class DustDevil {
        double x, y, z;
        final double vx, vz;
        final float fluxAxis;
        int ticksLeft;

        DustDevil(double x, double y, double z, double vx, double vz, int ticksLeft, float fluxAxis) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vz = vz;
            this.ticksLeft = ticksLeft;
            this.fluxAxis = fluxAxis;
        }

        /** @return true once expired, so the caller can remove it. */
        boolean tick(ServerLevel level, RandomSource random) {
            this.x += this.vx;
            this.z += this.vz;
            this.ticksLeft--;

            float columnHeight = AtmosphereCurve.dustDevilColumnHeightScale(this.fluxAxis);
            int particleCount = AtmosphereCurve.dustDevilParticleCount(this.fluxAxis);
            float horizontalSpread = AtmosphereCurve.dustDevilHorizontalSpreadScale(this.fluxAxis) * 0.3F;
            for (int i = 0; i < particleCount; i++) {
                double h = random.nextDouble() * columnHeight;
                level.sendParticles(ParticleTypes.ASH, this.x, this.y + h, this.z, 1, horizontalSpread, 0.1, horizontalSpread, 0.01);
            }

            return this.ticksLeft <= 0;
        }
    }

}
