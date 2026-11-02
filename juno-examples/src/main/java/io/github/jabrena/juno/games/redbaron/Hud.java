package io.github.jabrena.juno.games.redbaron;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;

/** Score, best score, wave, pilot, planes and round goal above the view. */
final class Hud {
    private static int status;

    private Hud() {
    }

    static void drawHeader(TftTouchShield tft) {
        tft.fillRect(0, 0, DisplayList.WIDTH, DisplayList.HEADER - 1, DisplayList.SKY);
        tft.drawHorizontalLine(0, DisplayList.HEADER - 1, DisplayList.WIDTH, SceneRenderer.HORIZON);
        tft.setTextSize(1);
        tft.setTextColor(TftTouchShield.YELLOW, DisplayList.SKY);
        tft.setCursor(4, 2);
        tft.print("SCORE ");
        tft.print(Session.score);
        tft.setTextColor(TftTouchShield.CYAN, DisplayList.SKY);
        tft.setCursor(136, 2);
        tft.print("HI ");
        tft.print(Session.best);
        tft.setCursor(262, 2);
        tft.print("WAVE ");
        tft.print(Session.wave);
        if (Controls.autopilot) {
            tft.setTextColor(TftTouchShield.MAGENTA, DisplayList.SKY);
            tft.setCursor(226, 2);
            tft.print("CPU");
        }
        tft.setTextColor(Session.planes <= 1 ? TftTouchShield.RED : TftTouchShield.GREEN,
                DisplayList.SKY);
        tft.setCursor(4, 11);
        tft.print("PLANES ");
        tft.print(Session.planes);
        status = -1;
        drawStatus(tft);
    }

    static void drawStatus(TftTouchShield tft) {
        int value = Session.round == Round.DOGFIGHT
                ? DogfightRound.fightersLeft() : GroundAttackRound.targetsAhead();
        int combined = value * 1000 + Camera.altitude / 10;
        if (combined == status) {
            return;
        }
        status = combined;
        tft.setTextSize(1);
        tft.setTextColor(TftTouchShield.WHITE, DisplayList.SKY);
        tft.setCursor(96, 11);
        if (Session.round == Round.DOGFIGHT) {
            tft.print("ENEMY PLANES ");
        } else {
            tft.print("TARGETS AHEAD ");
        }
        tft.print(value);
        tft.print("  ");
        tft.setCursor(250, 11);
        tft.print("ALT ");
        tft.print(Camera.altitude);
        tft.print("  ");
    }

    static void showCentered(TftTouchShield tft, String text, int y, int size, int color) {
        tft.setTextSize(size);
        tft.setTextColor(color, DisplayList.SKY);
        tft.setCursor((DisplayList.WIDTH - text.length() * 6 * size) / 2, y);
        tft.print(text);
    }
}
