package io.github.jabrena.juno.games.blackjack;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/** Animated casino cover: cards fan out while chips orbit the title. */
final class Interludes {
    private Interludes() {
    }

    static void cover() {
        TftTouchShield.fillScreen(0x0366);
        for (int frame = 0; frame < 24; frame++) {
            int spread = Math.min(60, frame * 3);
            drawCard(100 - spread, 116 + spread / 4, "A", TftTouchShield.RED);
            drawCard(100, 108, "J", TftTouchShield.BLACK);
            drawCard(100 + spread, 116 + spread / 4, "A", TftTouchShield.RED);
            int chipX = 120 + ((frame % 12) - 6) * 9;
            TftTouchShield.fillCircle(chipX, 238, 9, frame % 2 == 0 ? TftTouchShield.YELLOW : TftTouchShield.CYAN);
            TftTouchShield.fillCircle(chipX, 238, 5, 0x0366);
            Delay.millis(45);
        }
        for (int pulse = 0; pulse < 6; pulse++) {
            title(pulse % 2 == 0 ? TftTouchShield.YELLOW : TftTouchShield.WHITE);
            Delay.millis(180);
        }
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.CYAN, 0x0366);
        TftTouchShield.setCursor(80, 286);
        TftTouchShield.print("TAP TO DEAL");
    }

    private static void drawCard(int x, int y, String rank, int ink) {
        TftTouchShield.fillRect(x, y, 40, 58, TftTouchShield.WHITE);
        TftTouchShield.fillRect(x + 2, y + 2, 36, 54, 0xFFFF);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(ink, TftTouchShield.WHITE);
        TftTouchShield.setCursor(x + 5, y + 6);
        TftTouchShield.print(rank);
        TftTouchShield.fillCircle(x + 20, y + 36, 7, ink);
    }

    private static void title(int color) {
        TftTouchShield.fillRect(0, 18, 240, 48, 0x0366);
        TftTouchShield.setTextSize(3);
        TftTouchShield.setTextColor(0x0208, 0x0366);
        TftTouchShield.setCursor(42, 30);
        TftTouchShield.print("BLACKJACK");
        TftTouchShield.setTextColor(color, 0x0366);
        TftTouchShield.setCursor(39, 27);
        TftTouchShield.print("BLACKJACK");
    }
}
