package io.github.jabrena.juno.games.lunarlander;

import io.github.jabrena.juno.api.tft.TftTouchShield;

/** Cover and descent-result screens. */
final class Interludes {
    private Interludes() {
    }

    static void cover() {
        TftTouchShield.fillScreen(Terrain.SKY);
        drawMoon();
        drawCoverLander();
        Hud.showCentered("LUNAR", 82, 3, TftTouchShield.WHITE);
        Hud.showCentered("LANDER", 116, 3, TftTouchShield.YELLOW);
        Hud.showCentered("Touch down before fuel runs out", 174, 1, TftTouchShield.CYAN);
        Hud.showCentered("Tap to choose a pilot", 194, 1, TftTouchShield.WHITE);
    }

    private static void drawMoon() {
        TftTouchShield.fillCircle(120, 255, 82, 0x8410);
        TftTouchShield.fillCircle(82, 228, 12, 0x632C);
        TftTouchShield.fillCircle(158, 242, 18, 0x632C);
        TftTouchShield.fillCircle(118, 278, 9, 0x632C);
    }

    private static void drawCoverLander() {
        TftTouchShield.fillCircle(120, 46, 17, 0xC618);
        TftTouchShield.fillRect(103, 46, 35, 18, 0xC618);
        TftTouchShield.fillCircle(120, 45, 7, TftTouchShield.CYAN);
        TftTouchShield.fillRect(105, 63, 4, 13, 0xC618);
        TftTouchShield.fillRect(132, 63, 4, 13, 0xC618);
        TftTouchShield.drawHorizontalLine(93, 76, 12, 0xC618);
        TftTouchShield.drawHorizontalLine(136, 76, 12, 0xC618);
    }

    static void landed(int points) {
        Hud.showCentered("LANDED!", 100, 2, TftTouchShield.GREEN);
        TftTouchShield.setCursor(96, 124);
        TftTouchShield.print("+");
        TftTouchShield.print(points);
    }

    static void crashed() {
        Hud.showCentered("CRASHED", 100, 2, TftTouchShield.RED);
    }

    static void outOfFuel() {
        Hud.showCentered("OUT OF FUEL", 124, 2, TftTouchShield.WHITE);
        Hud.showCentered("Tap for the cover", 148, 1, TftTouchShield.YELLOW);
        Controls.waitForTap();
    }
}
