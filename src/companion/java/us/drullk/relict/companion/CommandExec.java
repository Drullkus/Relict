package us.drullk.relict.companion;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs a raw command string in-process against the integrated server: companion verbs are high-level, but
 * under the hood they mostly just issue plain Minecraft commands from code instead of typed into chat.
 * Output suppressed, since a caller reads the companion's own JSON response, never chat.
 *
 * The source is built from the actual singleplayer {@link ServerPlayer}, not the bare server --
 * {@code MinecraftServer.createCommandSourceStack()} carries no entity at all
 * ({@code CommandSourceStack.getEntity() == null}, confirmed by direct introspection), so every
 * {@code @s}-targeted command silently no-ops (`/give`, `/tp`, `/item replace` -- all of them; brigadier
 * swallows the "no entity" failure inside {@code performPrefixedCommand} rather than throwing, so nothing
 * in this module's own try/catch ever saw it). Op permission is added regardless of the source, since the
 * staged world's own op status shouldn't gate this module's work.
 */
final class CommandExec {

    private CommandExec() {
    }

    /**
     * Command execution has to happen on the server's own thread (the integrated server runs on a thread
     * separate from the client's render/tick thread even in singleplayer); this step submits the command
     * once, on its first poll, and reports done only after the server has actually run it -- never
     * resubmits while a submission is still in flight.
     */
    static Step run(String command) {
        AtomicBoolean submitted = new AtomicBoolean(false);
        AtomicBoolean done = new AtomicBoolean(false);
        Exception[] failure = new Exception[1];
        return ctx -> {
            if (submitted.compareAndSet(false, true)) {
                MinecraftServer server = ctx.server();
                var clientPlayer = ctx.mc().player;
                UUID playerId = clientPlayer != null ? clientPlayer.getUUID() : null;
                server.execute(() -> {
                    try {
                        ServerPlayer serverPlayer = playerId != null ? server.getPlayerList().getPlayer(playerId) : null;
                        CommandSourceStack source = (serverPlayer != null
                                ? serverPlayer.createCommandSourceStack()
                                : server.createCommandSourceStack())
                                .withSuppressedOutput()
                                .withPermission(PermissionSet.ALL_PERMISSIONS);
                        server.getCommands().performPrefixedCommand(source, command);
                    } catch (Exception e) {
                        failure[0] = e;
                    } finally {
                        done.set(true);
                    }
                });
            }
            if (done.get() && failure[0] != null) {
                throw new CompanionException("command failed: /" + command + " (" + failure[0].getMessage() + ")", failure[0]);
            }
            return done.get();
        };
    }

}
