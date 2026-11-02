package io.github.jabrena.juno.games.texasholdem;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.util.Delay;

/** Animated poker-table cover shown between tournaments. */
final class Interludes {
    private Interludes() {
    }

    static void cover(TftTouchShield tft) {
        tft.fillScreen(0x0366);
        for (int frame = 0; frame < 26; frame++) {
            int spread = Math.min(54, frame * 3);
            card(tft, 91 - spread, 116 + spread / 5, "A", TftTouchShield.RED);
            card(tft, 99, 106, "K", TftTouchShield.BLACK);
            card(tft, 107 + spread, 116 + spread / 5, "Q", TftTouchShield.RED);
            for (int chip = 0; chip < 4; chip++) {
                int x = 46 + chip * 46 + (frame % 4) * 2;
                int color = chip % 2 == 0 ? TftTouchShield.YELLOW : TftTouchShield.CYAN;
                tft.fillCircle(x, 244 - chip * 3, 10, color);
                tft.fillCircle(x, 244 - chip * 3, 5, 0x0366);
            }
            Delay.millis(45);
        }
        for (int pulse = 0; pulse < 6; pulse++) {
            title(tft, pulse % 2 == 0 ? TftTouchShield.YELLOW : TftTouchShield.WHITE);
            Delay.millis(180);
        }
        tft.setTextSize(1);
        tft.setTextColor(TftTouchShield.CYAN, 0x0366);
        tft.setCursor(74, 286);
        tft.print("TAP TO TAKE A SEAT");
    }

    private static void card(TftTouchShield tft, int x, int y, String rank, int ink) {
        tft.fillRect(x, y, 34, 50, TftTouchShield.WHITE);
        tft.setTextSize(2);
        tft.setTextColor(ink, TftTouchShield.WHITE);
        tft.setCursor(x + 5, y + 5);
        tft.print(rank);
        tft.fillCircle(x + 17, y + 33, 6, ink);
    }

    private static void title(TftTouchShield tft, int color) {
        tft.fillRect(0, 14, 240, 72, 0x0366);
        tft.setTextSize(2);
        tft.setTextColor(0x0208, 0x0366);
        tft.setCursor(45, 28);
        tft.print("TEXAS HOLD'EM");
        tft.setTextColor(color, 0x0366);
        tft.setCursor(42, 25);
        tft.print("TEXAS HOLD'EM");
        tft.setTextSize(1);
        tft.setCursor(96, 58);
        tft.print("NO LIMIT");
    }
}
