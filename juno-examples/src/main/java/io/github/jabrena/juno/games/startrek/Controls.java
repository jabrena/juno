package io.github.jabrena.juno.games.startrek;

import io.github.jabrena.juno.games.common.PilotMenu;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;

/** Touch input: steering, the three buttons, the header that hands the helm over, and the pilot screen. */
final class Controls {
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
        int choice = PilotMenu.waitForChoice();
        autopilot = choice == 1;
        drawChoice(choice, choice == 0 ? "HUMAN" : "CPU", choice == 0 ? "You steer and fire" : "Autopilot plays",
                true);
        PilotMenu.waitForRelease();
    }

    /** The pilot button at (x, y): 0 for HUMAN, 1 for CPU, or -1. */
    static int choiceAt(int x, int y) {
        return PilotMenu.choiceAt(x, y);
    }

    private static void drawChoice(int index, String label, String hint, boolean chosen) {
        PilotMenu.drawChoice(index, label, hint, chosen, SceneRenderer.FRAME, SceneRenderer.BUTTON,
                TftTouchShield.WHITE, SceneRenderer.FRAME, TftTouchShield.YELLOW, TftTouchShield.WHITE);
    }

    /** Waits for a tap, giving up and returning {@code false} if none arrives within {@code timeoutMillis}. */
    static boolean waitForTap(int timeoutMillis) {
        return PilotMenu.waitForTap(timeoutMillis);
    }
}
