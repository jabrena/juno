package io.github.jabrena.juno.games.startrek;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.util.Delay;

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
    static void drawPanel(TftTouchShield tft) {
        tft.drawRect(PANEL_X, BRIDGE_TOP - 1, WIDTH - PANEL_X, BRIDGE_BOTTOM - BRIDGE_TOP + 2, FRAME);
        tft.drawVerticalLine(TACTICAL_RIGHT + 1, HEADER, HEIGHT - HEADER, FRAME);
        drawButton(tft, 0, "PHASER", -1, true);
        drawButton(tft, 1, "PHOTON", Enterprise.photons, Enterprise.photons > 0);
        drawButton(tft, 2, "WARP", Enterprise.warps, Enterprise.warps > 0);
    }

    private static void drawButton(TftTouchShield tft, int index, String label, int count, boolean ready) {
        int y = BUTTON_Y + index * BUTTON_HEIGHT;
        int color = ready ? BUTTON : BUTTON_EMPTY;
        tft.fillRect(PANEL_X + 2, y + 2, WIDTH - PANEL_X - 4, BUTTON_HEIGHT - 4, color);
        tft.drawRect(PANEL_X + 2, y + 2, WIDTH - PANEL_X - 4, BUTTON_HEIGHT - 4, FRAME);
        tft.setTextSize(2);
        tft.setTextColor(ready ? TftTouchShield.YELLOW : TftTouchShield.GRAY, color);
        tft.setCursor(PANEL_X + (WIDTH - PANEL_X - label.length() * 12) / 2, y + 8);
        tft.print(label);
        if (count >= 0) {
            tft.setTextSize(1);
            tft.setTextColor(TftTouchShield.WHITE, color);
            tft.setCursor(PANEL_X + 44, y + 28);
            tft.print(count);
        }
    }

    static void drawHeader(TftTouchShield tft, int[] ents) {
        headerFlashed = false;
        tft.fillRect(0, 0, WIDTH, HEADER - 1, SPACE);
        tft.drawHorizontalLine(0, HEADER - 1, WIDTH, FRAME);
        status = -1;
        drawStatus(tft, ents);
    }

    /** A hit flashes the header red until the next frame redraws it. */
    static void flashHit(TftTouchShield tft) {
        tft.fillRect(0, 0, WIDTH, HEADER - 1, TftTouchShield.RED);
        Delay.millis(40);
        headerFlashed = true;
    }

    /** Score, sector, what is left to destroy, and the shields. */
    static void drawStatus(TftTouchShield tft, int[] ents) {
        int klingons = Entities.count(ents, T_KLINGON) + Entities.count(ents, T_NOMAD);
        int shields = Enterprise.shields;
        int combined = klingons * 1000 + shields + (Session.baseLost ? 100000 : 0)
                + (Controls.autopilot ? 200000 : 0);
        if (combined == status && Session.score == shownScore) {
            return;
        }
        status = combined;
        shownScore = Session.score;
        tft.setTextSize(1);
        tft.setTextColor(TftTouchShield.YELLOW, SPACE);
        tft.setCursor(4, 2);
        tft.print("SCORE ");
        tft.print(Session.score);
        tft.print("   ");
        tft.setTextColor(TftTouchShield.CYAN, SPACE);
        tft.setCursor(136, 2);
        tft.print("HI ");
        tft.print(Session.best);
        tft.setCursor(250, 2);
        tft.print("SECTOR ");
        tft.print(Session.sector);
        int color = TftTouchShield.GREEN;
        if (shields <= 25) {
            color = TftTouchShield.RED;
        } else if (shields <= 50) {
            color = TftTouchShield.YELLOW;
        }
        tft.setTextColor(color, SPACE);
        tft.setCursor(4, 11);
        tft.print("SHIELDS ");
        tft.print(shields);
        tft.print("%  ");
        tft.setTextColor(TftTouchShield.WHITE, SPACE);
        tft.setCursor(136, 11);
        tft.print("ENEMY SHIPS ");
        tft.print(klingons);
        tft.print("  ");
        tft.setTextColor(TftTouchShield.MAGENTA, SPACE);
        tft.setCursor(228, 11);
        tft.print(Controls.autopilot ? "CPU" : "   ");
        if (Session.baseLost) {
            tft.setTextColor(TftTouchShield.RED, SPACE);
            tft.setCursor(250, 11);
            tft.print("BASE LOST");
        }
    }

    static void showCentered(TftTouchShield tft, String text, int y, int size, int color) {
        tft.setTextSize(size);
        tft.setTextColor(color, SPACE);
        tft.setCursor((WIDTH - text.length() * 6 * size) / 2, y);
        tft.print(text);
    }
}
