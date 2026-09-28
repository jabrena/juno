package io.github.jabrena.juno.games.lunarlander;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/** Human buttons, pilot choice, header switching, and an automatic landing controller. */
final class Controls {
    static final int NONE = -1;
    static final int LEFT = 0;
    static final int BURN = 1;
    static final int RIGHT = 2;
    private static final int BUTTON_Y = 274;
    private static final int CHOICE_Y = 104;
    private static final int CHOICE_HEIGHT = 72;

    static boolean autopilot;
    private static boolean touching;
    private static int targetX;
    private static int targetY;

    private Controls() {
    }

    static void startDescent(short[] ground, byte[] pads) {
        targetX = reachablePadCenter(pads);
        targetY = ground[targetX];
        touching = false;
    }

    static int read(byte[] pads) {
        if (TftTouchShield.readTouch()) {
            int y = TftTouchShield.touchY();
            if (!touching && y < Hud.HEADER_HEIGHT) {
                autopilot = !autopilot;
                Hud.invalidate();
            }
            touching = true;
            if (!autopilot && y >= BUTTON_Y - 10) {
                return Math.min(2, TftTouchShield.touchX() * 3 / Terrain.WIDTH);
            }
        } else {
            touching = false;
        }
        return autopilot ? fly() : NONE;
    }

    /** A small feedback controller that aims for a reachable pad and bleeds off both speeds. */
    static int fly() {
        float error = targetX - Flight.x;
        Flight.tilt = 0;
        Flight.vx = Math.clamp(error * 0.03f, -0.55f, 0.55f);
        float safeDescent = Math.abs(error) > 8 ? 0.12f
                : Flight.y > targetY - 55 ? 0.28f : Flight.y > 125 ? 0.45f : 0.6f;
        return Flight.vy > safeDescent ? BURN : NONE;
    }

    private static int reachablePadCenter(byte[] pads) {
        int bestCenter = 120;
        int bestDistance = Terrain.WIDTH;
        int column = 0;
        while (column < Terrain.WIDTH) {
            if (pads[column] == 0) {
                column = column + 1;
            } else {
                int start = column;
                while (column < Terrain.WIDTH && pads[column] == pads[start]) {
                    column = column + 1;
                }
                int center = (start + column) / 2;
                int distance = Math.abs(center - 140);
                if (distance < bestDistance) {
                    bestCenter = center;
                    bestDistance = distance;
                }
            }
        }
        return bestCenter;
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
        if (x >= 8 && x < 112) {
            return 0;
        }
        return x >= 128 && x < 232 ? 1 : -1;
    }

    private static void drawPilotChoice(int selected) {
        TftTouchShield.fillScreen(Terrain.SKY);
        Hud.showCentered("CHOOSE PILOT", 55, 2, TftTouchShield.YELLOW);
        Hud.drawChoice(8, "HUMAN", selected == 0);
        Hud.drawChoice(128, "CPU", selected == 1);
        Hud.showCentered("Tap header to switch", 205, 1, TftTouchShield.CYAN);
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
