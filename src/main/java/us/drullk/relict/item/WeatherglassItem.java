package us.drullk.relict.item;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.saveddata.WeatherData;
import net.neoforged.fml.loading.FMLLoader;
import org.jspecify.annotations.Nullable;
import us.drullk.relict.RelictTags;
import us.drullk.relict.atmosphere.AtmosphereCurve;
import us.drullk.relict.atmosphere.AtmosphereCurve.CycleGeometry;
import us.drullk.relict.atmosphere.AtmospherePhase;
import us.drullk.relict.atmosphere.RelictAtmosphereData;
import us.drullk.relict.atmosphere.RelictAtmosphereServer;
import us.drullk.relict.atmosphere.RelictStormCommand;
import us.drullk.relict.atmosphere.StormSchedule;
import us.drullk.relict.init.RelictDataComponents;
import us.drullk.relict.init.RelictGameRules;
import us.drullk.relict.init.worldgen.RelictDimension;

/**
 * Reads the sky's schedule, not its state — Mars atmosphere clock and storm or vanilla's queued weather.
 */
public class WeatherglassItem extends Item {

    private static final int USE_COOLDOWN_TICKS = 20;

    public WeatherglassItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        if (player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.PASS;
        }
        player.getCooldowns().addCooldown(stack, USE_COOLDOWN_TICKS);

        MinecraftServer server = serverLevel.getServer();
        if (serverLevel.dimensionTypeRegistration().is(RelictTags.HAS_MARS_ATMOSPHERE)) {
            if (!FMLLoader.getCurrent().isProduction()) {
                readMarsAtmosphere(server, player);
            }
        } else if (serverLevel.canHaveWeather()) {
            readVanillaWeather(server, player, stack);
        } else {
            if (!FMLLoader.getCurrent().isProduction()) {
                player.sendSystemMessage(Component.literal("FIXME no weather here"));
            }
        }

        return InteractionResult.SUCCESS;
    }

    // ------------------------------------------------------------------------------------------------- Mars

    private static void readMarsAtmosphere(MinecraftServer server, Player player) {
        long now = RelictAtmosphereServer.marsTotalTicks(server);
        int cycleTenthSols = server.getGlobalGameRules().get(RelictGameRules.ATMOSPHERE_CYCLE_TENTH_SOLS.get());
        CycleGeometry geo = AtmosphereCurve.geometryAt(now, RelictDimension.SOL_TICKS, cycleTenthSols);
        StormSchedule schedule = server.getDataStorage().computeIfAbsent(RelictAtmosphereData.TYPE).schedule();

        AtmospherePhase phase = AtmosphereCurve.phaseAt(now, geo);
        long nextBoundary = RelictStormCommand.nextBoundaryTick(now, geo);
        player.sendSystemMessage(Component.literal(String.format("FIXME atmosphere: %s (next boundary in %s)", phase, RelictStormCommand.clock(nextBoundary - now))));

        String atmosphereTarget = describeAtmospherePhase(phase, nextBoundary - now);
        if (atmosphereTarget != null) {
            player.sendSystemMessage(Component.literal(atmosphereTarget));
        }

        if (!schedule.hasStorm()) {
            player.sendSystemMessage(Component.literal("FIXME no storm scheduled this stay"));
        } else {
            player.sendSystemMessage(Component.literal(String.format(
                    "FIXME storm promise: lead-in %s | start %s | duration %s | end %s (from now)",
                    RelictStormCommand.signedClock(schedule.leadInStartTick() - now),
                    RelictStormCommand.signedClock(schedule.stormStartTick() - now),
                    RelictStormCommand.clock(schedule.durationTicks()),
                    RelictStormCommand.signedClock(schedule.stormEndTick() - now))));
        }
    }

    /** @return a FIXME placeholder line for the three atmosphere-cycle states, or {@code null} for PRESENT
     * (still {@code mars_clear}, no countdown to narrate). */
    @Nullable
    private static String describeAtmospherePhase(AtmospherePhase phase, long ticksToBoundary) {
        String clock = RelictStormCommand.clock(ticksToBoundary);
        return switch (phase) {
            case FILLING -> "FIXME atmosphere filling in, full pressure in " + clock;
            case THINNING -> "FIXME atmosphere thinning out, vacuum in " + clock;
            case VACUUM -> "FIXME vacuum, atmosphere returning in " + clock;
            case PRESENT -> null;
        };
    }

    // ---------------------------------------------------------------------------------------- vanilla weather

    private static void readVanillaWeather(MinecraftServer server, Player player, ItemStack stack) {
        long now = overworldGameTime(server);
        boolean advancing = server.getGlobalGameRules().get(GameRules.ADVANCE_WEATHER);

        WeatherglassReading reading = advancing
                ? deriveReading(server.getWeatherData(), now)
                : new WeatherglassReading(WeatherglassReading.Kind.CLEAR, now, now);
        stack.set(RelictDataComponents.WEATHERGLASS_READING.get(), reading);

        if (!FMLLoader.getCurrent().isProduction()) {
            if (!advancing) {
                player.sendSystemMessage(Component.literal("FIXME weather is not advancing right now"));
                return;
            }

            player.sendSystemMessage(Component.literal(describe(reading, now)));
        }
    }

    /**
     * Vanilla's own weather-advance loop (a running rain/thunder RNG walk, not a stored schedule) has no
     * reusable read path the way {@code StormSchedule} does, so this reduces {@link WeatherData}'s four
     * counters to the single next change the glass promises: whichever of "thundering now", "raining now",
     * "thunder queued", or "rain queued" applies first.
     */
    private static WeatherglassReading deriveReading(WeatherData weather, long now) {
        if (weather.isThundering()) {
            return new WeatherglassReading(WeatherglassReading.Kind.THUNDER_EXIT, now, now + weather.getThunderTime());
        }
        if (weather.isRaining()) {
            return new WeatherglassReading(WeatherglassReading.Kind.RAIN_EXIT, now, now + weather.getRainTime());
        }
        if (weather.getThunderTime() > 0 && weather.getThunderTime() < weather.getRainTime()) {
            return new WeatherglassReading(WeatherglassReading.Kind.THUNDER_INTO, now, now + weather.getThunderTime());
        }
        if (weather.getRainTime() > 0) {
            return new WeatherglassReading(WeatherglassReading.Kind.RAIN_INTO, now, now + weather.getRainTime());
        }
        return new WeatherglassReading(WeatherglassReading.Kind.CLEAR, now, now);
    }

    private static String describe(WeatherglassReading reading, long now) {
        String remaining = RelictStormCommand.clock(reading.targetGameTime() - now);
        return switch (reading.kind()) {
            case CLEAR -> "FIXME clear, nothing queued";
            case RAIN_INTO -> "FIXME rain in " + remaining;
            case RAIN_EXIT -> "FIXME raining, clearing in " + remaining;
            case THUNDER_INTO -> "FIXME thunderstorm in " + remaining;
            case THUNDER_EXIT -> "FIXME thunderstorm, clearing in " + remaining;
        };
    }

    private static long overworldGameTime(MinecraftServer server) {
        Holder<WorldClock> overworldClock = server.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK).getOrThrow(WorldClocks.OVERWORLD);
        return server.clockManager().getTotalTicks(overworldClock);
    }

}
