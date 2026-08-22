package us.drullk.relict.atmosphere;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;
import us.drullk.relict.atmosphere.AtmosphereCurve.CycleGeometry;
import us.drullk.relict.atmosphere.AtmosphereCurve.StormArc;
import us.drullk.relict.init.RelictGameRules;
import us.drullk.relict.init.worldgen.RelictDimension;

import java.util.concurrent.CompletableFuture;

/**
 * {@code /relictstorm}: producer testing + media day tool. Also stands in for an F3 debug line — NeoForge
 * 26.2.0.57 has no mod-facing hook into the new {@code DebugScreenEntry} F3 overlay, so {@code status} is
 * the debug readout instead.
 * <p>
 * Every mutation rewrites {@link RelictAtmosphereData}'s {@link StormSchedule} and broadcasts immediately
 * (rather than waiting for the heartbeat), because {@code /relictstorm roll} is the producer's screenshot
 * tool.
 */
public class RelictStormCommand {

    private static final SimpleCommandExceptionType UNKNOWN_PHASE = new SimpleCommandExceptionType(Component.literal("Unknown storm phase"));

    /** Manual rolls do not sample the lead-in randomly (the point is an immediate, predictable result). */
    private static final int MANUAL_ROLL_LEAD_IN_TICKS = AtmosphereCurve.LEAD_IN_MIN_TICKS;

    /** Default storm length in minutes for {@code /relictstorm roll <static> <dust> <flux>} with no {@code [minutes]}. */
    private static final float DEFAULT_ROLL_MINUTES = 10.0F;

