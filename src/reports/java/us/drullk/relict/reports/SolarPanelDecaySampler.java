package us.drullk.relict.reports;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;
import us.drullk.relict.atmosphere.StormPhase;
import us.drullk.relict.block.wreck.SolarPanelDecay;
import us.drullk.relict.init.RelictBlocks;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Headless verification for the solar panel decay/brush gate — the wreck block set's equivalent of
 * {@code RidgeFieldSampler}. {@link SolarPanelDecay}'s gate math takes no world state beyond the values
 * passed in, so this asserts four scenarios directly, rather than standing up a live server world to
 * observe them. Failing an assertion fails the run. A verification instrument, not a datagen provider:
 * lives in the reports source set, never datagen.
 */
public final class SolarPanelDecaySampler implements DataProvider {

    public SolarPanelDecaySampler(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
    }

    @Override
    public String getName() {
        return "Solar panel decay/brush gate report";
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        StringBuilder report = new StringBuilder();

        reportStormGate(report);
        reportDecayChain(report);
        reportHeightmapGate(report);
        reportBrushTable(report);

        System.out.print(report);
        return CompletableFuture.completedFuture(null);
    }

    // ===================================================================================== (a) no storm gate

    private static void reportStormGate(StringBuilder report) {
        report.append("\n=== A. storm gate: no storm, ever, means no progression ===\n\n");

        for (StormPhase phase : StormPhase.values()) {
            boolean deposits = SolarPanelDecay.isStormDepositingDust(phase);
            boolean expected = switch (phase) {
                case DUST_ENVELOPE, WIND_BUILD, ELECTRIC_PEAK, TAIL -> true;
                case CLEAR, DISTANT, ARRIVAL -> false;
            };
            require(deposits == expected, "phase " + phase + ": expected isStormDepositingDust=" + expected + ", got " + deposits);
        }

        require(!SolarPanelDecay.isStormDepositingDust(StormPhase.CLEAR), "CLEAR must never deposit dust (this is the 'no storm' case forced over N random ticks)");
        report.append("A) PASS — CLEAR/DISTANT/ARRIVAL never deposit dust; DUST_ENVELOPE/WIND_BUILD/ELECTRIC_PEAK/TAIL do. "
                + "A caller forcing random ticks under CLEAR therefore sees zero progression by construction, for any N.\n");
    }

    // ================================================================================== (b) decay chain order

    private static void reportDecayChain(StringBuilder report) {
        report.append("\n=== B. decay chain: stages advance in order, stop at sanded ===\n\n");

        require(SolarPanelDecay.next(RelictBlocks.SOLAR_PANEL.get()).orElseThrow() == RelictBlocks.SOLAR_PANEL_SPRINKLED.get(),
                "clean -> sprinkled");
        require(SolarPanelDecay.next(RelictBlocks.SOLAR_PANEL_SPRINKLED.get()).orElseThrow() == RelictBlocks.SOLAR_PANEL_DUSTED.get(),
                "sprinkled -> dusted");
        require(SolarPanelDecay.next(RelictBlocks.SOLAR_PANEL_DUSTED.get()).orElseThrow() == RelictBlocks.SOLAR_PANEL_SANDED.get(),
                "dusted -> sanded");
        require(SolarPanelDecay.next(RelictBlocks.SOLAR_PANEL_SANDED.get()).isEmpty(),
                "sanded must have no next stage (decay stops here)");

        require(Math.abs(SolarPanelDecay.DECAY_CHANCE - 0.15F) < 1e-6F,
                "decay chance must be the producer-confirmed 0.15F (flagged tunable)");

        report.append("B) PASS — clean -> sprinkled -> dusted -> sanded, sanded is terminal, chance = ")
                .append(SolarPanelDecay.DECAY_CHANCE).append(".\n");
    }

    // =============================================================================== (c) heightmap (roof) gate

