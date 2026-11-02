package io.github.jabrena.juno.games.lunarlander;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;

/** Cover and descent-result screens. */
final class Interludes {
    private Interludes() {
    }

    static void cover(TftTouchShield tft) {
        tft.fillScreen(Terrain.SKY);
        drawMoon(tft);
        drawCoverLander(tft);
        Hud.showCentered(tft, "LUNAR", 82, 3, TftTouchShield.WHITE);
        Hud.showCentered(tft, "LANDER", 116, 3, TftTouchShield.YELLOW);
        Hud.showCentered(tft, "Touch down before fuel runs out", 174, 1, TftTouchShield.CYAN);
        Hud.showCentered(tft, "Tap to choose a pilot", 194, 1, TftTouchShield.WHITE);
    }

    private static void drawMoon(TftTouchShield tft) {
        tft.fillCircle(120, 255, 82, 0x8410);
        tft.fillCircle(82, 228, 12, 0x632C);
        tft.fillCircle(158, 242, 18, 0x632C);
        tft.fillCircle(118, 278, 9, 0x632C);
    }

    private static void drawCoverLander(TftTouchShield tft) {
        tft.fillCircle(120, 46, 17, 0xC618);
        tft.fillRect(103, 46, 35, 18, 0xC618);
        tft.fillCircle(120, 45, 7, TftTouchShield.CYAN);
        tft.fillRect(105, 63, 4, 13, 0xC618);
        tft.fillRect(132, 63, 4, 13, 0xC618);
        tft.drawHorizontalLine(93, 76, 12, 0xC618);
        tft.drawHorizontalLine(136, 76, 12, 0xC618);
    }

    static void landed(TftTouchShield tft, int points) {
        Hud.showCentered(tft, "LANDED!", 100, 2, TftTouchShield.GREEN);
        tft.setCursor(96, 124);
        tft.print("+");
        tft.print(points);
    }

    static void crashed(TftTouchShield tft) {
        Hud.showCentered(tft, "CRASHED", 100, 2, TftTouchShield.RED);
    }

    static void outOfFuel(TftTouchShield tft) {
        Hud.showCentered(tft, "OUT OF FUEL", 124, 2, TftTouchShield.WHITE);
        Hud.showCentered(tft, "Tap for the cover", 148, 1, TftTouchShield.YELLOW);
        Controls.waitForTap(tft);
    }
}
