package io.github.jabrena.juno.models;

/**
 * A depth-limited negamax search with alpha-beta pruning that labels Connect Four moves for training.
 *
 * <p>A full solution of Connect Four needs a far deeper search than a training run can afford for every position, so
 * the oracle looks {@code depth} moves ahead and scores the positions it stops at with an open-line heuristic. A win
 * found within the horizon always beats the heuristic, and a sooner win beats a later one. Columns are tried centre
 * first and ties keep the earlier column, so the oracle is deterministic.
 */
public final class ConnectFourOracle {
    /** Columns from the centre outwards: the usual best-first move order. */
    static final int[] MOVE_ORDER = {3, 2, 4, 1, 5, 0, 6};
    private static final int WIN = 1_000_000;
    private static final int[] LINE_VALUE = {0, 0, 2, 5};

    private final int depth;

    public ConnectFourOracle(int depth) {
        if (depth <= 0) {
            throw new IllegalArgumentException("search depth must be positive");
        }
        this.depth = depth;
    }

    /** The column the search prefers for the player to move; the position must not be finished. */
    public int bestAction(ConnectFourPosition position) {
        if (position.isTerminal()) {
            throw new IllegalArgumentException("a finished position has no move to choose");
        }
        int best = -1;
        int alpha = -WIN - depth - 1;
        for (int column : MOVE_ORDER) {
            if (!position.canPlay(column)) {
                continue;
            }
            int score = position.wins(column) ? WIN + depth : -search(position.play(column), depth - 1, -WIN - depth - 1, -alpha);
            if (best < 0 || score > alpha) {
                best = column;
                alpha = score;
            }
        }
        return best;
    }

    /** The heuristic value of {@code position} for the player to move. */
    public int heuristic(ConnectFourPosition position) {
        long own = position.current();
        long other = position.opponent();
        int value = 0;
        for (long line : ConnectFourLines.ALL) {
            int ownCount = Long.bitCount(own & line);
            int otherCount = Long.bitCount(other & line);
            if (otherCount == 0) {
                value += LINE_VALUE[ownCount];
            } else if (ownCount == 0) {
                value -= LINE_VALUE[otherCount];
            }
        }
        long centre = ConnectFourPosition.column(3);
        return value + Long.bitCount(own & centre) - Long.bitCount(other & centre);
    }

    private int search(ConnectFourPosition position, int remaining, int alpha, int beta) {
        if (position.occupied() == ConnectFourPosition.BOARD) {
            return 0;
        }
        for (int column : MOVE_ORDER) {
            if (position.wins(column)) {
                return WIN + remaining;
            }
        }
        if (remaining == 0) {
            return heuristic(position);
        }
        int best = -WIN - remaining - 1;
        for (int column : MOVE_ORDER) {
            if (!position.canPlay(column)) {
                continue;
            }
            int score = -search(position.play(column), remaining - 1, -beta, -Math.max(alpha, best));
            if (score > best) {
                best = score;
                if (best >= beta) {
                    return best;
                }
            }
        }
        return best;
    }
}
