package io.github.jabrena.juno.games.missilecommand;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;

/** The header (score, best and wave) and the shared centered-text helpers. */
final class Hud {
    private static final int HEADER_BACKGROUND = 0x2945;

    private Hud() {
    }

    static void drawHeader(TftTouchShield tft) {
        tft.fillRect(0, 0, Session.WIDTH, Session.HEADER, HEADER_BACKGROUND);
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.WHITE, HEADER_BACKGROUND);
        tft.setCursor(4, 3);
        tft.print(Session.score);
        tft.setTextSize(1);
        tft.setTextColor(TftTouchShield.YELLOW, HEADER_BACKGROUND);
        tft.setCursor(110, 7);
        tft.print("HI ");
        tft.print(Session.best);
        tft.setTextColor(Controls.autopilot ? TftTouchShield.MAGENTA : TftTouchShield.CYAN,
                HEADER_BACKGROUND);
        tft.setCursor(Controls.autopilot ? 166 : 190, 7);
        tft.print("WAVE ");
        tft.print(Session.wave);
        tft.print(Controls.autopilot ? " CPU" : "");
    }

    static void showCentered(TftTouchShield tft, String text, int y, int size, int color) {
        tft.setTextSize(size);
        tft.setTextColor(color, SceneRenderer.SKY);
        tft.setCursor((Session.WIDTH - text.length() * 6 * size) / 2, y);
        tft.print(text);
    }

    /** {@code label} followed by {@code value}, centered as one line. */
    static void showCenteredValue(TftTouchShield tft, String label, int value, int y, int size, int color) {
        int digits = 1;
        for (int rest = value / 10; rest > 0; rest = rest / 10) {
            digits = digits + 1;
        }
        tft.setTextSize(size);
        tft.setTextColor(color, SceneRenderer.SKY);
        tft.setCursor((Session.WIDTH - (label.length() + digits) * 6 * size) / 2, y);
        tft.print(label);
        tft.print(value);
    }
}
