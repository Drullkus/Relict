package us.drullk.relict.companion;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Places a real, reliably-attached item frame: a solid backing block plus a {@code summon} with an
 * explicit {@code Facing} tag, rather than a hand-picked bare coordinate -- a hanging entity's summon
 * position must have a solid block on the side opposite its Facing direction, and a bare superflat-floor
 * coordinate usually doesn't have one, which fails with "Block-attached entity at invalid position".
 * {@code Facing}'s byte value is {@link Direction#get3DDataValue()} (DOWN=0, UP=1, NORTH=2, SOUTH=3,
 * WEST=4, EAST=5), vanilla's standard hanging-entity convention.
 */
final class ItemFrames {

    private ItemFrames() {
    }

    /** @param spec {@code {"pos":[x,y,z], "facing":"north", "item":"relict:rubbing", "backing":"minecraft:gray_concrete"}} --
     *              {@code pos} is the BACKING block; the frame itself is summoned on {@code facing}'s side of it. */
    static List<Step> place(String dimension, JsonObject spec) {
        BlockPos backing = Cmd.readPos(spec.getAsJsonArray("pos"));
        Direction direction = parseDirection(spec.has("facing") ? spec.get("facing").getAsString() : "south");
        // Light and neutral, not the frame's own dark wood-brown tone -- a dark backing lets the frame's
        // border blend into its own mount and become hard to make out.
        String backingBlock = spec.has("backing") ? spec.get("backing").getAsString() : "minecraft:white_concrete";
        String item = spec.get("item").getAsString();
        BlockPos framePos = backing.relative(direction);

        List<Step> steps = new ArrayList<>();
        steps.add(CommandExec.run(Cmd.in(dimension, "setblock %d %d %d %s"
                .formatted(backing.getX(), backing.getY(), backing.getZ(), backingBlock))));
        steps.add(CommandExec.run(Cmd.in(dimension, "summon minecraft:item_frame %d %d %d {Facing:%db}"
                .formatted(framePos.getX(), framePos.getY(), framePos.getZ(), direction.get3DDataValue()))));
        steps.add(Steps.settle(10));
        steps.add(CommandExec.run(Cmd.in(dimension,
                "item replace entity @e[type=minecraft:item_frame,x=%d,y=%d,z=%d,distance=..1,limit=1,sort=nearest] contents with %s"
                        .formatted(framePos.getX(), framePos.getY(), framePos.getZ(), item))));
        return steps;
    }

    private static Direction parseDirection(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "north" -> Direction.NORTH;
            case "south" -> Direction.SOUTH;
            case "east" -> Direction.EAST;
            case "west" -> Direction.WEST;
            default -> throw new CompanionException("item frame: facing must be north/south/east/west, got \"" + name + "\"");
        };
    }

}
