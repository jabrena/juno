package io.github.jabrena.juno.games.tempest;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;

/** The header (score, best, level, lives, zapper and pilot) and the shared centered-text helper. */
final class Hud {
    static final int WIDTH = 240;
    static final int HEIGHT = 320;
    static final int HEADER = 20;
    static final int SPACE = TftTouchShield.BLACK;

    private Hud() {
    }

    static void drawHeader(TftTouchShield tft) {
        tft.fillRect(0, 0, WIDTH, HEADER, SPACE);
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.GREEN, SPACE);
        tft.setCursor(4, 3);
        tft.print(Session.score);
        tft.setTextSize(1);
        tft.setTextColor(SceneRenderer.CLAW, SPACE);
        tft.setCursor(110, 3);
        tft.print("HI ");
        tft.print(Session.best);
        tft.print(Controls.autopilot ? " CPU" : "");
        tft.setCursor(110, 12);
        tft.print("LEVEL ");
        tft.print(Session.level);
        tft.setTextColor(SceneRenderer.FLIPPER, SPACE);
        tft.setCursor(186, 3);
        tft.print("LIVES ");
        tft.print(Session.lives);
        if (Session.zapperReady) {
            tft.setTextColor(TftTouchShield.CYAN, SPACE);
            tft.setCursor(186, 12);
            tft.print("ZAP");
        }
    }

    static void showCentered(TftTouchShield tft, String text, int y, int size, int color) {
        tft.setTextSize(size);
        tft.setTextColor(color, SPACE);
        tft.setCursor((WIDTH - text.length() * 6 * size) / 2, y);
        tft.print(text);
    }

    /** {@code label} followed by {@code value}, centered as one line. */
    static void showCenteredValue(TftTouchShield tft, String label, int value, int y, int size, int color) {
        int digits = 1;
        for (int rest = value / 10; rest > 0; rest = rest / 10) {
            digits = digits + 1;
        }
        tft.setTextSize(size);
        tft.setTextColor(color, SPACE);
        tft.setCursor((WIDTH - (label.length() + digits) * 6 * size) / 2, y);
        tft.print(label);
        tft.print(value);
    }
}
