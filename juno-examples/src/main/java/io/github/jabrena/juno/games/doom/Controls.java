package io.github.jabrena.juno.games.doom;

import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * Touch controls: the left and right thirds of the view turn, the middle walks forward (upper half)
 * or back (lower half). Tapping the header hands the walk back to the autopilot, which also takes
 * over again after a while without touches.
 */
final class Controls {
    private static final float TURN = 0.09f;
    private static final float STRIDE = 8f;
    private static final int IDLE_FRAMES = 250;

    static boolean autopilot = true;
    private static int idle;
    private static boolean headerPressed;

    private Controls() {
    }

    /** Applies this frame's touch; returns whether the header needs redrawing. */
    static boolean handle(short[] ceilings) {
        if (!TftTouchShield.readTouch()) {
            headerPressed = false;
            idle = idle + 1;
            if (!autopilot && idle > IDLE_FRAMES) {
                engage();
                return true;
            }
            return false;
        }
        idle = 0;
        int x = TftTouchShield.touchX();
        int y = TftTouchShield.touchY();
        if (y < DisplayList.HEADER) {
            boolean toggle = !headerPressed && !autopilot;
            headerPressed = true;
            if (toggle) {
                engage();
            }
            return toggle;
        }
        boolean wasAutopilot = autopilot;
        autopilot = false;
        if (x < DisplayList.WIDTH / 3) {
            Player.turn(TURN);
        } else if (x >= 2 * DisplayList.WIDTH / 3) {
            Player.turn(-TURN);
        } else {
            Player.walk(y < (DisplayList.HEADER + DisplayList.HEIGHT) / 2 ? STRIDE : -STRIDE, ceilings);
        }
        return wasAutopilot;
    }

    private static void engage() {
        autopilot = true;
        Autopilot.resume();
    }
}
