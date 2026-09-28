package io.github.jabrena.juno.games.lunarlander;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;

/** Owns one lander's state and advances its flight physics. */
final class Flight {
    private static final int FRAME_MILLIS = 30;
    private static final int BURN_PER_FRAME = 3;
    private static final float GRAVITY = 0.010f;
    private static final float THRUST = 0.028f;
    static final float SAFE_VERTICAL = 0.55f;
    static final float SAFE_HORIZONTAL = 0.35f;
    static final int MAX_TILT = 6;
    private static final int ROTATE_FRAMES = 4;

    static float x;
    static float y;
    static float vx;
    static float vy;
    static int tilt;
    static int fuel;
    static int score;
    static int best;
    static boolean burning;
    static int descent;
    static int frame;

    private Flight() {
    }

    static void newGame(int startingFuel) {
        fuel = startingFuel;
        score = 0;
        descent = 0;
    }

    /** Flies one descent and returns its landing-pad multiplier, or zero after a crash. */
    static int fly(short[] ground, byte[] pads) {
        Terrain.generate(ground, pads);
        LanderRenderer.startDescent(ground, pads);
        resetPosition();
        Controls.startDescent(ground, pads);
        Hud.drawButtons();
        Hud.drawHeader();
        int rotateWait = 0;
        int next = Clock.millis();
        while (true) {
            next = awaitFrame(next);
            frame = frame + 1;
            int pressed = Controls.read(pads);
            Hud.updateButtons(pressed);
            rotateWait = rotate(pressed, rotateWait);
            accelerate(pressed);
            move();
            boolean touching = LanderRenderer.touchesGround(ground);
            if (touching) {
                burning = false;
            }
            LanderRenderer.draw(ground, pads);
            if (touching) {
                Hud.drawHeader();
                return landingMultiplier(ground, pads);
            }
            if (frame % 5 == 0) {
                Hud.drawHeader();
            }
        }
    }

    private static void resetPosition() {
        x = 24;
        y = Terrain.PLAY_TOP + 16;
        vx = 0.6f + Random.nextInt(5) * 0.1f;
        vy = 0;
        tilt = 0;
        burning = false;
        frame = 0;
        LanderRenderer.reset();
    }

    private static int awaitFrame(int next) {
        while (Clock.millis() < next) {
            Delay.millis(1);
        }
        return next + FRAME_MILLIS;
    }

    private static int rotate(int pressed, int wait) {
        if (pressed != Controls.LEFT && pressed != Controls.RIGHT) {
            return 0;
        }
        if (wait == 0) {
            tilt = pressed == Controls.LEFT ? Math.max(-MAX_TILT, tilt - 1) : Math.min(MAX_TILT, tilt + 1);
            return ROTATE_FRAMES - 1;
        }
        return wait - 1;
    }

    private static void accelerate(int pressed) {
        burning = pressed == Controls.BURN && fuel > 0;
        float angle = (float) Math.toRadians(tilt * 15);
        if (burning) {
            vx = vx + (float) Math.sin(angle) * THRUST;
            vy = vy - (float) Math.cos(angle) * THRUST;
            fuel = Math.max(0, fuel - BURN_PER_FRAME);
        }
        vy = vy + GRAVITY;
    }

    private static void move() {
        x = x + vx;
        y = y + vy;
        if (x < LanderRenderer.HALF) {
            x = LanderRenderer.HALF;
            vx = 0;
        } else if (x > Terrain.WIDTH - 1 - LanderRenderer.HALF) {
            x = Terrain.WIDTH - 1 - LanderRenderer.HALF;
            vx = 0;
        }
        if (y < Terrain.PLAY_TOP + LanderRenderer.HALF) {
            y = Terrain.PLAY_TOP + LanderRenderer.HALF;
            vy = Math.max(vy, 0);
        }
    }

    /** Zero unless upright, slow, and with both feet on the same pad. */
    static int landingMultiplier(short[] ground, byte[] pads) {
        if (tilt != 0 || vy > SAFE_VERTICAL || Math.abs(vx) > SAFE_HORIZONTAL) {
            return 0;
        }
        int left = Math.round(x) - 6;
        int right = Math.round(x) + 6;
        if (pads[left] == 0 || pads[left] != pads[right] || ground[left] != ground[right]) {
            return 0;
        }
        return pads[left];
    }
}
