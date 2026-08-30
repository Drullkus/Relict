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

    private RelictStructureTemplates() {
    }

    static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: RelictStructureTemplates <path to src/main/resources> <path to src/gametest/resources>");
            System.exit(2);
        }

        Path structureDir = Path.of(args[0]).resolve("data/relict/structure");

        portalRuinFrame().write(structureDir.resolve("mars_portal_ruin/frame.nbt"));

        System.out.println("Wrote a structure template under " + structureDir);

        // Test-only: written under src/gametest/resources, not src/main/resources, so it never ships in
        // the mod's own datapack/jar -- only runGameTestServer's own gametest source set sees it.
        Path gametestStructureDir = Path.of(args[1]).resolve("data/relict/structure");
        gametestBlankVolume().write(gametestStructureDir.resolve("gametest/basalt_sand_fall.nbt"));
        gametestPlatform().write(gametestStructureDir.resolve("gametest/platform.nbt"));
        gametestJigsawArena().write(gametestStructureDir.resolve("gametest/jigsaw_arena.nbt"));

        System.out.println("Wrote 3 gametest-only structure templates under " + gametestStructureDir);
    }

    /**
     * A bare 3x5x3 air volume with no content of its own: {@code minecraft:empty} (the sentinel vanilla
     * uses for zero-footprint tests like {@code always_pass}) resolves to an actual 0x0x0 template, which
     * is too small for a test that needs a real floor and a few blocks of fall clearance. This gives that
     * clearance; the gametest itself places the floor and the falling block.
     */
    private static Piece gametestBlankVolume() {
        return new Piece(3, 5, 3);
    }

    /**
     * A large empty volume, no content of its own -- exists only so GameTest reserves real space around it.
     * The framework spaces batch-mates apart using a test's declared structure size, and a jigsaw expansion
     * driven directly through {@code JigsawPlacement.generateJigsaw} places blocks in absolute world space
     * with no relation to a small template's footprint, so a tiny structure here would under-reserve and risk
     * collision with a neighboring test. 160x140x160 comfortably covers the largest authored template
     * (48x31x32) plus rotation and connector-offset slack.
     */
    private static Piece gametestJigsawArena() {
        return new Piece(160, 140, 160);
    }

    /**
     * A bare stone floor with headroom above, shared by every GameTest that just needs somewhere to place a
     * block and a player -- see {@code us.drullk.relict.gametest.RelictGameTests.PLATFORM}. Every position
     * in the volume is explicitly listed (floor stone, everything above explicit air), matching this tool's
     * own convention (see {@link #shell}): GameTest resets the structure between attempts by re-applying it,
     * so an unlisted position wouldn't reliably clear stale blocks from a previous attempt.
     */
    private static Piece gametestPlatform() {
        Piece piece = new Piece(5, 3, 5);
        piece.fill(0, 0, 0, 4, 0, 4, "minecraft:stone");
        piece.fill(0, 1, 0, 4, 2, 4, "minecraft:air");
        return piece;
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

    // --- Piece construction --------------------------------------------------------------------------

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

        void chest(int x, int y, int z, String facing, String lootTable) {
            CompoundTag blockEntity = new CompoundTag();
            blockEntity.putString("id", "minecraft:chest");
            blockEntity.putString("LootTable", lootTable);
            this.set(x, y, z, "minecraft:chest", Map.of("facing", facing, "type", "single", "waterlogged", "false"), blockEntity);
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
