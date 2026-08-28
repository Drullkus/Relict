package us.drullk.relict.companion;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code block_checklist &lt;block-id&gt;}: placed views (close-up + in-situ), {@code frame}-verb framing
 * throughout, from ONE call. {@code blocks} accepts either a single id or an array, since a "family" of
 * related block ids (e.g. several decay-state variants that are separate blocks, not blockstate
 * properties of one block) needs to render from a single call too.
 */
final class BlockChecklistVerb {

    private BlockChecklistVerb() {
    }

    static void plan(List<Step> steps, JsonObject request, JobContext ctx) {
        List<String> blockIds = readBlockIds(request);
        if (blockIds.isEmpty()) {
            throw new CompanionException("block_checklist: needs \"block\" or \"blocks\"");
        }
        String dimension = request.has("dimension") ? request.get("dimension").getAsString() : null;

        var player = ctx.mc().player;
        if (player == null) {
            throw new CompanionException("block_checklist: no player (no world loaded)");
        }
        BlockPos anchor = BlockPos.containing(player.position()).offset(5, 0, 5);
        String jobId = ctx.id;

        steps.add(CommandExec.run("difficulty peaceful"));
        steps.add(CommandExec.run("gamerule advance_time false"));
        steps.add(CommandExec.run("gamerule advance_weather false"));
        steps.add(CommandExec.run("gamemode spectator"));

        int spacing = 2;
        List<BlockPos> placed = new ArrayList<>();
        int lastX = anchor.getX() + (blockIds.size() - 1) * spacing;
        steps.add(CommandExec.run(Cmd.in(dimension, "forceload add %d %d %d %d"
                .formatted(anchor.getX() - 2, anchor.getZ() - 2, lastX + 2, anchor.getZ() + 2))));
        steps.add(Steps.settle(dimension != null ? 60 : 25));

        for (int i = 0; i < blockIds.size(); i++) {
            BlockPos pos = anchor.offset(i * spacing, 0, 0);
            placed.add(pos);
            steps.add(CommandExec.run(Cmd.in(dimension, "setblock %d %d %d %s"
                    .formatted(pos.getX(), pos.getY(), pos.getZ(), blockIds.get(i)))));
        }

        // Best-effort relight (the same forcing pass `stage` uses) so a sky-lit capture location doesn't
        // come out artificially dark -- not reported here (only `stage`'s response contract calls for
        // that), just applied for capture fidelity.
        for (BlockPos pos : placed) {
            steps.add(RelightStep.forceAndReport(pos.above(), "__unreported"));
        }
        steps.add(Steps.once(c -> c.extra.remove("__unreported")));
        steps.add(CommandExec.run("tick freeze"));

        for (int i = 0; i < placed.size(); i++) {
            BlockPos pos = placed.get(i);
            String name = jobId + "_" + String.format("%02d", i + 1) + "_" + safeName(blockIds.get(i)) + "_closeup";
            // Deferred to execution time (Steps.lazy): needs the block's REAL shape, not a generic unit
            // cube -- a flat block like a solar panel is ~1/16 as tall as a unit cube, and framing off the
            // wrong height means a fixed-offset shot only ever grazes the edge of a flat subject.
            // Framing.blockAabb reads the placed block's own collision shape, which only exists once this
            // step actually runs.
            addFramedShot(steps, name, c -> Framing.blockAabb(c.level(), pos), dimension);
        }

        if (placed.size() > 1) {
            addFramedShot(steps, jobId + "_00_wide_insitu", c -> {
                AABB wide = Framing.blockAabb(c.level(), placed.get(0));
                for (int i = 1; i < placed.size(); i++) {
                    wide = wide.minmax(Framing.blockAabb(c.level(), placed.get(i)));
                }
                return wide;
            }, dimension);
        }
    }

    private static List<String> readBlockIds(JsonObject request) {
        List<String> ids = new ArrayList<>();
        if (request.has("blocks")) {
            JsonArray array = request.getAsJsonArray("blocks");
            for (JsonElement e : array) {
                ids.add(e.getAsString());
            }
        } else if (request.has("block")) {
            ids.add(request.get("block").getAsString());
        }
        return ids;
    }

    private static String safeName(String blockId) {
        return blockId.replace(':', '_').replace('[', '_').replace(']', '_');
    }

    private static void addFramedShot(List<Step> steps, String fileName, java.util.function.Function<JobContext, AABB> region, String dimension) {
        steps.add(Steps.lazy(c -> CommandExec.run(Cmd.in(dimension, Cmd.tp(Framing.frame(region.apply(c), 200, 0))))));
        steps.add(Steps.settle(25));
        steps.add(Steps.once(c -> ClientOps.setHudHidden(true)));
        steps.add(Steps.settle(10));
        steps.add(ClientOps.capture(fileName + ".png"));
        steps.add(Steps.once(c -> ClientOps.setHudHidden(false)));
    }

}
