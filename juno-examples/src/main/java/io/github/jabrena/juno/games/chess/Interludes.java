package io.github.jabrena.juno.games.chess;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/** Animated chessboard cover shown again after every completed match. */
final class Interludes {
    private Interludes() {
    }

    static void cover() {
        TftTouchShield.fillScreen(0x0841);
        for (int frame = 0; frame < 24; frame++) {
            drawBoard(frame);
            Delay.millis(45);
        }
        for (int pulse = 0; pulse < 6; pulse++) {
            drawTitle(pulse % 2 == 0 ? TftTouchShield.YELLOW : TftTouchShield.WHITE);
            Delay.millis(180);
        }
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.CYAN, 0x0841);
        TftTouchShield.setCursor(80, 286);
        TftTouchShield.print("TAP TO PLAY");
    }

    private static void drawBoard(int frame) {
        int rows = Math.min(8, frame / 2 + 1);
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < 8; column++) {
                int color = (row + column) % 2 == 0 ? 0xF6D6 : 0xB44C;
                TftTouchShield.fillRect(36 + column * 21, 72 + row * 21, 21, 21, color);
            }
        }
    }

    private static void drawTitle(int color) {
        TftTouchShield.fillRect(0, 18, 240, 42, 0x0841);
        TftTouchShield.setTextSize(4);
        TftTouchShield.setTextColor(0x4208, 0x0841);
        TftTouchShield.setCursor(63, 24);
        TftTouchShield.print("CHESS");
        TftTouchShield.setTextColor(color, 0x0841);
        TftTouchShield.setCursor(60, 21);
        TftTouchShield.print("CHESS");
    }
}
