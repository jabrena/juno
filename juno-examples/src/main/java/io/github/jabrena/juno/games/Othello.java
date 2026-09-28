package io.github.jabrena.juno.games;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * Othello (Reversi) against the computer on the ELEGOO 2.8" TFT touch screen shield. You play
 * black and move first; the computer plays white.
 *
 * <p>Tap an empty square, marked with a small dot when it is a legal move, to place a disc: it
 * must outflank at least one line of the computer's discs, horizontally, vertically or
 * diagonally, and every outflanked disc turns black. A player without a legal move passes; when
 * neither can move, the one with more discs wins. The computer's last move carries a red dot.
 * {@code NEW} starts over, and the footer keeps the score of games won.
 *
 * <p>The computer runs a negamax search with alpha-beta pruning, deepening one ply at a time until
 * {@value #THINK_MILLIS} ms have passed (or it reaches {@value #MAX_DEPTH} plies, which near the
 * end of a game means playing it out perfectly). Positions are scored by a classic square-weight
 * table (corners good, the squares next to them bad) plus mobility, the difference in the number of
 * legal moves; finished games score by disc count. Moves are tried corners first.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class Othello {
    private static final int MAX_DEPTH = 12;
    private static final int THINK_MILLIS = 2000;
    private static final int MOVES_PER_PLY = 32;
    private static final int WIN = 100000;
    private static final int INFINITY = 1000000;

    private static final int HUMAN = 1;
    private static final int COMPUTER = -1;

    // Layout (portrait, 240x320).
    private static final int WIDTH = 240;
    private static final int HEADER_HEIGHT = 36;
    private static final int CELL = 28;
    private static final int BOARD_X = 8;
    private static final int BOARD_Y = 44;
    private static final int FOOTER_Y = BOARD_Y + 8 * CELL + 8;
    private static final int NEW_X = 172;
    private static final int DISC_RADIUS = 11;

    private static final int FELT = 0x03A0;
    private static final int GRID = 0x0200;
    private static final int HINT = 0x0260;
    private static final int LAST_MOVE = TftTouchShield.RED;
    private static final int HEADER_BACKGROUND = 0x2945;
    private static final int BUTTON = 0xCD05;

    private static int humanWins;
    private static int computerWins;
    private static boolean gameOver;
    private static int lastMove;
    private static int bestMove;
    private static int deadline;
    private static boolean aborted;
    private static int nodes;

    private Othello() {
    }

    public static void main(String[] args) {
        // Ply 0 is the real board; the search copies each ply into the next 64 bytes.
        byte[] board = new byte[64 * (MAX_DEPTH + 2)];
        int[] moves = new int[MOVES_PER_PLY * (MAX_DEPTH + 2)];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        newGame(board, moves);

        while (true) {
            if (!TftTouchShield.readTouch()) {
                Delay.millis(10);
                continue;
            }
            int x = TftTouchShield.touchX();
            int y = TftTouchShield.touchY();
            waitForRelease();
            if (y >= FOOTER_Y && x >= NEW_X) {
                newGame(board, moves);
                continue;
            }
            int square = squareAt(x, y);
            if (gameOver || square < 0 || !isLegal(board, 0, square, HUMAN)) {
                continue;
            }
            playMove(board, square, HUMAN);
            computerTurn(board, moves);
        }
    }

    // ---- Game flow ----

    private static void newGame(byte[] board, int[] moves) {
        for (int i = 0; i < 64; i++) {
            board[i] = 0;
        }
        board[27] = COMPUTER;
        board[36] = COMPUTER;
        board[28] = HUMAN;
        board[35] = HUMAN;
        gameOver = false;
        lastMove = -1;
        TftTouchShield.fillScreen(HEADER_BACKGROUND);
        drawBoard(board);
        drawHeader(board);
        drawFooter();
        showStatus("Your move", TftTouchShield.WHITE);
    }

    /** Lets the computer move (repeatedly, while you have to pass), then hands the turn back. */
    private static void computerTurn(byte[] board, int[] moves) {
        while (true) {
            if (countMoves(board, 0, COMPUTER) == 0) {
                if (countMoves(board, 0, HUMAN) == 0) {
                    finish(board);
                    return;
                }
                showStatus("CPU passes", TftTouchShield.YELLOW);
                Delay.millis(900);
                showStatus("Your move", TftTouchShield.WHITE);
                return;
            }
            showStatus("Thinking...", TftTouchShield.YELLOW);
            int square = chooseMove(board, moves);
            playMove(board, square, COMPUTER);
            if (countMoves(board, 0, HUMAN) > 0) {
                showStatus("Your move", TftTouchShield.WHITE);
                return;
            }
            if (countMoves(board, 0, COMPUTER) == 0) {
                finish(board);
                return;
            }
            showStatus("You must pass", TftTouchShield.YELLOW);
            Delay.millis(1200);
        }
    }

    /** Places a disc on the real board and turns the outflanked discs over one by one. */
    private static void playMove(byte[] board, int square, int side) {
        int previous = lastMove;
        lastMove = -1;
        if (previous >= 0) {
            drawSquare(board, previous);
        }
        clearHints(board);
        board[square] = (byte) side;
        drawSquare(board, square);
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int run = runLength(board, 0, square, dx, dy, side);
                int row = square / 8;
                int column = square % 8;
                for (int i = 1; i <= run; i++) {
                    int flipped = (row + dy * i) * 8 + column + dx * i;
                    board[flipped] = (byte) side;
                    Delay.millis(60);
                    drawSquare(board, flipped);
                }
            }
        }
        if (side == COMPUTER) {
            lastMove = square;
            drawSquare(board, square);
        }
        drawHeader(board);
        if (side == COMPUTER) {
            drawHints(board);
        }
    }

    private static void finish(byte[] board) {
        gameOver = true;
        int margin = discDifference(board, 0);
        if (margin > 0) {
            humanWins = humanWins + 1;
            showStatus("You win!", TftTouchShield.GREEN);
        } else if (margin < 0) {
            computerWins = computerWins + 1;
            showStatus("CPU wins", TftTouchShield.RED);
        } else {
            showStatus("A draw", TftTouchShield.WHITE);
        }
        drawFooter();
    }

    // ---- Rules (on the board copy starting at offset o) ----

    /**
     * How many of the opponent's discs {@code side} would outflank from {@code square} in direction
     * ({@code dx}, {@code dy}); 0 unless the run ends in one of {@code side}'s own discs.
     */
    private static int runLength(byte[] b, int o, int square, int dx, int dy, int side) {
        if (dx == 0 && dy == 0) {
            return 0;
        }
        int row = square / 8 + dy;
        int column = square % 8 + dx;
        int run = 0;
        while (row >= 0 && row < 8 && column >= 0 && column < 8) {
            int disc = b[o + row * 8 + column];
            if (disc == -side) {
                run = run + 1;
            } else if (disc == side) {
                return run;
            } else {
                return 0;
            }
            row = row + dy;
            column = column + dx;
        }
        return 0;
    }

    private static boolean isLegal(byte[] b, int o, int square, int side) {
        if (b[o + square] != 0) {
            return false;
        }
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (runLength(b, o, square, dx, dy, side) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Places {@code side}'s disc on {@code square} of the copy at {@code o} and flips. */
    private static void place(byte[] b, int o, int square, int side) {
        b[o + square] = (byte) side;
        int row = square / 8;
        int column = square % 8;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int run = runLength(b, o, square, dx, dy, side);
                for (int i = 1; i <= run; i++) {
                    b[o + (row + dy * i) * 8 + column + dx * i] = (byte) side;
                }
            }
        }
    }

    private static int countMoves(byte[] b, int o, int side) {
        int count = 0;
        for (int square = 0; square < 64; square++) {
            if (isLegal(b, o, square, side)) {
                count = count + 1;
            }
        }
        return count;
    }

    /** Fills {@code moves} from {@code base} with {@code side}'s legal moves, best-weighted first. */
    private static int generateMoves(byte[] b, int o, int[] moves, int base, int side) {
        int count = 0;
        for (int square = 0; square < 64; square++) {
            if (isLegal(b, o, square, side)) {
                // Insertion sort by square weight, so corners are searched first.
                int i = count;
                while (i > 0 && weight(moves[base + i - 1]) < weight(square)) {
                    moves[base + i] = moves[base + i - 1];
                    i = i - 1;
                }
                moves[base + i] = square;
                count = count + 1;
            }
        }
        return count;
    }

    /** Your discs minus the computer's. */
    private static int discDifference(byte[] b, int o) {
        int difference = 0;
        for (int square = 0; square < 64; square++) {
            difference = difference + b[o + square];
        }
        return difference;
    }

    /** The classic square weights, folded into one quadrant: corners are prized, their neighbours risky. */
    private static int weight(int square) {
        int row = Math.min(square / 8, 7 - square / 8);
        int column = Math.min(square % 8, 7 - square % 8);
        int low = Math.min(row, column);
        int high = Math.max(row, column);
        if (low == 0) {
            if (high == 0) {
                return 100;
            }
            if (high == 1) {
                return -20;
            }
            if (high == 2) {
                return 10;
            }
            return 5;
        }
        if (low == 1) {
            if (high == 1) {
                return -50;
            }
            return -2;
        }
        return -1;
    }

    // ---- Computer player ----

    /** Iterative deepening: the best move of the deepest search that finished in time. */
    private static int chooseMove(byte[] board, int[] moves) {
        deadline = Clock.millis() + THINK_MILLIS;
        aborted = false;
        nodes = 0;
        int chosen = -1;
        int empty = 0;
        for (int square = 0; square < 64; square++) {
            if (board[square] == 0) {
                empty = empty + 1;
            }
        }
        for (int depth = 1; depth <= MAX_DEPTH; depth++) {
            bestMove = -1;
            int score = search(board, moves, depth, 0, -INFINITY, INFINITY, COMPUTER, chosen);
            if (aborted) {
                break;
            }
            chosen = bestMove;
            // Once the search sees the end of the game, deeper searches cannot change the answer.
            if (depth >= empty || score > WIN / 2 || score < -WIN / 2 || Clock.millis() > deadline) {
                break;
            }
        }
        if (chosen < 0) {
            generateMoves(board, 0, moves, 0, COMPUTER);
            chosen = moves[0];
        }
        return chosen;
    }

    /**
     * Negamax with alpha-beta: the score for {@code side} to move. At the root the previous
     * iteration's best move ({@code first}) is searched first.
     */
    private static int search(byte[] b, int[] moves, int depth, int ply, int alpha, int beta, int side,
                              int first) {
        nodes = nodes + 1;
        if ((nodes & 63) == 0 && Clock.millis() > deadline) {
            aborted = true;
        }
        if (aborted) {
            return 0;
        }
        int o = ply * 64;
        int base = ply * MOVES_PER_PLY;
        int count = generateMoves(b, o, moves, base, side);
        if (count == 0) {
            if (countMoves(b, o, -side) == 0) {
                int margin = discDifference(b, o) * side;
                if (margin > 0) {
                    return WIN + margin - ply;
                }
                if (margin < 0) {
                    return -WIN + margin + ply;
                }
                return 0;
            }
            if (depth == 0) {
                return evaluate(b, o, side);
            }
            // Pass: copy the position unchanged so the opponent's ply has its own board.
            for (int i = 0; i < 64; i++) {
                b[o + 64 + i] = b[o + i];
            }
            return -search(b, moves, depth - 1, ply + 1, -beta, -alpha, -side, -1);
        }
        if (depth == 0) {
            return evaluate(b, o, side);
        }
        if (first >= 0) {
            for (int i = 1; i < count; i++) {
                if (moves[base + i] == first) {
                    moves[base + i] = moves[base];
                    moves[base] = first;
                }
            }
        }
        int best = -INFINITY;
        for (int i = 0; i < count; i++) {
            int square = moves[base + i];
            for (int k = 0; k < 64; k++) {
                b[o + 64 + k] = b[o + k];
            }
            place(b, o + 64, square, side);
            int score = -search(b, moves, depth - 1, ply + 1, -beta, -alpha, -side, -1);
            if (score > best) {
                best = score;
                if (ply == 0) {
                    bestMove = square;
                }
            }
            alpha = Math.max(alpha, score);
            if (alpha >= beta) {
                break;
            }
        }
        return best;
    }

    /** Square weights plus mobility, for {@code side}. */
    private static int evaluate(byte[] b, int o, int side) {
        int score = 0;
        for (int square = 0; square < 64; square++) {
            score = score + b[o + square] * weight(square);
        }
        int mobility = countMoves(b, o, side) - countMoves(b, o, -side);
        return score * side + mobility * 8;
    }

    // ---- Input ----

    private static int squareAt(int x, int y) {
        if (x < BOARD_X || y < BOARD_Y || x >= BOARD_X + 8 * CELL || y >= BOARD_Y + 8 * CELL) {
            return -1;
        }
        return (y - BOARD_Y) / CELL * 8 + (x - BOARD_X) / CELL;
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

    private static void drawBoard(byte[] board) {
        TftTouchShield.fillRect(BOARD_X - 2, BOARD_Y - 2, 8 * CELL + 4, 8 * CELL + 4, GRID);
        for (int square = 0; square < 64; square++) {
            drawSquare(board, square);
        }
        drawHints(board);
    }

    private static void drawSquare(byte[] board, int square) {
        int x = BOARD_X + square % 8 * CELL;
        int y = BOARD_Y + square / 8 * CELL;
        TftTouchShield.fillRect(x, y, CELL - 1, CELL - 1, FELT);
        int disc = board[square];
        int cx = x + CELL / 2 - 1;
        int cy = y + CELL / 2 - 1;
        if (disc == HUMAN) {
            TftTouchShield.fillCircle(cx, cy, DISC_RADIUS, TftTouchShield.BLACK);
            TftTouchShield.drawCircle(cx, cy, DISC_RADIUS, TftTouchShield.GRAY);
        } else if (disc == COMPUTER) {
            TftTouchShield.fillCircle(cx, cy, DISC_RADIUS, TftTouchShield.WHITE);
            TftTouchShield.drawCircle(cx, cy, DISC_RADIUS, TftTouchShield.GRAY);
        }
        if (square == lastMove) {
            TftTouchShield.fillCircle(cx, cy, 3, LAST_MOVE);
        }
    }

    private static void drawHints(byte[] board) {
        for (int square = 0; square < 64; square++) {
            if (isLegal(board, 0, square, HUMAN)) {
                int cx = BOARD_X + square % 8 * CELL + CELL / 2 - 1;
                int cy = BOARD_Y + square / 8 * CELL + CELL / 2 - 1;
                TftTouchShield.fillCircle(cx, cy, 3, HINT);
            }
        }
    }

    private static void clearHints(byte[] board) {
        for (int square = 0; square < 64; square++) {
            if (board[square] == 0) {
                drawSquare(board, square);
            }
        }
    }

    private static void drawHeader(byte[] board) {
        int black = 0;
        int white = 0;
        for (int square = 0; square < 64; square++) {
            if (board[square] == HUMAN) {
                black = black + 1;
            } else if (board[square] == COMPUTER) {
                white = white + 1;
            }
        }
        TftTouchShield.fillRect(0, 0, WIDTH, HEADER_HEIGHT, HEADER_BACKGROUND);
        TftTouchShield.fillCircle(20, 18, 11, TftTouchShield.BLACK);
        TftTouchShield.drawCircle(20, 18, 11, TftTouchShield.GRAY);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, HEADER_BACKGROUND);
        TftTouchShield.setCursor(38, 11);
        TftTouchShield.print("You ");
        TftTouchShield.print(black);
        TftTouchShield.fillCircle(136, 18, 11, TftTouchShield.WHITE);
        TftTouchShield.drawCircle(136, 18, 11, TftTouchShield.GRAY);
        TftTouchShield.setCursor(154, 11);
        TftTouchShield.print("CPU ");
        TftTouchShield.print(white);
    }

    private static void drawFooter() {
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.GRAY, HEADER_BACKGROUND);
        TftTouchShield.setCursor(8, FOOTER_Y + 26);
        TftTouchShield.print("Games: you ");
        TftTouchShield.print(humanWins);
        TftTouchShield.print(" - CPU ");
        TftTouchShield.print(computerWins);
        TftTouchShield.fillRect(NEW_X, FOOTER_Y, WIDTH - NEW_X - 8, 30, BUTTON);
        TftTouchShield.drawRect(NEW_X, FOOTER_Y, WIDTH - NEW_X - 8, 30, TftTouchShield.BLACK);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.BLACK, BUTTON);
        TftTouchShield.setCursor(NEW_X + 12, FOOTER_Y + 8);
        TftTouchShield.print("NEW");
    }

    private static void showStatus(String text, int color) {
        TftTouchShield.fillRect(0, FOOTER_Y, NEW_X - 4, 22, HEADER_BACKGROUND);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(color, HEADER_BACKGROUND);
        TftTouchShield.setCursor(8, FOOTER_Y + 4);
        TftTouchShield.print(text);
    }
}
