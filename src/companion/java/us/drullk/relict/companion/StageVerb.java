package us.drullk.relict.companion;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code stage}: builds studio staging from a declarative spec -- blocks (with facing arrays), items,
 * item frames, head-slot equip, region clearing. Forceloads the target chunks in code, which avoids
 * needing to physically visit a dimension first before placing blocks there. Then relights every placed
 * position and reports the resulting raw sky light so a caller can trust a sun-gated capture (see
 * {@link RelightStep}). Studio hygiene (peaceful, no daylight/weather cycle, tick freeze) is enforced on
 * every call, freeze always last so nothing placed above depends on ticking happening.
 */
final class StageVerb {

    private StageVerb() {
    }

    static void plan(List<Step> steps, JsonObject request, JobContext ctx) {
        String dimension = request.has("dimension") ? request.get("dimension").getAsString() : null;

        steps.add(CommandExec.run("difficulty peaceful"));
        steps.add(CommandExec.run("gamerule advance_time false"));
        steps.add(CommandExec.run("gamerule advance_weather false"));

        List<BlockPos> allPositions = new ArrayList<>();
        if (request.has("blocks")) {
            for (var el : request.getAsJsonArray("blocks")) {
                JsonObject spec = el.getAsJsonObject();
                allPositions.add(Cmd.readPos(spec.getAsJsonArray("pos")));
            }
        }
        if (request.has("clear")) {
            JsonObject clear = request.getAsJsonObject("clear");
            allPositions.add(Cmd.readPos(clear.getAsJsonArray("from")));
            allPositions.add(Cmd.readPos(clear.getAsJsonArray("to")));
        }
        if (!allPositions.isEmpty()) {
            int minX = allPositions.stream().mapToInt(BlockPos::getX).min().orElseThrow();
            int maxX = allPositions.stream().mapToInt(BlockPos::getX).max().orElseThrow();
            int minZ = allPositions.stream().mapToInt(BlockPos::getZ).min().orElseThrow();
            int maxZ = allPositions.stream().mapToInt(BlockPos::getZ).max().orElseThrow();
            steps.add(CommandExec.run(Cmd.in(dimension,
                    "forceload add %d %d %d %d".formatted(minX - 2, minZ - 2, maxX + 2, maxZ + 2))));
            steps.add(Steps.settle(dimension != null ? 60 : 25));
        }

        if (request.has("clear")) {
            JsonObject clear = request.getAsJsonObject("clear");
            BlockPos from = Cmd.readPos(clear.getAsJsonArray("from"));
            BlockPos to = Cmd.readPos(clear.getAsJsonArray("to"));
            steps.add(CommandExec.run(Cmd.in(dimension, "fill %d %d %d %d %d %d air"
                    .formatted(from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ()))));
        }

        List<BlockPos> placed = new ArrayList<>();
        if (request.has("blocks")) {
            for (var el : request.getAsJsonArray("blocks")) {
                JsonObject spec = el.getAsJsonObject();
                BlockPos pos = Cmd.readPos(spec.getAsJsonArray("pos"));
                String block = spec.get("block").getAsString();
                if (spec.has("facings")) {
                    JsonArray facings = spec.getAsJsonArray("facings");
                    for (int i = 0; i < facings.size(); i++) {
                        BlockPos facingPos = pos.offset(i * 2, 0, 0);
                        String facedBlock = block + "[facing=" + facings.get(i).getAsString() + "]";
                        steps.add(CommandExec.run(Cmd.in(dimension, "setblock %d %d %d %s"
                                .formatted(facingPos.getX(), facingPos.getY(), facingPos.getZ(), facedBlock))));
                        placed.add(facingPos);
                    }
                } else {
                    steps.add(CommandExec.run(Cmd.in(dimension, "setblock %d %d %d %s"
                            .formatted(pos.getX(), pos.getY(), pos.getZ(), block))));
                    placed.add(pos);
                }
            }
        }

        if (request.has("give")) {
            for (var el : request.getAsJsonArray("give")) {
                JsonObject spec = el.getAsJsonObject();
                String item = spec.get("item").getAsString();
                int count = spec.has("count") ? spec.get("count").getAsInt() : 1;
                steps.add(CommandExec.run("give @s " + item + " " + count));
            }
        }

        if (request.has("equipHead")) {
            steps.add(CommandExec.run("item replace entity @s armor.head with " + request.get("equipHead").getAsString()));
        }

        if (request.has("itemFrames")) {
            for (var el : request.getAsJsonArray("itemFrames")) {
                JsonObject spec = el.getAsJsonObject();
                for (Step s : ItemFrames.place(dimension, spec)) {
                    steps.add(s);
                }
            }
        }

        boolean relight = !request.has("relight") || request.get("relight").getAsBoolean();
        if (relight && !placed.isEmpty()) {
            JsonArray relightReport = new JsonArray();
            for (BlockPos pos : placed) {
                BlockPos surface = pos.above();
                steps.add(RelightStep.forceAndReport(surface, "__lastSkyLight"));
                steps.add(Steps.once(c -> {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("x", surface.getX());
                    entry.addProperty("y", surface.getY());
                    entry.addProperty("z", surface.getZ());
                    entry.addProperty("skyLight", c.extra.remove("__lastSkyLight").getAsInt());
                    relightReport.add(entry);
                }));
            }
            steps.add(Steps.once(c -> c.extra.add("skyLight", relightReport)));
        }

        // Determinism + near-zero server cost -- LAST staging command, per the studio hygiene law.
        steps.add(CommandExec.run("tick freeze"));
    }

}
