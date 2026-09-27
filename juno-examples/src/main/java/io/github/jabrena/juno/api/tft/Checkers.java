package io.github.jabrena.juno.api.tft;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;

/**
 * Checkers (English draughts) against the computer on the ELEGOO 2.8" TFT touch screen shield.
 * You play red from the bottom; the computer plays black.
 *
 * <p>Men move one square diagonally forward; kings (marked with a gold crown) move one square
 * diagonally in any direction. Captures are mandatory, and a capturing piece keeps jumping while it
 * can — tap each landing square in turn. A man reaching the far row is crowned, which ends its move.
 * A player with no legal move loses. Tap a piece to see its legal moves, then tap a destination;
 * {@code NEW} starts over.
 *
 * <p>The computer runs a {@value #SEARCH_DEPTH}-ply minimax search with alpha-beta pruning. Each
 * jump of a multi-jump is its own ply in which the same side moves again, so the search uses plain
 * minimax (scores from the computer's point of view) rather than negamax.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class Checkers {
    private static final int SEARCH_DEPTH = 5;
    private static final int MOVES_PER_PLY = 48;
    private static final int WIN = 100000;
    private static final int INFINITY = 1000000;

    private static final int HUMAN = 1;
    private static final int COMPUTER = -1;
    private static final int MAN = 1;
    private static final int KING = 2;

    // Layout (portrait, 240x320).
    private static final int HEADER_HEIGHT = 36;
    private static final int CELL = 28;
    private static final int BOARD_X = 8;
    private static final int BOARD_Y = 44;
    private static final int FOOTER_Y = BOARD_Y + 8 * CELL + 8;
    private static final int NEW_X = 172;
    private static final int BUTTON_WIDTH = 60;
    private static final int BUTTON_HEIGHT = 30;

    private static final int LIGHT = 0xF6D6;
    private static final int DARK = 0x6A69;
    private static final int HUMAN_COLOR = 0xD8A3;
    private static final int COMPUTER_COLOR = 0x2104;
    private static final int CROWN = 0xFEA0;
    private static final int SELECTED = 0x07E0;
    private static final int TARGET = 0x07E0;
    private static final int HEADER_BACKGROUND = 0x2945;

    private static final int DECO_SELECTED = 1;
    private static final int DECO_TARGET = 2;

    // Bits of the int returned by makeMove besides the captured piece + 3 (bits 0-2).
    private static final int UNDO_PROMOTED = 1 << 3;
    private static final int UNDO_HUMAN_TO_MOVE = 1 << 11;

    private static int side;
    // The piece that must keep jumping, or -1.
    private static int chainFrom;
    private static int bestMove;
    private static boolean gameOver;

    private Checkers() {
    }

    public static void main(String[] args) {
        byte[] board = new byte[64];
        byte[] shownPiece = new byte[64];
        byte[] shownDeco = new byte[64];
        byte[] deco = new byte[64];
        int[] moves = new int[MOVES_PER_PLY * (SEARCH_DEPTH + 2)];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        newGame(board, shownPiece, shownDeco, deco, moves);

        int selected = -1;
        while (true) {
            if (!TftTouchShield.readTouch()) {
                Delay.millis(10);
                continue;
            }
            int x = TftTouchShield.touchX();
            int y = TftTouchShield.touchY();
            waitForRelease();

            if (y >= FOOTER_Y && x >= NEW_X) {
                newGame(board, shownPiece, shownDeco, deco, moves);
                selected = -1;
                continue;
            }
            int square = squareAt(x, y);
            if (square < 0 || gameOver) {
                continue;
            }
            if (selected >= 0 && findMove(board, moves, selected, square) >= 0) {
                makeMove(board, findMove(board, moves, selected, square));
                selected = -1;
                if (side == HUMAN && !gameOver) {
                    // A multi-jump continues with the same piece.
                    selected = chainFrom;
                    refresh(board, shownPiece, shownDeco, deco, moves, selected);
                    showStatus("Keep jumping", TftTouchShield.YELLOW);
                    continue;
                }
                refresh(board, shownPiece, shownDeco, deco, moves, -1);
                computerTurn(board, shownPiece, shownDeco, deco, moves);
                continue;
            }
            if (chainFrom >= 0) {
                continue;
            }
            if (board[square] > 0 && square != selected && hasMoveFrom(board, moves, square)) {
                selected = square;
            } else {
                selected = -1;
            }
            refresh(board, shownPiece, shownDeco, deco, moves, selected);
        }
    }

    // ---- Game flow ----

    private static void newGame(byte[] board, byte[] shownPiece, byte[] shownDeco, byte[] deco, int[] moves) {
        for (int square = 0; square < 64; square++) {
            int row = square >> 3;
            int piece = 0;
            if (isDark(square) && row < 3) {
                piece = COMPUTER * MAN;
            } else if (isDark(square) && row > 4) {
                piece = HUMAN * MAN;
            }
            board[square] = (byte) piece;
            shownPiece[square] = 0;
            shownDeco[square] = -1;
        }
        side = HUMAN;
        chainFrom = -1;
        gameOver = false;
        TftTouchShield.fillScreen(TftTouchShield.BLACK);
        TftTouchShield.fillRect(NEW_X, FOOTER_Y, BUTTON_WIDTH, BUTTON_HEIGHT, TftTouchShield.GRAY);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.GRAY);
        TftTouchShield.setCursor(NEW_X + 12, FOOTER_Y + 8);
        TftTouchShield.print("NEW");
        refresh(board, shownPiece, shownDeco, deco, moves, -1);
        showStatus("Your move", TftTouchShield.WHITE);
    }

    private static void computerTurn(byte[] board, byte[] shownPiece, byte[] shownDeco, byte[] deco, int[] moves) {
        if (checkGameOver(board, moves)) {
            return;
        }
        showStatus("Thinking...", TftTouchShield.YELLOW);
        Random.seed(Clock.micros());
        while (side == COMPUTER) {
            bestMove = -1;
            search(board, moves, SEARCH_DEPTH, 0, -INFINITY, INFINITY);
            if (bestMove < 0) {
                break;
            }
            makeMove(board, bestMove);
            refresh(board, shownPiece, shownDeco, deco, moves, -1);
            Delay.millis(300);
        }
        if (!checkGameOver(board, moves)) {
            showStatus("Your move", TftTouchShield.WHITE);
        }
    }

    /** Ends the game when the side to move has no legal move. */
    private static boolean checkGameOver(byte[] board, int[] moves) {
        if (generateMoves(board, moves, 0) > 0) {
            return false;
        }
        gameOver = true;
        if (side == HUMAN) {
            showStatus("Computer wins", TftTouchShield.RED);
        } else {
            showStatus("You win!", TftTouchShield.GREEN);
        }
        return true;
    }

    private static int findMove(byte[] board, int[] moves, int from, int to) {
        int count = generateMoves(board, moves, 0);
        for (int i = 0; i < count; i++) {
            int move = moves[i];
            if ((move & 63) == from && ((move >> 6) & 63) == to) {
                return move;
            }
        }
        return -1;
    }

    private static boolean hasMoveFrom(byte[] board, int[] moves, int from) {
        int count = generateMoves(board, moves, 0);
        for (int i = 0; i < count; i++) {
            if ((moves[i] & 63) == from) {
                return true;
            }
        }
        return false;
    }

    // ---- Search ----

    private static int search(byte[] board, int[] moves, int depth, int ply, int alpha, int beta) {
        int base = ply * MOVES_PER_PLY;
        int count = generateMoves(board, moves, base);
        if (count == 0) {
            if (side == COMPUTER) {
                return -WIN + ply;
            }
            return WIN - ply;
        }
        if (depth == 0) {
            return evaluate(board);
        }
        if (ply == 0) {
            for (int i = count - 1; i > 0; i--) {
                int j = Random.nextInt(i + 1);
                int swap = moves[base + i];
                moves[base + i] = moves[base + j];
                moves[base + j] = swap;
            }
        }
        boolean maximizing = side == COMPUTER;
        int best = INFINITY;
        if (maximizing) {
            best = -INFINITY;
        }
        for (int i = 0; i < count; i++) {
            int move = moves[base + i];
            int undo = makeMove(board, move);
            int score = search(board, moves, depth - 1, ply + 1, alpha, beta);
            unmakeMove(board, move, undo);
            if (maximizing) {
                if (score > best) {
                    best = score;
                    if (ply == 0) {
                        bestMove = move;
                    }
                }
                alpha = Math.max(alpha, best);
            } else {
                if (score < best) {
                    best = score;
                }
                beta = Math.min(beta, best);
            }
            if (alpha >= beta) {
                break;
            }
        }
        return best;
    }

    /** Material and advancement, from the computer's point of view. */
    private static int evaluate(byte[] board) {
        int score = 0;
        for (int square = 0; square < 64; square++) {
            int piece = board[square];
            if (piece == 0) {
                continue;
            }
            int row = square >> 3;
            int value = 160;
            if (Math.abs(piece) == MAN) {
                value = 100 + 4 * row;
                if (piece > 0) {
                    value = 100 + 4 * (7 - row);
                }
            }
            if (piece < 0) {
                score = score + value;
            } else {
                score = score - value;
            }
        }
        return score;
    }

    // ---- Move generation ----

    private static boolean isDark(int square) {
        return ((square >> 3) + (square & 7)) % 2 == 1;
    }

    /**
     * Writes the side to move's legal moves (from | to << 6) from moves[base]: only captures when
     * any capture exists, and only the jumping piece's captures during a multi-jump.
     */
    private static int generateMoves(byte[] board, int[] moves, int base) {
        int count = base;
        for (int square = 0; square < 64; square++) {
            if (board[square] * side > 0 && (chainFrom < 0 || square == chainFrom)) {
                count = addMoves(board, moves, count, square, true);
            }
        }
        if (count > base || chainFrom >= 0) {
            return count - base;
        }
        for (int square = 0; square < 64; square++) {
            if (board[square] * side > 0) {
                count = addMoves(board, moves, count, square, false);
            }
        }
        return count - base;
    }

    private static int addMoves(byte[] board, int[] moves, int count, int from, boolean captures) {
        int piece = board[from];
        int row = from >> 3;
        int column = from & 7;
        for (int dr = -1; dr <= 1; dr = dr + 2) {
            if (Math.abs(piece) == MAN && dr != -side) {
                continue;
            }
            for (int dc = -1; dc <= 1; dc = dc + 2) {
                int r = row + dr;
                int c = column + dc;
                if (captures) {
                    int landingRow = row + 2 * dr;
                    int landingColumn = column + 2 * dc;
                    if (landingRow >= 0 && landingRow < 8 && landingColumn >= 0 && landingColumn < 8
                            && board[r * 8 + c] * side < 0 && board[landingRow * 8 + landingColumn] == 0) {
                        moves[count] = from | ((landingRow * 8 + landingColumn) << 6);
                        count = count + 1;
                    }
                } else if (r >= 0 && r < 8 && c >= 0 && c < 8 && board[r * 8 + c] == 0) {
                    moves[count] = from | ((r * 8 + c) << 6);
                    count = count + 1;
                }
            }
        }
        return count;
    }

    // ---- Make / unmake ----

    /**
     * Plays {@code move}. After a capture the same side moves again when the piece can keep jumping
     * (unless it was just crowned); otherwise the turn passes. Returns what {@link #unmakeMove}
     * needs: the captured piece + 3, the UNDO_* flags, and the previous chainFrom + 1 in bits 4-10.
     */
    private static int makeMove(byte[] board, int move) {
        int from = move & 63;
        int to = (move >> 6) & 63;
        int piece = board[from];
        int undo = (chainFrom + 1) << 4;
        if (side == HUMAN) {
            undo = undo | UNDO_HUMAN_TO_MOVE;
        }
        board[to] = (byte) piece;
        board[from] = 0;
        int captured = 0;
        boolean capture = Math.abs((to >> 3) - (from >> 3)) == 2;
        if (capture) {
            int middle = (from + to) / 2;
            captured = board[middle];
            board[middle] = 0;
        }
        boolean promoted = false;
        int toRow = to >> 3;
        if (Math.abs(piece) == MAN && ((piece > 0 && toRow == 0) || (piece < 0 && toRow == 7))) {
            board[to] = (byte) (piece * KING);
            promoted = true;
            undo = undo | UNDO_PROMOTED;
        }
        if (capture && !promoted && canCaptureWith(board, to)) {
            chainFrom = to;
        } else {
            chainFrom = -1;
            side = -side;
        }
        return undo | (captured + 3);
    }

    private static boolean canCaptureWith(byte[] board, int from) {
        int piece = board[from];
        int row = from >> 3;
        int column = from & 7;
        for (int dr = -1; dr <= 1; dr = dr + 2) {
            if (Math.abs(piece) == MAN && dr != -side) {
                continue;
            }
            for (int dc = -1; dc <= 1; dc = dc + 2) {
                int landingRow = row + 2 * dr;
                int landingColumn = column + 2 * dc;
                if (landingRow >= 0 && landingRow < 8 && landingColumn >= 0 && landingColumn < 8
                        && board[(row + dr) * 8 + column + dc] * side < 0
                        && board[landingRow * 8 + landingColumn] == 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void unmakeMove(byte[] board, int move, int undo) {
        int from = move & 63;
        int to = (move >> 6) & 63;
        side = COMPUTER;
        if ((undo & UNDO_HUMAN_TO_MOVE) != 0) {
            side = HUMAN;
        }
        chainFrom = ((undo >> 4) & 127) - 1;
        int piece = board[to];
        if ((undo & UNDO_PROMOTED) != 0) {
            piece = piece / KING;
        }
        board[from] = (byte) piece;
        board[to] = 0;
        if (Math.abs((to >> 3) - (from >> 3)) == 2) {
            board[(from + to) / 2] = (byte) ((undo & 7) - 3);
        }
    }

    // ---- Input ----

    private static int squareAt(int x, int y) {
        int column = (x - BOARD_X) / CELL;
        int row = (y - BOARD_Y) / CELL;
        if (x < BOARD_X || y < BOARD_Y || column > 7 || row > 7) {
            return -1;
        }
        return row * 8 + column;
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

    /** Redraws every square whose piece or decoration differs from what is on screen. */
    private static void refresh(byte[] board, byte[] shownPiece, byte[] shownDeco, byte[] deco, int[] moves,
            int selected) {
        for (int square = 0; square < 64; square++) {
            deco[square] = 0;
        }
        if (selected >= 0) {
            deco[selected] = DECO_SELECTED;
            int count = generateMoves(board, moves, 0);
            for (int i = 0; i < count; i++) {
                if ((moves[i] & 63) == selected) {
                    int to = (moves[i] >> 6) & 63;
                    deco[to] = DECO_TARGET;
                }
            }
        }
        int human = 0;
        int computer = 0;
        for (int square = 0; square < 64; square++) {
            if (board[square] > 0) {
                human = human + 1;
            } else if (board[square] < 0) {
                computer = computer + 1;
            }
            if (board[square] != shownPiece[square] || deco[square] != shownDeco[square]) {
                drawSquare(square, board[square], deco[square]);
                shownPiece[square] = board[square];
                shownDeco[square] = deco[square];
            }
        }
        TftTouchShield.setTextSize(2);
        TftTouchShield.setCursor(BOARD_X, FOOTER_Y + 8);
        TftTouchShield.setTextColor(HUMAN_COLOR, TftTouchShield.BLACK);
        TftTouchShield.print("You ");
        TftTouchShield.print(human);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
        TftTouchShield.print(" CPU ");
        TftTouchShield.print(computer);
        TftTouchShield.print(" ");
    }

    private static void drawSquare(int square, int piece, int decoration) {
        int x = BOARD_X + (square & 7) * CELL;
        int y = BOARD_Y + (square >> 3) * CELL;
        int color = LIGHT;
        if (isDark(square)) {
            color = DARK;
        }
        TftTouchShield.fillRect(x, y, CELL, CELL, color);
        if (decoration == DECO_SELECTED) {
            TftTouchShield.drawRect(x, y, CELL, CELL, SELECTED);
            TftTouchShield.drawRect(x + 1, y + 1, CELL - 2, CELL - 2, SELECTED);
        }
        int cx = x + CELL / 2;
        int cy = y + CELL / 2;
        if (piece != 0) {
            int fill = HUMAN_COLOR;
            if (piece < 0) {
                fill = COMPUTER_COLOR;
            }
            TftTouchShield.fillCircle(cx, cy, 11, fill);
            TftTouchShield.drawCircle(cx, cy, 11, TftTouchShield.BLACK);
            TftTouchShield.drawCircle(cx, cy, 8, TftTouchShield.GRAY);
            if (Math.abs(piece) == KING) {
                TftTouchShield.fillCircle(cx, cy, 5, CROWN);
            }
        }
        if (decoration == DECO_TARGET) {
            TftTouchShield.fillCircle(cx, cy, 4, TARGET);
        }
    }

    private static void showStatus(String text, int color) {
        TftTouchShield.fillRect(0, 0, TftTouchShield.width(), HEADER_HEIGHT, HEADER_BACKGROUND);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(color, HEADER_BACKGROUND);
        TftTouchShield.setCursor(8, 10);
        TftTouchShield.print(text);
    }
}
