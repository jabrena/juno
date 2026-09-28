package io.github.jabrena.juno.games.starwars;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/** The header over the view (score, best, wave, pilot, shields and the phase's goal) and centered text. */
final class Hud {
    /** The goal last shown in the header, so it is only printed again when it changes. */
    private static int status;

    private Hud() {
    }

    static void drawHeader() {
        int space = DisplayList.SPACE;
        TftTouchShield.fillRect(0, 0, DisplayList.WIDTH, DisplayList.HEADER - 1, space);
        TftTouchShield.drawHorizontalLine(0, DisplayList.HEADER - 1, DisplayList.WIDTH, SceneRenderer.STRUCTURE);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.YELLOW, space);
        TftTouchShield.setCursor(4, 2);
        TftTouchShield.print("SCORE ");
        TftTouchShield.print(Session.score);
        TftTouchShield.setTextColor(TftTouchShield.CYAN, space);
        TftTouchShield.setCursor(136, 2);
        TftTouchShield.print("HI ");
        TftTouchShield.print(Session.best);
        TftTouchShield.setCursor(262, 2);
        TftTouchShield.print("WAVE ");
        TftTouchShield.print(Session.wave);
        if (Controls.autopilot) {
            TftTouchShield.setTextColor(TftTouchShield.MAGENTA, space);
            TftTouchShield.setCursor(226, 2);
            TftTouchShield.print("CPU");
        }
        int color = TftTouchShield.GREEN;
        if (Session.shields == 0) {
            color = TftTouchShield.RED;
        } else if (Session.shields <= 2) {
            color = TftTouchShield.YELLOW;
        }
        TftTouchShield.setTextColor(color, space);
        TftTouchShield.setCursor(4, 11);
        TftTouchShield.print("SHIELDS ");
        TftTouchShield.print(Session.shields);
        status = -1;
        drawStatus();
    }

    /** Flashes the header red for a hit on the X-wing, then shows the shields left. */
    static void flashHit() {
        TftTouchShield.fillRect(0, 0, DisplayList.WIDTH, DisplayList.HEADER - 1, TftTouchShield.RED);
        Delay.millis(60);
        drawHeader();
    }

    /** The phase's goal in the header's second row: fighters or towers left, or distance to the port. */
    static void drawStatus() {
        int value = switch (Session.phase) {
            case SPACE -> SpacePhase.fightersLeft();
            case SURFACE -> SurfacePhase.towersAhead();
            default -> TrenchPhase.distanceToPort();
        };
        if (value == status) {
            return;
        }
        status = value;
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, DisplayList.SPACE);
        TftTouchShield.setCursor(136, 11);
        switch (Session.phase) {
            case SPACE -> TftTouchShield.print("TIE FIGHTERS LEFT ");
            case SURFACE -> TftTouchShield.print("TOWERS AHEAD ");
            default -> TftTouchShield.print("EXHAUST PORT ");
        }
        TftTouchShield.print(value);
        TftTouchShield.print("   ");
    }

    static void showCentered(String text, int y, int size, int color) {
        TftTouchShield.setTextSize(size);
        TftTouchShield.setTextColor(color, DisplayList.SPACE);
        TftTouchShield.setCursor((DisplayList.WIDTH - text.length() * 6 * size) / 2, y);
        TftTouchShield.print(text);
    }
}
