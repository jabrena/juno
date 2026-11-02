package io.github.jabrena.juno.games.starwars;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.util.Delay;

/** The header over the view (score, best, wave, pilot, shields and the phase's goal) and centered text. */
final class Hud {
    /** The goal last shown in the header, so it is only printed again when it changes. */
    private static int status;

    private Hud() {
    }

    static void drawHeader(TftTouchShield tft) {
        int space = DisplayList.SPACE;
        tft.fillRect(0, 0, DisplayList.WIDTH, DisplayList.HEADER - 1, space);
        tft.drawHorizontalLine(0, DisplayList.HEADER - 1, DisplayList.WIDTH, SceneRenderer.STRUCTURE);
        tft.setTextSize(1);
        tft.setTextColor(TftTouchShield.YELLOW, space);
        tft.setCursor(4, 2);
        tft.print("SCORE ");
        tft.print(Session.score);
        tft.setTextColor(TftTouchShield.CYAN, space);
        tft.setCursor(136, 2);
        tft.print("HI ");
        tft.print(Session.best);
        tft.setCursor(262, 2);
        tft.print("WAVE ");
        tft.print(Session.wave);
        if (Controls.autopilot) {
            tft.setTextColor(TftTouchShield.MAGENTA, space);
            tft.setCursor(226, 2);
            tft.print("CPU");
        }
        int color = TftTouchShield.GREEN;
        if (Session.shields == 0) {
            color = TftTouchShield.RED;
        } else if (Session.shields <= 2) {
            color = TftTouchShield.YELLOW;
        }
        tft.setTextColor(color, space);
        tft.setCursor(4, 11);
        tft.print("SHIELDS ");
        tft.print(Session.shields);
        status = -1;
        drawStatus(tft);
    }

    /** Flashes the header red for a hit on the X-wing, then shows the shields left. */
    static void flashHit(TftTouchShield tft) {
        tft.fillRect(0, 0, DisplayList.WIDTH, DisplayList.HEADER - 1, TftTouchShield.RED);
        Delay.millis(60);
        drawHeader(tft);
    }

    /** The phase's goal in the header's second row: fighters or towers left, or distance to the port. */
    static void drawStatus(TftTouchShield tft) {
        int value = switch (Session.phase) {
            case SPACE -> SpacePhase.fightersLeft();
            case SURFACE -> SurfacePhase.towersAhead();
            default -> TrenchPhase.distanceToPort();
        };
        if (value == status) {
            return;
        }
        status = value;
        tft.setTextSize(1);
        tft.setTextColor(TftTouchShield.WHITE, DisplayList.SPACE);
        tft.setCursor(136, 11);
        switch (Session.phase) {
            case SPACE -> tft.print("TIE FIGHTERS LEFT ");
            case SURFACE -> tft.print("TOWERS AHEAD ");
            default -> tft.print("EXHAUST PORT ");
        }
        tft.print(value);
        tft.print("   ");
    }

    static void showCentered(TftTouchShield tft, String text, int y, int size, int color) {
        tft.setTextSize(size);
        tft.setTextColor(color, DisplayList.SPACE);
        tft.setCursor((DisplayList.WIDTH - text.length() * 6 * size) / 2, y);
        tft.print(text);
    }
}
