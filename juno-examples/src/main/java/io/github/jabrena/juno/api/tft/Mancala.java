package io.github.jabrena.juno.api.tft;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;

/**
 * Mancala (Kalah, six pits and four seeds each) against the computer on the ELEGOO 2.8" TFT touch
 * screen shield, in landscape. Your pits are the bottom row and your store is on the right; the
 * computer's pits are the top row and its store is on the left.
 *
 * <p>Tap one of your pits to pick up its seeds and sow them one by one counter-clockwise, into your
 * store but never the computer's. A last seed that lands in your store earns another turn; one
 * that lands in an empty pit of yours captures it together with the seeds in the pit opposite, if
 * there are any. When either row runs out, each player banks the seeds left on their own side and
 * the fuller store wins. The loser starts the next game; {@code NEW} starts over.
 *
 * <p>The computer searches with minimax and alpha-beta pruning (an extra turn is a ply in which the
 * same side moves again), deepening one ply at a time until {@value #THINK_MILLIS} ms have passed
 * or it reaches {@value #MAX_DEPTH} plies.
 *
 * <p>The board is 14 bytes: pits 0-5 are yours from left to right, 6 is your store, 7-12 are the
 * computer's pits from right to left and 13 is its store, so sowing simply walks up the indices
 * and the pit opposite {@code i} is {@code 12 - i}.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class Mancala {
    private static final int PITS = 14;
    private static final int HUMAN_STORE = 6;
    private static final int COMPUTER_STORE = 13;
    private static final int SEEDS = 4;
    private static final int MAX_DEPTH = 14;
    private static final int THINK_MILLIS = 1500;
    private static final int WIN = 100000;
    private static final int INFINITY = 1000000;

    // Layout (landscape, 320x240).
    private static final int WIDTH = 320;
    private static final int HEADER_HEIGHT = 24;
    private static final int PIT_X = 48;
    private static final int PIT_SPACING = 38;
    private static final int COMPUTER_ROW_Y = 84;
    private static final int HUMAN_ROW_Y = 146;
    private static final int PIT_RADIUS = 16;
    private static final int STORE_TOP = 50;
    private static final int STORE_HEIGHT = 130;
    private static final int STORE_WIDTH = 38;
    private static final int FOOTER_Y = 200;
    private static final int NEW_X = 248;

    private static final int TABLE = 0x3186;
    private static final int WOOD = 0x9A64;
    private static final int PIT = 0x61A2;
    private static final int SEED_TEXT = 0xFFDF;
    private static final int HIGHLIGHT = TftTouchShield.YELLOW;
    private static final int HEADER_BACKGROUND = 0x2945;
    private static final int BUTTON = 0xCD05;

    private static int humanWins;
    private static int computerWins;
    private static boolean humanStarts;
    private static boolean gameOver;
    private static int bestPit;
    private static int deadline;
    private static boolean aborted;
    private static int nodes;

    private Mancala() {
    }

    public static void main(String[] args) {
        // Ply 0 is the real board; plies 1.. are the search's scratch copies.
        byte[] board = new byte[PITS * (MAX_DEPTH + 2)];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);
        humanStarts = true;
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
            int pit = humanPitAt(x, y);
            if (gameOver || pit < 0 || board[pit] == 0) {
                continue;
            }
            boolean again = play(board, pit);
            if (!gameOver && !again) {
                computerTurn(board);
            } else if (!gameOver) {
                showStatus("Go again!", TftTouchShield.GREEN);
            }
        }
    }

    // ---- Game flow ----

    private static void newGame(byte[] board) {
        for (int i = 0; i < PITS; i++) {
            board[i] = SEEDS;
        }
        board[HUMAN_STORE] = 0;
        board[COMPUTER_STORE] = 0;
        gameOver = false;
        TftTouchShield.fillScreen(TABLE);
        drawHeader();
        drawBoard(board);
        drawNewButton();
        if (humanStarts) {
            showStatus("Your move", TftTouchShield.WHITE);
        } else {
            computerTurn(board);
        }
    }

    /** Plays the computer's moves (several, while it earns extra turns). */
    private static void computerTurn(byte[] board) {
        boolean again = true;
        while (again && !gameOver) {
            showStatus("Thinking...", TftTouchShield.YELLOW);
            int pit = choosePit(board);
            again = play(board, pit);
        }
        if (!gameOver) {
            showStatus("Your move", TftTouchShield.WHITE);
        }
    }

    /** Sows {@code pit} on the real board with animation; returns whether its owner goes again. */
    private static boolean play(byte[] board, int pit) {
        int store = HUMAN_STORE;
        int skip = COMPUTER_STORE;
        if (pit > HUMAN_STORE) {
            store = COMPUTER_STORE;
            skip = HUMAN_STORE;
        }
        highlight(pit, true);
        Delay.millis(250);
        int seeds = board[pit];
        board[pit] = 0;
        drawPit(board, pit);
        highlight(pit, true);
        int at = pit;
        while (seeds > 0) {
            at = (at + 1) % PITS;
            if (at == skip) {
                continue;
            }
            board[at] = (byte) (board[at] + 1);
            seeds = seeds - 1;
            drawPit(board, at);
            highlight(at, true);
            Delay.millis(140);
            highlight(at, false);
        }
        highlight(pit, false);
        boolean again = at == store;
        int opposite = 12 - at;
        if (!again && ownPit(at, store) && board[at] == 1 && board[opposite] > 0) {
            highlight(opposite, true);
            Delay.millis(400);
            board[store] = (byte) (board[store] + board[opposite] + 1);
            board[at] = 0;
            board[opposite] = 0;
            drawPit(board, at);
            drawPit(board, opposite);
            drawPit(board, store);
        }
        if (rowEmpty(board, 0) || rowEmpty(board, 7)) {
            Delay.millis(400);
            sweep(board, 0);
            drawBoard(board);
            finish(board);
            return false;
        }
        return again;
    }

    private static void finish(byte[] board) {
        gameOver = true;
        if (board[HUMAN_STORE] > board[COMPUTER_STORE]) {
            humanWins = humanWins + 1;
            humanStarts = false;
            showStatus("You win!", TftTouchShield.GREEN);
        } else if (board[HUMAN_STORE] < board[COMPUTER_STORE]) {
            computerWins = computerWins + 1;
            humanStarts = true;
            showStatus("CPU wins", TftTouchShield.RED);
        } else {
            humanStarts = !humanStarts;
            showStatus("A draw", TftTouchShield.WHITE);
        }
        drawHeader();
    }

    // ---- Rules (on the board copy starting at offset o) ----

    private static boolean ownPit(int pit, int store) {
        return pit < store && pit >= store - 6;
    }

    private static boolean rowEmpty(byte[] b, int first) {
        for (int i = first; i < first + 6; i++) {
            if (b[i] != 0) {
                return false;
            }
        }
        return true;
    }

    /** Banks every seed still in a pit into the store of that pit's owner. */
    private static void sweep(byte[] b, int o) {
        for (int i = 0; i < 6; i++) {
            b[o + HUMAN_STORE] = (byte) (b[o + HUMAN_STORE] + b[o + i]);
            b[o + i] = 0;
            b[o + COMPUTER_STORE] = (byte) (b[o + COMPUTER_STORE] + b[o + 7 + i]);
            b[o + 7 + i] = 0;
        }
    }

    /**
     * Sows {@code pit} of the board at offset {@code o}, applying captures and the end-of-game sweep.
     * Returns 1 if the same player moves again, 0 if the turn passes, and -1 if the game ended.
     */
    private static int sow(byte[] b, int o, int pit) {
        int store = HUMAN_STORE;
        int skip = COMPUTER_STORE;
        if (pit > HUMAN_STORE) {
            store = COMPUTER_STORE;
            skip = HUMAN_STORE;
        }
        int seeds = b[o + pit];
        b[o + pit] = 0;
        int at = pit;
        while (seeds > 0) {
            at = at + 1;
            if (at == PITS) {
                at = 0;
            }
            if (at != skip) {
                b[o + at] = (byte) (b[o + at] + 1);
                seeds = seeds - 1;
            }
        }
        int result = 0;
        if (at == store) {
            result = 1;
        } else if (ownPit(at, store) && b[o + at] == 1 && b[o + 12 - at] > 0) {
            b[o + store] = (byte) (b[o + store] + b[o + 12 - at] + 1);
            b[o + at] = 0;
            b[o + 12 - at] = 0;
        }
        boolean humanEmpty = true;
        boolean computerEmpty = true;
        for (int i = 0; i < 6; i++) {
            if (b[o + i] != 0) {
                humanEmpty = false;
            }
            if (b[o + 7 + i] != 0) {
                computerEmpty = false;
            }
        }
        if (humanEmpty || computerEmpty) {
            sweep(b, o);
            return -1;
        }
        return result;
    }

    // ---- Computer player ----

    /** Iterative deepening: the best pit of the deepest search that finished within the time budget. */
    private static int choosePit(byte[] board) {
        deadline = Clock.millis() + THINK_MILLIS;
        aborted = false;
        nodes = 0;
        int chosen = -1;
        for (int depth = 1; depth <= MAX_DEPTH; depth++) {
            bestPit = -1;
            int score = search(board, depth, 0, -INFINITY, INFINITY, true);
            if (aborted) {
                break;
            }
            chosen = bestPit;
            // A forced win or loss will not change with more depth.
            if (score > WIN / 2 || score < -WIN / 2 || Clock.millis() > deadline) {
                break;
            }
        }
        if (chosen < 0) {
            for (int pit = 12; pit >= 7; pit--) {
                if (board[pit] != 0) {
                    chosen = pit;
                }
            }
        }
        return chosen;
    }

    /** Minimax with alpha-beta, scored from the computer's point of view. */
    private static int search(byte[] b, int depth, int ply, int alpha, int beta, boolean computerToMove) {
        nodes = nodes + 1;
        if ((nodes & 255) == 0 && Clock.millis() > deadline) {
            aborted = true;
        }
        if (aborted) {
            return 0;
        }
        int o = ply * PITS;
        if (depth == 0) {
            return evaluate(b, o);
        }
        int first = 0;
        int best = INFINITY;
        if (computerToMove) {
            first = 7;
            best = -INFINITY;
        }
        // Pits nearest the store first: they most often give extra turns, which helps pruning.
        for (int k = 5; k >= 0; k--) {
            int pit = first + k;
            if (b[o + pit] == 0) {
                continue;
            }
            int next = o + PITS;
            for (int i = 0; i < PITS; i++) {
                b[next + i] = b[o + i];
            }
            int result = sow(b, next, pit);
            int score;
            if (result < 0) {
                score = finalScore(b, next, ply);
            } else if (result == 1) {
                score = search(b, depth - 1, ply + 1, alpha, beta, computerToMove);
            } else {
                score = search(b, depth - 1, ply + 1, alpha, beta, !computerToMove);
            }
            if (computerToMove) {
                if (score > best) {
                    best = score;
                    if (ply == 0) {
                        bestPit = pit;
                    }
                }
                alpha = Math.max(alpha, score);
            } else {
                best = Math.min(best, score);
                beta = Math.min(beta, score);
            }
            if (alpha >= beta) {
                break;
            }
        }
        return best;
    }

    /** Score of a finished game: a win beats any heuristic, and sooner beats later. */
    private static int finalScore(byte[] b, int o, int ply) {
        int margin = b[o + COMPUTER_STORE] - b[o + HUMAN_STORE];
        if (margin > 0) {
            return WIN + margin * 100 - ply;
        }
        if (margin < 0) {
            return -WIN + margin * 100 + ply;
        }
        return 0;
    }

    /** Store difference, plus a little for seeds still on the computer's side. */
    private static int evaluate(byte[] b, int o) {
        int side = 0;
        for (int i = 0; i < 6; i++) {
            side = side + b[o + 7 + i] - b[o + i];
        }
        return (b[o + COMPUTER_STORE] - b[o + HUMAN_STORE]) * 100 + side * 10;
    }

    // ---- Input ----

    private static int humanPitAt(int x, int y) {
        if (y < HUMAN_ROW_Y - PIT_RADIUS - 8 || y > HUMAN_ROW_Y + PIT_RADIUS + 8 || x < PIT_X) {
            return -1;
        }
        int column = (x - PIT_X) / PIT_SPACING;
        if (column > 5) {
            return -1;
        }
        return column;
    }

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

    private static int pitX(int pit) {
        if (pit < HUMAN_STORE) {
            return PIT_X + pit * PIT_SPACING + PIT_SPACING / 2;
        }
        return PIT_X + (12 - pit) * PIT_SPACING + PIT_SPACING / 2;
    }

    private static int pitY(int pit) {
        if (pit < HUMAN_STORE) {
            return HUMAN_ROW_Y;
        }
        return COMPUTER_ROW_Y;
    }

    private static int storeX(int store) {
        if (store == HUMAN_STORE) {
            return WIDTH - 4 - STORE_WIDTH;
        }
        return 4;
    }

    private static void drawBoard(byte[] board) {
        TftTouchShield.fillRect(0, STORE_TOP - 12, WIDTH, STORE_HEIGHT + 24, WOOD);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.BLACK, WOOD);
        TftTouchShield.setCursor(PIT_X + 2, STORE_TOP - 8);
        TftTouchShield.print("CPU");
        TftTouchShield.setCursor(WIDTH - PIT_X - 20, STORE_TOP + STORE_HEIGHT + 3);
        TftTouchShield.print("YOU");
        for (int pit = 0; pit < PITS; pit++) {
            drawPit(board, pit);
        }
    }

    private static void drawPit(byte[] board, int pit) {
        TftTouchShield.setTextColor(SEED_TEXT, PIT);
        if (pit == HUMAN_STORE || pit == COMPUTER_STORE) {
            int x = storeX(pit);
            TftTouchShield.fillRect(x, STORE_TOP, STORE_WIDTH, STORE_HEIGHT, PIT);
            TftTouchShield.setTextSize(3);
            int value = board[pit];
            int digits = 1;
            if (value >= 10) {
                digits = 2;
            }
            TftTouchShield.setCursor(x + (STORE_WIDTH - digits * 18) / 2, STORE_TOP + STORE_HEIGHT / 2 - 11);
            TftTouchShield.print(value);
            return;
        }
        int cx = pitX(pit);
        int cy = pitY(pit);
        TftTouchShield.fillCircle(cx, cy, PIT_RADIUS + 2, WOOD);
        TftTouchShield.fillCircle(cx, cy, PIT_RADIUS, PIT);
        int value = board[pit];
        if (value == 0) {
            return;
        }
        TftTouchShield.setTextSize(2);
        int digits = 1;
        if (value >= 10) {
            digits = 2;
        }
        TftTouchShield.setCursor(cx - digits * 6 + 1, cy - 7);
        TftTouchShield.print(value);
    }

    private static void highlight(int pit, boolean on) {
        int color = WOOD;
        if (on) {
            color = HIGHLIGHT;
        }
        if (pit == HUMAN_STORE || pit == COMPUTER_STORE) {
            int x = storeX(pit);
            TftTouchShield.drawRect(x - 2, STORE_TOP - 2, STORE_WIDTH + 4, STORE_HEIGHT + 4, color);
            TftTouchShield.drawRect(x - 1, STORE_TOP - 1, STORE_WIDTH + 2, STORE_HEIGHT + 2, color);
            return;
        }
        TftTouchShield.drawCircle(pitX(pit), pitY(pit), PIT_RADIUS + 1, color);
        TftTouchShield.drawCircle(pitX(pit), pitY(pit), PIT_RADIUS + 2, color);
    }

    private static void drawHeader() {
        TftTouchShield.fillRect(0, 0, WIDTH, HEADER_HEIGHT, HEADER_BACKGROUND);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, HEADER_BACKGROUND);
        TftTouchShield.setCursor(6, 4);
        TftTouchShield.print("MANCALA");
        TftTouchShield.setTextColor(TftTouchShield.CYAN, HEADER_BACKGROUND);
        TftTouchShield.setCursor(150, 4);
        TftTouchShield.print("You ");
        TftTouchShield.print(humanWins);
        TftTouchShield.setTextColor(TftTouchShield.ORANGE, HEADER_BACKGROUND);
        TftTouchShield.print("  CPU ");
        TftTouchShield.print(computerWins);
    }

    private static void drawNewButton() {
        TftTouchShield.fillRect(NEW_X, FOOTER_Y + 6, WIDTH - NEW_X - 6, 30, BUTTON);
        TftTouchShield.drawRect(NEW_X, FOOTER_Y + 6, WIDTH - NEW_X - 6, 30, TftTouchShield.BLACK);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.BLACK, BUTTON);
        TftTouchShield.setCursor(NEW_X + 15, FOOTER_Y + 14);
        TftTouchShield.print("NEW");
    }

    private static void showStatus(String text, int color) {
        TftTouchShield.fillRect(0, FOOTER_Y + 6, NEW_X - 4, 30, TABLE);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(color, TABLE);
        TftTouchShield.setCursor(10, FOOTER_Y + 14);
        TftTouchShield.print(text);
    }
}
