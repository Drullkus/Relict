package us.drullk.relict.companion;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.server.IntegratedServer;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything one job needs to carry between its {@link Step}s: the parsed request, an accumulating list
 * of capture paths for the response's {@code shots} array, and a grab-bag of extra response fields a verb
 * wants to report (e.g. {@code stage}'s resulting raw sky light).
 */
final class JobContext {

    final String id;
    final JsonObject request;
    final List<String> shots = new ArrayList<>();
    final JsonObject extra = new JsonObject();

    JobContext(String id, JsonObject request) {
        this.id = id;
        this.request = request;
    }

    Minecraft mc() {
        return Minecraft.getInstance();
    }

    ClientLevel level() {
        ClientLevel level = mc().level;
        if (level == null) {
            throw new CompanionException("no world loaded");
        }
        return level;
    }

    /** The integrated (singleplayer) server backing this client's world -- the companion only makes sense
     * against a singleplayer/studio world, never a remote connection. */
    IntegratedServer server() {
        IntegratedServer server = mc().getSingleplayerServer();
        if (server == null) {
            throw new CompanionException("no integrated server -- the companion only works in a singleplayer world");
        }
        return server;
    }

}
