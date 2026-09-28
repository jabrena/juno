package io.github.jabrena.juno.games.sundance;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/** Touch input, pilot selection, and the deliberately imperfect CPU player. */
final class Controls {
    private static final int HATCH_FRAMES = 12;
    private static final int MAX_OPEN = 2;
    private static final int CPU_TAP_INTERVAL = 6;
    private static final int CPU_LOOKAHEAD = 8;
    private static final int CPU_MISS_PERCENT = 24;
    private static final int CHOICE_X = 20;
    private static final int CHOICE_Y = 104;
    private static final int CHOICE_WIDTH = 130;
    private static final int CHOICE_HEIGHT = 72;
    private static final int CHOICE_GAP = 20;
    private static final int CHOICE = 0x2124;
    private static final int CHOICE_CHOSEN = 0x7800;

    static boolean autopilot;
    private static boolean touching;
    private static int lastCpuTap;

    private Controls() {
    }

    static void startRound() {
        touching = false;
        lastCpuTap = -100;
    }

    static void update(int[] suns, int[] hatches) {
        if (TftTouchShield.readTouch()) {
            int x = TftTouchShield.touchX();
            int y = TftTouchShield.touchY();
            if (!touching && y < Hud.HEADER) {
                autopilot = !autopilot;
                Hud.invalidate();
            } else if (!touching && !autopilot) {
                openAt(hatches, x, y);
            }
            touching = true;
        } else {
            touching = false;
        }
        if (autopilot) {
            fly(suns, hatches);
        }
    }

    private static void openAt(int[] hatches, int x, int y) {
        int cell = Grid.cellAt(x, y);
        if (cell >= 0) {
            openHatch(hatches, cell);
        }
    }

    /** Opens a hatch; if too many are open, the one closest to closing shuts first. */
    static void openHatch(int[] hatches, int cell) {
        if (hatches[cell] == 0 && openCount(hatches) >= MAX_OPEN) {
            hatches[oldestOpen(hatches)] = 0;
        }
        hatches[cell] = HATCH_FRAMES;
    }

    private static int oldestOpen(int[] hatches) {
        int oldest = -1;
        for (int cell = 0; cell < Grid.CELLS; cell++) {
            if (hatches[cell] > 0 && (oldest < 0 || hatches[cell] < hatches[oldest])) {
                oldest = cell;
            }
        }
        return oldest;
    }

    private static int openCount(int[] hatches) {
        int total = 0;
        for (int cell = 0; cell < Grid.CELLS; cell++) {
            if (hatches[cell] > 0) {
                total = total + 1;
            }
        }
        return total;
    }

    /** Opens an imminent landing hatch, with occasional human-like wrong taps. */
    static void fly(int[] suns, int[] hatches) {
        if (Session.frame - lastCpuTap < CPU_TAP_INTERVAL) {
            return;
        }
        int cell = nextLanding(suns, hatches);
        if (cell < 0) {
            return;
        }
        if (Random.nextInt(100) < CPU_MISS_PERCENT) {
            cell = (cell + 1 + Random.nextInt(Grid.CELLS - 1)) % Grid.CELLS;
        }
        openHatch(hatches, cell);
        lastCpuTap = Session.frame;
    }

    private static int nextLanding(int[] suns, int[] hatches) {
        int best = -1;
        int soonest = CPU_LOOKAHEAD;
        for (int slot = 0; slot < Session.SUNS; slot++) {
            int base = slot * Session.S_STRIDE;
            int left = suns[base + Session.S_DUR] - suns[base + Session.S_T];
            int target = suns[base + Session.S_TO];
            if (suns[base + Session.S_STATE] == Session.FLYING && left <= soonest && hatches[target] == 0) {
                best = target;
                soonest = left;
            }
        }
        return best;
    }

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
        TftTouchShield.fillScreen(TftTouchShield.BLACK);
        Hud.showCentered("WHO OPENS THE HATCHES?", 55, 2, TftTouchShield.YELLOW);
        drawChoice(CHOICE_X, "HUMAN", selected == 0);
        drawChoice(CHOICE_X + CHOICE_WIDTH + CHOICE_GAP, "CPU", selected == 1);
        Hud.showCentered("Tap the header to switch", 205, 1, TftTouchShield.CYAN);
    }

    private static void drawChoice(int x, String label, boolean selected) {
        int color = selected ? CHOICE_CHOSEN : CHOICE;
        TftTouchShield.fillRect(x, CHOICE_Y, CHOICE_WIDTH, CHOICE_HEIGHT, color);
        TftTouchShield.drawRect(x, CHOICE_Y, CHOICE_WIDTH, CHOICE_HEIGHT, TftTouchShield.WHITE);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, color);
        TftTouchShield.setCursor(x + (CHOICE_WIDTH - label.length() * 12) / 2, CHOICE_Y + 28);
        TftTouchShield.print(label);
    }

    static void waitForTap() {
        while (!TftTouchShield.readTouch()) {
            Delay.millis(10);
        }
        waitForRelease();
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
