package io.github.jabrena.juno.games.tempest;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.util.Delay;

/** Touch input and pilot selection; the CPU player itself lives in {@link AutopilotTempest}. */
final class Controls {
    private static final int CHOICE_WIDTH = 100;
    private static final int CHOICE_HEIGHT = 60;
    private static final int CHOICE_GAP = 16;
    private static final int CHOICE_X = (Hud.WIDTH - 2 * CHOICE_WIDTH - CHOICE_GAP) / 2;
    private static final int CHOICE_Y = 150;
    private static final int CHOICE = 0x2124;
    private static final int CHOICE_CHOSEN = 0x7800;

    static boolean autopilot;
    private static boolean touching;

    private Controls() {
    }

    static void resetForLevel() {
        touching = false;
    }

    static void update(TftTouchShield tft, int[] tube, int[] shots) {
        if (tft.readTouch()) {
            handleTouchDown(tft, tube, shots);
        } else {
            touching = false;
        }
    }

    private static void handleTouchDown(TftTouchShield tft, int[] tube, int[] shots) {
        int x = tft.touchX();
        int y = tft.touchY();
        if (!touching && y < Hud.HEADER) {
            autopilot = !autopilot;
        } else if (!autopilot) {
            aimAndFire(tft, tube, shots, x, y);
        }
        touching = true;
    }

    private static void aimAndFire(TftTouchShield tft, int[] tube, int[] shots, int x, int y) {
        int dx = x - Tube.CENTER_X;
        int dy = y - Tube.CENTER_Y;
        if (dx * dx + dy * dy <= Tube.ZAPPER_RADIUS * Tube.ZAPPER_RADIUS) {
            Session.requestZap();
            return;
        }
        Session.moveToward(tft, tube, Tube.nearestLane(tube, x, y));
        Session.fire(shots);
    }

    // ---- Pilot choice ----

    static void choosePilot(TftTouchShield tft) {
        drawPilotChoice(tft, -1);
        waitForRelease(tft);
        int choice = -1;
        while (choice < 0) {
            if (tft.readTouch()) {
                choice = choiceAt(tft.touchX(), tft.touchY());
            }
            Delay.millis(10);
        }
        autopilot = choice == 1;
        drawPilotChoice(tft, choice);
        Delay.millis(350);
        waitForRelease(tft);
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

    private static void drawPilotChoice(TftTouchShield tft, int selected) {
        tft.fillRect(0, CHOICE_Y - 40, Hud.WIDTH, CHOICE_HEIGHT + 70, Hud.SPACE);
        Hud.showCentered(tft, "WHO RUNS THE CLAW?", CHOICE_Y - 28, 1, TftTouchShield.YELLOW);
        drawChoice(tft, CHOICE_X, "HUMAN", selected == 0);
        drawChoice(tft, CHOICE_X + CHOICE_WIDTH + CHOICE_GAP, "CPU", selected == 1);
        Hud.showCentered(tft, "Tap the header to switch", CHOICE_Y + CHOICE_HEIGHT + 16, 1, TftTouchShield.CYAN);
    }

    private static void drawChoice(TftTouchShield tft, int x, String label, boolean selected) {
        int color = selected ? CHOICE_CHOSEN : CHOICE;
        tft.fillRect(x, CHOICE_Y, CHOICE_WIDTH, CHOICE_HEIGHT, color);
        tft.drawRect(x, CHOICE_Y, CHOICE_WIDTH, CHOICE_HEIGHT, TftTouchShield.WHITE);
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.WHITE, color);
        tft.setCursor(x + (CHOICE_WIDTH - label.length() * 12) / 2, CHOICE_Y + 22);
        tft.print(label);
    }

    // ---- Taps ----

    static void waitForTap(TftTouchShield tft) {
        while (!tft.readTouch()) {
            Delay.millis(10);
        }
        waitForRelease(tft);
    }

    private static void waitForRelease(TftTouchShield tft) {
        int misses = 0;
        while (misses < 3) {
            if (tft.readTouch()) {
                misses = 0;
            } else {
                misses = misses + 1;
            }
            Delay.millis(10);
        }
    }
}
