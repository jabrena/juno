package io.github.jabrena.juno.games.missilecommand;

import io.github.jabrena.juno.api.tft.TftTouchShield;

/** The header (score, best and wave) and the shared centered-text helpers. */
final class Hud {
    private static final int HEADER_BACKGROUND = 0x2945;

    private Hud() {
    }

    static void drawHeader() {
        TftTouchShield.fillRect(0, 0, Session.WIDTH, Session.HEADER, HEADER_BACKGROUND);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, HEADER_BACKGROUND);
        TftTouchShield.setCursor(4, 3);
        TftTouchShield.print(Session.score);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.YELLOW, HEADER_BACKGROUND);
        TftTouchShield.setCursor(110, 7);
        TftTouchShield.print("HI ");
        TftTouchShield.print(Session.best);
        TftTouchShield.setTextColor(Controls.autopilot ? TftTouchShield.MAGENTA : TftTouchShield.CYAN,
                HEADER_BACKGROUND);
        TftTouchShield.setCursor(190, 7);
        TftTouchShield.print("WAVE ");
        TftTouchShield.print(Session.wave);
        TftTouchShield.print(Controls.autopilot ? " CPU" : "");
    }

    static void showCentered(String text, int y, int size, int color) {
        TftTouchShield.setTextSize(size);
        TftTouchShield.setTextColor(color, SceneRenderer.SKY);
        TftTouchShield.setCursor((Session.WIDTH - text.length() * 6 * size) / 2, y);
        TftTouchShield.print(text);
    }

    /** {@code label} followed by {@code value}, centered as one line. */
    static void showCenteredValue(String label, int value, int y, int size, int color) {
        int digits = 1;
        for (int rest = value / 10; rest > 0; rest = rest / 10) {
            digits = digits + 1;
        }
        TftTouchShield.setTextSize(size);
        TftTouchShield.setTextColor(color, SceneRenderer.SKY);
        TftTouchShield.setCursor((Session.WIDTH - (label.length() + digits) * 6 * size) / 2, y);
        TftTouchShield.print(label);
        TftTouchShield.print(value);
    }
}
