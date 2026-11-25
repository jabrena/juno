package io.github.jabrena.juno.models;

import java.util.Arrays;

/**
 * Encodes a Connect Four position as the JEV state vector: one block of {@value #PER_COLUMN} small integers per column,
 * describing what dropping a disc there would do for the player to move. A full column's block is all zero.
 *
 * <p>The layout, at {@code column × PER_COLUMN + k}, is the contract a board program reproduces:
 * <ol start="0">
 *   <li>{@code WIN}: 1 if the move wins.</li>
 *   <li>{@code BLOCK}: 1 if the opponent would win on that cell.</li>
 *   <li>{@code LOSES}: 1 if, after the move, the opponent can win at once: on the cell above it, or on a threat the
 *   move left unblocked.</li>
 *   <li>{@code SPOIL}: 1 if the move makes the player's own winning cell above it playable, so the opponent blocks
 *   it.</li>
 *   <li>{@code THREES}: the lines through the cell that the move leaves with three own discs and one empty cell.</li>
 *   <li>{@code TWOS}: the lines through the cell that the move leaves with two own discs and two empty cells.</li>
 *   <li>{@code DENIED}: the lines through the cell that held two or more opponent discs and none of the player's.</li>
 *   <li>{@code CENTRE}: 3 minus the column's distance from the centre column.</li>
 * </ol>
 */
public final class ConnectFourFeatures {
    public static final int PER_COLUMN = 8;
    public static final int COUNT = ConnectFourPosition.COLUMNS * PER_COLUMN;

    static final int WIN = 0;
    static final int BLOCK = 1;
    static final int LOSES = 2;
    static final int SPOIL = 3;
    static final int THREES = 4;
    static final int TWOS = 5;
    static final int DENIED = 6;
    static final int CENTRE = 7;

    /** Writes the features of {@code position} into the first {@link #COUNT} entries of {@code features}. */
    public void encode(ConnectFourPosition position, float[] features) {
        if (features.length < COUNT) {
            throw new IllegalArgumentException("features needs %d entries, not %d".formatted(COUNT, features.length));
        }
        Arrays.fill(features, 0, COUNT, 0f);
        if (position.isTerminal()) {
            return;
        }
        long own = position.current();
        long other = position.opponent();
        for (int column = 0; column < ConnectFourPosition.COLUMNS; column++) {
            if (position.canPlay(column)) {
                encodeColumn(position, column, own, other, features, column * PER_COLUMN);
            }
        }
    }

    /** A new feature vector for {@code position}. */
    public float[] of(ConnectFourPosition position) {
        float[] features = new float[COUNT];
        encode(position, features);
        return features;
    }

    private void encodeColumn(ConnectFourPosition position, int column, long own, long other, float[] features, int base) {
        long cell = position.landing(column);
        long empty = ConnectFourPosition.BOARD & ~position.occupied() & ~cell;
        long above = (cell << 1) & ConnectFourPosition.column(column);
        features[base + WIN] = flag(ConnectFourPosition.hasWon(own | cell));
        features[base + BLOCK] = flag(ConnectFourPosition.hasWon(other | cell));
        features[base + LOSES] = flag(!ConnectFourPosition.hasWon(own | cell) && opponentWinsAfter(position, column));
        features[base + SPOIL] = flag(above != 0 && ConnectFourPosition.hasWon(own | cell | above));
        int threes = 0;
        int twos = 0;
        int denied = 0;
        for (long line : ConnectFourLines.through(cell)) {
            int ownCount = Long.bitCount((own | cell) & line);
            int otherCount = Long.bitCount(other & line);
            int emptyCount = Long.bitCount(empty & line);
            if (ownCount == 3 && emptyCount == 1) {
                threes++;
            } else if (ownCount == 2 && emptyCount == 2) {
                twos++;
            }
            if (otherCount >= 2 && ownCount == 1) {
                denied++;
            }
        }
        features[base + THREES] = threes;
        features[base + TWOS] = twos;
        features[base + DENIED] = denied;
        features[base + CENTRE] = 3 - Math.abs(column - 3);
    }

    private static boolean opponentWinsAfter(ConnectFourPosition position, int column) {
        ConnectFourPosition next = position.play(column);
        for (int reply = 0; reply < ConnectFourPosition.COLUMNS; reply++) {
            if (next.wins(reply)) {
                return true;
            }
        }
        return false;
    }

    private static float flag(boolean value) {
        return value ? 1f : 0f;
    }
}
