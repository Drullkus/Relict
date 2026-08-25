package us.drullk.relict.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import us.drullk.relict.RelictTags;
import us.drullk.relict.atmosphere.AtmosphereCurve;
import us.drullk.relict.atmosphere.AtmosphereCurve.CycleGeometry;
import us.drullk.relict.atmosphere.RelictAtmosphereData;
import us.drullk.relict.atmosphere.RelictAtmosphereServer;
import us.drullk.relict.atmosphere.StormPhase;
import us.drullk.relict.init.RelictGameRules;
import us.drullk.relict.init.worldgen.RelictBiomes;
import us.drullk.relict.init.worldgen.RelictDimension;
import us.drullk.relict.worldgen.DuneCrest;

import java.util.Map;

/**
 * The dust layer's storm clock: while the storm is depositing, sky-exposed dust grows toward its province's
 * cap; between storms, it erodes back toward the province's calm baseline. Both directions retire old
 * {@code trodden} tracks — growth buries them under a fresh (untrodden) layer, and erosion below one layer
 * removes the block outright — so a footprint trail survives roughly until the next storm, by design.
 */
public final class DustLayerWeather {

    private DustLayerWeather() {
    }

    /** Chance per qualifying random tick that a sky-exposed column gains one layer during a depositing storm phase. */
    public static final float DEPOSIT_CHANCE = 0.05F;

    /** Chance per qualifying random tick that a column above its province baseline loses one layer in calm. */
    public static final float ERODE_CHANCE = 0.01F;

    /** Same heightmap and same "any block above counts as roofed" reading as {@link us.drullk.relict.block.wreck.SolarPanelDecay}'s own roof gate. */
    private static final Heightmap.Types SURFACE_HEIGHTMAP = Heightmap.Types.WORLD_SURFACE;

    /** How a province's dust behaves under the storm clock: rest depth in calm, ceiling under an active storm, and whether growth is further confined to dune crests. */
    private record Profile(int baseline, int cap, boolean crestOnly) {
    }

    /** Baseline/cap per placed province; unlisted biomes (including the unplaced surface province and every underground one) get no growth or erosion at all. */
    private static final Map<ResourceKey<Biome>, Profile> PROFILES = Map.of(
            RelictBiomes.WRINKLE_PLAINS, new Profile(1, 2, false),
            RelictBiomes.RUSTED_DUNES, new Profile(1, 2, true),
            RelictBiomes.FRETTED_MESAS, new Profile(1, 4, false)
    );

    /**
     * Storm phases where dust is actually falling — [VANILLACOPY, adapted]
     * {@code SolarPanelDecay.isDustFallingPhase}, same phase set, same reasoning (lead-in haze and the
     * silent impact instant do not count). Public, like {@code SolarPanelDecay.isStormDepositingDust}, so
     * the reports source set's {@code DustLayerWeatherSampler} can assert the gate directly.
     */
    public static boolean isDustFallingPhase(StormPhase phase) {
        return switch (phase) {
            case DUST_ENVELOPE, WIND_BUILD, ELECTRIC_PEAK, TAIL -> true;
            case CLEAR, DISTANT, ARRIVAL -> false;
        };
    }

    /** Pure: true when nothing in the column stands above the dust layer's own position — [VANILLACOPY, pattern] {@code SolarPanelDecay.isAtOrAboveSurface}. */
    public static boolean isSkyExposed(int layerY, int surfaceHeight) {
        return surfaceHeight <= layerY + 1;
    }

    /**
     * Baseline/cap/crest-only for a placed province, or {@code null} for anything else (unplaced provinces,
     * caves, the two non-listed surface provinces). Exposed read-only for {@code DustLayerWeatherSampler}.
     */
    public static int[] profileFor(ResourceKey<Biome> biome) {
        Profile profile = PROFILES.get(biome);
        return profile == null ? null : new int[]{profile.baseline(), profile.cap(), profile.crestOnly() ? 1 : 0};
    }

    public static void tick(DustLayerBlock block, BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.dimensionTypeRegistration().is(RelictTags.HAS_MARS_ATMOSPHERE)) {
            return;
        }

        Profile profile = profileAt(level, pos);
        if (profile == null) {
            return;
        }

        StormPhase phase = currentStormPhase(level);
        int layers = state.getValue(AbstractRelictLayerBlock.LAYERS);

        if (isDustFallingPhase(phase)) {
            growTick(block, state, level, pos, random, profile, layers);
        } else {
            erodeTick(state, level, pos, random, profile, layers);
        }
    }

    private static void growTick(DustLayerBlock block, BlockState state, ServerLevel level, BlockPos pos, RandomSource random, Profile profile, int layers) {
        if (layers >= profile.cap() || random.nextFloat() >= DEPOSIT_CHANCE) {
            return;
        }

        int surfaceHeight = level.getHeight(SURFACE_HEIGHTMAP, pos.getX(), pos.getZ());
        if (!isSkyExposed(pos.getY(), surfaceHeight)) {
            // Roofed: something else sits above this column, same rule that keeps a roofed solar panel clean.
            return;
        }

        if (profile.crestOnly() && !isDuneCrest(level, pos)) {
            return;
        }

        level.setBlockAndUpdate(pos, block.stack(state, layers + 1));
    }

    private static void erodeTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, Profile profile, int layers) {
        if (layers <= profile.baseline() || random.nextFloat() >= ERODE_CHANCE) {
            return;
        }

        if (layers <= 1) {
            level.removeBlock(pos, false);
        } else {
            level.setBlockAndUpdate(pos, state.setValue(AbstractRelictLayerBlock.LAYERS, layers - 1));
        }
    }

    private static boolean isDuneCrest(ServerLevel level, BlockPos pos) {
        return DuneCrest.isCrest((offsetX, offsetZ) -> level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, pos.getX() + offsetX, pos.getZ() + offsetZ));
    }

    private static Profile profileAt(ServerLevel level, BlockPos pos) {
        Holder<Biome> biome = level.getBiome(pos);
        return biome.unwrapKey().map(PROFILES::get).orElse(null);
    }

    /**
     * Reads the storm's current phase the same way {@link RelictAtmosphereServer} and
     * {@link us.drullk.relict.block.wreck.SolarPanelDecay} do: the schedule off the server-wide saved data,
     * the arc derived fresh from it. {@code SolarPanelDecay}'s own copy of this stays private to its own
     * {@code block/wreck} package, so this is the same six lines a second time rather than a widened
     * cross-package accessor — not a parallel state system, since there is still exactly one source of truth
     * ({@link RelictAtmosphereData}'s saved schedule).
     */
    private static StormPhase currentStormPhase(ServerLevel level) {
        MinecraftServer server = level.getServer();
        long totalTicks = RelictAtmosphereServer.marsTotalTicks(server);
        GameRules rules = server.getGlobalGameRules();
        int cycleTenthSols = rules.get(RelictGameRules.ATMOSPHERE_CYCLE_TENTH_SOLS.get());
        CycleGeometry geo = AtmosphereCurve.geometryAt(totalTicks, RelictDimension.SOL_TICKS, cycleTenthSols);
        RelictAtmosphereData data = server.getDataStorage().computeIfAbsent(RelictAtmosphereData.TYPE);
        return AtmosphereCurve.stormAt(totalTicks, geo, data.schedule()).phase();
    }

}
