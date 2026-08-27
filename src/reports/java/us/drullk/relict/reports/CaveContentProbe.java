package us.drullk.relict.reports;

import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.PotentSulfurBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.PotentSulfurState;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import us.drullk.relict.Relict;
import us.drullk.relict.init.RelictBlocks;
import us.drullk.relict.init.worldgen.RelictBiomes;
import us.drullk.relict.init.worldgen.RelictDimension;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds real, screenshot-ready coordinates for a generated sulfur deep lake and a dusted ice-cave floor
 * stretch, by force-generating a bounded grid of real chunks around each cave biome's known cell center and
 * scanning the resulting blocks — not a frequency/density measurement (that's {@link CaveContentGameTests}
 * in the gametest source set), just an existence search that prints teleport-ready coordinates.
 *
 * <p>Dev-only, in the reports source set. Rides the existing {@code -Drelict.locateProbe=true} flag and the
 * {@code locateProbe} run config rather than adding a new one — this probe is a one-off coordinate finder,
 * not a permanent measurement, so it doesn't earn its own run config.
 */
@EventBusSubscriber(modid = Relict.MODID)
public final class CaveContentProbe {

    private static final long BUDGET_NANOS = 90_000_000_000L;

    /** Cell centers from the underground voronoi field's own report (independent of world seed). */
    private static final BlockPos SULFUR_CELL_CENTER = new BlockPos(32, 40, 98);
    private static final BlockPos ICE_CELL_CENTER = new BlockPos(31, 40, -652);

    private static final int SEARCH_RADIUS_CHUNKS = 12;
    private static final int LAKE_Y_MIN = -32;
    private static final int LAKE_Y_MAX = 0;
    private static final int ICE_Y_MIN = 0;
    private static final int ICE_Y_MAX = 80;
    private static final int MAX_HITS = 4;

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!Boolean.getBoolean("relict.locateProbe")) {
            return;
        }

        MinecraftServer server = event.getServer();
        ServerLevel mars = server.getLevel(RelictDimension.MARS_LEVEL);

        if (mars == null) {
            System.out.println("=== cave content probe: relict:mars is not loaded ===");
            return;
        }

        System.out.println("\n=== cave content probe ===");
        search(mars, "sulfur_deep_lake", SULFUR_CELL_CENTER, LAKE_Y_MIN, LAKE_Y_MAX, RelictBiomes.SULFUR_CAVES,
                CaveContentProbe::isLakePotentSulfur);
        search(mars, "frost_floor dusting", ICE_CELL_CENTER, ICE_Y_MIN, ICE_Y_MAX, RelictBiomes.ICE_CAVES,
                CaveContentProbe::isDrySnowLayer);
    }

    private static boolean isLakePotentSulfur(BlockState state) {
        return state.is(net.minecraft.world.level.block.Blocks.POTENT_SULFUR)
                && state.getValue(PotentSulfurBlock.STATE) == PotentSulfurState.WET;
    }

    private static boolean isDrySnowLayer(BlockState state) {
        return state.is(RelictBlocks.DRY_SNOW_LAYER.get());
    }

    private static void search(ServerLevel level, String label, BlockPos center, int yMin, int yMax,
            net.minecraft.resources.ResourceKey<Biome> biome, java.util.function.Predicate<BlockState> match) {
        int centerChunkX = center.getX() >> 4;
        int centerChunkZ = center.getZ() >> 4;
        long deadline = System.nanoTime() + BUDGET_NANOS;
        int chunksScanned = 0;
        int biomeChunks = 0;
        List<BlockPos> hits = new ArrayList<>();

        outer:
        for (int radius = 0; radius <= SEARCH_RADIUS_CHUNKS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    if (System.nanoTime() > deadline) {
                        break outer;
                    }

                    int chunkX = centerChunkX + dx;
                    int chunkZ = centerChunkZ + dz;
                    ChunkAccess chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FEATURES, true);
                    chunksScanned++;

                    if (chunk == null || !chunk.getNoiseBiome(QuartPos.fromBlock(chunkX << 4), QuartPos.fromBlock(yMin), QuartPos.fromBlock(chunkZ << 4)).is(biome)) {
                        continue;
                    }
                    biomeChunks++;

                    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                    int baseX = chunkX << 4;
                    int baseZ = chunkZ << 4;

                    for (int x = 0; x < 16 && hits.size() < MAX_HITS; x++) {
                        for (int z = 0; z < 16 && hits.size() < MAX_HITS; z++) {
                            for (int y = yMin; y <= yMax; y++) {
                                pos.set(baseX + x, y, baseZ + z);
                                if (match.test(chunk.getBlockState(pos))) {
                                    hits.add(pos.immutable());
                                    break;
                                }
                            }
                        }
                    }

                    if (hits.size() >= MAX_HITS) {
                        break outer;
                    }
                }
            }
        }

        System.out.printf("--- %s --- chunks_scanned=%d biome_chunks=%d hits=%d%n", label, chunksScanned, biomeChunks, hits.size());
        for (BlockPos hit : hits) {
            System.out.printf("    /execute in relict:mars run tp @s %d %d %d%n", hit.getX(), hit.getY() + 2, hit.getZ());
        }
    }

}
