package io.github.jabrena.juno.models;

import java.util.Arrays;

/** The 69 lines of four cells on a Connect Four board, as bitboard masks, and the lines through each cell. */
final class ConnectFourLines {
    /** Every horizontal, vertical and diagonal line of four cells. */
    static final long[] ALL = all();
    /** For each cell bit index, the masks of the lines that contain it. */
    private static final long[][] THROUGH = through();

    private ConnectFourLines() {
    }

    /** The lines through {@code cell}, a single-bit bitboard. */
    static long[] through(long cell) {
        return THROUGH[Long.numberOfTrailingZeros(cell)];
    }

    private static long[] all() {
        int[][] directions = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};
        long[] lines = new long[69];
        int count = 0;
        for (int column = 0; column < ConnectFourPosition.COLUMNS; column++) {
            for (int row = 0; row < ConnectFourPosition.ROWS; row++) {
                for (int[] direction : directions) {
                    int lastColumn = column + 3 * direction[0];
                    int lastRow = row + 3 * direction[1];
                    if (lastColumn < ConnectFourPosition.COLUMNS && lastRow >= 0 && lastRow < ConnectFourPosition.ROWS) {
                        long line = 0L;
                        for (int step = 0; step < 4; step++) {
                            line |= cell(column + step * direction[0], row + step * direction[1]);
                        }
                        lines[count++] = line;
                    }
                }
            }
        }
        return Arrays.copyOf(lines, count);
    }

    private static long[][] through() {
        long[][] through = new long[ConnectFourPosition.COLUMNS * ConnectFourPosition.COLUMN_BITS][];
        for (int bit = 0; bit < through.length; bit++) {
            long cell = 1L << bit;
            through[bit] = Arrays.stream(ALL).filter(line -> (line & cell) != 0).toArray();
        }
        return through;
    }

    private static long cell(int column, int row) {
        return 1L << (column * ConnectFourPosition.COLUMN_BITS + row);
    }
}