    public void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("relictstorm")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("status").executes(this::status))
                .then(Commands.literal("skip").executes(this::skip))
                .then(Commands.literal("clear").executes(this::clear))
                .then(Commands.literal("force")
                        .then(Commands.argument("phase", StringArgumentType.word())
                                .suggests(RelictStormCommand::suggestPhases)
                                .executes(this::force)))
                .then(Commands.literal("roll")
                        .executes(this::rollRandom)
                        .then(Commands.argument("static", FloatArgumentType.floatArg(0.0F, 1.0F))
                                .then(Commands.argument("dust", FloatArgumentType.floatArg(0.0F, 1.0F))
                                        .then(Commands.argument("flux", FloatArgumentType.floatArg(0.0F, 1.0F))
                                                .executes(context -> rollManual(context, DEFAULT_ROLL_MINUTES))
                                                .then(Commands.argument("minutes", FloatArgumentType.floatArg(5.0F, 15.0F))
                                                        .executes(context -> rollManual(context, FloatArgumentType.getFloat(context, "minutes")))))))));
    }

    // ------------------------------------------------------------------------------------------------- status

    private int status(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        long now = RelictAtmosphereServer.marsTotalTicks(server);
        GameRules rules = server.getGlobalGameRules();
        int cycleTenthSols = rules.get(RelictGameRules.ATMOSPHERE_CYCLE_TENTH_SOLS.get());

        CycleGeometry geo = AtmosphereCurve.geometryAt(now, RelictDimension.SOL_TICKS, cycleTenthSols);
        RelictAtmosphereData data = data(server);
        StormSchedule schedule = data.schedule();

        float pressure = AtmosphereCurve.pressureAt(now, geo);
        AtmospherePhase atmospherePhase = AtmosphereCurve.phaseAt(now, geo);
        StormArc arc = AtmosphereCurve.stormAt(now, geo, schedule);

        long nextBoundary = nextBoundaryTick(now, geo);

        context.getSource().sendSuccess(() -> Component.literal(String.format(
                "atmosphere: %s pressure=%.2f (next boundary in %s) | cycleTenthSols=%d",
                atmospherePhase, pressure, clock(nextBoundary - now), cycleTenthSols)), false);

        if (!schedule.hasStorm()) {
            context.getSource().sendSuccess(() -> Component.literal("storm: none scheduled this stay"), false);
        } else {
            boolean willTruncate = schedule.stormEndTick() > geo.presentEnd();
            context.getSource().sendSuccess(() -> Component.literal(String.format(
                    "storm: lead-in %s | start %s | duration %s | end %s (from now) | static=%.2f dust=%.2f flux=%.2f%s",
                    signedClock(schedule.leadInStartTick() - now), signedClock(schedule.stormStartTick() - now),
                    clock(schedule.durationTicks()), signedClock(schedule.stormEndTick() - now),
                    schedule.staticAxis(), schedule.dustAxis(), schedule.fluxAxis(),
                    willTruncate ? " | WILL TRUNCATE at THINNING" : "")), false);
        }

        context.getSource().sendSuccess(() -> Component.literal(String.format(
                "arc: %s tau=%.2f (%d/%dt into phase)", arc.phase(), arc.tau(), arc.ticksIntoPhase(), arc.phaseDurationTicks())), false);
        return 1;
    }

    private static long nextBoundaryTick(long now, CycleGeometry geo) {
        long offset = now - geo.cycleStartTick();
        long[] boundaries = {geo.rampTicks(), geo.halfTicks(), geo.halfTicks() + geo.rampTicks(), geo.cycleTicks()};
        for (long boundary : boundaries) {
            if (offset < boundary) {
                return geo.cycleStartTick() + boundary;
            }
        }
        return geo.cycleStartTick() + geo.cycleTicks();
    }

    // --------------------------------------------------------------------------------------------------- roll

    private int rollRandom(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        long now = RelictAtmosphereServer.marsTotalTicks(server);
        CycleGeometry geo = geometry(server, now);

        RandomSource random = context.getSource().getLevel().getRandom();
        float staticAxis = random.nextFloat();
        float dustAxis = random.nextFloat();
        float fluxAxis = random.nextFloat();
        int durationTicks = AtmosphereCurve.STORM_MIN_TICKS
                + random.nextInt(AtmosphereCurve.STORM_MAX_TICKS - AtmosphereCurve.STORM_MIN_TICKS + 1);

        StormSchedule schedule = new StormSchedule(geo.cycleIndex(), now, MANUAL_ROLL_LEAD_IN_TICKS, durationTicks, staticAxis, dustAxis, fluxAxis);
        return applySchedule(context, schedule, "Random storm rolled");
    }

    private int rollManual(CommandContext<CommandSourceStack> context, float minutes) throws CommandSyntaxException {
        MinecraftServer server = context.getSource().getServer();
        long now = RelictAtmosphereServer.marsTotalTicks(server);
        CycleGeometry geo = geometry(server, now);

        float staticAxis = FloatArgumentType.getFloat(context, "static");
        float dustAxis = FloatArgumentType.getFloat(context, "dust");
        float fluxAxis = FloatArgumentType.getFloat(context, "flux");
        int durationTicks = Math.round(minutes * 1200.0F);

        StormSchedule schedule = new StormSchedule(geo.cycleIndex(), now, MANUAL_ROLL_LEAD_IN_TICKS, durationTicks, staticAxis, dustAxis, fluxAxis);
        return applySchedule(context, schedule, "Storm rolled");
    }

    // -------------------------------------------------------------------------------------------- force/skip/clear

    private int force(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "phase");
        StormPhase phase = parsePhase(name);
        if (phase == null) {
            throw UNKNOWN_PHASE.create();
        }

        return forcePhase(context, phase);
    }

    private int skip(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        long now = RelictAtmosphereServer.marsTotalTicks(server);
        CycleGeometry geo = geometry(server, now);
        StormSchedule schedule = data(server).schedule();
        StormPhase current = AtmosphereCurve.stormAt(now, geo, schedule).phase();
        return forcePhase(context, current.next());
    }

    /** Shared by {@code force <phase>} and {@code skip} (= {@code force currentPhase.next()}). */
    private int forcePhase(CommandContext<CommandSourceStack> context, StormPhase phase) {
        MinecraftServer server = context.getSource().getServer();
        long now = RelictAtmosphereServer.marsTotalTicks(server);
        CycleGeometry geo = geometry(server, now);
        StormSchedule current = data(server).schedule();

        if (phase == StormPhase.CLEAR) {
            return applySchedule(context, StormSchedule.none(geo.cycleIndex()), "Storm phase set to CLEAR");
        }

        StormSchedule base = current.hasStorm() ? current
                : new StormSchedule(geo.cycleIndex(), now, MANUAL_ROLL_LEAD_IN_TICKS, Math.round(DEFAULT_ROLL_MINUTES * 1200.0F), 0.5F, 0.5F, 0.5F);
        long offset = AtmosphereCurve.phaseStartOffset(base, phase);
        long leadInStartTick = now - offset;
        StormSchedule forced = new StormSchedule(geo.cycleIndex(), leadInStartTick, base.leadInTicks(), base.durationTicks(),
                base.staticAxis(), base.dustAxis(), base.fluxAxis());
        return applySchedule(context, forced, "Storm phase set to " + phase);
    }

    private int clear(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        long now = RelictAtmosphereServer.marsTotalTicks(server);
        CycleGeometry geo = geometry(server, now);
        return applySchedule(context, StormSchedule.none(geo.cycleIndex()), "Storm cleared");
    }

    // -------------------------------------------------------------------------------------------------- shared

    private int applySchedule(CommandContext<CommandSourceStack> context, StormSchedule schedule, String message) {
        MinecraftServer server = context.getSource().getServer();
        RelictAtmosphereData data = data(server);
        data.setSchedule(schedule);

        GameRules rules = server.getGlobalGameRules();
        int cycleTenthSols = rules.get(RelictGameRules.ATMOSPHERE_CYCLE_TENTH_SOLS.get());

        ServerLevel marsLevel = server.getLevel(RelictDimension.MARS_LEVEL);
        if (marsLevel != null) {
            PacketDistributor.sendToPlayersInDimension(marsLevel, new AtmosphereSyncPayload(cycleTenthSols, schedule));
        }

        context.getSource().sendSuccess(() -> Component.literal(message), true);
        return 1;
    }

    private static RelictAtmosphereData data(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(RelictAtmosphereData.TYPE);
    }

    private static CycleGeometry geometry(MinecraftServer server, long now) {
        int cycleTenthSols = server.getGlobalGameRules().get(RelictGameRules.ATMOSPHERE_CYCLE_TENTH_SOLS.get());
        return AtmosphereCurve.geometryAt(now, RelictDimension.SOL_TICKS, cycleTenthSols);
    }

    @Nullable
    private static StormPhase parsePhase(String name) {
        for (StormPhase phase : StormPhase.values()) {
            if (phase.name().equalsIgnoreCase(name)) {
                return phase;
            }
        }

        return null;
    }

    private static CompletableFuture<Suggestions> suggestPhases(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        for (StormPhase phase : StormPhase.values()) {
            builder.suggest(phase.name());
        }

        return builder.buildFuture();
    }

    /** {@code mm:ss}, always non-negative (a duration or a magnitude). */
    private static String clock(long ticks) {
        long seconds = Math.abs(ticks) / 20;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    /** {@code mm:ss} with an explicit sign, for "offset from now" fields where negative means already past. */
    private static String signedClock(long ticks) {
        return (ticks < 0 ? "-" : "") + clock(ticks);
    }

}
