package us.drullk.relict.companion;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * {@code run}: the raw-MC-command escape hatch. Every string is exactly what would follow a {@code /} in
 * chat -- nothing companion-specific happens to it.
 */
final class RunVerb {

    private RunVerb() {
    }

    static void plan(List<Step> steps, JsonObject request, JobContext ctx) {
        if (!request.has("commands")) {
            throw new CompanionException("run: missing \"commands\" array");
        }
        for (JsonElement e : request.getAsJsonArray("commands")) {
            steps.add(CommandExec.run(e.getAsString()));
        }
    }

}
