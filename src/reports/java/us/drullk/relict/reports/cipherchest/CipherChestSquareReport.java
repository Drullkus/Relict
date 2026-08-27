package us.drullk.relict.reports.cipherchest;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import us.drullk.relict.Relict;
import us.drullk.relict.block.cipherchest.CipherChestSquare;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid = Relict.MODID)
public final class CipherChestSquareReport implements DataProvider {

    private static final int PLACEMENT_TRIALS = 1000;

    @SubscribeEvent
    public static void onGatherData(GatherDataEvent.Server event) {
        if (Boolean.getBoolean("relict.reportsOnly")) {
            event.addProvider(new CipherChestSquareReport(event.getGenerator().getPackOutput(), event.getLookupProvider()));
        }
    }

    public CipherChestSquareReport(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
    }

    @Override
    public String getName() {
        return "Cipher chest square report";
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        StringBuilder report = new StringBuilder();
        reportLineSums(report);
        reportEveryCellAsHypotheticalBlank(report);
        reportWrapValue(report);
        reportBlankPlacementInvariant(report);
        System.out.print(report);
        return CompletableFuture.completedFuture(null);
    }

    private static void reportLineSums(StringBuilder report) {
        report.append("=== canon square: row/column/diagonal sums ===\n\n");
        for (int row = 0; row < CipherChestSquare.SIZE; row++) {
            require(CipherChestSquare.rowSum(row) == CipherChestSquare.LINE_SUM, "row " + row + " must sum to 65");
        }
        for (int col = 0; col < CipherChestSquare.SIZE; col++) {
            require(CipherChestSquare.colSum(col) == CipherChestSquare.LINE_SUM, "col " + col + " must sum to 65");
        }

        int diagA = 0;
        int diagB = 0;
        for (int i = 0; i < CipherChestSquare.SIZE; i++) {
            diagA += CipherChestSquare.valueAt(i, i);
            diagB += CipherChestSquare.valueAt(i, CipherChestSquare.SIZE - 1 - i);
        }
        require(diagA == CipherChestSquare.LINE_SUM, "main diagonal must sum to 65, was " + diagA);
        require(diagB == CipherChestSquare.LINE_SUM, "anti-diagonal must sum to 65, was " + diagB);

        Set<Integer> seen = new HashSet<>();
        for (int cell = 0; cell < CipherChestSquare.CELL_COUNT; cell++) {
            int value = CipherChestSquare.valueAt(cell);
            require(value >= CipherChestSquare.MIN_VALUE && value <= CipherChestSquare.MAX_VALUE, "cell " + cell + " value " + value + " out of 1..25");
            require(seen.add(value), "cell " + cell + " value " + value + " repeats -- the square must use every number 1..25 exactly once");
        }

        report.append("PASS: all 5 rows, 5 columns, and both diagonals sum to 65; all 25 values are 1..25 with no repeats\n\n");
    }

    /** Verification checklist #1: all 25 cells as hypothetical blanks, row AND column sums re-verified. */
    private static void reportEveryCellAsHypotheticalBlank(StringBuilder report) {
        report.append("=== all 25 cells as hypothetical blanks ===\n\n");
        for (int cell = 0; cell < CipherChestSquare.CELL_COUNT; cell++) {
            int row = CipherChestSquare.rowOf(cell);
            int col = CipherChestSquare.colOf(cell);
            int answer = CipherChestSquare.valueAt(cell);

            int rowSumOfOthers = CipherChestSquare.rowSum(row) - answer;
            int colSumOfOthers = CipherChestSquare.colSum(col) - answer;
            require(65 - rowSumOfOthers == answer, "cell " + cell + ": 65 minus the other 4 row cells must recover the blanked answer via the row line");
            require(65 - colSumOfOthers == answer, "cell " + cell + ": 65 minus the other 4 col cells must recover the blanked answer via the column line");
        }
        report.append("PASS: every one of the 25 cells is independently recoverable from its own row sum and its own column sum\n\n");
    }

    private static void reportWrapValue(StringBuilder report) {
        report.append("=== dial wrap (+1 / +5, wraps 25->1) ===\n\n");
        require(CipherChestSquare.wrapValue(25, 1) == 1, "25 + 1 must wrap to 1");
        require(CipherChestSquare.wrapValue(1, -1) == 25, "1 - 1 must wrap to 25");
        require(CipherChestSquare.wrapValue(23, 5) == 3, "23 + 5 must wrap to 3");
        require(CipherChestSquare.wrapValue(25, 5) == 5, "25 + 5 must wrap to 5");
        for (int value = CipherChestSquare.MIN_VALUE; value <= CipherChestSquare.MAX_VALUE; value++) {
            int wrapped = CipherChestSquare.wrapValue(value, 1);
            require(wrapped >= 1 && wrapped <= 25, "wrapValue must always stay in 1..25 (value=" + value + " -> " + wrapped + ")");
        }
        report.append("PASS: wraps 25->1 and 1->25, stays in [1,25] for every starting value\n\n");
    }

    /** Verification checklist #1: distinct-row/distinct-column invariant across >=100 random placements. */
    private static void reportBlankPlacementInvariant(StringBuilder report) {
        report.append("=== blank placement: distinct rows AND distinct columns, " + PLACEMENT_TRIALS + " trials ===\n\n");
        RandomSource random = RandomSource.create(20260824L);
        for (int count = 1; count <= 3; count++) {
            for (int trial = 0; trial < PLACEMENT_TRIALS; trial++) {
                int[] cells = CipherChestSquare.pickBlankCells(random, count);
                require(cells.length == count, "pickBlankCells(" + count + ") must return exactly " + count + " cells");

                Set<Integer> rows = new HashSet<>();
                Set<Integer> cols = new HashSet<>();
                for (int cell : cells) {
                    require(cell >= 0 && cell < CipherChestSquare.CELL_COUNT, "cell index " + cell + " out of 0..24");
                    require(rows.add(CipherChestSquare.rowOf(cell)), "count=" + count + " trial=" + trial + ": two blanks share a row (" + java.util.Arrays.toString(cells) + ")");
                    require(cols.add(CipherChestSquare.colOf(cell)), "count=" + count + " trial=" + trial + ": two blanks share a column (" + java.util.Arrays.toString(cells) + ")");
                }
            }
        }
        report.append("PASS: for blank counts 1..3, " + PLACEMENT_TRIALS + " trials each hold distinct rows AND distinct columns\n");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Cipher chest square report assertion failed: " + message);
        }
    }

}
