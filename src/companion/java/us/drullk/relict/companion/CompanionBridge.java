package us.drullk.relict.companion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import us.drullk.relict.Relict;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * The blocking bridge's game side: watches {@code run/companion/inbox/} for request files {@code rigctl}
 * drops there, runs them one at a time on the client's own tick loop, and writes a matching response into
 * {@code run/companion/outbox/} -- file-based request/response, the simplest transport that keeps this
 * module free of any extra dependency. One job in flight at a time, matching rigctl's own
 * one-request-outstanding contract; a second request arriving mid-job queues behind it rather than
 * interleaving.
 */
final class CompanionBridge {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path inbox;
    private static Path outbox;
    private static final Deque<Path> queue = new ArrayDeque<>();
    private static Job activeJob;
    private static JobContext activeContext;

    private CompanionBridge() {
    }

    static void onClientTick(ClientTickEvent.Post event) {
        try {
            tick();
        } catch (Exception e) {
            Relict.LOGGER.error("[companion] tick failed", e);
        }
    }

    private static void tick() throws IOException {
        ensureDirs();
        if (activeJob == null) {
            pollInbox();
        }
        if (activeJob != null) {
            advance();
        }
    }

    private static void ensureDirs() throws IOException {
        if (inbox != null) {
            return;
        }
        File gameDir = Minecraft.getInstance().gameDirectory;
        inbox = gameDir.toPath().resolve("companion/inbox");
        outbox = gameDir.toPath().resolve("companion/outbox");
        Files.createDirectories(inbox);
        Files.createDirectories(outbox);
        Relict.LOGGER.info("[companion] watching {}", inbox);
    }

    private static void pollInbox() throws IOException {
        if (queue.isEmpty()) {
            List<Path> found = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(inbox, "*.json")) {
                stream.forEach(found::add);
            }
            found.sort(Comparator.comparing(p -> p.getFileName().toString()));
            queue.addAll(found);
        }
        if (queue.isEmpty()) {
            return;
        }
        startJob(queue.poll());
    }

    private static void startJob(Path requestFile) {
        String fileName = requestFile.getFileName().toString();
        String id = fileName.endsWith(".json") ? fileName.substring(0, fileName.length() - 5) : fileName;
        try {
            String text = Files.readString(requestFile, StandardCharsets.UTF_8);
            Files.deleteIfExists(requestFile);
            JsonObject request = JsonParser.parseString(text).getAsJsonObject();
            activeContext = new JobContext(id, request);
            activeJob = Verbs.plan(request, activeContext);
        } catch (Exception e) {
            Relict.LOGGER.error("[companion] failed to plan job {}", id, e);
            writeError(id, messageOf(e));
            activeJob = null;
            activeContext = null;
        }
    }

    private static void advance() {
        try {
            if (activeJob.tick(activeContext)) {
                writeSuccess(activeContext);
                activeJob = null;
                activeContext = null;
            }
        } catch (Exception e) {
            Relict.LOGGER.error("[companion] job {} failed", activeContext.id, e);
            writeError(activeContext.id, messageOf(e));
            activeJob = null;
            activeContext = null;
        }
    }

    private static String messageOf(Exception e) {
        return e.getMessage() == null ? e.toString() : e.getMessage();
    }

    private static void writeSuccess(JobContext ctx) {
        JsonObject response = new JsonObject();
        response.addProperty("id", ctx.id);
        response.addProperty("status", "ok");
        response.addProperty("verb", ctx.request.has("verb") ? ctx.request.get("verb").getAsString() : "?");
        var shots = new com.google.gson.JsonArray();
        ctx.shots.forEach(shots::add);
        response.add("shots", shots);
        for (Map.Entry<String, JsonElement> entry : ctx.extra.entrySet()) {
            response.add(entry.getKey(), entry.getValue());
        }
        write(ctx.id, response);
    }

    private static void writeError(String id, String message) {
        JsonObject response = new JsonObject();
        response.addProperty("id", id);
        response.addProperty("status", "error");
        response.addProperty("message", message);
        write(id, response);
    }

    /** Write-then-atomic-rename so rigctl's poll loop never observes a half-written response. */
    private static void write(String id, JsonObject response) {
        Path tmp = outbox.resolve(id + ".json.tmp");
        Path fin = outbox.resolve(id + ".json");
        try {
            Files.writeString(tmp, GSON.toJson(response), StandardCharsets.UTF_8);
            Files.move(tmp, fin, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Relict.LOGGER.error("[companion] failed to write response for {}", id, e);
        }
    }

}
