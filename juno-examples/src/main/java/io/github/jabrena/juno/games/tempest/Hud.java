package io.github.jabrena.juno.games.tempest;

import io.github.jabrena.juno.api.tft.TftTouchShield;

/** The header (score, best, level, lives, zapper and pilot) and the shared centered-text helper. */
final class Hud {
    static final int WIDTH = 240;
    static final int HEIGHT = 320;
    static final int HEADER = 20;
    static final int SPACE = TftTouchShield.BLACK;

    private Hud() {
    }

    static void drawHeader() {
        TftTouchShield.fillRect(0, 0, WIDTH, HEADER, SPACE);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.GREEN, SPACE);
        TftTouchShield.setCursor(4, 3);
        TftTouchShield.print(Session.score);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(SceneRenderer.CLAW, SPACE);
        TftTouchShield.setCursor(110, 3);
        TftTouchShield.print("HI ");
        TftTouchShield.print(Session.best);
        TftTouchShield.print(Controls.autopilot ? " CPU" : "");
        TftTouchShield.setCursor(110, 12);
        TftTouchShield.print("LEVEL ");
        TftTouchShield.print(Session.level);
        TftTouchShield.setTextColor(SceneRenderer.FLIPPER, SPACE);
        TftTouchShield.setCursor(186, 3);
        TftTouchShield.print("LIVES ");
        TftTouchShield.print(Session.lives);
        if (Session.zapperReady) {
            TftTouchShield.setTextColor(TftTouchShield.CYAN, SPACE);
            TftTouchShield.setCursor(186, 12);
            TftTouchShield.print("ZAP");
        }
    }

    static void showCentered(String text, int y, int size, int color) {
        TftTouchShield.setTextSize(size);
        TftTouchShield.setTextColor(color, SPACE);
        TftTouchShield.setCursor((WIDTH - text.length() * 6 * size) / 2, y);
        TftTouchShield.print(text);
    }

    /** {@code label} followed by {@code value}, centered as one line. */
    static void showCenteredValue(String label, int value, int y, int size, int color) {
        int digits = 1;
        for (int rest = value / 10; rest > 0; rest = rest / 10) {
            digits = digits + 1;
        }
        TftTouchShield.setTextSize(size);
        TftTouchShield.setTextColor(color, SPACE);
        TftTouchShield.setCursor((WIDTH - (label.length() + digits) * 6 * size) / 2, y);
        TftTouchShield.print(label);
        TftTouchShield.print(value);
    }
}
