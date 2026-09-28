package io.github.jabrena.juno.games;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * Connect Four against the computer on the ELEGOO 2.8" TFT touch screen shield. You drop red
 * discs, the computer yellow ones; tap a column to drop a disc there. The first to line up four
 * discs horizontally, vertically or diagonally wins; a full board is a draw. {@code NEW} starts the
 * next game, and the loser of the previous game moves first.
 *
 * <p>The computer runs a {@value #SEARCH_DEPTH}-ply negamax search with alpha-beta pruning,
 * trying central columns first, and scores positions by counting open windows of four.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class ConnectFour {
    private static final int SEARCH_DEPTH = 4;
    private static final int COLUMNS = 7;
    private static final int ROWS = 6;
    private static final int WIN_SCORE = 100000;

    private static final int HUMAN = 1;
    private static final int COMPUTER = -1;

    // Layout (portrait, 240x320).
    private static final int HEADER_HEIGHT = 40;
    private static final int CELL = 32;
    private static final int BOARD_X = 8;
    private static final int BOARD_Y = 56;
    private static final int DISC_RADIUS = 13;
    private static final int FOOTER_Y = BOARD_Y + ROWS * CELL + 14;
    private static final int NEW_X = 172;
    private static final int BUTTON_WIDTH = 60;
    private static final int BUTTON_HEIGHT = 30;

    private static final int BOARD_BLUE = 0x19F9;
    private static final int HUMAN_COLOR = TftTouchShield.RED;
    private static final int COMPUTER_COLOR = TftTouchShield.YELLOW;
    private static final int HEADER_BACKGROUND = 0x2945;

    private static int discs;
    private static boolean gameOver;
    private static int humanWins;
    private static int computerWins;
    private static int firstPlayer;
    private static int bestColumn;

    private ConnectFour() {
    }

    public static void main(String[] args) {
        byte[] board = new byte[COLUMNS * ROWS];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        firstPlayer = HUMAN;
        newGame(board);

        while (true) {
            if (!TftTouchShield.readTouch()) {
                Delay.millis(10);
                continue;
            }
            int x = TftTouchShield.touchX();
            int y = TftTouchShield.touchY();
            waitForRelease();

            if (y >= FOOTER_Y && x >= NEW_X) {
                newGame(board);
                continue;
            }
            if (gameOver || y < BOARD_Y || y >= BOARD_Y + ROWS * CELL || x < BOARD_X) {
                continue;
            }
            int column = (x - BOARD_X) / CELL;
            if (column >= COLUMNS || dropRow(board, column) < 0) {
                continue;
            }
            if (play(board, column, HUMAN)) {
                continue;
            }
            computerTurn(board);
        }
    }

    // ---- Game flow ----

    private static void newGame(byte[] board) {
        for (int i = 0; i < COLUMNS * ROWS; i++) {
            board[i] = 0;
        }
        discs = 0;
        gameOver = false;
        drawScreen();
        if (firstPlayer == COMPUTER) {
            computerTurn(board);
        } else {
            showStatus("Your move", TftTouchShield.WHITE);
        }
    }

    private static void computerTurn(byte[] board) {
        showStatus("Thinking...", TftTouchShield.YELLOW);
        Random.seed(Clock.micros());
        bestColumn = -1;
        negamax(board, SEARCH_DEPTH, COMPUTER, -WIN_SCORE * 2, WIN_SCORE * 2, true);
        if (!play(board, bestColumn, COMPUTER)) {
            showStatus("Your move", TftTouchShield.WHITE);
        }
    }

    /** Drops a disc with a short falling animation; returns whether the game ended. */
    private static boolean play(byte[] board, int column, int player) {
        int row = dropRow(board, column);
        int color = HUMAN_COLOR;
        if (player == COMPUTER) {
            color = COMPUTER_COLOR;
        }
        for (int r = 0; r < row; r++) {
            drawDisc(r, column, color);
            Delay.millis(25);
            drawDisc(r, column, TftTouchShield.BLACK);
        }
        board[row * COLUMNS + column] = (byte) player;
        discs = discs + 1;
        drawDisc(row, column, color);

        if (wins(board, row, column, player)) {
            highlightWin(board, row, column, player);
            gameOver = true;
            if (player == HUMAN) {
                humanWins = humanWins + 1;
                firstPlayer = COMPUTER;
                showStatus("You win!", TftTouchShield.GREEN);
            } else {
                computerWins = computerWins + 1;
                firstPlayer = HUMAN;
                showStatus("Computer wins", TftTouchShield.RED);
            }
            drawScore();
            return true;
        }
        if (discs == COLUMNS * ROWS) {
            gameOver = true;
            showStatus("Draw", TftTouchShield.CYAN);
            return true;
        }
        return false;
    }

    // ---- Rules ----

    /** The lowest empty row in {@code column}, or -1 when the column is full. */
    private static int dropRow(byte[] board, int column) {
        for (int row = ROWS - 1; row >= 0; row--) {
            if (board[row * COLUMNS + column] == 0) {
                return row;
            }
        }
        return -1;
    }

    private static boolean wins(byte[] board, int row, int column, int player) {
        return runLength(board, row, column, 0, 1, player) >= 4
                || runLength(board, row, column, 1, 0, player) >= 4
                || runLength(board, row, column, 1, 1, player) >= 4
                || runLength(board, row, column, 1, -1, player) >= 4;
    }

    /** Length of {@code player}'s run through (row, column) along (dr, dc) in both directions. */
    private static int runLength(byte[] board, int row, int column, int dr, int dc, int player) {
        return 1 + count(board, row, column, dr, dc, player) + count(board, row, column, -dr, -dc, player);
    }

    private static int count(byte[] board, int row, int column, int dr, int dc, int player) {
        int n = 0;
        int r = row + dr;
        int c = column + dc;
        while (r >= 0 && r < ROWS && c >= 0 && c < COLUMNS && board[r * COLUMNS + c] == player) {
            n = n + 1;
            r = r + dr;
            c = c + dc;
        }
        return n;
    }

    // ---- Computer ----

    // Columns ordered from the center outwards: central moves are usually best, so alpha-beta
    // prunes more when they come first.
    private static int orderedColumn(int index) {
        if (index == 0) {
            return 3;
        }
        if (index % 2 == 1) {
            return 3 - (index + 1) / 2;
        }
        return 3 + index / 2;
    }

    private static int negamax(byte[] board, int depth, int player, int alpha, int beta, boolean root) {
        int rootShift = 0;
        if (root) {
            rootShift = Random.nextInt(2);
        }
        for (int i = 0; i < COLUMNS; i++) {
            int column = orderedColumn(i);
            if (root && rootShift == 1 && i > 0) {
                // Swap each mirrored pair so equal moves vary between games.
                column = 6 - column;
            }
            int row = dropRow(board, column);
            if (row < 0) {
                continue;
            }
            int index = row * COLUMNS + column;
            board[index] = (byte) player;
            discs = discs + 1;
            int score;
            if (wins(board, row, column, player)) {
                score = WIN_SCORE + depth;
            } else if (discs == COLUMNS * ROWS) {
                score = 0;
            } else if (depth <= 1) {
                score = evaluate(board, player);
            } else {
                score = -negamax(board, depth - 1, -player, -beta, -alpha, false);
            }
            board[index] = 0;
            discs = discs - 1;
            if (score > alpha || (root && bestColumn < 0)) {
                if (score > alpha) {
                    alpha = score;
                }
                if (root) {
                    bestColumn = column;
                }
            }
            if (alpha >= beta) {
                return alpha;
            }
        }
        return alpha;
    }

    /** Scores every window of four cells from {@code player}'s point of view, plus center control. */
    private static int evaluate(byte[] board, int player) {
        int score = 0;
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                score = score + window(board, row, column, 0, 1, player)
                        + window(board, row, column, 1, 0, player)
                        + window(board, row, column, 1, 1, player)
                        + window(board, row, column, 1, -1, player);
            }
            int center = board[row * COLUMNS + 3] * player;
            score = score + center * 3;
        }
        return score;
    }

    private static int window(byte[] board, int row, int column, int dr, int dc, int player) {
        int endRow = row + 3 * dr;
        int endColumn = column + 3 * dc;
        if (endRow >= ROWS || endColumn < 0 || endColumn >= COLUMNS) {
            return 0;
        }
        int mine = 0;
        int theirs = 0;
        for (int i = 0; i < 4; i++) {
            int cell = board[(row + i * dr) * COLUMNS + column + i * dc] * player;
            if (cell > 0) {
                mine = mine + 1;
            } else if (cell < 0) {
                theirs = theirs + 1;
            }
        }
        if (mine > 0 && theirs > 0) {
            return 0;
        }
        if (mine == 3) {
            return 5;
        }
        if (mine == 2) {
            return 2;
        }
        if (theirs == 3) {
            return -4;
        }
        if (theirs == 2) {
            return -1;
        }
        return 0;
    }

    // ---- Input ----

    private static void waitForRelease() {
        int misses = 0;
        while (misses < 3) {
            if (TftTouchShield.readTouch()) {
                misses = 0;
            } else {
                misses = misses + 1;
            }
            Delay.millis(10);
        }
    }

    // ---- Drawing ----

    private static void drawScreen() {
        TftTouchShield.fillScreen(TftTouchShield.BLACK);
        TftTouchShield.fillRect(BOARD_X, BOARD_Y, COLUMNS * CELL, ROWS * CELL, BOARD_BLUE);
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                drawDisc(row, column, TftTouchShield.BLACK);
            }
        }
        TftTouchShield.fillRect(NEW_X, FOOTER_Y, BUTTON_WIDTH, BUTTON_HEIGHT, TftTouchShield.GRAY);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.GRAY);
        TftTouchShield.setCursor(NEW_X + 12, FOOTER_Y + 8);
        TftTouchShield.print("NEW");
        drawScore();
    }

    private static void drawDisc(int row, int column, int color) {
        TftTouchShield.fillCircle(BOARD_X + column * CELL + CELL / 2, BOARD_Y + row * CELL + CELL / 2, DISC_RADIUS,
                color);
    }

    // Rings every disc of the winning run in white.
    private static void highlightWin(byte[] board, int row, int column, int player) {
        for (int direction = 0; direction < 4; direction++) {
            int dr = 1;
            int dc = 0;
            if (direction == 0) {
                dr = 0;
                dc = 1;
            } else if (direction == 2) {
                dc = 1;
            } else if (direction == 3) {
                dc = -1;
            }
            if (runLength(board, row, column, dr, dc, player) < 4) {
                continue;
            }
            int back = count(board, row, column, -dr, -dc, player);
            int length = runLength(board, row, column, dr, dc, player);
            for (int i = 0; i < length; i++) {
                int r = row + (i - back) * dr;
                int c = column + (i - back) * dc;
                int cx = BOARD_X + c * CELL + CELL / 2;
                int cy = BOARD_Y + r * CELL + CELL / 2;
                TftTouchShield.drawCircle(cx, cy, DISC_RADIUS, TftTouchShield.WHITE);
                TftTouchShield.drawCircle(cx, cy, DISC_RADIUS - 1, TftTouchShield.WHITE);
                TftTouchShield.drawCircle(cx, cy, DISC_RADIUS - 2, TftTouchShield.WHITE);
            }
        }
    }

    private static void drawScore() {
        TftTouchShield.setTextSize(2);
        TftTouchShield.setCursor(BOARD_X, FOOTER_Y + 8);
        TftTouchShield.setTextColor(HUMAN_COLOR, TftTouchShield.BLACK);
        TftTouchShield.print("You ");
        TftTouchShield.print(humanWins);
        TftTouchShield.setTextColor(COMPUTER_COLOR, TftTouchShield.BLACK);
        TftTouchShield.print(" CPU ");
        TftTouchShield.print(computerWins);
    }

    private static void showStatus(String text, int color) {
        TftTouchShield.fillRect(0, 0, TftTouchShield.width(), HEADER_HEIGHT, HEADER_BACKGROUND);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(color, HEADER_BACKGROUND);
        TftTouchShield.setCursor(12, 12);
        TftTouchShield.print(text);
    }
}
