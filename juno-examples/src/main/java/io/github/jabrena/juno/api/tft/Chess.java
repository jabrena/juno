package io.github.jabrena.juno.api.tft;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.io.usb.BaudRate;
import io.github.jabrena.juno.api.io.usb.Serial;

/**
 * Chess against the board on the ELEGOO 2.8" TFT touch screen shield: you play White, a small
 * built-in engine plays Black.
 *
 * <p>Tap one of your pieces to select it (its legal destinations are marked), then tap a
 * destination to move. Tap {@code UNDO} to take back your last move together with the engine's
 * reply (repeatable back to the start of the game), or {@code NEW} to start over. The rules are complete apart from
 * draw claims: moves that leave your king in check are rejected, check, checkmate and stalemate are
 * detected, and castling, en passant and promotion (always to a queen) are supported. The fifty-move
 * rule, threefold repetition and insufficient material are not detected.
 *
 * <p>The engine is a {@value #SEARCH_DEPTH}-ply negamax search with alpha-beta pruning,
 * captures-first move ordering and a material plus piece-placement evaluation. Its root moves are
 * shuffled first, so equally good replies vary between games. Each engine move is also logged to
 * Serial.
 *
 * <p>Squares are numbered 0-63 from a8 (top left) to h1 (bottom right); pieces are signed bytes,
 * positive for White and negative for Black. Juno has no reference-typed static fields, so the
 * board and move lists are allocated once in {@code main} and passed down, while the recursive
 * search keeps each move's undo information in its own locals.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class Chess {
    private static final int SEARCH_DEPTH = 3;

    private static final int PAWN = 1;
    private static final int KNIGHT = 2;
    private static final int BISHOP = 3;
    private static final int ROOK = 4;
    private static final int QUEEN = 5;
    private static final int KING = 6;

    private static final int WHITE = 1;
    private static final int BLACK = -1;

    private static final int WHITE_KING_SIDE = 1;
    private static final int WHITE_QUEEN_SIDE = 2;
    private static final int BLACK_KING_SIDE = 4;
    private static final int BLACK_QUEEN_SIDE = 8;

    // Bits of the int returned by makeMove, besides the captured piece, castling rights and en
    // passant square it packs.
    private static final int UNDO_PROMOTION = 1 << 16;
    private static final int UNDO_CASTLE = 1 << 17;
    private static final int UNDO_EN_PASSANT = 1 << 18;

    private static final int MOVES_PER_PLY = 128;
    private static final int MAX_PLY = SEARCH_DEPTH + 1;
    private static final int MATE = 100000;
    private static final int INFINITY = 1000000;

    // Screen layout (portrait, 240x320).
    private static final int CELL = 28;
    private static final int BOARD_X = 8;
    private static final int BOARD_Y = 44;
    private static final int FOOTER_Y = BOARD_Y + 8 * CELL + 8;
    private static final int BUTTON_X = 172;
    private static final int BUTTON_WIDTH = 60;
    private static final int BUTTON_HEIGHT = 30;
    private static final int UNDO_X = 84;
    private static final int UNDO_WIDTH = 76;

    // Plies kept for UNDO; when full, the oldest full move is forgotten.
    private static final int HISTORY_SIZE = 256;

    private static final int LIGHT = 0xF6D6;
    private static final int DARK = 0xB44C;
    private static final int LIGHT_LAST = 0xF7B0;
    private static final int DARK_LAST = 0xBE48;
    private static final int SELECTED = 0x07E0;
    private static final int TARGET = 0x0400;
    private static final int CAPTURE_TARGET = 0xF800;
    private static final int HEADER_BACKGROUND = 0x2945;

    // Decoration bits for a square, compared against what is on screen to redraw only changes.
    private static final int DECO_LAST = 1;
    private static final int DECO_SELECTED = 2;
    private static final int DECO_TARGET = 4;

    // Game state; arrays live in main because Juno has no reference-typed static fields.
    private static int side;
    private static int castling;
    private static int enPassant;
    private static int whiteKing;
    private static int blackKing;
    private static int bestMove;
    private static int lastFrom;
    private static int lastTo;
    private static boolean gameOver;
    private static int historyCount;

    private Chess() {
    }

    public static void main(String[] args) {
        byte[] board = new byte[64];
        byte[] shownPiece = new byte[64];
        byte[] shownDeco = new byte[64];
        byte[] deco = new byte[64];
        int[] moves = new int[MOVES_PER_PLY * (MAX_PLY + 1)];
        int[] historyMoves = new int[HISTORY_SIZE];
        int[] historyUndos = new int[HISTORY_SIZE];

        Serial.begin(BaudRate.BAUD_115200);
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

            if (y >= FOOTER_Y && x >= BUTTON_X) {
                newGame(board, shownPiece, shownDeco, deco, moves);
                selected = -1;
                continue;
            }
            if (y >= FOOTER_Y && x >= UNDO_X && x < UNDO_X + UNDO_WIDTH) {
                takeBack(board, historyMoves, historyUndos);
                selected = -1;
                refresh(board, shownPiece, shownDeco, deco, moves, -1);
                if (!showOutcome(board, moves)) {
                    showStatus("Your move", TftTouchShield.WHITE);
                }
                continue;
            }
            int square = squareAt(x, y);
            if (square < 0 || gameOver) {
                continue;
            }

            if (selected >= 0 && isLegal(board, moves, selected, square)) {
                playMove(board, historyMoves, historyUndos, selected | (square << 6));
                selected = -1;
                refresh(board, shownPiece, shownDeco, deco, moves, -1);
                if (showOutcome(board, moves)) {
                    continue;
                }
                engineMove(board, shownPiece, shownDeco, deco, moves, historyMoves, historyUndos);
                continue;
            }
            if (board[square] > 0 && square != selected) {
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
            board[square] = 0;
            shownDeco[square] = -1;
        }
        for (int column = 0; column < 8; column++) {
            board[8 + column] = (byte) -PAWN;
            board[48 + column] = (byte) PAWN;
            int piece = backRankPiece(column);
            board[column] = (byte) -piece;
            board[56 + column] = (byte) piece;
        }
        side = WHITE;
        castling = WHITE_KING_SIDE | WHITE_QUEEN_SIDE | BLACK_KING_SIDE | BLACK_QUEEN_SIDE;
        enPassant = -1;
        whiteKing = 60;
        blackKing = 4;
        lastFrom = -1;
        lastTo = -1;
        gameOver = false;
        historyCount = 0;

        TftTouchShield.fillScreen(TftTouchShield.BLACK);
        TftTouchShield.fillRect(0, 0, TftTouchShield.width(), 36, HEADER_BACKGROUND);
        showStatus("Your move", TftTouchShield.WHITE);
        TftTouchShield.fillRect(BUTTON_X, FOOTER_Y, BUTTON_WIDTH, BUTTON_HEIGHT, TftTouchShield.GRAY);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.GRAY);
        TftTouchShield.setCursor(BUTTON_X + 12, FOOTER_Y + 8);
        TftTouchShield.print("NEW");
        TftTouchShield.fillRect(UNDO_X, FOOTER_Y, UNDO_WIDTH, BUTTON_HEIGHT, TftTouchShield.GRAY);
        TftTouchShield.setCursor(UNDO_X + 14, FOOTER_Y + 8);
        TftTouchShield.print("UNDO");
        showLastMove();
        refresh(board, shownPiece, shownDeco, deco, moves, -1);
    }

    private static int backRankPiece(int column) {
        if (column == 0 || column == 7) {
            return ROOK;
        }
        if (column == 1 || column == 6) {
            return KNIGHT;
        }
        if (column == 2 || column == 5) {
            return BISHOP;
        }
        if (column == 3) {
            return QUEEN;
        }
        return KING;
    }

    private static void playMove(byte[] board, int[] historyMoves, int[] historyUndos, int move) {
        if (historyCount == HISTORY_SIZE) {
            for (int i = 2; i < HISTORY_SIZE; i++) {
                historyMoves[i - 2] = historyMoves[i];
                historyUndos[i - 2] = historyUndos[i];
            }
            historyCount = HISTORY_SIZE - 2;
        }
        historyUndos[historyCount] = makeMove(board, move);
        historyMoves[historyCount] = move;
        historyCount = historyCount + 1;
        lastFrom = move & 63;
        lastTo = (move >> 6) & 63;
        showLastMove();
    }

    /**
     * Takes back plies until it is White's turn again: normally the engine's reply and your move
     * before it, or just your move when the game ended on it.
     */
    private static void takeBack(byte[] board, int[] historyMoves, int[] historyUndos) {
        if (historyCount == 0) {
            return;
        }
        historyCount = historyCount - 1;
        unmakeMove(board, historyMoves[historyCount], historyUndos[historyCount]);
        if (side != WHITE && historyCount > 0) {
            historyCount = historyCount - 1;
            unmakeMove(board, historyMoves[historyCount], historyUndos[historyCount]);
        }
        gameOver = false;
        lastFrom = -1;
        lastTo = -1;
        if (historyCount > 0) {
            int previous = historyMoves[historyCount - 1];
            lastFrom = previous & 63;
            lastTo = (previous >> 6) & 63;
        }
        showLastMove();
    }

    private static void engineMove(byte[] board, byte[] shownPiece, byte[] shownDeco, byte[] deco, int[] moves,
            int[] historyMoves, int[] historyUndos) {
        showStatus("Thinking...", TftTouchShield.YELLOW);
        int started = Clock.millis();
        Random.seed(Clock.micros());
        bestMove = -1;
        negamax(board, moves, SEARCH_DEPTH, 0, -INFINITY, INFINITY);
        if (bestMove < 0) {
            return;
        }
        Serial.print("Engine: ");
        printSquare(bestMove & 63);
        printSquare((bestMove >> 6) & 63);
        Serial.print(" in ");
        Serial.print(Clock.millis() - started);
        Serial.println(" ms");

        playMove(board, historyMoves, historyUndos, bestMove);
        refresh(board, shownPiece, shownDeco, deco, moves, -1);
        if (!showOutcome(board, moves)) {
            showStatus("Your move", TftTouchShield.WHITE);
        }
    }

    /** Shows check, checkmate or stalemate for the side to move; returns whether the game ended. */
    private static boolean showOutcome(byte[] board, int[] moves) {
        boolean check = inCheck(board, side);
        if (countLegalMoves(board, moves) > 0) {
            if (check) {
                showStatus("Check!", TftTouchShield.ORANGE);
            }
            return false;
        }
        gameOver = true;
        if (!check) {
            showStatus("Stalemate", TftTouchShield.CYAN);
        } else if (side == WHITE) {
            showStatus("Checkmate: you lose", TftTouchShield.RED);
        } else {
            showStatus("Checkmate: you win!", TftTouchShield.GREEN);
        }
        return true;
    }

    private static boolean isLegal(byte[] board, int[] moves, int from, int to) {
        int count = generateMoves(board, moves, 0);
        for (int i = 0; i < count; i++) {
            int move = moves[i];
            if ((move & 63) == from && ((move >> 6) & 63) == to && isLegalMove(board, move)) {
                return true;
            }
        }
        return false;
    }

    private static int countLegalMoves(byte[] board, int[] moves) {
        int count = generateMoves(board, moves, 0);
        int legal = 0;
        for (int i = 0; i < count; i++) {
            if (isLegalMove(board, moves[i])) {
                legal = legal + 1;
            }
        }
        return legal;
    }

    private static boolean isLegalMove(byte[] board, int move) {
        int undo = makeMove(board, move);
        boolean legal = !inCheck(board, -side);
        unmakeMove(board, move, undo);
        return legal;
    }

    // ---- Search ----

    private static int negamax(byte[] board, int[] moves, int depth, int ply, int alpha, int beta) {
        if (depth == 0) {
            return evaluate(board) * side;
        }
        int base = ply * MOVES_PER_PLY;
        int count = generateMoves(board, moves, base);
        if (ply == 0) {
            shuffle(moves, base, count);
        }
        orderCapturesFirst(board, moves, base, count);

        int legal = 0;
        for (int i = 0; i < count; i++) {
            int move = moves[base + i];
            int undo = makeMove(board, move);
            if (inCheck(board, -side)) {
                unmakeMove(board, move, undo);
                continue;
            }
            legal = legal + 1;
            int score = -negamax(board, moves, depth - 1, ply + 1, -beta, -alpha);
            unmakeMove(board, move, undo);
            if (score > alpha) {
                alpha = score;
                if (ply == 0) {
                    bestMove = move;
                }
            }
            if (alpha >= beta) {
                return alpha;
            }
        }
        if (legal == 0) {
            if (inCheck(board, side)) {
                return -MATE + ply;
            }
            return 0;
        }
        return alpha;
    }

    private static void shuffle(int[] moves, int base, int count) {
        for (int i = count - 1; i > 0; i--) {
            int j = Random.nextInt(i + 1);
            int swap = moves[base + i];
            moves[base + i] = moves[base + j];
            moves[base + j] = swap;
        }
    }

    // Stable partition: moves that capture something go first, which lets alpha-beta cut sooner.
    private static void orderCapturesFirst(byte[] board, int[] moves, int base, int count) {
        int next = 0;
        for (int i = 0; i < count; i++) {
            int move = moves[base + i];
            if (board[(move >> 6) & 63] != 0) {
                for (int j = i; j > next; j--) {
                    moves[base + j] = moves[base + j - 1];
                }
                moves[base + next] = move;
                next = next + 1;
            }
        }
    }

    /** Material plus piece placement, from White's point of view. */
    private static int evaluate(byte[] board) {
        int score = 0;
        for (int square = 0; square < 64; square++) {
            int piece = board[square];
            if (piece == 0) {
                continue;
            }
            int type = Math.abs(piece);
            int row = square >> 3;
            int column = square & 7;
            int center = 7 - Math.max(Math.abs(2 * column - 7), Math.abs(2 * row - 7));
            int value = pieceValue(type);
            if (type == PAWN) {
                int advance = 6 - row;
                if (piece < 0) {
                    advance = row - 1;
                }
                value = value + advance * 6 + center * 2;
            } else if (type == KNIGHT) {
                value = value + center * 5;
            } else if (type == BISHOP) {
                value = value + center * 3;
            } else if (type == QUEEN) {
                value = value + center;
            } else if (type == KING) {
                value = value - center * 3;
            }
            if (piece > 0) {
                score = score + value;
            } else {
                score = score - value;
            }
        }
        return score;
    }

    private static int pieceValue(int type) {
        if (type == PAWN) {
            return 100;
        }
        if (type == KNIGHT) {
            return 320;
        }
        if (type == BISHOP) {
            return 330;
        }
        if (type == ROOK) {
            return 500;
        }
        if (type == QUEEN) {
            return 900;
        }
        return 0;
    }

    // ---- Move generation ----

    /** Writes the side to move's pseudo-legal moves (from | to << 6) from moves[base], returning the count. */
    private static int generateMoves(byte[] board, int[] moves, int base) {
        int count = base;
        for (int from = 0; from < 64; from++) {
            int piece = board[from] * side;
            if (piece <= 0) {
                continue;
            }
            int row = from >> 3;
            int column = from & 7;
            if (piece == PAWN) {
                count = pawnMoves(board, moves, count, from, row, column);
            } else if (piece == KNIGHT) {
                for (int dr = -2; dr <= 2; dr++) {
                    for (int dc = -2; dc <= 2; dc++) {
                        if (dr != 0 && dc != 0 && Math.abs(dr) != Math.abs(dc)) {
                            count = addStep(board, moves, count, from, row + dr, column + dc);
                        }
                    }
                }
            } else if (piece == KING) {
                for (int dr = -1; dr <= 1; dr++) {
                    for (int dc = -1; dc <= 1; dc++) {
                        if (dr != 0 || dc != 0) {
                            count = addStep(board, moves, count, from, row + dr, column + dc);
                        }
                    }
                }
                count = castlingMoves(board, moves, count);
            } else {
                for (int dr = -1; dr <= 1; dr++) {
                    for (int dc = -1; dc <= 1; dc++) {
                        boolean straight = dr == 0 || dc == 0;
                        if ((dr != 0 || dc != 0)
                                && (piece == QUEEN || (piece == ROOK && straight) || (piece == BISHOP && !straight))) {
                            count = addRay(board, moves, count, from, row, column, dr, dc);
                        }
                    }
                }
            }
        }
        return count - base;
    }

    private static int pawnMoves(byte[] board, int[] moves, int count, int from, int row, int column) {
        int direction = -side;
        int next = row + direction;
        if (next < 0 || next > 7) {
            return count;
        }
        if (board[next * 8 + column] == 0) {
            moves[count] = from | ((next * 8 + column) << 6);
            count = count + 1;
            int startRow = 6;
            if (side == BLACK) {
                startRow = 1;
            }
            int jump = (row + 2 * direction) * 8 + column;
            if (row == startRow && board[jump] == 0) {
                moves[count] = from | (jump << 6);
                count = count + 1;
            }
        }
        for (int dc = -1; dc <= 1; dc = dc + 2) {
            int target = column + dc;
            if (target >= 0 && target < 8) {
                int to = next * 8 + target;
                if (board[to] * side < 0 || to == enPassant) {
                    moves[count] = from | (to << 6);
                    count = count + 1;
                }
            }
        }
        return count;
    }

    private static int addStep(byte[] board, int[] moves, int count, int from, int row, int column) {
        if (row < 0 || row > 7 || column < 0 || column > 7) {
            return count;
        }
        int to = row * 8 + column;
        if (board[to] * side > 0) {
            return count;
        }
        moves[count] = from | (to << 6);
        return count + 1;
    }

    private static int addRay(byte[] board, int[] moves, int count, int from, int row, int column, int dr, int dc) {
        int r = row + dr;
        int c = column + dc;
        while (r >= 0 && r <= 7 && c >= 0 && c <= 7) {
            int to = r * 8 + c;
            int target = board[to] * side;
            if (target > 0) {
                return count;
            }
            moves[count] = from | (to << 6);
            count = count + 1;
            if (target < 0) {
                return count;
            }
            r = r + dr;
            c = c + dc;
        }
        return count;
    }

    private static int castlingMoves(byte[] board, int[] moves, int count) {
        int king = 60;
        int kingSide = WHITE_KING_SIDE;
        int queenSide = WHITE_QUEEN_SIDE;
        if (side == BLACK) {
            king = 4;
            kingSide = BLACK_KING_SIDE;
            queenSide = BLACK_QUEEN_SIDE;
        }
        if (board[king] != KING * side || isAttacked(board, king, -side)) {
            return count;
        }
        if ((castling & kingSide) != 0 && board[king + 1] == 0 && board[king + 2] == 0
                && board[king + 3] == ROOK * side
                && !isAttacked(board, king + 1, -side) && !isAttacked(board, king + 2, -side)) {
            moves[count] = king | ((king + 2) << 6);
            count = count + 1;
        }
        if ((castling & queenSide) != 0 && board[king - 1] == 0 && board[king - 2] == 0 && board[king - 3] == 0
                && board[king - 4] == ROOK * side
                && !isAttacked(board, king - 1, -side) && !isAttacked(board, king - 2, -side)) {
            moves[count] = king | ((king - 2) << 6);
            count = count + 1;
        }
        return count;
    }

    private static boolean inCheck(byte[] board, int color) {
        int king = whiteKing;
        if (color == BLACK) {
            king = blackKing;
        }
        return isAttacked(board, king, -color);
    }

    /** Whether {@code by}'s pieces attack {@code square}. */
    private static boolean isAttacked(byte[] board, int square, int by) {
        int row = square >> 3;
        int column = square & 7;
        // A pawn attacks towards the opponent, so an attacking pawn sits one row "behind" the square.
        int pawnRow = row + by;
        if (pawnRow >= 0 && pawnRow <= 7) {
            if (column > 0 && board[pawnRow * 8 + column - 1] == PAWN * by) {
                return true;
            }
            if (column < 7 && board[pawnRow * 8 + column + 1] == PAWN * by) {
                return true;
            }
        }
        for (int dr = -2; dr <= 2; dr++) {
            for (int dc = -2; dc <= 2; dc++) {
                if (dr != 0 && dc != 0 && Math.abs(dr) != Math.abs(dc)
                        && pieceAt(board, row + dr, column + dc) == KNIGHT * by) {
                    return true;
                }
            }
        }
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) {
                    continue;
                }
                if (pieceAt(board, row + dr, column + dc) == KING * by) {
                    return true;
                }
                int slider = ROOK;
                if (dr != 0 && dc != 0) {
                    slider = BISHOP;
                }
                int r = row + dr;
                int c = column + dc;
                while (r >= 0 && r <= 7 && c >= 0 && c <= 7) {
                    int piece = board[r * 8 + c];
                    if (piece != 0) {
                        if (piece == slider * by || piece == QUEEN * by) {
                            return true;
                        }
                        break;
                    }
                    r = r + dr;
                    c = c + dc;
                }
            }
        }
        return false;
    }

    private static int pieceAt(byte[] board, int row, int column) {
        if (row < 0 || row > 7 || column < 0 || column > 7) {
            return 0;
        }
        return board[row * 8 + column];
    }

    // ---- Make / unmake ----

    /**
     * Plays {@code move} and switches sides, returning the information {@link #unmakeMove} needs:
     * the captured piece + 8 (bits 0-3), the previous castling rights (4-7), the previous en passant
     * square + 1 (8-14), and the UNDO_* flags.
     */
    private static int makeMove(byte[] board, int move) {
        int from = move & 63;
        int to = (move >> 6) & 63;
        int piece = board[from];
        int captured = board[to];
        int type = Math.abs(piece);
        int undo = (castling << 4) | ((enPassant + 1) << 8);

        board[to] = (byte) piece;
        board[from] = 0;
        int previousEnPassant = enPassant;
        enPassant = -1;
        if (type == PAWN) {
            if (to == previousEnPassant && captured == 0) {
                int capturedSquare = (from & ~7) | (to & 7);
                captured = board[capturedSquare];
                board[capturedSquare] = 0;
                undo = undo | UNDO_EN_PASSANT;
            }
            if (to - from == 16 || from - to == 16) {
                enPassant = (from + to) / 2;
            }
            if (to < 8 || to >= 56) {
                board[to] = (byte) (QUEEN * side);
                undo = undo | UNDO_PROMOTION;
            }
        } else if (type == KING) {
            if (piece > 0) {
                whiteKing = to;
            } else {
                blackKing = to;
            }
            if (to - from == 2) {
                board[from + 1] = board[from + 3];
                board[from + 3] = 0;
                undo = undo | UNDO_CASTLE;
            } else if (from - to == 2) {
                board[from - 1] = board[from - 4];
                board[from - 4] = 0;
                undo = undo | UNDO_CASTLE;
            }
        }
        castling = castling & castlingKept(from) & castlingKept(to);
        side = -side;
        return undo | (captured + 8);
    }

    private static int castlingKept(int square) {
        if (square == 60) {
            return ~(WHITE_KING_SIDE | WHITE_QUEEN_SIDE);
        }
        if (square == 63) {
            return ~WHITE_KING_SIDE;
        }
        if (square == 56) {
            return ~WHITE_QUEEN_SIDE;
        }
        if (square == 4) {
            return ~(BLACK_KING_SIDE | BLACK_QUEEN_SIDE);
        }
        if (square == 7) {
            return ~BLACK_KING_SIDE;
        }
        if (square == 0) {
            return ~BLACK_QUEEN_SIDE;
        }
        return ~0;
    }

    private static void unmakeMove(byte[] board, int move, int undo) {
        side = -side;
        int from = move & 63;
        int to = (move >> 6) & 63;
        int piece = board[to];
        int captured = (undo & 15) - 8;
        castling = (undo >> 4) & 15;
        enPassant = ((undo >> 8) & 127) - 1;

        if ((undo & UNDO_PROMOTION) != 0) {
            piece = PAWN * side;
        }
        board[from] = (byte) piece;
        if ((undo & UNDO_EN_PASSANT) != 0) {
            board[to] = 0;
            board[(from & ~7) | (to & 7)] = (byte) captured;
        } else {
            board[to] = (byte) captured;
        }
        if (Math.abs(piece) == KING) {
            if (piece > 0) {
                whiteKing = from;
            } else {
                blackKing = from;
            }
            if ((undo & UNDO_CASTLE) != 0) {
                if (to > from) {
                    board[from + 3] = board[from + 1];
                    board[from + 1] = 0;
                } else {
                    board[from - 4] = board[from - 1];
                    board[from - 1] = 0;
                }
            }
        }
    }

    // ---- Drawing ----

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

    /** Redraws every square whose piece or decoration differs from what is on screen. */
    private static void refresh(byte[] board, byte[] shownPiece, byte[] shownDeco, byte[] deco, int[] moves,
            int selected) {
        for (int square = 0; square < 64; square++) {
            deco[square] = 0;
        }
        if (lastFrom >= 0) {
            deco[lastFrom] = DECO_LAST;
            deco[lastTo] = DECO_LAST;
        }
        if (selected >= 0) {
            deco[selected] = (byte) (deco[selected] | DECO_SELECTED);
            int count = generateMoves(board, moves, 0);
            for (int i = 0; i < count; i++) {
                int move = moves[i];
                if ((move & 63) == selected && isLegalMove(board, move)) {
                    int to = (move >> 6) & 63;
                    deco[to] = (byte) (deco[to] | DECO_TARGET);
                }
            }
        }
        for (int square = 0; square < 64; square++) {
            if (board[square] != shownPiece[square] || deco[square] != shownDeco[square]) {
                drawSquare(square, board[square], deco[square]);
                shownPiece[square] = board[square];
                shownDeco[square] = deco[square];
            }
        }
    }

    private static void drawSquare(int square, int piece, int decoration) {
        int row = square >> 3;
        int column = square & 7;
        int x = BOARD_X + column * CELL;
        int y = BOARD_Y + row * CELL;
        boolean light = (row + column) % 2 == 0;
        int color = DARK;
        if (light) {
            color = LIGHT;
        }
        if ((decoration & DECO_LAST) != 0) {
            color = DARK_LAST;
            if (light) {
                color = LIGHT_LAST;
            }
        }
        TftTouchShield.fillRect(x, y, CELL, CELL, color);
        if ((decoration & DECO_SELECTED) != 0) {
            TftTouchShield.drawRect(x, y, CELL, CELL, SELECTED);
            TftTouchShield.drawRect(x + 1, y + 1, CELL - 2, CELL - 2, SELECTED);
        }
        if (piece != 0) {
            drawPiece(x + (CELL - PIECE_SIZE) / 2, y + (CELL - PIECE_SIZE) / 2, piece, color);
        }
        if ((decoration & DECO_TARGET) != 0) {
            if (piece != 0) {
                TftTouchShield.drawRect(x, y, CELL, CELL, CAPTURE_TARGET);
                TftTouchShield.drawRect(x + 1, y + 1, CELL - 2, CELL - 2, CAPTURE_TARGET);
            } else {
                TftTouchShield.fillCircle(x + CELL / 2, y + CELL / 2, 4, TARGET);
            }
        }
    }

    // Pieces are 20x20 silhouettes shaped like the Unicode chess symbols (U+2654-U+265F). Each
    // piece has a shape mask and a detail mask per row (bit n = column n); the outline is every shape
    // pixel with an empty neighbour, computed while drawing. White pieces are white with a black
    // outline, Black pieces are black with a light gray outline and details.
    private static final int PIECE_SIZE = 20;
    private static final int BLACK_PIECE_EDGE = 0xA514;

    private static void drawPiece(int x, int y, int piece, int background) {
        int type = Math.abs(piece);
        int fill = TftTouchShield.WHITE;
        int ink = TftTouchShield.BLACK;
        if (piece < 0) {
            fill = TftTouchShield.BLACK;
            ink = BLACK_PIECE_EDGE;
        }
        if (!TftTouchShield.beginPixels(x, y, PIECE_SIZE, PIECE_SIZE)) {
            return;
        }
        int above = 0;
        int shape = shapeRow(type, 0);
        for (int row = 0; row < PIECE_SIZE; row++) {
            int below = 0;
            if (row + 1 < PIECE_SIZE) {
                below = shapeRow(type, row + 1);
            }
            int interior = shape & above & below & (shape << 1) & (shape >> 1);
            int outline = (shape & ~interior) | detailRow(type, row);
            for (int column = 0; column < PIECE_SIZE; column++) {
                int color = background;
                if (((outline >> column) & 1) != 0) {
                    color = ink;
                } else if (((shape >> column) & 1) != 0) {
                    color = fill;
                }
                TftTouchShield.pushPixel(color);
            }
            above = shape;
            shape = below;
        }
    }

    private static int shapeRow(int type, int row) {
        if (type == PAWN) {
            return pawnShape(row);
        }
        if (type == KNIGHT) {
            return knightShape(row);
        }
        if (type == BISHOP) {
            return bishopShape(row);
        }
        if (type == ROOK) {
            return rookShape(row);
        }
        if (type == QUEEN) {
            return queenShape(row);
        }
        return kingShape(row);
    }

    private static int detailRow(int type, int row) {
        if (type == PAWN) {
            return pawnDetail(row);
        }
        if (type == KNIGHT) {
            return knightDetail(row);
        }
        if (type == BISHOP) {
            return bishopDetail(row);
        }
        if (type == ROOK) {
            return rookDetail(row);
        }
        if (type == QUEEN) {
            return queenDetail(row);
        }
        return kingDetail(row);
    }

    private static int pawnShape(int row) {
        if (row == 3) {
            return 0x00F00;
        }
        if (row == 4) {
            return 0x01F80;
        }
        if (row == 5) {
            return 0x01F80;
        }
        if (row == 6) {
            return 0x01F80;
        }
        if (row == 7) {
            return 0x00F00;
        }
        if (row == 8) {
            return 0x03FC0;
        }
        if (row == 9) {
            return 0x01F80;
        }
        if (row == 10) {
            return 0x00F00;
        }
        if (row == 11) {
            return 0x00F00;
        }
        if (row == 12) {
            return 0x01F80;
        }
        if (row == 13) {
            return 0x03FC0;
        }
        if (row == 14) {
            return 0x07FE0;
        }
        if (row == 15) {
            return 0x0FFF0;
        }
        if (row == 16) {
            return 0x1FFF8;
        }
        if (row == 17) {
            return 0x1FFF8;
        }
        return 0;
    }

    private static int pawnDetail(int row) {
        if (row == 8) {
            return 0x03FC0;
        }
        return 0;
    }

    private static int knightShape(int row) {
        if (row == 1) {
            return 0x00D00;
        }
        if (row == 2) {
            return 0x01F80;
        }
        if (row == 3) {
            return 0x03FC0;
        }
        if (row == 4) {
            return 0x07FE0;
        }
        if (row == 5) {
            return 0x07FF0;
        }
        if (row == 6) {
            return 0x07FF8;
        }
        if (row == 7) {
            return 0x07FFC;
        }
        if (row == 8) {
            return 0x07FFC;
        }
        if (row == 9) {
            return 0x07F78;
        }
        if (row == 10) {
            return 0x0FF80;
        }
        if (row == 11) {
            return 0x0FFC0;
        }
        if (row == 12) {
            return 0x0FFE0;
        }
        if (row == 13) {
            return 0x0FFE0;
        }
        if (row == 14) {
            return 0x0FFE0;
        }
        if (row == 15) {
            return 0x0FFF0;
        }
        if (row == 16) {
            return 0x1FFF8;
        }
        if (row == 17) {
            return 0x1FFF8;
        }
        return 0;
    }

    private static int knightDetail(int row) {
        if (row == 4) {
            return 0x00100;
        }
        if (row == 8) {
            return 0x00004;
        }
        return 0;
    }

    private static int bishopShape(int row) {
        if (row == 1) {
            return 0x00600;
        }
        if (row == 2) {
            return 0x00F00;
        }
        if (row == 3) {
            return 0x00600;
        }
        if (row == 4) {
            return 0x00F00;
        }
        if (row == 5) {
            return 0x01F80;
        }
        if (row == 6) {
            return 0x03FC0;
        }
        if (row == 7) {
            return 0x03FC0;
        }
        if (row == 8) {
            return 0x03FC0;
        }
        if (row == 9) {
            return 0x01F80;
        }
        if (row == 10) {
            return 0x00F00;
        }
        if (row == 11) {
            return 0x01F80;
        }
        if (row == 12) {
            return 0x00F00;
        }
        if (row == 13) {
            return 0x00F00;
        }
        if (row == 14) {
            return 0x01F80;
        }
        if (row == 15) {
            return 0x07FE0;
        }
        if (row == 16) {
            return 0x1FFF8;
        }
        if (row == 17) {
            return 0x1FFF8;
        }
        return 0;
    }

    private static int bishopDetail(int row) {
        if (row == 5) {
            return 0x01000;
        }
        if (row == 6) {
            return 0x00800;
        }
        if (row == 7) {
            return 0x00400;
        }
        if (row == 11) {
            return 0x01F80;
        }
        return 0;
    }

    private static int rookShape(int row) {
        if (row == 1) {
            return 0x1CF38;
        }
        if (row == 2) {
            return 0x1CF38;
        }
        if (row == 3) {
            return 0x1FFF8;
        }
        if (row == 4) {
            return 0x1FFF8;
        }
        if (row == 5) {
            return 0x0FFF0;
        }
        if (row == 6) {
            return 0x07FE0;
        }
        if (row == 7) {
            return 0x07FE0;
        }
        if (row == 8) {
            return 0x07FE0;
        }
        if (row == 9) {
            return 0x07FE0;
        }
        if (row == 10) {
            return 0x07FE0;
        }
        if (row == 11) {
            return 0x07FE0;
        }
        if (row == 12) {
            return 0x07FE0;
        }
        if (row == 13) {
            return 0x0FFF0;
        }
        if (row == 14) {
            return 0x1FFF8;
        }
        if (row == 15) {
            return 0x3FFFC;
        }
        if (row == 16) {
            return 0x3FFFC;
        }
        if (row == 17) {
            return 0x3FFFC;
        }
        return 0;
    }

    private static int rookDetail(int row) {
        if (row == 5) {
            return 0x0FFF0;
        }
        if (row == 13) {
            return 0x0FFF0;
        }
        return 0;
    }

    private static int queenShape(int row) {
        if (row == 0) {
            return 0x20604;
        }
        if (row == 1) {
            return 0x70F0E;
        }
        if (row == 2) {
            return 0x20604;
        }
        if (row == 3) {
            return 0x3060C;
        }
        if (row == 4) {
            return 0x38F1C;
        }
        if (row == 5) {
            return 0x3DFBC;
        }
        if (row == 6) {
            return 0x1FFF8;
        }
        if (row == 7) {
            return 0x1FFF8;
        }
        if (row == 8) {
            return 0x0FFF0;
        }
        if (row == 9) {
            return 0x0FFF0;
        }
        if (row == 10) {
            return 0x07FE0;
        }
        if (row == 11) {
            return 0x07FE0;
        }
        if (row == 12) {
            return 0x0FFF0;
        }
        if (row == 13) {
            return 0x0FFF0;
        }
        if (row == 14) {
            return 0x07FE0;
        }
        if (row == 15) {
            return 0x0FFF0;
        }
        if (row == 16) {
            return 0x1FFF8;
        }
        if (row == 17) {
            return 0x3FFFC;
        }
        if (row == 18) {
            return 0x3FFFC;
        }
        return 0;
    }

    private static int queenDetail(int row) {
        if (row == 9) {
            return 0x0FFF0;
        }
        if (row == 13) {
            return 0x0FFF0;
        }
        return 0;
    }

    private static int kingShape(int row) {
        if (row == 0) {
            return 0x00600;
        }
        if (row == 1) {
            return 0x00F00;
        }
        if (row == 2) {
            return 0x00600;
        }
        if (row == 3) {
            return 0x1C638;
        }
        if (row == 4) {
            return 0x3EF7C;
        }
        if (row == 5) {
            return 0x7FFFE;
        }
        if (row == 6) {
            return 0x7FFFE;
        }
        if (row == 7) {
            return 0x7FFFE;
        }
        if (row == 8) {
            return 0x3FFFC;
        }
        if (row == 9) {
            return 0x3FFFC;
        }
        if (row == 10) {
            return 0x1FFF8;
        }
        if (row == 11) {
            return 0x1FFF8;
        }
        if (row == 12) {
            return 0x0FFF0;
        }
        if (row == 13) {
            return 0x0FFF0;
        }
        if (row == 14) {
            return 0x0FFF0;
        }
        if (row == 15) {
            return 0x1FFF8;
        }
        if (row == 16) {
            return 0x1FFF8;
        }
        if (row == 17) {
            return 0x3FFFC;
        }
        if (row == 18) {
            return 0x3FFFC;
        }
        return 0;
    }

    private static int kingDetail(int row) {
        if (row == 5) {
            return 0x00400;
        }
        if (row == 6) {
            return 0x00400;
        }
        if (row == 7) {
            return 0x00400;
        }
        if (row == 8) {
            return 0x00400;
        }
        if (row == 9) {
            return 0x00400;
        }
        if (row == 10) {
            return 0x00400;
        }
        if (row == 13) {
            return 0x0FFF0;
        }
        return 0;
    }

    private static void showStatus(String text, int color) {
        TftTouchShield.fillRect(0, 0, TftTouchShield.width(), 36, HEADER_BACKGROUND);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(color, HEADER_BACKGROUND);
        TftTouchShield.setCursor(8, 10);
        TftTouchShield.print(text);
    }

    private static void showLastMove() {
        TftTouchShield.fillRect(0, FOOTER_Y, UNDO_X - 4, BUTTON_HEIGHT, TftTouchShield.BLACK);
        if (lastFrom < 0) {
            return;
        }
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
        TftTouchShield.setCursor(BOARD_X, FOOTER_Y + 8);
        drawSquareName(lastFrom);
        drawSquareName(lastTo);
    }

    private static void drawSquareName(int square) {
        TftTouchShield.print(fileName(square & 7));
        TftTouchShield.print(8 - (square >> 3));
    }

    private static void printSquare(int square) {
        Serial.print(fileName(square & 7));
        Serial.print(8 - (square >> 3));
    }

    private static String fileName(int column) {
        if (column == 0) {
            return "a";
        }
        if (column == 1) {
            return "b";
        }
        if (column == 2) {
            return "c";
        }
        if (column == 3) {
            return "d";
        }
        if (column == 4) {
            return "e";
        }
        if (column == 5) {
            return "f";
        }
        if (column == 6) {
            return "g";
        }
        return "h";
    }
}
