package io.github.jabrena.juno.games.startrek;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/** Touch input: steering, the three buttons, the header that hands the helm over, and the pilot screen. */
final class Controls {
    /** The HUMAN and CPU buttons of the pilot screen. */
    private static final int CHOICE_X = 20;
    private static final int CHOICE_Y = 104;
    private static final int CHOICE_WIDTH = 130;
    private static final int CHOICE_HEIGHT = 72;
    private static final int CHOICE_GAP = 20;
    /** {@link #pressed} while a finger is on the header. */
    private static final int HEADER_PRESSED = 3;

    static boolean steering;
    static int steerX;
    static int steerY;
    private static int pressed = -1;
    static boolean autopilot;

    private Controls() {
    }

    /**
     * Holding a finger in the tactical view turns the Enterprise towards it and runs the impulse
     * engines; the buttons act when pressed. Tapping the header hands the helm to the CPU or back.
     */
    static void handleTouch(int[] ents) {
        if (!TftTouchShield.readTouch()) {
            if (!autopilot) {
                steering = false;
            }
            pressed = -1;
            return;
        }
        int x = TftTouchShield.touchX();
        int y = TftTouchShield.touchY();
        if (y < DisplayList.HEADER) {
            if (pressed != HEADER_PRESSED) {
                autopilot = !autopilot;
                steering = false;
                Hud.invalidate();
            }
            pressed = HEADER_PRESSED;
            return;
        }
        if (autopilot) {
            pressed = -1;
            return;
        }
        if (x <= DisplayList.TACTICAL_RIGHT) {
            steering = true;
            steerX = x;
            steerY = y;
            pressed = -1;
            return;
        }
        steering = false;
        int button = -1;
        if (x >= DisplayList.PANEL_X && y >= DisplayList.BUTTON_Y) {
            button = Math.min(2, (y - DisplayList.BUTTON_Y) / DisplayList.BUTTON_HEIGHT);
        }
        if (button >= 0 && button != pressed) {
            if (button == 0) {
                Combat.firePhasers(ents);
            } else if (button == 1) {
                Combat.firePhoton(ents);
            } else {
                Enterprise.warp();
            }
        }
        pressed = button;
    }

    /** The pilot screen: waits for a tap on HUMAN or CPU. */
    static void choosePilot() {
        TftTouchShield.fillScreen(DisplayList.SPACE);
        Hud.showCentered("CHOOSE PILOT", 36, 3, TftTouchShield.YELLOW);
        Hud.showCentered("Who commands the Enterprise?", 74, 1, TftTouchShield.WHITE);
        drawChoice(0, "HUMAN", "You steer and fire", false);
        drawChoice(1, "CPU", "Autopilot plays", false);
        Hud.showCentered("Tap the header in game to switch", 206, 1, SceneRenderer.STAR);
        int choice = -1;
        while (choice < 0) {
            if (TftTouchShield.readTouch()) {
                choice = choiceAt(TftTouchShield.touchX(), TftTouchShield.touchY());
            }
            Delay.millis(10);
        }
        autopilot = choice == 1;
        drawChoice(choice, choice == 0 ? "HUMAN" : "CPU", choice == 0 ? "You steer and fire" : "Autopilot plays",
                true);
        waitForRelease();
    }

    /** The pilot button at (x, y): 0 for HUMAN, 1 for CPU, or -1. */
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

    private static void drawChoice(int index, String label, String hint, boolean chosen) {
        int x = CHOICE_X + index * (CHOICE_WIDTH + CHOICE_GAP);
        int color = chosen ? SceneRenderer.FRAME : SceneRenderer.BUTTON;
        TftTouchShield.fillRect(x, CHOICE_Y, CHOICE_WIDTH, CHOICE_HEIGHT, color);
        TftTouchShield.drawRect(x, CHOICE_Y, CHOICE_WIDTH, CHOICE_HEIGHT,
                chosen ? TftTouchShield.WHITE : SceneRenderer.FRAME);
        TftTouchShield.setTextSize(3);
        TftTouchShield.setTextColor(TftTouchShield.YELLOW, color);
        TftTouchShield.setCursor(x + (CHOICE_WIDTH - label.length() * 18) / 2, CHOICE_Y + 16);
        TftTouchShield.print(label);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, color);
        TftTouchShield.setCursor(x + (CHOICE_WIDTH - hint.length() * 6) / 2, CHOICE_Y + 52);
        TftTouchShield.print(hint);
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
