package io.github.jabrena.juno.games.chess;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.tft.TftTouchShield;
import static io.github.jabrena.juno.games.chess.ChessEngine.*;
import static io.github.jabrena.juno.games.chess.ChessRules.*;
import static io.github.jabrena.juno.games.chess.MoveGenerator.*;
import static io.github.jabrena.juno.games.chess.Controls.*;
import static io.github.jabrena.juno.games.chess.SceneRenderer.*;
import static io.github.jabrena.juno.games.chess.PieceSprites.*;
import static io.github.jabrena.juno.games.chess.PawnSprite.*;
import static io.github.jabrena.juno.games.chess.KnightSprite.*;
import static io.github.jabrena.juno.games.chess.BishopSprite.*;
import static io.github.jabrena.juno.games.chess.RookSprite.*;
import static io.github.jabrena.juno.games.chess.QueenSprite.*;
import static io.github.jabrena.juno.games.chess.KingSprite.*;

/**
 * Chess on the ELEGOO 2.8" TFT touch screen shield. An animated cover leads to a HUMAN/CPU
 * choice: in human mode you play White against the built-in engine; in CPU mode it plays both
 * colors. Checkmate and stalemate return to the cover.
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
 *
 * <p>The package separates orchestration, search, move generation and attack rules, controls,
 * rendering, interludes and the six piece bitmaps into focused collaborators.
 */
@Board({ArduinoUnoQ.class, ArduinoUnoR4WiFi.class})
public final class Chess {
    static final int COVER_TIMEOUT_MILLIS = 60_000;
    static final int SEARCH_DEPTH = 3;

    static final int PAWN = 1;
    static final int KNIGHT = 2;
    static final int BISHOP = 3;
    static final int ROOK = 4;
    static final int QUEEN = 5;
    static final int KING = 6;

    static final int WHITE = 1;
    static final int BLACK = -1;

    static final int WHITE_KING_SIDE = 1;
    static final int WHITE_QUEEN_SIDE = 2;
    static final int BLACK_KING_SIDE = 4;
    static final int BLACK_QUEEN_SIDE = 8;

    // Bits of the int returned by makeMove, besides the captured piece, castling rights and en
    // passant square it packs.
    static final int UNDO_PROMOTION = 1 << 16;
    static final int UNDO_CASTLE = 1 << 17;
    static final int UNDO_EN_PASSANT = 1 << 18;

    static final int MOVES_PER_PLY = 128;
    static final int MAX_PLY = SEARCH_DEPTH + 1;
    static final int MATE = 100000;
    static final int INFINITY = 1000000;

    // Screen layout (portrait, 240x320).
    static final int CELL = 28;
    static final int BOARD_X = 8;
    static final int BOARD_Y = 44;
    static final int FOOTER_Y = BOARD_Y + 8 * CELL + 8;
    static final int BUTTON_X = 172;
    static final int BUTTON_WIDTH = 60;
    static final int BUTTON_HEIGHT = 30;
    static final int UNDO_X = 84;
    static final int UNDO_WIDTH = 76;

    // Plies kept for UNDO; when full, the oldest full move is forgotten.
    static final int HISTORY_SIZE = 256;

    static final int LIGHT = 0xF6D6;
    static final int DARK = 0xB44C;
    static final int LIGHT_LAST = 0xF7B0;
    static final int DARK_LAST = 0xBE48;
    static final int SELECTED = 0x07E0;
    static final int TARGET = 0x0400;
    static final int CAPTURE_TARGET = 0xF800;
    static final int HEADER_BACKGROUND = 0x2945;
    static final int PIECE_SIZE = 20;
    static final int BLACK_PIECE_EDGE = 0xA514;

    // Decoration bits for a square, compared against what is on screen to redraw only changes.
    static final int DECO_LAST = 1;
    static final int DECO_SELECTED = 2;
    static final int DECO_TARGET = 4;

    // Game state; arrays live in main because Juno has no reference-typed static fields.
    static int side;
    static int castling;
    static int enPassant;
    static int whiteKing;
    static int blackKing;
    static int bestMove;
    static int lastFrom;
    static int lastTo;
    static boolean gameOver;
    static int historyCount;

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
        while (true) {
            boolean tapped = false;
            while (!tapped) {
                Interludes.cover();
                tapped = Controls.waitForTap(COVER_TIMEOUT_MILLIS);
            }
            Controls.choosePlayer();
            newGame(board, shownPiece, shownDeco, deco, moves);
            playGame(board, shownPiece, shownDeco, deco, moves, historyMoves, historyUndos);
            Delay.millis(3000);
        }
    }

    private static void playGame(byte[] board, byte[] shownPiece, byte[] shownDeco, byte[] deco, int[] moves,
            int[] historyMoves, int[] historyUndos) {
        int selected = -1;
        while (!gameOver) {
            if (Controls.autopilot || side == BLACK) {
                engineMove(board, shownPiece, shownDeco, deco, moves, historyMoves, historyUndos);
                Delay.millis(Controls.autopilot ? 650 : 120);
                continue;
            }
            if (!TftTouchShield.readTouch()) {
                Delay.millis(10);
                continue;
            }
            int x = TftTouchShield.touchX();
            int y = TftTouchShield.touchY();
            waitForRelease();

            if (y < 36) {
                Controls.autopilot = !Controls.autopilot;
                showStatus(Controls.autopilot ? "CPU plays" : "Your move", TftTouchShield.WHITE);
                continue;
            }

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
            showStatus(Controls.autopilot ? "CPU plays" : "Your move", TftTouchShield.WHITE);
                }
                continue;
            }
            int square = squareAt(x, y);
            if (square < 0) {
                continue;
            }

            if (selected >= 0 && isLegal(board, moves, selected, square)) {
                playMove(board, historyMoves, historyUndos, selected | (square << 6));
                selected = -1;
                refresh(board, shownPiece, shownDeco, deco, moves, -1);
                if (showOutcome(board, moves)) {
                    continue;
                }
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

    static void newGame(byte[] board, byte[] shownPiece, byte[] shownDeco, byte[] deco, int[] moves) {
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

    static int backRankPiece(int column) {
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

    static void playMove(byte[] board, int[] historyMoves, int[] historyUndos, int move) {
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
    static void takeBack(byte[] board, int[] historyMoves, int[] historyUndos) {
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

    static void engineMove(byte[] board, byte[] shownPiece, byte[] shownDeco, byte[] deco, int[] moves,
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
            showStatus(Controls.autopilot ? "CPU plays" : "Your move", TftTouchShield.WHITE);
        }
    }

    /** Shows check, checkmate or stalemate for the side to move; returns whether the game ended. */
    static boolean showOutcome(byte[] board, int[] moves) {
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
            showStatus(Controls.autopilot ? "Black wins" : "Checkmate: you lose", TftTouchShield.RED);
        } else {
            showStatus(Controls.autopilot ? "White wins" : "Checkmate: you win!", TftTouchShield.GREEN);
        }
        return true;
    }
}
