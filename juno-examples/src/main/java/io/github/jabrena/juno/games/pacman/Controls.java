package io.github.jabrena.juno.games.pacman;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/** Touch input and pilot selection; the CPU player itself lives in {@link AutopilotPacMan}. */
final class Controls {
    private static final int CHOICE_WIDTH = 100;
    private static final int CHOICE_HEIGHT = 60;
    private static final int CHOICE_GAP = 16;
    private static final int CHOICE_X = (Session.WIDTH - 2 * CHOICE_WIDTH - CHOICE_GAP) / 2;
    private static final int CHOICE_Y = 150;
    private static final int CHOICE = 0x2124;
    private static final int CHOICE_CHOSEN = 0x7800;

    static boolean autopilot;
    private static boolean headerTouching;

    private Controls() {
    }

    static void resetForLife() {
        headerTouching = false;
    }

    /** While held below the header, turns Pac-Man towards the finger; a tap on the header switches pilot. */
    static void steer() {
        if (!TftTouchShield.readTouch()) {
            headerTouching = false;
            return;
        }
        int x = TftTouchShield.touchX();
        int y = TftTouchShield.touchY();
        if (y < Maze.MAZE_Y) {
            if (!headerTouching) {
                autopilot = !autopilot;
            }
            headerTouching = true;
            return;
        }
        headerTouching = false;
        if (autopilot) {
            return;
        }
        int dx = x - (Maze.MAZE_X + Session.pacX);
        int dy = y - (Maze.MAZE_Y + Session.pacY);
        if (Math.abs(dx) < 6 && Math.abs(dy) < 6) {
            return;
        }
        if (Math.abs(dx) > Math.abs(dy)) {
            Session.pacNext = dx < 0 ? Session.LEFT : Session.RIGHT;
        } else {
            Session.pacNext = dy < 0 ? Session.UP : Session.DOWN;
        }
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
        // Tall enough to also erase the cover's "TAP TO CHOOSE A PILOT" / steering-hint lines below it.
        TftTouchShield.fillRect(0, CHOICE_Y - 40, Session.WIDTH, CHOICE_HEIGHT + 110, SceneRenderer.SPACE);
        Hud.showCentered("WHO EATS THE DOTS?", CHOICE_Y - 28, 1, TftTouchShield.YELLOW);
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

    /** Waits for a tap, giving up and returning {@code false} if none arrives within {@code timeoutMillis}. */
    static boolean waitForTap(int timeoutMillis) {
        int start = Clock.millis();
        while (!TftTouchShield.readTouch()) {
            if (Clock.millis() - start >= timeoutMillis) {
                return false;
            }
            Delay.millis(10);
        }
        waitForRelease();
        headerTouching = false;
        return true;
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
