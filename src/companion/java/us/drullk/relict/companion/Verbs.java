package us.drullk.relict.companion;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** Routes a parsed request to its verb's planner and wraps the resulting step list as a {@link Job}. */
final class Verbs {

    private Verbs() {
    }

    static Job plan(JsonObject request, JobContext ctx) {
        if (!request.has("verb")) {
            throw new CompanionException("request has no \"verb\" field");
        }
        String verb = request.get("verb").getAsString();
        List<Step> steps = new ArrayList<>();
        switch (verb) {
            case "stage" -> StageVerb.plan(steps, request, ctx);
            case "frame" -> FrameVerb.plan(steps, request, ctx);
            case "lookfor" -> LookforVerb.plan(steps, request, ctx);
            case "item_checklist" -> ItemChecklistVerb.plan(steps, request, ctx);
            case "block_checklist" -> BlockChecklistVerb.plan(steps, request, ctx);
            case "shot" -> ShotVerb.plan(steps, request, ctx);
            case "run" -> RunVerb.plan(steps, request, ctx);
            default -> throw new CompanionException("unknown verb \"" + verb + "\"");
        }
        return new Job(steps);
    }

}
