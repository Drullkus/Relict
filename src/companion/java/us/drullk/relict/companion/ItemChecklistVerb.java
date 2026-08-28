package us.drullk.relict.companion;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * {@code item_checklist &lt;item-id&gt;}: 8 shots from ONE call -- main hand 1st+3rd person, off hand
 * 1st+3rd person, worn on head, inventory GUI slot, dropped xN (tick-frozen), item frame -- plus the map
 * pose for map-like items (a map item renders via a different path in hands/frames than an ordinary
 * item). Fully self-sufficient: stages its own studio hygiene and anchor, no separate {@code stage} call
 * required first -- one call in, gallery out.
 */
final class ItemChecklistVerb {

    private static final int DROP_COUNT = 3;

    private ItemChecklistVerb() {
    }

    static void plan(List<Step> steps, JsonObject request, JobContext ctx) {
        if (!request.has("item")) {
            throw new CompanionException("item_checklist: missing \"item\"");
        }
        String item = request.get("item").getAsString();
        boolean isMap = request.has("isMap") ? request.get("isMap").getAsBoolean() : item.contains("rubbing");
        String dimension = request.has("dimension") ? request.get("dimension").getAsString() : null;

        var player = ctx.mc().player;
        if (player == null) {
            throw new CompanionException("item_checklist: no player (no world loaded)");
        }
        BlockPos anchor = BlockPos.containing(player.position());
        String jobId = ctx.id;

        steps.add(CommandExec.run("difficulty peaceful"));
        steps.add(CommandExec.run("gamerule advance_time false"));
        steps.add(CommandExec.run("gamerule advance_weather false"));
        steps.add(CommandExec.run("gamemode creative"));

        // 1-2: main hand, first + third person.
        steps.add(CommandExec.run("item replace entity @s weapon.mainhand with " + item));
        addHeldShot(steps, jobId + "_01_mainhand_first", "first_person");
        addHeldShot(steps, jobId + "_02_mainhand_third", "third_person_front");

        // 3-4: off hand, first + third person.
        steps.add(CommandExec.run("item replace entity @s weapon.offhand with " + item));
        steps.add(CommandExec.run("item replace entity @s weapon.mainhand with air"));
        addHeldShot(steps, jobId + "_03_offhand_first", "first_person");
        addHeldShot(steps, jobId + "_04_offhand_third", "third_person_front");
        steps.add(CommandExec.run("item replace entity @s weapon.offhand with air"));

        // 5: worn on head.
        steps.add(CommandExec.run("item replace entity @s armor.head with " + item));
        addHeldShot(steps, jobId + "_05_head", "third_person_front");
        steps.add(CommandExec.run("item replace entity @s armor.head with air"));

        // Optional 5b: map pose -- a MapItem renders via a different path than an ordinary item, called
        // out separately from the ordinary mainhand shots above even though it exercises largely the
        // same code path, so the checklist output always names it explicitly for a map-like item.
        if (isMap) {
            steps.add(CommandExec.run("item replace entity @s weapon.mainhand with " + item));
            addHeldShot(steps, jobId + "_05b_map_pose", "third_person_front");
            steps.add(CommandExec.run("item replace entity @s weapon.mainhand with air"));
        }

        // 6: inventory GUI slot.
        steps.add(CommandExec.run("item replace entity @s hotbar.0 with " + item));
        for (Step s : GuiShots.inventory(jobId + "_06_inventory")) {
            steps.add(s);
        }

        // 7: dropped x N on the ground, tick-frozen so the pose is deterministic. Spaced tightly (0.4
        // blocks apart, not a full block) so the framed region stays close to the items' own tiny size
        // instead of a block-grid-sized area they'd only fill a corner of.
        BlockPos dropAnchor = anchor.offset(3, 1, 3);
        double dropSpacing = 0.4;
        for (int i = 0; i < DROP_COUNT; i++) {
            double dropX = dropAnchor.getX() + 0.5 + i * dropSpacing;
            steps.add(CommandExec.run(Cmd.in(dimension, "summon minecraft:item %s %d %s {Item:{id:\"%s\",count:1},NoGravity:1b}"
                    .formatted(Cmd.fmt(dropX), dropAnchor.getY(), Cmd.fmt(dropAnchor.getZ() + 0.5), item))));
        }
        steps.add(CommandExec.run("tick freeze"));
        double dropSpanEnd = dropAnchor.getX() + 0.5 + (DROP_COUNT - 1) * dropSpacing;
        AABB dropRegion = new AABB(dropAnchor.getX() + 0.5, dropAnchor.getY(), dropAnchor.getZ() + 0.3,
                dropSpanEnd, dropAnchor.getY() + 0.4, dropAnchor.getZ() + 0.7);
        addFramedShot(steps, jobId + "_07_dropped", dropRegion, "spectator", dimension);

        // 8: item frame. A light, neutral backing keeps the (dark-bordered) frame visually distinct from
        // its own mount instead of blending into it.
        BlockPos framePos = anchor.offset(-3, 0, 0);
        for (Step s : ItemFrames.place(dimension, frameSpec(framePos, item))) {
            steps.add(s);
        }
        AABB frameRegion = new AABB(framePos.relative(net.minecraft.core.Direction.SOUTH)).inflate(0.4);
        addFramedShot(steps, jobId + "_08_item_frame", frameRegion, "spectator", dimension);
    }

    private static JsonObject frameSpec(BlockPos backing, String item) {
        JsonObject spec = new JsonObject();
        var pos = new com.google.gson.JsonArray();
        pos.add(backing.getX());
        pos.add(backing.getY());
        pos.add(backing.getZ());
        spec.add("pos", pos);
        spec.addProperty("facing", "south");
        spec.addProperty("item", item);
        return spec;
    }

    private static void addHeldShot(List<Step> steps, String fileName, String camera) {
        steps.add(Steps.once(c -> ClientOps.setCameraType(camera)));
        steps.add(Steps.settle(10));
        steps.add(ClientOps.capture(fileName + ".png"));
    }

    private static void addFramedShot(List<Step> steps, String fileName, AABB region, String gamemode, String dimension) {
        Framing.Pose pose = Framing.frame(region, 200, 0);
        if (gamemode != null) {
            steps.add(CommandExec.run("gamemode " + gamemode));
        }
        steps.add(CommandExec.run(Cmd.in(dimension, Cmd.tp(pose))));
        steps.add(Steps.settle(25));
        steps.add(Steps.once(c -> ClientOps.setHudHidden(true)));
        steps.add(Steps.settle(10));
        steps.add(ClientOps.capture(fileName + ".png"));
        steps.add(Steps.once(c -> ClientOps.setHudHidden(false)));
        steps.add(CommandExec.run("gamemode creative"));
    }

}
