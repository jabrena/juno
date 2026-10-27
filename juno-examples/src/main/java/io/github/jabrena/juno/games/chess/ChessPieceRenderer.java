package io.github.jabrena.juno.games.chess;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;

/** Renders the shared 20x20 chess bitmap sprites used by the chess examples. */
public final class ChessPieceRenderer {
    public static final int PIECE_SIZE = 20;
    private static final int BLACK_PIECE_EDGE = 0xA514;

    private ChessPieceRenderer() {
    }

    /** Draws one signed chess piece into an already opened square background. */
    public static void draw(int x, int y, int piece, int background) {
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
        int shape = PieceSprites.shapeRow(type, 0);
        for (int row = 0; row < PIECE_SIZE; row++) {
            int below = 0;
            if (row + 1 < PIECE_SIZE) {
                below = PieceSprites.shapeRow(type, row + 1);
            }
            int interior = shape & above & below & (shape << 1) & (shape >> 1);
            int outline = (shape & ~interior) | PieceSprites.detailRow(type, row);
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
}
