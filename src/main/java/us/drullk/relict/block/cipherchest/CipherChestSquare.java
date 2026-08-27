package us.drullk.relict.block.cipherchest;

import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * The historical Agrippa Square of Mars (De Occulta Philosophia, 1531). Fixed for every chest; only the
 * blanked cells vary per placement. Row and column indices run 0..4; a cell index is row * 5 + col.
 */
public final class CipherChestSquare {

    public static final int SIZE = 5;
    public static final int CELL_COUNT = SIZE * SIZE;
    public static final int LINE_SUM = 65;
    public static final int MIN_VALUE = 1;
    public static final int MAX_VALUE = 25;

    private static final int[][] CANON = {
            {11, 24, 7, 20, 3},
            {4, 12, 25, 8, 16},
            {17, 5, 13, 21, 9},
            {10, 18, 1, 14, 22},
            {23, 6, 19, 2, 15}
    };

    private CipherChestSquare() {
    }

    public static int valueAt(int row, int col) {
        return CANON[row][col];
    }

    public static int valueAt(int cellIndex) {
        return valueAt(rowOf(cellIndex), colOf(cellIndex));
    }

    public static int cellIndex(int row, int col) {
        return row * SIZE + col;
    }

    public static int rowOf(int cellIndex) {
        return cellIndex / SIZE;
    }

    public static int colOf(int cellIndex) {
        return cellIndex % SIZE;
    }

    public static int rowSum(int row) {
        int sum = 0;
        for (int col = 0; col < SIZE; col++) {
            sum += CANON[row][col];
        }
        return sum;
    }

    public static int colSum(int col) {
        int sum = 0;
        for (int row = 0; row < SIZE; row++) {
            sum += CANON[row][col];
        }
        return sum;
    }

    /**
     * Wraps a dial value by {@code amount} (positive or negative), staying inside 1..25.
     */
    public static int wrapValue(int value, int amount) {
        int zeroBased = value - MIN_VALUE;
        int wrapped = Math.floorMod(zeroBased + amount, MAX_VALUE);
        return wrapped + MIN_VALUE;
    }

    /**
     * Picks {@code count} blank cells at random, guaranteeing distinct rows AND distinct columns so each
     * blank is uniquely determined by its own row or column sum. Deterministic for a given {@code random}
     * so a chest's blanks can be reproduced from a stored seed.
     */
    public static int[] pickBlankCells(RandomSource random, int count) {
        if (count < 1 || count > SIZE) {
            throw new IllegalArgumentException("Blank count must be 1.." + SIZE + ", got " + count);
        }

        List<Integer> rows = shuffledRange(random);
        List<Integer> cols = shuffledRange(random);

        int[] cells = new int[count];
        for (int i = 0; i < count; i++) {
            cells[i] = cellIndex(rows.get(i), cols.get(i));
        }
        return cells;
    }

    private static List<Integer> shuffledRange(RandomSource random) {
        List<Integer> values = new ArrayList<>(SIZE);
        for (int i = 0; i < SIZE; i++) {
            values.add(i);
        }
        for (int i = values.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Integer temp = values.get(i);
            values.set(i, values.get(j));
            values.set(j, temp);
        }
        return values;
    }
}
