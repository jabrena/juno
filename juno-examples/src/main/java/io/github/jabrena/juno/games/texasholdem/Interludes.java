package io.github.jabrena.juno.games.texasholdem;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/** Animated poker-table cover shown between tournaments. */
final class Interludes {
    private Interludes() {
    }

    static void cover() {
        TftTouchShield.fillScreen(0x0366);
        for (int frame = 0; frame < 26; frame++) {
            int spread = Math.min(54, frame * 3);
            card(91 - spread, 116 + spread / 5, "A", TftTouchShield.RED);
            card(99, 106, "K", TftTouchShield.BLACK);
            card(107 + spread, 116 + spread / 5, "Q", TftTouchShield.RED);
            for (int chip = 0; chip < 4; chip++) {
                int x = 46 + chip * 46 + (frame % 4) * 2;
                int color = chip % 2 == 0 ? TftTouchShield.YELLOW : TftTouchShield.CYAN;
                TftTouchShield.fillCircle(x, 244 - chip * 3, 10, color);
                TftTouchShield.fillCircle(x, 244 - chip * 3, 5, 0x0366);
            }
            Delay.millis(45);
        }
        for (int pulse = 0; pulse < 6; pulse++) {
            title(pulse % 2 == 0 ? TftTouchShield.YELLOW : TftTouchShield.WHITE);
            Delay.millis(180);
        }
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.CYAN, 0x0366);
        TftTouchShield.setCursor(74, 286);
        TftTouchShield.print("TAP TO TAKE A SEAT");
    }

    private static void card(int x, int y, String rank, int ink) {
        TftTouchShield.fillRect(x, y, 34, 50, TftTouchShield.WHITE);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(ink, TftTouchShield.WHITE);
        TftTouchShield.setCursor(x + 5, y + 5);
        TftTouchShield.print(rank);
        TftTouchShield.fillCircle(x + 17, y + 33, 6, ink);
    }

    private static void title(int color) {
        TftTouchShield.fillRect(0, 14, 240, 72, 0x0366);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(0x0208, 0x0366);
        TftTouchShield.setCursor(45, 28);
        TftTouchShield.print("TEXAS HOLD'EM");
        TftTouchShield.setTextColor(color, 0x0366);
        TftTouchShield.setCursor(42, 25);
        TftTouchShield.print("TEXAS HOLD'EM");
        TftTouchShield.setTextSize(1);
        TftTouchShield.setCursor(96, 58);
        TftTouchShield.print("NO LIMIT");
    }
}
