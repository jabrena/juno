package io.github.jabrena.juno.games.blackjack;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.util.Delay;

/** Animated casino cover: cards fan out while chips orbit the title. */
final class Interludes {
    private Interludes() {
    }

    static void cover(TftTouchShield tft) {
        tft.fillScreen(0x0366);
        for (int frame = 0; frame < 24; frame++) {
            int spread = Math.min(60, frame * 3);
            drawCard(tft, 100 - spread, 116 + spread / 4, "A", TftTouchShield.RED);
            drawCard(tft, 100, 108, "J", TftTouchShield.BLACK);
            drawCard(tft, 100 + spread, 116 + spread / 4, "A", TftTouchShield.RED);
            int chipX = 120 + ((frame % 12) - 6) * 9;
            tft.fillCircle(chipX, 238, 9, frame % 2 == 0 ? TftTouchShield.YELLOW : TftTouchShield.CYAN);
            tft.fillCircle(chipX, 238, 5, 0x0366);
            Delay.millis(45);
        }
        for (int pulse = 0; pulse < 6; pulse++) {
            title(tft, pulse % 2 == 0 ? TftTouchShield.YELLOW : TftTouchShield.WHITE);
            Delay.millis(180);
        }
        tft.setTextSize(1);
        tft.setTextColor(TftTouchShield.CYAN, 0x0366);
        tft.setCursor(80, 286);
        tft.print("TAP TO DEAL");
    }

    private static void drawCard(TftTouchShield tft, int x, int y, String rank, int ink) {
        tft.fillRect(x, y, 40, 58, TftTouchShield.WHITE);
        tft.fillRect(x + 2, y + 2, 36, 54, 0xFFFF);
        tft.setTextSize(2);
        tft.setTextColor(ink, TftTouchShield.WHITE);
        tft.setCursor(x + 5, y + 6);
        tft.print(rank);
        tft.fillCircle(x + 20, y + 36, 7, ink);
    }

    private static void title(TftTouchShield tft, int color) {
        tft.fillRect(0, 18, 240, 48, 0x0366);
        tft.setTextSize(3);
        tft.setTextColor(0x0208, 0x0366);
        tft.setCursor(42, 30);
        tft.print("BLACKJACK");
        tft.setTextColor(color, 0x0366);
        tft.setCursor(39, 27);
        tft.print("BLACKJACK");
    }
}
