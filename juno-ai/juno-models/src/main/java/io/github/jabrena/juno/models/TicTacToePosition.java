package io.github.jabrena.juno.models;

import java.util.Arrays;

/** An immutable, reachable tic-tac-toe position. Board cells are numbered row-major from 0 to 8. */
public record TicTacToePosition(int xMask, int oMask, boolean xTurn) {
    public static final int BOARD_SIZE = 9;
    public static final int FULL_BOARD = (1 << BOARD_SIZE) - 1;

    private static final int[][] WINNING_LINES = {
        {0, 1, 2},
        {3, 4, 5},
        {6, 7, 8},
        {0, 3, 6},
        {1, 4, 7},
        {2, 5, 8},
        {0, 4, 8},
        {2, 4, 6}
    };

    public TicTacToePosition {
        if ((xMask & ~FULL_BOARD) != 0 || (oMask & ~FULL_BOARD) != 0 || (xMask & oMask) != 0) {
            throw new IllegalArgumentException("board masks must contain disjoint cells from the board");
        }
        int xCount = Integer.bitCount(xMask);
        int oCount = Integer.bitCount(oMask);
        if (xCount < oCount || xCount > oCount + 1 || xTurn != (xCount == oCount)) {
            throw new IllegalArgumentException("piece counts and turn do not describe a reachable position");
        }
        if (hasWon(xMask) && hasWon(oMask)) {
            throw new IllegalArgumentException("both players cannot win in one position");
        }
        if ((hasWon(xMask) && xCount != oCount + 1) || (hasWon(oMask) && xCount != oCount)) {
            throw new IllegalArgumentException("the winning player must have made the last move");
        }
    }

    public static TicTacToePosition empty() {
        return new TicTacToePosition(0, 0, true);
    }

    public boolean isTerminal() {
        return hasWon(xMask) || hasWon(oMask) || (xMask | oMask) == FULL_BOARD;
    }

    public int[] legalActions() {
        int[] actions = new int[BOARD_SIZE];
        int count = 0;
        int occupied = xMask | oMask;
        for (int action = 0; action < BOARD_SIZE; action++) {
            if ((occupied & (1 << action)) == 0) {
                actions[count++] = action;
            }
        }
        return Arrays.copyOf(actions, count);
    }

    public int legalActionCount() {
        return BOARD_SIZE - Integer.bitCount(xMask | oMask);
    }

    public TicTacToePosition play(int action) {
        if (isTerminal() || action < 0 || action >= BOARD_SIZE || ((xMask | oMask) & (1 << action)) != 0) {
            throw new IllegalArgumentException("action is not legal in this position");
        }
        int bit = 1 << action;
        return xTurn
                ? new TicTacToePosition(xMask | bit, oMask, false)
                : new TicTacToePosition(xMask, oMask | bit, true);
    }

    static boolean hasWon(int mask) {
        for (int[] line : WINNING_LINES) {
            if ((mask & (1 << line[0])) != 0
                    && (mask & (1 << line[1])) != 0
                    && (mask & (1 << line[2])) != 0) {
                return true;
            }
        }
        return false;
    }
}
