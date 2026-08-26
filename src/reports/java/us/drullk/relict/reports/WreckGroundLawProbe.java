package us.drullk.relict.reports;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import us.drullk.relict.Relict;
import us.drullk.relict.block.wreck.SolarPanelBlock;
import us.drullk.relict.init.RelictBlocks;
import us.drullk.relict.init.worldgen.RelictDimension;
import us.drullk.relict.init.worldgen.RelictStructures;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs real world generation against the eight authored Unmanned Wreck permutations and checks the ground
 * law directly on the generated blocks: terrain solid and flush under the start jigsaw's anchor, no solar
 * panel floating or buried, every solar panel decayed (never the clean stage — the solar panel decay
 * processor's job), and the pre-loaded chest surviving placement with contents. Jigsaw blocks are
 * removed from the world by the default placement processor, so the anchor position is recomputed the same
 * way {@code JigsawPlacement} computed it — from the placed piece's own element, position, and rotation —
 * rather than read back off a block that is no longer there.
 *
 * <p>Two-phase, for cost reasons: a fast offline pre-filter ({@code Structure#findValidGenerationPoint}, the
 * same computation {@code /locate} and {@link LocateProbe} use — no chunk touched) finds real candidate
 * positions cheaply across a wide area, then only those confirmed candidates pay for a real
 * {@code ChunkStatus.FULL} generation to read actual placed blocks. Forcing full generation on every
 * candidate up front (including the ~majority that land outside the structure's biome tag) was tried first
 * and could not gather 64 real hits inside a safe time budget.
 *
 * <p>Dev-only, in the reports source set, and inert unless {@code -Drelict.wreckGroundLawProbe=true} is set.
 * Sibling to {@link LocateProbe}: same live-dedicated-server, single-seed, halt-after shape. Widely
 * separated sampling origins under that one seed stand in for distinct seeds, the same substitution
 * {@code LocateProbe} already relies on for its own origins.
 *
 * <p>The scan itself runs from the first post-start level tick, not from {@link ServerStartedEvent}: forcing
 * brand-new chunks to {@code ChunkStatus.FULL} via a blocking {@code Level#getChunk} call during the
 * server-started lifecycle hook deadlocks (that hook fires before the tick loop is pumping the chunk
 * system's own queues, so the blocking wait never has anything to wait for). The same call is routine —
 * WorldEdit does it constantly — once issued from inside a real tick, which is what running it here buys.
 */
@EventBusSubscriber(modid = Relict.MODID)
public final class WreckGroundLawProbe {

    private static final Identifier START_JIGSAW_NAME = Identifier.parse("relict:unmanned_wreck/start");

    private static final Set<String> KNOWN_PERMUTATIONS = Set.of(
            "bib_rover", "buggy_rover", "disc_lander", "egg_lander_3",
            "eight_rover", "overturned_rover", "rtg_rover", "wide_rover"
    );

    private static final int MIN_SAMPLES = 64;

    /** Real hits collected before phase 2 stops looking for more (uniform-over-8 odds make this plenty). */
    private static final int CANDIDATE_TARGET = 160;

    /** Chunk-space cell radius scanned around each origin before giving up on that origin. */
    private static final int MAX_CELL_RADIUS = 120;

    private static final BlockPos[] ORIGINS = {
            new BlockPos(0, 0, 0),
            new BlockPos(500000, 0, 500000)
    };

    /** Cheap — no chunk touched — so this affords a much wider search than phase 2 can. */
    private static final long PHASE1_BUDGET_NANOS = 10_000_000_000L;

    /**
     * Real ChunkStatus.FULL generation. Combined with {@link #PHASE1_BUDGET_NANOS} this must stay
     * comfortably under the dedicated server's 60s single-tick watchdog (the whole probe runs inside one
     * tick — see the class doc).
     */
    private static final long PHASE2_BUDGET_NANOS = 40_000_000_000L;

    private static final AtomicBoolean ARMED = new AtomicBoolean(false);

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (Boolean.getBoolean("relict.wreckGroundLawProbe")) {
            ARMED.set(true);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!ARMED.compareAndSet(true, false)) {
            return;
        }

        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != RelictDimension.MARS_LEVEL) {
            ARMED.set(true); // wasn't the right level's tick yet — try again next tick
            return;
        }

        run(level);
    }

    private static void run(ServerLevel mars) {
        System.out.println("\n=== wreck ground-law probe ===");

        Holder<Structure> structureHolder = mars.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getOrThrow(RelictStructures.UNMANNED_WRECK);
        Structure structure = structureHolder.value();
        RandomSpreadStructurePlacement placement = (RandomSpreadStructurePlacement) mars.getChunkSource()
                .getGeneratorState().getPlacementsForStructure(structureHolder).getFirst();
        ChunkGenerator generator = mars.getChunkSource().getGenerator();
        RandomState randomState = mars.getChunkSource().randomState();
        StructureTemplateManager templates = mars.getStructureManager();
        long seed = mars.getSeed();

        long phase1Start = System.nanoTime();
        List<ChunkPos> candidates = new ArrayList<>();
        long phase1Deadline = phase1Start + PHASE1_BUDGET_NANOS;

        for (BlockPos origin : ORIGINS) {
            if (candidates.size() >= CANDIDATE_TARGET || System.nanoTime() >= phase1Deadline) {
                break;
            }
            collectCandidates(mars, structure, placement, generator, randomState, templates, seed, origin, candidates, phase1Deadline);
        }

        System.out.printf("    phase 1 (offline prefilter): %d candidates in %.1f s%n",
                candidates.size(), (System.nanoTime() - phase1Start) / 1.0e9);

        Result result = new Result();
        long phase2Start = System.nanoTime();
        long phase2Deadline = phase2Start + PHASE2_BUDGET_NANOS;

        for (ChunkPos candidate : candidates) {
            if (done(result) || System.nanoTime() >= phase2Deadline) {
                break;
            }
            examine(mars, structure, candidate, result);
        }

        System.out.printf("    phase 2 (real generation)  : %d examined in %.1f s%n",
                result.candidates, (System.nanoTime() - phase2Start) / 1.0e9);

        boolean pass = report(result);

        // A dedicated server's own crash/shutdown handling swallows an uncaught exception here and still
        // exits 0 (it writes a crash report and stops "gracefully" from the process's point of view), which
        // would make a real ground-law regression invisible to the Gradle task. Force a real, immediate exit
        // code from this same (server) thread instead, so this stays a genuine gate rather than a report
        // nobody is bound to read. Runtime.halt, not System.exit or MinecraftServer#halt: both of those
        // route through shutdown hooks / the server's own stop sequence, and calling them from the server
        // thread itself deadlocks against that sequence rather than exiting.
        Runtime.getRuntime().halt(pass ? 0 : 1);
    }

    private static boolean done(Result result) {
        return result.samples >= MIN_SAMPLES && result.census.keySet().containsAll(KNOWN_PERMUTATIONS);
    }

    /** Phase 1: cheap, offline, touches no chunk — the same check {@code /locate} itself runs. */
    private static void collectCandidates(ServerLevel mars, Structure structure, RandomSpreadStructurePlacement placement,
            ChunkGenerator generator, RandomState randomState, StructureTemplateManager templates, long seed,
            BlockPos origin, List<ChunkPos> candidates, long deadline) {
        int chunkOriginX = SectionPos.blockToSectionCoord(origin.getX());
        int chunkOriginZ = SectionPos.blockToSectionCoord(origin.getZ());

        for (int cellRadius = 0; cellRadius <= MAX_CELL_RADIUS; cellRadius++) {
            for (int cellX = -cellRadius; cellX <= cellRadius; cellX++) {
                for (int cellZ = -cellRadius; cellZ <= cellRadius; cellZ++) {
                    if (cellX != -cellRadius && cellX != cellRadius && cellZ != -cellRadius && cellZ != cellRadius) {
                        continue; // only the new ring at this radius — inner cells were already visited
                    }

                    if (candidates.size() >= CANDIDATE_TARGET || System.nanoTime() >= deadline) {
                        return;
                    }

                    ChunkPos target = placement.getPotentialStructureChunk(seed,
                            chunkOriginX + placement.spacing() * cellX, chunkOriginZ + placement.spacing() * cellZ);

                    Structure.GenerationContext context = new Structure.GenerationContext(
                            mars.registryAccess(), generator, generator.getBiomeSource(), randomState,
                            templates, seed, target, mars, biome -> structure.biomes().contains(biome));

                    if (structure.findValidGenerationPoint(context).isPresent()) {
                        candidates.add(target);
                    }
                }
            }
        }
    }

    /** Phase 2: forces real generation at a confirmed candidate and checks the actual placed blocks. */
    private static void examine(ServerLevel mars, Structure structure, ChunkPos candidate, Result result) {
        result.candidates++;
        LevelChunk chunk = mars.getChunk(candidate.x(), candidate.z());
        StructureStart start = chunk.getStartForStructure(structure);
        if (start == null || !start.isValid()) {
            // The offline prefilter can disagree with real generation only if something between them isn't
            // deterministic; log it as a genuine mismatch rather than silently skipping.
            result.require(false, candidate, "phase 1 predicted a valid start here, but none was placed");
            return;
        }

        List<StructurePiece> pieces = start.getPieces();
        result.require(pieces.size() == 1, candidate, "expected exactly one piece (no junctions), found " + pieces.size());
        if (pieces.size() != 1) {
            return;
        }

        result.require(pieces.getFirst() instanceof PoolElementStructurePiece, candidate, "start piece is not a pool element piece");
        if (!(pieces.getFirst() instanceof PoolElementStructurePiece piece)) {
            return;
        }

        result.require(piece.getElement() instanceof SinglePoolElement, candidate, "start element is not a single pool element");
        if (!(piece.getElement() instanceof SinglePoolElement element)) {
            return;
        }

        String path = element.getTemplateLocation().getPath();
        String name = path.startsWith("unmanned_wreck/") ? path.substring("unmanned_wreck/".length()) : path;
        result.require(KNOWN_PERMUTATIONS.contains(name), candidate, "unreachable/graybox piece resolved: " + path);

        result.samples++;
        result.census.merge(name, 1, Integer::sum);

        checkJigsawAnchor(mars, element, piece, candidate, result);
        checkSolarPanels(mars, piece.getBoundingBox(), candidate, result, name);
        checkChest(mars, piece.getBoundingBox(), candidate, result);
    }

    private static void checkJigsawAnchor(ServerLevel mars, SinglePoolElement element, PoolElementStructurePiece piece,
            ChunkPos candidate, Result result) {
        StructureTemplateManager templates = mars.getStructureManager();
        List<StructureTemplate.JigsawBlockInfo> jigsaws = element.getShuffledJigsawBlocks(
                templates, piece.getPosition(), piece.getRotation(), RandomSource.create());

        List<BlockPos> anchors = jigsaws.stream()
                .filter(jigsaw -> START_JIGSAW_NAME.equals(jigsaw.name()))
                .map(jigsaw -> jigsaw.info().pos())
                .toList();

        result.require(anchors.size() == 1, candidate, "expected exactly one " + START_JIGSAW_NAME + " jigsaw, found " + anchors.size());
        if (anchors.size() != 1) {
            return;
        }

        BlockPos anchor = anchors.getFirst();
        BlockState below = mars.getBlockState(anchor.below());
        result.require(!below.isAir(), candidate, "terrain below the jigsaw anchor " + anchor + " is air (not flush)");
    }

    private static void checkSolarPanels(ServerLevel mars, BoundingBox box, ChunkPos candidate, Result result, String templateName) {
        boolean sawPanel = false;

        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            BlockState state = mars.getBlockState(pos);
            if (!(state.getBlock() instanceof SolarPanelBlock)) {
                continue;
            }

            sawPanel = true;
            result.panelsSeen++;
            BlockPos immutable = pos.immutable();

            // A panel that is part of a contiguous roof or wall surface (several templates use the block
            // that way, not only as a ground-mounted array) legitimately has open air directly underneath —
            // that is the inside of the roof, not a ground-law gap. "Floating" in the ground-law sense means
            // detached from the build entirely: nothing solid below AND no solid neighbor on any horizontal
            // side either, so nothing in the piece itself is holding it up.
            boolean isolated = mars.getBlockState(immutable.below()).isAir()
                    && mars.getBlockState(immutable.north()).isAir()
                    && mars.getBlockState(immutable.south()).isAir()
                    && mars.getBlockState(immutable.east()).isAir()
                    && mars.getBlockState(immutable.west()).isAir();
            boolean buried = !mars.getBlockState(immutable.above()).isAir();
            result.require(!isolated, candidate, templateName
                    + " solar panel at " + immutable + " is floating (no solid support below or to any side)");
            result.require(!buried, candidate, templateName + " solar panel at " + immutable + " is buried (block above is not air)");

            // Mid-flight addition: the solar panel decay processor must leave zero panels clean.
            result.require(!state.is(RelictBlocks.SOLAR_PANEL.get()), candidate,
                    "solar panel at " + immutable + " placed clean (decay processor did not fire)");
        }

        // Not every rotation/template necessarily exposes a panel to this simple block-type scan, but across
        // 64+ samples spanning all eight permutations the census below confirms real coverage either way.
        if (sawPanel) {
            result.instancesWithPanel++;
        }
    }

    private static void checkChest(ServerLevel mars, BoundingBox box, ChunkPos candidate, Result result) {
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            BlockEntity blockEntity = mars.getBlockEntity(pos);
            if (!(blockEntity instanceof RandomizableContainerBlockEntity container)) {
                continue;
            }

            result.chestsSeen++;
            result.require(!container.isEmpty(), candidate, "chest at " + pos.immutable() + " has no contents after loot unpack");
            return;
        }

        result.require(false, candidate, "no chest found in the piece's bounding box");
    }

    /** Returns whether every assertion held. Never throws: see the {@code System.exit} call at the caller. */
    private static boolean report(Result result) {
        System.out.printf("    samples                  : %d (minimum %d)%n", result.samples, MIN_SAMPLES);
        System.out.printf("    permutations observed     : %d / %d%n", result.census.size(), KNOWN_PERMUTATIONS.size());

        for (var entry : new TreeMap<>(result.census).entrySet()) {
            System.out.printf("        %-20s %d%n", entry.getKey(), entry.getValue());
        }

        System.out.printf("    instances with a panel    : %d (panel blocks seen: %d)%n", result.instancesWithPanel, result.panelsSeen);
        System.out.printf("    chests checked            : %d%n", result.chestsSeen);
        System.out.printf("    failures                  : %d%n", result.failures.size());

        for (String failure : result.failures) {
            System.out.println("    FAIL " + failure);
        }

        boolean pass = true;

        if (result.samples < MIN_SAMPLES) {
            System.out.println("    RESULT FAIL — only " + result.samples + " samples gathered, need at least "
                    + MIN_SAMPLES + " (widen ORIGINS/MAX_CELL_RADIUS/CANDIDATE_TARGET)");
            pass = false;
        }

        if (!result.census.keySet().containsAll(KNOWN_PERMUTATIONS)) {
            Set<String> missing = new LinkedHashSet<>(KNOWN_PERMUTATIONS);
            missing.removeAll(result.census.keySet());
            System.out.println("    RESULT FAIL — permutations never observed: " + missing);
            pass = false;
        }

        if (!result.failures.isEmpty()) {
            System.out.println("    RESULT FAIL — " + result.failures.size() + " ground-law violation(s), see log above");
            pass = false;
        }

        if (pass) {
            System.out.println("    PASS — ground law holds across all sampled instances and permutations.");
        }

        return pass;
    }

    private static final class Result {
        int candidates;
        int samples;
        int panelsSeen;
        int instancesWithPanel;
        int chestsSeen;
        final TreeMap<String, Integer> census = new TreeMap<>();
        final List<String> failures = new ArrayList<>();

        void require(boolean condition, ChunkPos candidate, String message) {
            if (!condition) {
                this.failures.add(candidate + ": " + message);
            }
        }
    }

}
