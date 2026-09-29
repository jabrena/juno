package io.github.jabrena.juno.games.missilecommand;

import io.github.jabrena.juno.api.Random;

/** The CPU pilot: intercepts whichever warhead is closest to the ground, leading its aim to meet it. */
final class AutopilotMissileCommand {
    private static final int REACT_FRAMES = 8;
    private static final int SHOT_PIXELS_PER_FRAME = Session.SHOT_SPEED / 64;
    private static final int CPU_MISS_PIXELS = 10;
    private static final int CPU_MISS_PERCENT = 15;
    private static final int CPU_HESITATE_PERCENT = 10;

    private static int cooldown;

    private AutopilotMissileCommand() {
    }

    /** Picks the most urgent unengaged warhead, leads its aim to meet it, and fires. */
    static void fly(int[] missiles, int[] shots, int[] ammo) {
        if (cooldown > 0) {
            cooldown = cooldown - 1;
            return;
        }
        cooldown = REACT_FRAMES;
        if (Random.nextInt(100) < CPU_HESITATE_PERCENT) {
            return;
        }
        int slot = threatSlot(missiles, shots);
        if (slot < 0) {
            return;
        }
        int base = slot * Session.T_STRIDE;
        int headX = missiles[base + Session.T_HEAD_X];
        int headY = missiles[base + Session.T_HEAD_Y];
        int endX = missiles[base + Session.T_END_X];
        int endY = missiles[base + Session.T_END_Y];
        int fromBase = Session.nearestBase(ammo, endX);
        if (fromBase < 0) {
            return;
        }
        int baseX = SceneRenderer.baseX(fromBase);
        int baseY = Session.GROUND_Y - 14;
        int remaining = Math.max(1, Math.max(Math.abs(endX - headX), Math.abs(endY - headY)));
        int lead = leadSteps(headX, headY, endX, endY, baseX, baseY, remaining);
        int aimX = headX + (endX - headX) * lead / remaining;
        int aimY = headY + (endY - headY) * lead / remaining;
        if (Random.nextInt(100) < CPU_MISS_PERCENT) {
            aimX = aimX + Random.nextInt(-CPU_MISS_PIXELS, CPU_MISS_PIXELS + 1);
        }
        aimX = Math.max(3, Math.min(aimX, Session.WIDTH - 4));
        aimY = Math.max(Session.HEADER + 4, Math.min(aimY, Session.LOWEST_TARGET_Y));
        int shot = Session.fire(shots, ammo, aimX, aimY);
        if (shot >= 0) {
            // Claims the target for as long as this shot is in flight, so a still-falling warhead
            // already covered by a travelling interceptor isn't fired at again.
            shots[shot * Session.T_STRIDE + Session.T_TARGET] = missiles[base + Session.T_TARGET];
        }
    }

    /**
     * How many steps ahead of the missile's current head to aim so a shot fired now — travelling at
     * a constant {@value #SHOT_PIXELS_PER_FRAME} px/frame from {@code (baseX, baseY)} — reaches the
     * missile where it will be, not where it was. Three rounds of fixed-point iteration are enough:
     * the interceptor is several times faster than any warhead, so it converges almost at once.
     */
    private static int leadSteps(int headX, int headY, int endX, int endY, int baseX, int baseY, int remaining) {
        int lead = 0;
        for (int round = 0; round < 3; round++) {
            int aimX = headX + (endX - headX) * lead / remaining;
            int aimY = headY + (endY - headY) * lead / remaining;
            int dx = aimX - baseX;
            int dy = aimY - baseY;
            int shotDistance = (int) Math.sqrt((double) (dx * dx + dy * dy));
            int shotFrames = shotDistance / SHOT_PIXELS_PER_FRAME;
            lead = Math.min(remaining, shotFrames * Session.enemySpeed / 64);
        }
        return lead;
    }

    /** The active warhead lowest in the sky whose target no in-flight shot already covers. */
    private static int threatSlot(int[] missiles, int[] shots) {
        int best = -1;
        int lowest = Session.HEADER;
        for (int slot = 0; slot < Session.MISSILES; slot++) {
            int base = slot * Session.T_STRIDE;
            int y = missiles[base + Session.T_HEAD_Y];
            if (missiles[base + Session.T_ACTIVE] != 0 && y > lowest
                    && !targetClaimed(shots, missiles[base + Session.T_TARGET])) {
                lowest = y;
                best = slot;
            }
        }
        return best;
    }

    private static boolean targetClaimed(int[] shots, int target) {
        for (int slot = 0; slot < Session.SHOTS; slot++) {
            int base = slot * Session.T_STRIDE;
            if (shots[base + Session.T_ACTIVE] != 0 && shots[base + Session.T_TARGET] == target) {
                return true;
            }
        }
        return false;
    }
}
