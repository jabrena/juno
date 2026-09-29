package io.github.jabrena.juno.games.missilecommand;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/** Touch input and pilot selection; the CPU player itself lives in {@link AutopilotMissileCommand}. */
final class Controls {
    private static final int CHOICE_WIDTH = 100;
    private static final int CHOICE_HEIGHT = 60;
    private static final int CHOICE_GAP = 16;
    private static final int CHOICE_X = (Session.WIDTH - 2 * CHOICE_WIDTH - CHOICE_GAP) / 2;
    private static final int CHOICE_Y = 150;
    private static final int CHOICE = 0x2124;
    private static final int CHOICE_CHOSEN = 0x7800;

    static boolean autopilot;
    private static boolean touching;
    private static int releaseMisses;

    private Controls() {
    }

    static void resetForWave() {
        touching = false;
        releaseMisses = 0;
    }

    /** Fires on each new press in the sky; holding a touch does not repeat. */
    static void pollTouch(int[] shots, int[] ammo) {
        if (!TftTouchShield.readTouch()) {
            releaseMisses = releaseMisses + 1;
            if (releaseMisses >= 3) {
                touching = false;
            }
            return;
        }
        releaseMisses = 0;
        if (touching) {
            return;
        }
        touching = true;
        int x = TftTouchShield.touchX();
        int y = TftTouchShield.touchY();
        if (y < Session.HEADER) {
            autopilot = !autopilot;
            return;
        }
        if (autopilot) {
            return;
        }
        int clampedX = Math.max(3, Math.min(x, Session.WIDTH - 4));
        int clampedY = Math.max(Session.HEADER + 4, Math.min(y, Session.LOWEST_TARGET_Y));
        Session.fire(shots, ammo, clampedX, clampedY);
    }

    // ---- Pilot choice ----

    static void choosePilot() {
        drawPilotChoice(-1);
        waitForRelease();
        int choice = -1;
        while (choice < 0) {
            if (TftTouchShield.readTouch()) {
                choice = choiceAt(TftTouchShield.touchX(), TftTouchShield.touchY());
            }
            Delay.millis(10);
        }
        autopilot = choice == 1;
        drawPilotChoice(choice);
        Delay.millis(350);
        waitForRelease();
    }

    static int choiceAt(int x, int y) {
        if (y < CHOICE_Y || y >= CHOICE_Y + CHOICE_HEIGHT) {
            return -1;
        }
        if (x >= CHOICE_X && x < CHOICE_X + CHOICE_WIDTH) {
            return 0;
        }
        int cpuX = CHOICE_X + CHOICE_WIDTH + CHOICE_GAP;
        return x >= cpuX && x < cpuX + CHOICE_WIDTH ? 1 : -1;
    }

    private static void drawPilotChoice(int selected) {
        TftTouchShield.fillRect(0, CHOICE_Y - 40, Session.WIDTH, CHOICE_HEIGHT + 70, SceneRenderer.SKY);
        Hud.showCentered("WHO MANS THE BATTERY?", CHOICE_Y - 28, 1, TftTouchShield.YELLOW);
        drawChoice(CHOICE_X, "HUMAN", selected == 0);
        drawChoice(CHOICE_X + CHOICE_WIDTH + CHOICE_GAP, "CPU", selected == 1);
        Hud.showCentered("Tap the header to switch", CHOICE_Y + CHOICE_HEIGHT + 16, 1, TftTouchShield.CYAN);
    }

    private static void drawChoice(int x, String label, boolean selected) {
        int color = selected ? CHOICE_CHOSEN : CHOICE;
        TftTouchShield.fillRect(x, CHOICE_Y, CHOICE_WIDTH, CHOICE_HEIGHT, color);
        TftTouchShield.drawRect(x, CHOICE_Y, CHOICE_WIDTH, CHOICE_HEIGHT, TftTouchShield.WHITE);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, color);
        TftTouchShield.setCursor(x + (CHOICE_WIDTH - label.length() * 12) / 2, CHOICE_Y + 22);
        TftTouchShield.print(label);
    }

    // ---- Taps ----

    static void waitForTap() {
        while (!TftTouchShield.readTouch()) {
            Delay.millis(10);
        }
        waitForRelease();
        touching = false;
    }

    private static void waitForRelease() {
        int misses = 0;
        while (misses < 3) {
            if (TftTouchShield.readTouch()) {
                misses = 0;
            } else {
                misses = misses + 1;
            }
            Delay.millis(10);
        }
    }
}
