package io.github.jabrena.juno.language.concurrency;

/**
 * Counts the solutions of the N-Queens puzzle with {@code int} bitmasks and recursion, so a search allocates nothing.
 * The work can be split by the column of the first row's queen, which is what lets {@link NQueensBenchmark} hand
 * every other column to a different worker.
 */
final class NQueens {

    private NQueens() {
    }

    /**
     * Counts the solutions on an {@code n} by {@code n} board whose first-row queen stands in column {@code first},
     * {@code first + step}, {@code first + 2 * step}, and so on; {@code count(n, 0, 1)} counts them all.
     */
    static int count(int n, int first, int step) {
        int all = (1 << n) - 1;
        int count = 0;
        for (int column = first; column < n; column += step) {
            int queen = 1 << column;
            count += place(all, queen, queen << 1, queen >>> 1);
        }
        return count;
    }

    /**
     * Counts the ways to fill the remaining rows, given the columns and the two diagonal directions the queens above
     * already attack.
     */
    private static int place(int all, int columns, int leftDiagonals, int rightDiagonals) {
        if (columns == all) {
            return 1;
        }
        int count = 0;
        int free = all & ~(columns | leftDiagonals | rightDiagonals);
        while (free != 0) {
            int queen = free & -free;
            free -= queen;
            count += place(all, columns | queen, (leftDiagonals | queen) << 1, (rightDiagonals | queen) >>> 1);
        }
        return count;
    }
}
