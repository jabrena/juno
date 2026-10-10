package io.github.jabrena.juno.games.startrek;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;

import static io.github.jabrena.juno.games.startrek.DisplayList.BRIDGE_BOTTOM;
import static io.github.jabrena.juno.games.startrek.DisplayList.BRIDGE_TOP;
import static io.github.jabrena.juno.games.startrek.DisplayList.BUTTON_HEIGHT;
import static io.github.jabrena.juno.games.startrek.DisplayList.BUTTON_Y;
import static io.github.jabrena.juno.games.startrek.DisplayList.HEADER;
import static io.github.jabrena.juno.games.startrek.DisplayList.HEIGHT;
import static io.github.jabrena.juno.games.startrek.DisplayList.PANEL_X;
import static io.github.jabrena.juno.games.startrek.DisplayList.SPACE;
import static io.github.jabrena.juno.games.startrek.DisplayList.TACTICAL_RIGHT;
import static io.github.jabrena.juno.games.startrek.DisplayList.WIDTH;
import static io.github.jabrena.juno.games.startrek.Entities.T_KLINGON;
import static io.github.jabrena.juno.games.startrek.Entities.T_NOMAD;
import static io.github.jabrena.juno.games.startrek.SceneRenderer.BUTTON;
import static io.github.jabrena.juno.games.startrek.SceneRenderer.BUTTON_EMPTY;
import static io.github.jabrena.juno.games.startrek.SceneRenderer.FRAME;

/** The header with the score, sector and shields, the panel of buttons, and centered text. */
final class Hud {
    private static int status;
    private static int shownScore;
    static boolean headerFlashed;

    private Hud() {
    }

    /** Makes the next {@link #drawStatus} redraw the status line even if nothing it shows changed. */
    static void invalidate() {
        status = -1;
    }

    /** The bridge window's frame and the three buttons, with the torpedoes and warps left. */
    static void drawPanel() {
        TftTouchShield.drawRect(PANEL_X, BRIDGE_TOP - 1, WIDTH - PANEL_X, BRIDGE_BOTTOM - BRIDGE_TOP + 2, FRAME);
        TftTouchShield.drawVerticalLine(TACTICAL_RIGHT + 1, HEADER, HEIGHT - HEADER, FRAME);
        drawButton(0, "PHASER", -1, true);
        drawButton(1, "PHOTON", Enterprise.photons, Enterprise.photons > 0);
        drawButton(2, "WARP", Enterprise.warps, Enterprise.warps > 0);
    }

    private static void drawButton(int index, String label, int count, boolean ready) {
        int y = BUTTON_Y + index * BUTTON_HEIGHT;
        int color = ready ? BUTTON : BUTTON_EMPTY;
        TftTouchShield.fillRect(PANEL_X + 2, y + 2, WIDTH - PANEL_X - 4, BUTTON_HEIGHT - 4, color);
        TftTouchShield.drawRect(PANEL_X + 2, y + 2, WIDTH - PANEL_X - 4, BUTTON_HEIGHT - 4, FRAME);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(ready ? TftTouchShield.YELLOW : TftTouchShield.GRAY, color);
        TftTouchShield.setCursor(PANEL_X + (WIDTH - PANEL_X - label.length() * 12) / 2, y + 8);
        TftTouchShield.print(label);
        if (count >= 0) {
            TftTouchShield.setTextSize(1);
            TftTouchShield.setTextColor(TftTouchShield.WHITE, color);
            TftTouchShield.setCursor(PANEL_X + 44, y + 28);
            TftTouchShield.print(count);
        }
    }

    static void drawHeader(int[] ents) {
        headerFlashed = false;
        TftTouchShield.fillRect(0, 0, WIDTH, HEADER - 1, SPACE);
        TftTouchShield.drawHorizontalLine(0, HEADER - 1, WIDTH, FRAME);
        status = -1;
        drawStatus(ents);
    }

    /** A hit flashes the header red until the next frame redraws it. */
    static void flashHit() {
        TftTouchShield.fillRect(0, 0, WIDTH, HEADER - 1, TftTouchShield.RED);
        Delay.millis(40);
        headerFlashed = true;
    }

    /** Score, sector, what is left to destroy, and the shields. */
    static void drawStatus(int[] ents) {
        int klingons = Entities.count(ents, T_KLINGON) + Entities.count(ents, T_NOMAD);
        int shields = Enterprise.shields;
        int combined = klingons * 1000 + shields + (Session.baseLost ? 100000 : 0)
                + (Controls.autopilot ? 200000 : 0);
        if (combined == status && Session.score == shownScore) {
            return;
        }
        status = combined;
        shownScore = Session.score;
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.YELLOW, SPACE);
        TftTouchShield.setCursor(4, 2);
        TftTouchShield.print("SCORE ");
        TftTouchShield.print(Session.score);
        TftTouchShield.print("   ");
        TftTouchShield.setTextColor(TftTouchShield.CYAN, SPACE);
        TftTouchShield.setCursor(136, 2);
        TftTouchShield.print("HI ");
        TftTouchShield.print(Session.best);
        TftTouchShield.setCursor(250, 2);
        TftTouchShield.print("SECTOR ");
        TftTouchShield.print(Session.sector);
        int color = TftTouchShield.GREEN;
        if (shields <= 25) {
            color = TftTouchShield.RED;
        } else if (shields <= 50) {
            color = TftTouchShield.YELLOW;
        }
        TftTouchShield.setTextColor(color, SPACE);
        TftTouchShield.setCursor(4, 11);
        TftTouchShield.print("SHIELDS ");
        TftTouchShield.print(shields);
        TftTouchShield.print("%  ");
        TftTouchShield.setTextColor(TftTouchShield.WHITE, SPACE);
        TftTouchShield.setCursor(136, 11);
        TftTouchShield.print("ENEMY SHIPS ");
        TftTouchShield.print(klingons);
        TftTouchShield.print("  ");
        TftTouchShield.setTextColor(TftTouchShield.MAGENTA, SPACE);
        TftTouchShield.setCursor(228, 11);
        TftTouchShield.print(Controls.autopilot ? "CPU" : "   ");
        if (Session.baseLost) {
            TftTouchShield.setTextColor(TftTouchShield.RED, SPACE);
            TftTouchShield.setCursor(250, 11);
            TftTouchShield.print("BASE LOST");
        }
    }

    static void showCentered(String text, int y, int size, int color) {
        TftTouchShield.setTextSize(size);
        TftTouchShield.setTextColor(color, SPACE);
        TftTouchShield.setCursor((WIDTH - text.length() * 6 * size) / 2, y);
        TftTouchShield.print(text);
    }
}
