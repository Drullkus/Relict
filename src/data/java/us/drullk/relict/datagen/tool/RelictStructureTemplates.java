package us.drullk.relict.datagen.tool;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public final class RelictStructureTemplates {

    private static final int DATA_VERSION = 4903;

    private static final String BASALT = "minecraft:smooth_basalt";
    private static final String MARKER = "minecraft:sea_lantern";
    private static final String NEXT_POOL = "relict:ruin_a/next";
    private static final String GENERIC = "relict:ruin_a/generic";

    private RelictStructureTemplates() {
    }

    static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: RelictStructureTemplates <path to src/main/resources>");
            System.exit(2);
        }

        Path structureDir = Path.of(args[0]).resolve("data/relict/structure");

        portalRuinFrame().write(structureDir.resolve("mars_portal_ruin/frame.nbt"));

        ruinAStart().write(structureDir.resolve("ruin_a/start.nbt"));
        ruinAMessageRoom().write(structureDir.resolve("ruin_a/message_room.nbt"));
        ruinACorridorStraight().write(structureDir.resolve("ruin_a/corridor_straight.nbt"));
        ruinACorridorTurn().write(structureDir.resolve("ruin_a/corridor_turn.nbt"));
        ruinARoomA().write(structureDir.resolve("ruin_a/room_a.nbt"));
        ruinARoomB().write(structureDir.resolve("ruin_a/room_b.nbt"));
        ruinARoomC().write(structureDir.resolve("ruin_a/room_c.nbt"));
        ruinACapA().write(structureDir.resolve("ruin_a/cap_a.nbt"));
        ruinACapB().write(structureDir.resolve("ruin_a/cap_b.nbt"));

        System.out.println("Wrote 10 structure templates under " + structureDir);
    }

    private static Piece portalRuinFrame() {
        Piece piece = new Piece(7, 5, 7); // no outer shell — a scatter, not a room
        piece.fill(0, 0, 0, 6, 0, 6, BASALT);

        // Intact section (north half): a two-post frame with the portal-interior block lit inside it.
        piece.fill(2, 1, 2, 2, 4, 2, BASALT);
        piece.fill(4, 1, 2, 4, 4, 2, BASALT);
        piece.fill(2, 4, 2, 4, 4, 2, BASALT);
        for (int y = 1; y <= 3; y++) {
            piece.set(3, y, 2, "relict:mars_portal", Map.of("axis", "x"));
        }

        // Ruined section (south half): scattered rubble, no standing structure, plus the kit chest.
        piece.set(1, 1, 5, BASALT);
        piece.set(5, 1, 5, BASALT);
        piece.set(2, 1, 6, "minecraft:cobbled_deepslate");
        piece.set(1, 2, 5, BASALT);
        piece.chest(3, 1, 5, "north", "relict:chests/portal_ruin");

        return piece;
    }

    private static Piece ruinAStart() {
        Piece piece = shell(9, 6, 9, BASALT);
        piece.set(4, 4, 4, MARKER);
        piece.doorway(Facing.NORTH, 4, GENERIC, GENERIC, NEXT_POOL);
        piece.doorway(Facing.SOUTH, 4, GENERIC, GENERIC, NEXT_POOL);
        piece.doorway(Facing.EAST, 4, GENERIC, GENERIC, NEXT_POOL);
        // The one guaranteed connector: routes to a pool with a single entry, so it always resolves to
        // the message room. Swapping the authored room at M2 is a template-file edit on that one pool
        // member — this connector and the start piece around it don't change.
        piece.doorway(Facing.WEST, 4, GENERIC, "relict:ruin_a/message_room_link", "relict:ruin_a/message_room");
        return piece;
    }

    private static Piece ruinAMessageRoom() {
        // Deliberately empty (D2: fossilized = zero entity AI, empty is the design) — no marker, no loot,
        // just the one connector back to the start piece.
        Piece piece = shell(7, 5, 7, BASALT);
        piece.doorway(Facing.EAST, 3, "relict:ruin_a/message_room_link", GENERIC, "minecraft:empty");
        return piece;
    }

    private static Piece ruinACorridorStraight() {
        Piece piece = shell(7, 5, 7, BASALT);
        piece.set(3, 3, 3, MARKER);
        piece.doorway(Facing.NORTH, 3, GENERIC, GENERIC, NEXT_POOL);
        piece.doorway(Facing.SOUTH, 3, GENERIC, GENERIC, NEXT_POOL);
        return piece;
    }

    private static Piece ruinACorridorTurn() {
        Piece piece = shell(7, 5, 7, BASALT);
        piece.set(3, 3, 3, MARKER);
        piece.doorway(Facing.NORTH, 3, GENERIC, GENERIC, NEXT_POOL);
        piece.doorway(Facing.EAST, 3, GENERIC, GENERIC, NEXT_POOL);
        return piece;
    }

    private static Piece ruinARoomA() {
        Piece piece = shell(7, 5, 7, BASALT);
        piece.set(3, 3, 3, MARKER);
        piece.doorway(Facing.NORTH, 3, GENERIC, GENERIC, NEXT_POOL);
        piece.doorway(Facing.SOUTH, 3, GENERIC, GENERIC, NEXT_POOL);
        piece.doorway(Facing.EAST, 3, GENERIC, GENERIC, NEXT_POOL);
        return piece;
    }

    private static Piece ruinARoomB() {
        Piece piece = shell(7, 5, 7, BASALT);
        piece.set(3, 3, 3, MARKER);
        piece.doorway(Facing.NORTH, 3, GENERIC, GENERIC, NEXT_POOL);
        piece.doorway(Facing.WEST, 3, GENERIC, GENERIC, NEXT_POOL);
        return piece;
    }

    private static Piece ruinARoomC() {
        // Taller ceiling — the "monumental" scale beat (story beat 5) gets one register of relief.
        Piece piece = shell(7, 7, 7, BASALT);
        piece.set(3, 5, 3, MARKER);
        piece.doorway(Facing.NORTH, 3, GENERIC, GENERIC, NEXT_POOL);
        piece.doorway(Facing.SOUTH, 3, GENERIC, GENERIC, NEXT_POOL);
        return piece;
    }

    private static Piece ruinACapA() {
        Piece piece = shell(5, 4, 5, BASALT);
        piece.set(2, 2, 2, MARKER);
        piece.doorway(Facing.NORTH, 2, GENERIC, GENERIC, "minecraft:empty");
        return piece;
    }

    private static Piece ruinACapB() {
        Piece piece = shell(5, 4, 5, BASALT);
        piece.set(2, 2, 2, MARKER);
        piece.doorway(Facing.SOUTH, 2, GENERIC, GENERIC, "minecraft:empty");
        return piece;
    }

    // --- Piece construction --------------------------------------------------------------------------

    /** A solid shell (floor, ceiling, four walls) with the interior hollowed to air. */
    private static Piece shell(int sx, int sy, int sz, String wall) {
        Piece piece = new Piece(sx, sy, sz);
        piece.fill(0, 0, 0, sx - 1, sy - 1, sz - 1, wall);
        if (sx > 2 && sy > 2 && sz > 2) {
            piece.fill(1, 1, 1, sx - 2, sy - 2, sz - 2, "minecraft:air");
        }
        return piece;
    }

    /** The four horizontal directions a doorway or jigsaw connector can face, in the piece's own space. */
    private enum Facing {
        NORTH("north_up"),
        SOUTH("south_up"),
        EAST("east_up"),
        WEST("west_up");

        private final String orientation;

        Facing(String orientation) {
            this.orientation = orientation;
        }
    }

    private record Pos(int x, int y, int z) {
    }

    private record BlockEntry(String name, Map<String, String> properties, CompoundTag blockEntity) {
    }

    /** A graybox piece under construction: a size and a sparse map of non-air blocks. */
    private static final class Piece {

        private final int sx;
        private final int sy;
        private final int sz;
        private final Map<Pos, BlockEntry> blocks = new LinkedHashMap<>();

        private Piece(int sx, int sy, int sz) {
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
        }

        void set(int x, int y, int z, String name) {
            this.set(x, y, z, name, Map.of(), null);
        }

        void set(int x, int y, int z, String name, Map<String, String> properties) {
            this.set(x, y, z, name, properties, null);
        }

        private void set(int x, int y, int z, String name, Map<String, String> properties, CompoundTag blockEntity) {
            this.blocks.put(new Pos(x, y, z), new BlockEntry(name, properties, blockEntity));
        }

        void fill(int x1, int y1, int z1, int x2, int y2, int z2, String name) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                    for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                        this.set(x, y, z, name);
                    }
                }
            }
        }

        void fillAir(int x1, int y1, int z1, int x2, int y2, int z2) {
            this.fill(x1, y1, z1, x2, y2, z2, "minecraft:air");
        }

        void chest(int x, int y, int z, String facing, String lootTable) {
            CompoundTag blockEntity = new CompoundTag();
            blockEntity.putString("id", "minecraft:chest");
            blockEntity.putString("LootTable", lootTable);
            this.set(x, y, z, "minecraft:chest", Map.of("facing", facing, "type", "single", "waterlogged", "false"), blockEntity);
        }

        void jigsaw(int x, int y, int z, Facing facing, String name, String target, String pool) {
            CompoundTag blockEntity = new CompoundTag();
            blockEntity.putString("id", "minecraft:jigsaw");
            blockEntity.putString("name", name);
            blockEntity.putString("target", target);
            blockEntity.putString("pool", pool);
            blockEntity.putString("joint", "aligned");
            this.set(x, y, z, "minecraft:jigsaw", Map.of("orientation", facing.orientation), blockEntity);
        }

        /**
         * Carves a 3-wide by 3-tall opening centered on {@code center} (an x coordinate for the north/south
         * walls, a z coordinate for east/west) and places the connector jigsaw at its floor-level center.
         */
        void doorway(Facing facing, int center, String jigsawName, String jigsawTarget, String jigsawPool) {
            switch (facing) {
                case NORTH -> {
                    this.fillAir(center - 1, 1, 0, center + 1, 3, 0);
                    this.jigsaw(center, 1, 0, facing, jigsawName, jigsawTarget, jigsawPool);
                }
                case SOUTH -> {
                    this.fillAir(center - 1, 1, this.sz - 1, center + 1, 3, this.sz - 1);
                    this.jigsaw(center, 1, this.sz - 1, facing, jigsawName, jigsawTarget, jigsawPool);
                }
                case EAST -> {
                    this.fillAir(this.sx - 1, 1, center - 1, this.sx - 1, 3, center + 1);
                    this.jigsaw(this.sx - 1, 1, center, facing, jigsawName, jigsawTarget, jigsawPool);
                }
                case WEST -> {
                    this.fillAir(0, 1, center - 1, 0, 3, center + 1);
                    this.jigsaw(0, 1, center, facing, jigsawName, jigsawTarget, jigsawPool);
                }
            }
        }

        void write(Path path) {
            CompoundTag root = new CompoundTag();
            root.put("size", intList(this.sx, this.sy, this.sz));
            root.put("entities", new ListTag());

            Map<String, Integer> paletteIndex = new LinkedHashMap<>();
            ListTag paletteList = new ListTag();
            ListTag blockList = new ListTag();
            int jigsawCount = 0;

            for (Map.Entry<Pos, BlockEntry> entry : this.blocks.entrySet()) {
                Pos pos = entry.getKey();
                BlockEntry block = entry.getValue();
                String paletteKey = block.name() + new TreeMap<>(block.properties());
                Integer index = paletteIndex.get(paletteKey);
                if (index == null) {
                    CompoundTag paletteEntry = new CompoundTag();
                    paletteEntry.putString("Name", block.name());
                    if (!block.properties().isEmpty()) {
                        CompoundTag properties = new CompoundTag();
                        block.properties().forEach(properties::putString);
                        paletteEntry.put("Properties", properties);
                    }
                    index = paletteList.size();
                    paletteList.add(paletteEntry);
                    paletteIndex.put(paletteKey, index);
                }

                CompoundTag blockTag = new CompoundTag();
                blockTag.put("pos", intList(pos.x(), pos.y(), pos.z()));
                blockTag.putInt("state", index);
                if (block.blockEntity() != null) {
                    blockTag.put("nbt", block.blockEntity());
                    if ("minecraft:jigsaw".equals(block.blockEntity().getString("id").orElse(""))) {
                        jigsawCount++;
                    }
                }
                blockList.add(blockTag);
            }

            root.put("palette", paletteList);
            root.put("blocks", blockList);
            root.putInt("DataVersion", DATA_VERSION);

            try {
                Files.createDirectories(path.getParent());
                NbtIo.writeCompressed(root, path);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            this.validate(path, blockList.size(), paletteList.size(), jigsawCount);
        }

        /** Reads the file straight back and checks it round-trips the counts written — the tool's only test. */
        private void validate(Path path, int expectedBlocks, int expectedPalette, int expectedJigsaws) {
            try {
                CompoundTag reread = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
                int blockCount = reread.getListOrEmpty("blocks").size();
                int paletteCount = reread.getListOrEmpty("palette").size();
                if (blockCount != expectedBlocks || paletteCount != expectedPalette) {
                    throw new IllegalStateException("Round-trip mismatch for " + path + ": wrote " + expectedBlocks
                            + " blocks / " + expectedPalette + " palette entries, read back " + blockCount + " / " + paletteCount);
                }

                System.out.printf("  %-45s %dx%dx%d, %3d blocks, %2d palette, %d jigsaws%n",
                        path.getFileName(), this.sx, this.sy, this.sz, blockCount, paletteCount, expectedJigsaws);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private static ListTag intList(int... values) {
            ListTag list = new ListTag();
            for (int value : values) {
                list.add(IntTag.valueOf(value));
            }
            return list;
        }

    }

}
