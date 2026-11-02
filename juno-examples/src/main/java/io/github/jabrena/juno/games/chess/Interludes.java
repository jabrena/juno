package io.github.jabrena.juno.games.chess;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.util.Delay;

/** Animated chessboard cover shown again after every completed match. */
final class Interludes {
    private Interludes() {
    }

    static void cover(TftTouchShield tft) {
        tft.fillScreen(0x0841);
        for (int frame = 0; frame < 24; frame++) {
            drawBoard(tft, frame);
            Delay.millis(45);
        }
        for (int pulse = 0; pulse < 6; pulse++) {
            drawTitle(tft, pulse % 2 == 0 ? TftTouchShield.YELLOW : TftTouchShield.WHITE);
            Delay.millis(180);
        }
        tft.setTextSize(1);
        tft.setTextColor(TftTouchShield.CYAN, 0x0841);
        tft.setCursor(80, 286);
        tft.print("TAP TO PLAY");
    }

    private static void drawBoard(TftTouchShield tft, int frame) {
        int rows = Math.min(8, frame / 2 + 1);
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < 8; column++) {
                int color = (row + column) % 2 == 0 ? 0xF6D6 : 0xB44C;
                tft.fillRect(36 + column * 21, 72 + row * 21, 21, 21, color);
            }
        }
    }

    private static void drawTitle(TftTouchShield tft, int color) {
        tft.fillRect(0, 18, 240, 42, 0x0841);
        tft.setTextSize(4);
        tft.setTextColor(0x4208, 0x0841);
        tft.setCursor(63, 24);
        tft.print("CHESS");
        tft.setTextColor(color, 0x0841);
        tft.setCursor(60, 21);
        tft.print("CHESS");
    }
}
