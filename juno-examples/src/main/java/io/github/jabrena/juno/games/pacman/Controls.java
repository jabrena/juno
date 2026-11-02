package io.github.jabrena.juno.games.pacman;

import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;

/** Touch input and pilot selection; the CPU player itself lives in {@link AutopilotPacMan}. */
final class Controls {
    private static final Clock CLOCK = ArduinoUno.getClock();
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
    static void steer(TftTouchShield tft) {
        if (!tft.readTouch()) {
            headerTouching = false;
            return;
        }
        int x = tft.touchX();
        int y = tft.touchY();
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
        // Tall enough to also erase the cover's "TAP TO CHOOSE A PILOT" / steering-hint lines below it.
        tft.fillRect(0, CHOICE_Y - 40, Session.WIDTH, CHOICE_HEIGHT + 110, SceneRenderer.SPACE);
        Hud.showCentered(tft, "WHO EATS THE DOTS?", CHOICE_Y - 28, 1, TftTouchShield.YELLOW);
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

    /** Waits for a tap, giving up and returning {@code false} if none arrives within {@code timeoutMillis}. */
    static boolean waitForTap(TftTouchShield tft, int timeoutMillis) {
        int start = CLOCK.millis();
        while (!tft.readTouch()) {
            if (CLOCK.millis() - start >= timeoutMillis) {
                return false;
            }
            Delay.millis(10);
        }
        waitForRelease(tft);
        headerTouching = false;
        return true;
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
