package io.github.jabrena.juno.games.tempest;

import io.github.jabrena.juno.api.Random;

/** The CPU pilot: lets flippers close in before engaging, like a person spotting them. */
final class AutopilotTempest {
    /** How often the CPU glances at the tube, rather than reacting every single frame. */
    private static final int REACT_FRAMES = 10;
    /** Flippers below this depth are still small and far off; the CPU leaves them be for now. */
    private static final int ENGAGE_DEPTH = Tube.DEPTH * 2 / 5;
    private static final int CPU_MISS_PERCENT = 20;
    private static final int CPU_HESITATE_PERCENT = 15;
    private static final int CPU_ZAP_THRESHOLD = 5;

    private static int cooldown;

    private AutopilotTempest() {
    }

    /**
     * Chases whichever visible flipper is closest to the rim, occasionally hesitating or misjudging
     * its lane, and zaps when swarmed.
     */
    static void fly(int[] tube, int[] enemies, int[] shots) {
        if (cooldown > 0) {
            cooldown = cooldown - 1;
            return;
        }
        cooldown = REACT_FRAMES;
        if (Session.zapperReady && Session.count(enemies, Session.ENEMIES, Session.E_STRIDE) >= CPU_ZAP_THRESHOLD) {
            Session.requestZap();
            return;
        }
        int target = threatLane(enemies);
        if (target < 0 || Random.nextInt(100) < CPU_HESITATE_PERCENT) {
            return;
        }
        if (Random.nextInt(100) < CPU_MISS_PERCENT) {
            target = Random.nextInt(2) == 0 ? (target + 1) % Tube.LANES : (target + Tube.LANES - 1) % Tube.LANES;
        }
        Session.moveToward(tube, target);
        Session.fire(shots);
    }

    /** The lane of the visible flipper closest to the rim, the most urgent target. */
    private static int threatLane(int[] enemies) {
        int lane = -1;
        int deepest = ENGAGE_DEPTH;
        for (int slot = 0; slot < Session.ENEMIES; slot++) {
            int base = slot * Session.E_STRIDE;
            if (enemies[base + Session.E_ACTIVE] != 0 && enemies[base + Session.E_DEPTH] > deepest) {
                deepest = enemies[base + Session.E_DEPTH];
                lane = enemies[base + Session.E_LANE];
            }
        }
        return lane;
    }
}
