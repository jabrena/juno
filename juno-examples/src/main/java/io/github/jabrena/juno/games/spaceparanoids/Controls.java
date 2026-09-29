package io.github.jabrena.juno.games.spaceparanoids;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/** Bottom-row drive buttons, pilot choice, and switching between human and CPU control. */
final class Controls {
    static final float TURN = 0.075f;
    static final float SPEED = 0.07f;

    private static final int B_NONE = 0;
    private static final int B_LEFT = 1;
    private static final int B_FORWARD = 2;
    private static final int B_FIRE = 3;
    private static final int B_BACK = 4;
    private static final int B_RIGHT = 5;

    private static final int CHOICE_X = 20;
    private static final int CHOICE_Y = 104;
    private static final int CHOICE_WIDTH = 130;
    private static final int CHOICE_HEIGHT = 72;
    private static final int CHOICE_GAP = 20;

    static int held;
    static boolean autopilot;
    private static boolean headerPressed;

    private Controls() {
    }

    static void handleTouch(byte[] maze, int[] fs, int[] is) {
        Combat.tickCooldown();
        held = B_NONE;
        if (TftTouchShield.readTouch()) {
            int x = TftTouchShield.touchX();
            int y = TftTouchShield.touchY();
            if (y < DisplayList.HEADER) {
                if (!headerPressed) {
                    autopilot = !autopilot;
                    Hud.drawHeader();
                }
                headerPressed = true;
                return;
            }
            headerPressed = false;
            if (autopilot) {
                return;
            }
            held = y >= DisplayList.BAR_TOP ? buttonAt(x) : B_FIRE;
        } else {
            headerPressed = false;
        }
        if (held == B_LEFT) {
            Camera.setAngle(Camera.angle - TURN);
        } else if (held == B_RIGHT) {
            Camera.setAngle(Camera.angle + TURN);
        } else if (held == B_FORWARD) {
            Camera.drive(maze, SPEED);
        } else if (held == B_BACK) {
            Camera.drive(maze, -SPEED * 0.7f);
        } else if (held == B_FIRE) {
            Combat.fire(fs, is);
        }
    }

    static void release() {
        held = B_NONE;
    }

    private static int buttonAt(int x) {
        if (x < 50) {
            return B_LEFT;
        }
        if (x < 100) {
            return B_FORWARD;
        }
        if (x < 176) {
            return B_FIRE;
        }
        if (x < 222) {
            return B_NONE;
        }
        if (x < 271) {
            return B_BACK;
        }
        return B_RIGHT;
    }

    static void choosePilot() {
        TftTouchShield.fillScreen(DisplayList.SPACE);
        Hud.showCentered("CHOOSE PILOT", 36, 3, TftTouchShield.ORANGE);
        Hud.showCentered("Who drives the tank?", 74, 1, TftTouchShield.WHITE);
        drawChoice(0, "HUMAN", "You drive and fire", false);
        drawChoice(1, "CPU", "Autopilot plays", false);
        Hud.showCentered("Tap the header in game to switch", 206, 1, SceneRenderer.WALL_SEAM);
        int choice = -1;
        while (choice < 0) {
            if (TftTouchShield.readTouch()) {
                choice = choiceAt(TftTouchShield.touchX(), TftTouchShield.touchY());
            }
            Delay.millis(10);
        }
        autopilot = choice == 1;
        drawChoice(choice, choice == 0 ? "HUMAN" : "CPU",
                choice == 0 ? "You drive and fire" : "Autopilot plays", true);
        waitForRelease();
    }

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
        int color = chosen ? Hud.BUTTON : 0x2124;
        TftTouchShield.fillRect(x, CHOICE_Y, CHOICE_WIDTH, CHOICE_HEIGHT, color);
        TftTouchShield.drawRect(x, CHOICE_Y, CHOICE_WIDTH, CHOICE_HEIGHT,
                chosen ? TftTouchShield.WHITE : SceneRenderer.WALL_TOP);
        TftTouchShield.setTextSize(3);
        TftTouchShield.setTextColor(TftTouchShield.ORANGE, color);
        TftTouchShield.setCursor(x + (CHOICE_WIDTH - label.length() * 18) / 2, CHOICE_Y + 16);
        TftTouchShield.print(label);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, color);
        TftTouchShield.setCursor(x + (CHOICE_WIDTH - hint.length() * 6) / 2, CHOICE_Y + 52);
        TftTouchShield.print(hint);
    }

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
