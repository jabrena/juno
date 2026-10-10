package io.github.jabrena.juno.games.doom;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * Pilot selection and touch controls. A HUMAN marine turns with the left and right thirds of the
 * view, walks forward with the upper half of the middle and fires the pistol with its lower half; the
 * CPU marine walks the map's route and shoots what it meets. Tapping the CPU/HUMAN label switches
 * between the two at any time.
 */
final class Controls {
    private static final float TURN = 0.09f;
    private static final float STRIDE = 8f;

    private static final int CHOICE_X = 20;
    private static final int CHOICE_Y = 104;
    private static final int CHOICE_WIDTH = 130;
    private static final int CHOICE_HEIGHT = 72;
    private static final int CHOICE_GAP = 20;
    private static final int PILOT_LEFT = 276;
    private static final int PILOT_TOP = DisplayList.VIEW_BOTTOM + 11;
    private static final int PILOT_BOTTOM = DisplayList.VIEW_BOTTOM + 26;
    static final int CHOICE = 0x2124;
    static final int CHOICE_CHOSEN = 0x7800;

    static boolean autopilot = true;
    private static boolean barPressed;

    private Controls() {
    }

    /** Asks who walks the map, HUMAN or CPU, and waits for the answer. */
    static void choosePilot() {
        TftTouchShield.fillScreen(DisplayList.BACKGROUND);
        Hud.showCentered("CHOOSE PILOT", 36, 3, TftTouchShield.RED);
        if (World.fromWad) {
            Hud.showCentered("Who walks the episode?", 74, 1, TftTouchShield.WHITE);
        } else {
            Hud.showCentered("Who walks " + Level.NAME + "?", 74, 1, TftTouchShield.WHITE);
        }
        drawChoice(0, "HUMAN", "You walk and shoot", false);
        drawChoice(1, "CPU", "Autopilot plays", false);
        Hud.showCentered("Tap CPU/HUMAN in game to switch", 206, 1, Renderer.WALL_FAR);
        int choice = -1;
        while (choice < 0) {
            if (TftTouchShield.readTouch()) {
                choice = choiceAt(TftTouchShield.touchX(), TftTouchShield.touchY());
            }
            Delay.millis(10);
        }
        autopilot = choice == 1;
        drawChoice(choice, choice == 0 ? "HUMAN" : "CPU", choice == 0 ? "You walk and shoot" : "Autopilot plays", true);
        waitForRelease();
        Interludes.flood();
        barPressed = false;
    }

    /** The pilot box at ({@code x}, {@code y}): 0 for HUMAN, 1 for CPU, -1 for neither. */
    static int choiceAt(int x, int y) {
        if (y < CHOICE_Y || y >= CHOICE_Y + CHOICE_HEIGHT) {
            return -1;
        }
        for (int index = 0; index < 2; index++) {
            int left = CHOICE_X + index * (CHOICE_WIDTH + CHOICE_GAP);
            if (x >= left && x < left + CHOICE_WIDTH) {
                return index;
            }
        }
        return -1;
    }

    /** Switches the pilot when ({@code x}, {@code y}) is on the CPU/HUMAN label. */
    static boolean switchPilotAt(int x, int y, short[] ceilings) {
        if (x < PILOT_LEFT || x >= DisplayList.WIDTH || y < PILOT_TOP || y >= PILOT_BOTTOM) {
            return false;
        }
        autopilot = !autopilot;
        if (autopilot) {
            Autopilot.resume(ceilings);
        }
        return true;
    }

    /** Applies this frame's touch; returns whether the status bar needs redrawing. */
    static boolean handle(short[] ceilings, short[] monsters) {
        if (!TftTouchShield.readTouch()) {
            barPressed = false;
            return false;
        }
        int x = TftTouchShield.touchX();
        int y = TftTouchShield.touchY();
        if (x >= PILOT_LEFT && y >= PILOT_TOP && y < PILOT_BOTTOM) {
            boolean toggle = !barPressed;
            barPressed = true;
            return toggle && switchPilotAt(x, y, ceilings);
        }
        barPressed = false;
        if (y >= DisplayList.VIEW_BOTTOM) {
            return false;
        }
        if (autopilot) {
            return false;
        }
        if (x < DisplayList.WIDTH / 3) {
            Player.turn(TURN);
        } else if (x >= 2 * DisplayList.WIDTH / 3) {
            Player.turn(-TURN);
        } else if (y < (DisplayList.VIEW_TOP + DisplayList.VIEW_BOTTOM) / 2) {
            Player.walk(STRIDE, ceilings);
        } else {
            Weapon.fire(monsters, ceilings);
        }
        return false;
    }

    /** Waits for a tap anywhere and its release. */
    static void waitForTap() {
        while (!TftTouchShield.readTouch()) {
            Delay.millis(10);
        }
        waitForRelease();
    }

    static void waitForRelease() {
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

    private static void drawChoice(int index, String label, String hint, boolean chosen) {
        int x = CHOICE_X + index * (CHOICE_WIDTH + CHOICE_GAP);
        int color = chosen ? CHOICE_CHOSEN : CHOICE;
        TftTouchShield.fillRect(x, CHOICE_Y, CHOICE_WIDTH, CHOICE_HEIGHT, color);
        TftTouchShield.drawRect(x, CHOICE_Y, CHOICE_WIDTH, CHOICE_HEIGHT,
                chosen ? TftTouchShield.WHITE : Renderer.WALL_FAR);
        TftTouchShield.setTextSize(3);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, color);
        TftTouchShield.setCursor(x + (CHOICE_WIDTH - label.length() * 18) / 2, CHOICE_Y + 16);
        TftTouchShield.print(label);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.YELLOW, color);
        TftTouchShield.setCursor(x + (CHOICE_WIDTH - hint.length() * 6) / 2, CHOICE_Y + 52);
        TftTouchShield.print(hint);
    }
}
