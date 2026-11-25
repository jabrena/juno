package io.github.jabrena.juno.api.ai;

/**
 * A Connect Four board for the JEV model, with every buffer allocated once: the 7 × 6 cells, the 56 model features and
 * the 7 legal actions. Cell {@code column × 6 + row} holds {@link #EMPTY}, {@link #FIRST} or {@link #SECOND}, row 0 at
 * the bottom; {@link #FIRST} moves first.
 *
 * <p>{@link #encode()} writes the features exactly as {@code ConnectFourFeatures} in {@code juno-models} computes them
 * for training: one block of 8 per column, WIN, BLOCK, LOSES, SPOIL, THREES, TWOS, DENIED and CENTRE.
 */
public final class ConnectFourGame {
    public static final int COLUMNS = 7;
    public static final int ROWS = 6;
    public static final int EMPTY = 0;
    public static final int FIRST = 1;
    public static final int SECOND = 2;
    public static final int FEATURES = COLUMNS * 8;

    private static final int LINES = 69;

    private final int[] cells = new int[COLUMNS * ROWS];
    private final int[] heights = new int[COLUMNS];
    /** The cells of every line of four, four entries per line. */
    private final int[] lines = new int[LINES * 4];
    private final int[] features = new int[FEATURES];
    private final int[] legalActions = new int[COLUMNS];
    private int moves;
    private int winner;

    public ConnectFourGame() {
        int count = 0;
        for (int column = 0; column < COLUMNS; column++) {
            for (int row = 0; row < ROWS; row++) {
                count = addLine(count, column, row, 0, 1);
                count = addLine(count, column, row, 1, 0);
                count = addLine(count, column, row, 1, 1);
                count = addLine(count, column, row, 1, -1);
            }
        }
    }

    public void reset() {
        for (int cell = 0; cell < cells.length; cell++) cells[cell] = EMPTY;
        for (int column = 0; column < COLUMNS; column++) heights[column] = 0;
        moves = 0;
        winner = EMPTY;
    }

    /** The player to move: {@link #FIRST} or {@link #SECOND}. */
    public int turn() {
        return moves % 2 == 0 ? FIRST : SECOND;
    }

    /** {@link #FIRST} or {@link #SECOND} once four are in a line, otherwise {@link #EMPTY}. */
    public int winner() {
        return winner;
    }

    public boolean isOver() {
        return winner != EMPTY || moves == cells.length;
    }

    public boolean canPlay(int column) {
        return !isOver() && column >= 0 && column < COLUMNS && heights[column] < ROWS;
    }

    /** Drops the player to move's disc into {@code column}; false, and nothing changes, when the move is illegal. */
    public boolean play(int column) {
        if (!canPlay(column)) {
            return false;
        }
        int player = turn();
        int cell = column * ROWS + heights[column];
        if (completes(cell, -1, player)) winner = player;
        cells[cell] = player;
        heights[column]++;
        moves++;
        return true;
    }

    /** The cell's content, {@link #EMPTY}, {@link #FIRST} or {@link #SECOND}. */
    public int cell(int column, int row) {
        return cells[column * ROWS + row];
    }

    /**
     * The legal columns for the model, always {@link #COLUMNS} entries: the free columns, then the first of them
     * repeated, which the model scores twice without effect. Call it only while the game is not over.
     */
    public int[] legalActions() {
        int count = 0;
        for (int column = 0; column < COLUMNS; column++) if (heights[column] < ROWS) legalActions[count++] = column;
        for (int index = count; index < COLUMNS; index++) legalActions[index] = legalActions[0];
        return legalActions;
    }

    /** Writes the player to move's 56 features and returns the buffer, which the next call reuses. */
    public int[] encode() {
        for (int index = 0; index < FEATURES; index++) features[index] = 0;
        if (isOver()) {
            return features;
        }
        int own = turn();
        int other = own == FIRST ? SECOND : FIRST;
        for (int column = 0; column < COLUMNS; column++) {
            if (heights[column] < ROWS) encodeColumn(column, own, other, column * 8);
        }
        return features;
    }

    private void encodeColumn(int column, int own, int other, int base) {
        int cell = column * ROWS + heights[column];
        int above = heights[column] + 1 < ROWS ? cell + 1 : -1;
        boolean wins = completes(cell, -1, own);
        features[base] = wins ? 1 : 0;
        features[base + 1] = completes(cell, -1, other) ? 1 : 0;
        features[base + 2] = !wins && opponentWinsAfter(column, own, other) ? 1 : 0;
        features[base + 3] = above >= 0 && completes(cell, above, own) ? 1 : 0;
        int threes = 0;
        int twos = 0;
        int denied = 0;
        for (int line = 0; line < LINES * 4; line += 4) {
            if (!contains(line, cell)) continue;
            int ownCount = 1;
            int otherCount = 0;
            int emptyCount = 0;
            for (int index = line; index < line + 4; index++) {
                int content = lines[index] == cell ? -1 : cells[lines[index]];
                if (content == own) ownCount++;
                else if (content == other) otherCount++;
                else if (content == EMPTY) emptyCount++;
            }
            if (ownCount == 3 && emptyCount == 1) threes++;
            else if (ownCount == 2 && emptyCount == 2) twos++;
            if (otherCount >= 2 && ownCount == 1) denied++;
        }
        features[base + 4] = threes;
        features[base + 5] = twos;
        features[base + 6] = denied;
        features[base + 7] = column < 3 ? column : 6 - column;
    }

    /** Whether, after {@code own} plays {@code column}, {@code other} can win with its next disc. */
    private boolean opponentWinsAfter(int column, int own, int other) {
        int cell = column * ROWS + heights[column];
        cells[cell] = own;
        heights[column]++;
        boolean loses = false;
        for (int reply = 0; reply < COLUMNS && !loses; reply++) {
            if (heights[reply] < ROWS) loses = completes(reply * ROWS + heights[reply], -1, other);
        }
        heights[column]--;
        cells[cell] = EMPTY;
        return loses;
    }

    /** Whether {@code player} owns a whole line once it also holds {@code cell} and, if not -1, {@code extra}. */
    private boolean completes(int cell, int extra, int player) {
        for (int line = 0; line < LINES * 4; line += 4) {
            if (!contains(line, cell) && (extra < 0 || !contains(line, extra))) continue;
            boolean complete = true;
            for (int index = line; index < line + 4 && complete; index++) {
                int at = lines[index];
                complete = at == cell || at == extra || cells[at] == player;
            }
            if (complete) return true;
        }
        return false;
    }

    private boolean contains(int line, int cell) {
        return lines[line] == cell || lines[line + 1] == cell || lines[line + 2] == cell || lines[line + 3] == cell;
    }

    private int addLine(int count, int column, int row, int columnStep, int rowStep) {
        int lastColumn = column + 3 * columnStep;
        int lastRow = row + 3 * rowStep;
        if (lastColumn >= COLUMNS || lastRow < 0 || lastRow >= ROWS) {
            return count;
        }
        for (int step = 0; step < 4; step++) {
            lines[count * 4 + step] = (column + step * columnStep) * ROWS + row + step * rowStep;
        }
        return count + 1;
    }
}
