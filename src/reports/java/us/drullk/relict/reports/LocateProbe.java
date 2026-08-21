package us.drullk.relict.reports;

import com.mojang.datafixers.util.Pair;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.jspecify.annotations.Nullable;
import us.drullk.relict.Relict;
import us.drullk.relict.RelictTags;
import us.drullk.relict.worldgen.LatticeHash;
import us.drullk.relict.init.worldgen.RelictDimension;
import us.drullk.relict.init.worldgen.RelictStructures;

/**
 * Times the real structure search on a live dedicated server and stops the server again, so the ring scan
 * can be measured against the watchdog without a client or a human at the console.
 *
 * <p>The server console's own command source carries no level on a dev dedicated server
 * ({@code MinecraftServer.createCommandSourceStack} hands back a null level, and every console command then
 * fails before dispatch), so this builds a source that names the Mars level and runs the command through it.
 *
 * <p>Dev-only, in the reports source set, and inert unless {@code -Drelict.locateProbe=true} is set.
 */
@EventBusSubscriber(modid = Relict.MODID)
public final class LocateProbe {

    /** Radius the vanilla {@code /locate structure} command hard-codes. */
    private static final int COMMAND_RADIUS_CHUNKS = 100;

    /** Radius {@code SeismicLocatorItem} asks for. */
    private static final int ITEM_RADIUS_CHUNKS = 10;

    /** Long enough for a stable per-candidate figure, short enough that the run is not a coffee break. */
    private static final long SCAN_BUDGET_NANOS = 30_000_000_000L;

    /** Candidates a radius-10 ring scan visits, which is the item's whole worst case. */
    private static final int ITEM_SCAN_CANDIDATES = 441;

    private static final int PROBE_COLUMNS = 4096;

    private static final int EQUALITY_COLUMNS = 512;