    private static void reportHeightmapGate(StringBuilder report) {
        report.append("\n=== C. heightmap gate: a block anywhere above blocks progression ===\n\n");

        int panelY = 64;

        // Open sky: nothing above the panel. WORLD_SURFACE's own height value is "first free Y above the
        // highest non-air block", so when the panel itself IS that highest block, the reported height is
        // exactly panelY + 1.
        require(SolarPanelDecay.isAtOrAboveSurface(panelY, panelY + 1),
                "open sky: surface height == panelY+1 must count as 'at the top'");

        // Roofed: literally any block anywhere above, even far above, in the same column.
        for (int roofY : new int[] {panelY + 2, panelY + 5, panelY + 40, 300}) {
            require(!SolarPanelDecay.isAtOrAboveSurface(panelY, roofY + 1),
                    "roofed at y=" + roofY + ": surface height " + (roofY + 1) + " above panelY+1=" + (panelY + 1) + " must gate the panel off");
        }

        // Uncovering: removing the roof must re-arm it — this gate is a pure function of the current
        // heightmap reading, so "restored when uncovered" falls out for free (no separate state to reset).
        require(SolarPanelDecay.isAtOrAboveSurface(panelY, panelY + 1),
                "uncovered: re-evaluating at surface height panelY+1 must count as 'at the top' again");

        report.append("C) PASS — WORLD_SURFACE height <= panelY+1 arms the gate, any higher height (any block\n")
                .append("   above, at any distance) disarms it, and un-covering re-arms it (pure function, no stuck state).\n")
                .append("   Heightmap type: WORLD_SURFACE — counts every non-air block (including non-solid decorative\n")
                .append("   ones like a torch or flower), matching the producer's plain \"any block above\" ruling more\n")
                .append("   closely than MOTION_BLOCKING would (which ignores non-collidable blocks).\n");
    }

    // ============================================================================================ (d) brushing

    private static void reportBrushTable(StringBuilder report) {
        report.append("\n=== D. brush loot table wiring (asserting the key mapping, not the datagenned table's roll) ===\n\n");

        Optional<ResourceKey<LootTable>> sprinkled = SolarPanelDecay.brushLootTable(RelictBlocks.SOLAR_PANEL_SPRINKLED.get());
        Optional<ResourceKey<LootTable>> dusted = SolarPanelDecay.brushLootTable(RelictBlocks.SOLAR_PANEL_DUSTED.get());
        Optional<ResourceKey<LootTable>> sanded = SolarPanelDecay.brushLootTable(RelictBlocks.SOLAR_PANEL_SANDED.get());
        Optional<ResourceKey<LootTable>> clean = SolarPanelDecay.brushLootTable(RelictBlocks.SOLAR_PANEL.get());

        require(sprinkled.orElseThrow() == SolarPanelDecay.BRUSH_SOLAR_PANEL_SPRINKLED, "sprinkled must map to its own brush loot table key");
        require(dusted.orElseThrow() == SolarPanelDecay.BRUSH_SOLAR_PANEL_DUSTED, "dusted must map to its own brush loot table key");
        require(sanded.orElseThrow() == SolarPanelDecay.BRUSH_SOLAR_PANEL_SANDED, "sanded must map to its own brush loot table key");
        require(clean.isEmpty(), "clean must have no brush-loot-table entry at all (brushing clean is a no-op, not a 0%-chance roll)");

        require(sprinkled.get() != dusted.get() && dusted.get() != sanded.get() && sprinkled.get() != sanded.get(),
                "the three dusty stages must each have their own distinct loot table, one per stage");

        report.append("D) PASS — sprinkled/dusted/sanded each map to their own distinct brush loot table key, clean has none.\n")
                .append("   The 1%/2%/5% chances live in the datagenned loot table JSON (random_chance conditions), not\n")
                .append("   in this pure-function mapping; verify those values from the generated tree directly.\n");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("ASSERTION FAILED: " + message);
        }
    }

}
