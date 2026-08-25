package us.drullk.relict.reports;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import us.drullk.relict.atmosphere.StormPhase;
import us.drullk.relict.block.DustLayerWeather;
import us.drullk.relict.init.worldgen.RelictBiomes;

import java.util.concurrent.CompletableFuture;

/**
 * Headless verification for the dust layer's storm-coupling gates — the same shape as
 * {@code SolarPanelDecaySampler} (its roof-gate equivalent for the solar panel set), placed in the reports
 * source set per {@code Relict/CLAUDE.md}'s rule that a permanent verification instrument belongs there,
 * never in datagen, unlike {@code SolarPanelDecaySampler}'s own older location. Every function under test
 * takes no world state beyond its arguments, so this asserts the storm-phase gate, the roof gate, and the
 * per-province baseline/cap table directly rather than standing up a server world to force a storm and
 * watch.
 */
public final class DustLayerWeatherSampler implements DataProvider {

    public DustLayerWeatherSampler(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
    }

    @Override
    public String getName() {
        return "Relict Dust Layer Weather Gate Sampler";
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        StringBuilder report = new StringBuilder("\n=== dust layer storm-coupling gate report ===\n");

        reportStormPhaseGate(report);
        reportRoofGate(report);
        reportProvinceTable(report);

        System.out.print(report);
        return CompletableFuture.completedFuture(null);
    }

    private static void reportStormPhaseGate(StringBuilder report) {
        report.append("\n=== A. storm phase gate: only the falling phases deposit ===\n\n");

        for (StormPhase phase : StormPhase.values()) {
            boolean falls = DustLayerWeather.isDustFallingPhase(phase);
            boolean expected = switch (phase) {
                case DUST_ENVELOPE, WIND_BUILD, ELECTRIC_PEAK, TAIL -> true;
                case CLEAR, DISTANT, ARRIVAL -> false;
            };
            require(falls == expected, "phase " + phase + ": expected isDustFallingPhase=" + expected + ", got " + falls);
        }

        report.append("A) PASS — CLEAR/DISTANT/ARRIVAL never deposit; DUST_ENVELOPE/WIND_BUILD/ELECTRIC_PEAK/TAIL do. "
                + "A forced storm outside these four phases therefore places nothing, by construction.\n");
    }

    private static void reportRoofGate(StringBuilder report) {
        report.append("\n=== B. roof gate: any block above the column blocks growth, at any distance ===\n\n");

        int layerY = 64;

        require(DustLayerWeather.isSkyExposed(layerY, layerY + 1), "open sky: surface height == layerY+1 must count as sky-exposed");

        for (int roofY : new int[]{layerY + 2, layerY + 5, layerY + 40, 300}) {
            require(!DustLayerWeather.isSkyExposed(layerY, roofY + 1), "roofed at y=" + roofY + ": must gate growth off");
        }

        require(DustLayerWeather.isSkyExposed(layerY, layerY + 1), "uncovered: re-evaluating at layerY+1 must count as sky-exposed again (pure function, no stuck state)");

        report.append("B) PASS — WORLD_SURFACE height <= layerY+1 is sky-exposed, anything higher (any block above,\n")
                .append("   any distance) roofs it off, and removing the roof re-arms it for free.\n");
    }

    private static void reportProvinceTable(StringBuilder report) {
        report.append("\n=== C. per-province baseline/cap table ===\n\n");

        assertProfile(RelictBiomes.WRINKLE_PLAINS, 0, 1, false, "wrinkle_plains: thin, patchy 0-1 layers, not crest-gated");
        assertProfile(RelictBiomes.RUSTED_DUNES, 0, 1, true, "rusted_dunes: 0-1 layers, confined to dune crests");
        assertProfile(RelictBiomes.FRETTED_MESAS, 1, 3, false, "fretted_mesas: 1-3 layers, not crest-gated");

        require(DustLayerWeather.profileFor(RelictBiomes.SHATTERED_HIGHLANDS) == null, "shattered_highlands is unplaced: must have no profile (no growth, no erosion)");
        require(DustLayerWeather.profileFor(RelictBiomes.BASALT_CAVES) == null, "basalt_caves: underground, must have no profile");

        report.append("C) PASS — wrinkle_plains 0..1, rusted_dunes 0..1 (crest-only), fretted_mesas 1..3; ")
                .append("shattered_highlands and the underground provinces carry no profile at all.\n");
    }

    private static void assertProfile(net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome> biome,
            int expectedBaseline, int expectedCap, boolean expectedCrestOnly, String label) {
        int[] profile = DustLayerWeather.profileFor(biome);
        require(profile != null, label + ": expected a profile, got none");
        require(profile[0] == expectedBaseline, label + ": expected baseline " + expectedBaseline + ", got " + profile[0]);
        require(profile[1] == expectedCap, label + ": expected cap " + expectedCap + ", got " + profile[1]);
        require((profile[2] == 1) == expectedCrestOnly, label + ": expected crestOnly " + expectedCrestOnly + ", got " + (profile[2] == 1));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("ASSERTION FAILED: " + message);
        }
    }

}
