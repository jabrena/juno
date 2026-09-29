package io.github.jabrena.juno.games.chess;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.io.usb.Serial;
import io.github.jabrena.juno.api.tft.TftTouchShield;
import static io.github.jabrena.juno.games.chess.Chess.*;
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

final class SceneRenderer {
    private SceneRenderer() {
    }

    static void refresh(byte[] board, byte[] shownPiece, byte[] shownDeco, byte[] deco, int[] moves,
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

    static void drawSquare(int square, int piece, int decoration) {
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

    static void drawPiece(int x, int y, int piece, int background) {
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

    static void showStatus(String text, int color) {
        TftTouchShield.fillRect(0, 0, TftTouchShield.width(), 36, HEADER_BACKGROUND);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(color, HEADER_BACKGROUND);
        TftTouchShield.setCursor(8, 10);
        TftTouchShield.print(text);
    }

    static void showLastMove() {
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

    static void drawSquareName(int square) {
        TftTouchShield.print(fileName(square & 7));
        TftTouchShield.print(8 - (square >> 3));
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
