package io.github.jabrena.juno.games.chess;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.serial.Serial;

import static io.github.jabrena.juno.games.chess.Chess.BOARD_X;
import static io.github.jabrena.juno.games.chess.Chess.BOARD_Y;
import static io.github.jabrena.juno.games.chess.Chess.BUTTON_HEIGHT;
import static io.github.jabrena.juno.games.chess.Chess.CAPTURE_TARGET;
import static io.github.jabrena.juno.games.chess.Chess.CELL;
import static io.github.jabrena.juno.games.chess.Chess.DARK;
import static io.github.jabrena.juno.games.chess.Chess.DARK_LAST;
import static io.github.jabrena.juno.games.chess.Chess.DECO_LAST;
import static io.github.jabrena.juno.games.chess.Chess.DECO_SELECTED;
import static io.github.jabrena.juno.games.chess.Chess.DECO_TARGET;
import static io.github.jabrena.juno.games.chess.Chess.FOOTER_Y;
import static io.github.jabrena.juno.games.chess.Chess.HEADER_BACKGROUND;
import static io.github.jabrena.juno.games.chess.Chess.LIGHT;
import static io.github.jabrena.juno.games.chess.Chess.LIGHT_LAST;
import static io.github.jabrena.juno.games.chess.Chess.SELECTED;
import static io.github.jabrena.juno.games.chess.Chess.TARGET;
import static io.github.jabrena.juno.games.chess.Chess.UNDO_X;
import static io.github.jabrena.juno.games.chess.Chess.lastFrom;
import static io.github.jabrena.juno.games.chess.Chess.lastTo;
import static io.github.jabrena.juno.games.chess.ChessEngine.isLegalMove;
import static io.github.jabrena.juno.games.chess.MoveGenerator.generateMoves;

final class SceneRenderer {
    private SceneRenderer() {
    }

    static void refresh(TftTouchShield tft, byte[] board, byte[] shownPiece, byte[] shownDeco, byte[] deco, int[] moves,
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
                drawSquare(tft, square, board[square], deco[square]);
                shownPiece[square] = board[square];
                shownDeco[square] = deco[square];
            }
        }
    }

    static void drawSquare(TftTouchShield tft, int square, int piece, int decoration) {
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
        tft.fillRect(x, y, CELL, CELL, color);
        if ((decoration & DECO_SELECTED) != 0) {
            tft.drawRect(x, y, CELL, CELL, SELECTED);
            tft.drawRect(x + 1, y + 1, CELL - 2, CELL - 2, SELECTED);
        }
        if (piece != 0) {
            ChessPieceRenderer.draw(tft, x + (CELL - ChessPieceRenderer.PIECE_SIZE) / 2,
                    y + (CELL - ChessPieceRenderer.PIECE_SIZE) / 2, piece, color);
        }
        if ((decoration & DECO_TARGET) != 0) {
            if (piece != 0) {
                tft.drawRect(x, y, CELL, CELL, CAPTURE_TARGET);
                tft.drawRect(x + 1, y + 1, CELL - 2, CELL - 2, CAPTURE_TARGET);
            } else {
                tft.fillCircle(x + CELL / 2, y + CELL / 2, 4, TARGET);
            }
        }
    }

    static void showStatus(TftTouchShield tft, String text, int color) {
        tft.fillRect(0, 0, tft.width(), 36, HEADER_BACKGROUND);
        tft.setTextSize(2);
        tft.setTextColor(color, HEADER_BACKGROUND);
        tft.setCursor(8, 10);
        tft.print(text);
    }

    static void showLastMove(TftTouchShield tft) {
        tft.fillRect(0, FOOTER_Y, UNDO_X - 4, BUTTON_HEIGHT, TftTouchShield.BLACK);
        if (lastFrom < 0) {
            return;
        }
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
        tft.setCursor(BOARD_X, FOOTER_Y + 8);
        drawSquareName(tft, lastFrom);
        drawSquareName(tft, lastTo);
    }

    static void drawSquareName(TftTouchShield tft, int square) {
        tft.print(fileName(square & 7));
        tft.print(8 - (square >> 3));
    }

    static void printSquare(int square) {
        Serial.print(fileName(square & 7));
        Serial.print(8 - (square >> 3));
    }

    static String fileName(int column) {
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
