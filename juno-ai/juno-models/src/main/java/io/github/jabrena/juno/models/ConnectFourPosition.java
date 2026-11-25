package io.github.jabrena.juno.models;

import java.util.Arrays;

/**
 * An immutable, reachable Connect Four position on the standard 7 × 6 board; red moves first.
 *
 * <p>Each player's discs are a bitboard: column {@code c}, row {@code r} (0 at the bottom) is bit {@code c × 7 + r}, so
 * every column has one spare bit above its top row and a line test never wraps into the next column.
 */
public record ConnectFourPosition(long red, long yellow) {
    public static final int COLUMNS = 7;
    public static final int ROWS = 6;
    public static final int CELLS = COLUMNS * ROWS;
    static final int COLUMN_BITS = ROWS + 1;
    static final long BOTTOM_ROW = bottomRow();
    static final long BOARD = BOTTOM_ROW * ((1L << ROWS) - 1);
    /** Bit distances between neighbours along a line: vertical, diagonal down, horizontal, diagonal up. */
    private static final int[] DIRECTIONS = {1, COLUMN_BITS - 1, COLUMN_BITS, COLUMN_BITS + 1};

    public ConnectFourPosition {
        if ((red & ~BOARD) != 0 || (yellow & ~BOARD) != 0 || (red & yellow) != 0) {
            throw new IllegalArgumentException("disc bitboards must contain disjoint cells from the board");
        }
        int redCount = Long.bitCount(red);
        int yellowCount = Long.bitCount(yellow);
        if (redCount < yellowCount || redCount > yellowCount + 1) {
            throw new IllegalArgumentException("red moves first, so it has as many discs as yellow or one more");
        }
        long occupied = red | yellow;
        if ((occupied & ((occupied << 1) | BOTTOM_ROW)) != occupied) {
            throw new IllegalArgumentException("every disc must rest on the bottom row or on another disc");
        }
        if (hasWon(red) && hasWon(yellow)) {
            throw new IllegalArgumentException("both players cannot win in one position");
        }
        if ((hasWon(red) && redCount != yellowCount + 1) || (hasWon(yellow) && redCount != yellowCount)) {
            throw new IllegalArgumentException("the winning player must have made the last move");
        }
    }

    public static ConnectFourPosition empty() {
        return new ConnectFourPosition(0L, 0L);
    }

    public boolean redTurn() {
        return Long.bitCount(red) == Long.bitCount(yellow);
    }

    /** The discs of the player to move. */
    public long current() {
        return redTurn() ? red : yellow;
    }

    /** The discs of the player who moved last. */
    public long opponent() {
        return redTurn() ? yellow : red;
    }

    public long occupied() {
        return red | yellow;
    }

    public int moves() {
        return Long.bitCount(occupied());
    }

    public boolean isTerminal() {
        return hasWon(red) || hasWon(yellow) || occupied() == BOARD;
    }

    public boolean canPlay(int column) {
        return column >= 0 && column < COLUMNS && (occupied() & topCell(column)) == 0;
    }

    /** The single cell a disc dropped into {@code column} lands on; the column must not be full. */
    public long landing(int column) {
        return (occupied() + bottomCell(column)) & column(column);
    }

    public int[] legalActions() {
        int[] actions = new int[COLUMNS];
        int count = 0;
        if (!isTerminal()) {
            for (int column = 0; column < COLUMNS; column++) {
                if (canPlay(column)) {
                    actions[count++] = column;
                }
            }
        }
        return Arrays.copyOf(actions, count);
    }

    public ConnectFourPosition play(int column) {
        if (isTerminal() || !canPlay(column)) {
            throw new IllegalArgumentException("column %d is not a legal move in this position".formatted(column));
        }
        long cell = landing(column);
        return redTurn() ? new ConnectFourPosition(red | cell, yellow) : new ConnectFourPosition(red, yellow | cell);
    }

    /** Whether the player to move wins by dropping a disc into {@code column}. */
    public boolean wins(int column) {
        return canPlay(column) && hasWon(current() | landing(column));
    }

    static long column(int column) {
        return ((1L << ROWS) - 1) << (column * COLUMN_BITS);
    }

    static long bottomCell(int column) {
        return 1L << (column * COLUMN_BITS);
    }

    static long topCell(int column) {
        return 1L << (column * COLUMN_BITS + ROWS - 1);
    }

    /** Whether {@code discs} hold four in a line. */
    public static boolean hasWon(long discs) {
        for (int shift : DIRECTIONS) {
            long pairs = discs & (discs >>> shift);
            if ((pairs & (pairs >>> (2 * shift))) != 0) {
                return true;
            }
        }
        return false;
    }

    private static long bottomRow() {
        long row = 0L;
        for (int column = 0; column < COLUMNS; column++) {
            row |= 1L << (column * COLUMN_BITS);
        }
        return row;
    }
}
