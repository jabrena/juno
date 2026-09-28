package io.github.jabrena.juno.games.empirestrikesback;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * The header over the view (score, best, wave, pilot, shields, JEDI letters and the round's goal)
 * and centered text.
 */
final class Hud {
    /** The goal last shown in the header, so it is only printed again when it changes. */
    private static int status;

    private Hud() {
    }

    static void drawHeader() {
        int space = DisplayList.SPACE;
        TftTouchShield.fillRect(0, 0, DisplayList.WIDTH, DisplayList.HEADER - 1, space);
        TftTouchShield.drawHorizontalLine(0, DisplayList.HEADER - 1, DisplayList.WIDTH, SceneRenderer.HUD);
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
        TftTouchShield.setTextColor(TftTouchShield.CYAN, space);
        TftTouchShield.setCursor(262, 11);
        TftTouchShield.print(Session.jediWord());
        status = -1;
        drawStatus();
    }

    /** Flashes the header red for a hit on your craft, then shows the shields left. */
    static void flashHit() {
        TftTouchShield.fillRect(0, 0, DisplayList.WIDTH, DisplayList.HEADER - 1, TftTouchShield.RED);
        Delay.millis(60);
        drawHeader();
    }

    /** The round's goal in the header's second row. */
    static void drawStatus() {
        int value = switch (Session.round) {
            case PROBES -> ProbesRound.probesLeft();
            case WALKERS -> WalkersRound.atatsLeft();
            default -> AsteroidsRound.distanceOut();
        };
        if (value == status) {
            return;
        }
        status = value;
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, DisplayList.SPACE);
        TftTouchShield.setCursor(86, 11);
        switch (Session.round) {
            case PROBES -> TftTouchShield.print("PROBE DROIDS ");
            case WALKERS -> TftTouchShield.print("AT-ATS LEFT ");
            default -> TftTouchShield.print("OUT OF THE FIELD ");
        }
        TftTouchShield.print(value);
        TftTouchShield.print("  ");
    }

    static void showCentered(String text, int y, int size, int color) {
        TftTouchShield.setTextSize(size);
        TftTouchShield.setTextColor(color, DisplayList.SPACE);
        TftTouchShield.setCursor((DisplayList.WIDTH - text.length() * 6 * size) / 2, y);
        TftTouchShield.print(text);
    }
}
