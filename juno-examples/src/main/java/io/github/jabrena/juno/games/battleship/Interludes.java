package io.github.jabrena.juno.games.battleship;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/** Radar sweep, incoming salvos and animated title cover. */
final class Interludes {
    private Interludes() {
    }

    static void cover() {
        TftTouchShield.fillScreen(0x0012);
        TftTouchShield.fillCircle(120, 154, 82, 0x0208);
        TftTouchShield.fillCircle(120, 154, 78, 0x0012);
        for (int ring = 1; ring <= 3; ring++) {
            radarRing(120, 154, ring * 19, 0x03E0);
        }
        for (int frame = 0; frame < 24; frame++) {
            int x = 120 + (frame - 12) * 6;
            int y = 154 + ((frame - 12) * (frame - 12)) / 4 - 36;
            TftTouchShield.fillCircle(x, y, 3, TftTouchShield.ORANGE);
            TftTouchShield.fillRect(42 + frame * 6, 236, 22, 5, 0x8410);
            Delay.millis(45);
        }
        for (int pulse = 0; pulse < 6; pulse++) {
            drawTitle(pulse % 2 == 0 ? TftTouchShield.CYAN : TftTouchShield.WHITE);
            Delay.millis(180);
        }
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.YELLOW, 0x0012);
        TftTouchShield.setCursor(80, 286);
        TftTouchShield.print("TAP TO DEPLOY");
    }

    private static void radarRing(int cx, int cy, int radius, int color) {
        for (int y = -radius; y <= radius; y = y + 3) {
            int x = (int) Math.sqrt(radius * radius - y * y);
            TftTouchShield.fillRect(cx - x, cy + y, x * 2 + 1, 1, color);
        }
    }

    private static void drawTitle(int color) {
        TftTouchShield.fillRect(0, 18, 240, 44, 0x0012);
        TftTouchShield.setTextSize(3);
        TftTouchShield.setTextColor(0x4208, 0x0012);
        TftTouchShield.setCursor(33, 28);
        TftTouchShield.print("BATTLESHIP");
        TftTouchShield.setTextColor(color, 0x0012);
        TftTouchShield.setCursor(30, 25);
        TftTouchShield.print("BATTLESHIP");
    }
}
