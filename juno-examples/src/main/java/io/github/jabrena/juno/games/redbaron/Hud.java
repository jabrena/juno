package io.github.jabrena.juno.games.redbaron;

import io.github.jabrena.juno.api.tft.TftTouchShield;

/** Score, best score, wave, pilot, planes and round goal above the view. */
final class Hud {
    private static int status;

    private Hud() {
    }

    static void drawHeader() {
        TftTouchShield.fillRect(0, 0, DisplayList.WIDTH, DisplayList.HEADER - 1, DisplayList.SKY);
        TftTouchShield.drawHorizontalLine(0, DisplayList.HEADER - 1, DisplayList.WIDTH, SceneRenderer.HORIZON);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.YELLOW, DisplayList.SKY);
        TftTouchShield.setCursor(4, 2);
        TftTouchShield.print("SCORE ");
        TftTouchShield.print(Session.score);
        TftTouchShield.setTextColor(TftTouchShield.CYAN, DisplayList.SKY);
        TftTouchShield.setCursor(136, 2);
        TftTouchShield.print("HI ");
        TftTouchShield.print(Session.best);
        TftTouchShield.setCursor(262, 2);
        TftTouchShield.print("WAVE ");
        TftTouchShield.print(Session.wave);
        if (Controls.autopilot) {
            TftTouchShield.setTextColor(TftTouchShield.MAGENTA, DisplayList.SKY);
            TftTouchShield.setCursor(226, 2);
            TftTouchShield.print("CPU");
        }
        TftTouchShield.setTextColor(Session.planes <= 1 ? TftTouchShield.RED : TftTouchShield.GREEN,
                DisplayList.SKY);
        TftTouchShield.setCursor(4, 11);
        TftTouchShield.print("PLANES ");
        TftTouchShield.print(Session.planes);
        status = -1;
        drawStatus();
    }

    static void drawStatus() {
        int value = Session.round == Round.DOGFIGHT
                ? DogfightRound.fightersLeft() : GroundAttackRound.targetsAhead();
        int combined = value * 1000 + Camera.altitude / 10;
        if (combined == status) {
            return;
        }
        status = combined;
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, DisplayList.SKY);
        TftTouchShield.setCursor(96, 11);
        if (Session.round == Round.DOGFIGHT) {
            TftTouchShield.print("ENEMY PLANES ");
        } else {
            TftTouchShield.print("TARGETS AHEAD ");
        }
        TftTouchShield.print(value);
        TftTouchShield.print("  ");
        TftTouchShield.setCursor(250, 11);
        TftTouchShield.print("ALT ");
        TftTouchShield.print(Camera.altitude);
        TftTouchShield.print("  ");
    }

    static void showCentered(String text, int y, int size, int color) {
        TftTouchShield.setTextSize(size);
        TftTouchShield.setTextColor(color, DisplayList.SKY);
        TftTouchShield.setCursor((DisplayList.WIDTH - text.length() * 6 * size) / 2, y);
        TftTouchShield.print(text);
    }
}