    private static final BlockPos[] ORIGINS = {
            new BlockPos(0, 128, 0),
            new BlockPos(500000, 128, 500000),
            new BlockPos(1200000, 128, -900000)
    };

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!Boolean.getBoolean("relict.locateProbe")) {
            return;
        }

        MinecraftServer server = event.getServer();
        ServerLevel mars = server.getLevel(RelictDimension.MARS_LEVEL);

        if (mars == null) {
            System.out.println("=== locate probe: relict:mars is not loaded ===");
            server.halt(false);
            return;
        }

        HolderSet<Structure> located = mars.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getOrThrow(RelictTags.SEISMIC_LOCATED);

        System.out.println("\n=== locate probe ===");

        for (BlockPos origin : ORIGINS) {
            search(mars, located, origin, ITEM_RADIUS_CHUNKS, "item cap");
            search(mars, located, origin, COMMAND_RADIUS_CHUNKS, "command radius");
            command(server, mars, origin);
        }

        // The item's miss branch. On Mars the located structures reach everywhere, so the only honest way to
        // see a miss is a dimension whose biomes none of them accept.
        search(server.overworld(), located, ORIGINS[0], ITEM_RADIUS_CHUNKS, "item miss");

        candidateScan(server, mars, ORIGINS[2]);
        server.halt(false);
    }

    /**
     * The cost a miss pays, in worldgen alone: the generation-point search a ring-scan candidate runs, over
     * candidate chunks with no early exit. A hit near the origin never reaches this, which is why the
     * timings above say nothing about the worst case. Deliberately not routed through
     * {@code StructureManager.checkStructurePresence}: that reads a region file per candidate and parks the
     * thread on storage, which measures disk latency rather than the terrain graph.
     */
    private static void candidateScan(MinecraftServer server, ServerLevel mars, BlockPos origin) {
        Holder<Structure> structureHolder = mars.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getOrThrow(RelictStructures.UNMANNED_WRECK);
        Structure structure = structureHolder.value();
        StructurePlacement placement = mars.getChunkSource().getGeneratorState().getPlacementsForStructure(structureHolder).getFirst();

        if (!(placement instanceof RandomSpreadStructurePlacement spread)) {
            return;
        }

        ChunkGenerator generator = mars.getChunkSource().getGenerator();
        RandomState randomState = mars.getChunkSource().randomState();
        long seed = mars.getSeed();
        int chunkOriginX = SectionPos.blockToSectionCoord(origin.getX());
        int chunkOriginZ = SectionPos.blockToSectionCoord(origin.getZ());
        int candidates = 0;
        int valid = 0;
        int reached = 0;
        long start = System.nanoTime();
        long deadline = start + SCAN_BUDGET_NANOS;

        for (int radius = 1; radius <= COMMAND_RADIUS_CHUNKS && System.nanoTime() < deadline; radius++) {
            reached = radius;

            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x != -radius && x != radius && z != -radius && z != radius) {
                        continue;
                    }

                    ChunkPos target = spread.getPotentialStructureChunk(seed,
                            chunkOriginX + spread.spacing() * x, chunkOriginZ + spread.spacing() * z);
                    candidates++;

                    Structure.GenerationContext context = new Structure.GenerationContext(
                            mars.registryAccess(), generator, generator.getBiomeSource(), randomState,
                            server.getStructureManager(), seed, target, mars,
                            biome -> structure.biomes().contains(biome));

                    if (structure.findValidGenerationPoint(context).isPresent()) {
                        valid++;
                    }
                }
            }
        }

        long elapsed = System.nanoTime() - start;
        long fullScan = 1L + 4L * COMMAND_RADIUS_CHUNKS * (COMMAND_RADIUS_CHUNKS + 1L);
        double perCandidateMs = elapsed / 1.0e6 / candidates;
        System.out.printf("    %-14s rings 1..%d, %d candidates, %d valid : %8.3f s (%.3f ms each)%n",
                "candidate scan", reached, candidates, valid, elapsed / 1.0e9, perCandidateMs);
        System.out.printf("    %-14s radius 100 is %d candidates : %8.1f s projected%n",
                "", fullScan, perCandidateMs * fullScan / 1000.0);
        System.out.printf("    %-14s radius %d is %d candidates : %8.3f s projected%n",
                "", ITEM_RADIUS_CHUNKS, ITEM_SCAN_CANDIDATES, perCandidateMs * ITEM_SCAN_CANDIDATES / 1000.0);

        heightProbe(generator, mars, randomState);
        equality(generator, mars, randomState);
    }

    private static void heightProbe(ChunkGenerator generator, ServerLevel mars, RandomState randomState) {
        long sink = 0L;
        long start = System.nanoTime();

        for (int index = 0; index < PROBE_COLUMNS; index++) {
            sink += generator.getBaseHeight((index & 63) << 4, (index >> 6) << 4,
                    Heightmap.Types.WORLD_SURFACE_WG, mars, randomState);
        }

        long elapsed = System.nanoTime() - start;
        System.out.printf("    %-14s %d columns : %8.3f us each, mean surface y %d%n",
                "height probe", PROBE_COLUMNS, elapsed / 1000.0 / PROBE_COLUMNS, sink / PROBE_COLUMNS);
    }

    /** The same bar section (H) of the terrain report holds, taken against the live level's own generator. */
    private static void equality(ChunkGenerator generator, ServerLevel mars, RandomState randomState) {
        NoiseBasedChunkGenerator wholeColumn = new NoiseBasedChunkGenerator(generator.getBiomeSource(),
                ((NoiseBasedChunkGenerator) generator).generatorSettings());
        int mismatches = 0;

        for (int index = 0; index < EQUALITY_COLUMNS; index++) {
            int columnX = (int) (LatticeHash.mix(index * 0x9E3779B97F4A7C15L) % 200000L);
            int columnZ = (int) (LatticeHash.mix(index * 0xC2B2AE3D27D4EB4FL + 17L) % 200000L);
            int windowed = generator.getBaseHeight(columnX, columnZ, Heightmap.Types.WORLD_SURFACE_WG, mars, randomState);
            int whole = wholeColumn.getBaseHeight(columnX, columnZ, Heightmap.Types.WORLD_SURFACE_WG, mars, randomState);

            if (windowed != whole && mismatches++ < 4) {
                System.out.printf("    MISMATCH at %d %d: window %d, whole column %d%n", columnX, columnZ, windowed, whole);
            }
        }

        System.out.printf("    %-14s %d columns, %d mismatched%n", "live equality", EQUALITY_COLUMNS, mismatches);
    }

    private static void search(ServerLevel mars, HolderSet<Structure> located, BlockPos origin, int radius, String label) {
        long start = System.nanoTime();
        @Nullable Pair<BlockPos, Holder<Structure>> found = mars.getChunkSource().getGenerator()
                .findNearestMapStructure(mars, located, origin, radius, false);
        long elapsed = System.nanoTime() - start;

        System.out.printf("    %-14s radius %3d from %8d %8d : %8.3f s  %s%n",
                label, radius, origin.getX(), origin.getZ(), elapsed / 1.0e9,
                found == null ? "no signal" : found.getFirst().toShortString());
    }

    private static void command(MinecraftServer server, ServerLevel mars, BlockPos origin) {
        CommandSourceStack source = new CommandSourceStack(server, Vec3.atCenterOf(origin), Vec2.ZERO, mars,
                LevelBasedPermissionSet.OWNER, "LocateProbe", Component.literal("LocateProbe"), server, null);

        long start = System.nanoTime();
        server.getCommands().performPrefixedCommand(source, "locate structure relict:unmanned_wreck");
        long elapsed = System.nanoTime() - start;

        System.out.printf("    %-14s radius %3d from %8d %8d : %8.3f s%n",
                "/locate", COMMAND_RADIUS_CHUNKS, origin.getX(), origin.getZ(), elapsed / 1.0e9);
    }

}
