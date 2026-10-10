package io.github.jabrena.juno.games.redbaron;

import io.github.jabrena.juno.api.Random;

import static io.github.jabrena.juno.games.redbaron.Entities.ENTITIES;
import static io.github.jabrena.juno.games.redbaron.Entities.E_STRIDE;
import static io.github.jabrena.juno.games.redbaron.Entities.E_TYPE;
import static io.github.jabrena.juno.games.redbaron.Entities.E_X;
import static io.github.jabrena.juno.games.redbaron.Entities.E_Y;
import static io.github.jabrena.juno.games.redbaron.Entities.E_Z;
import static io.github.jabrena.juno.games.redbaron.Entities.T_BLIMP;
import static io.github.jabrena.juno.games.redbaron.Entities.T_FLAK;
import static io.github.jabrena.juno.games.redbaron.Entities.T_HANGAR;
import static io.github.jabrena.juno.games.redbaron.Entities.T_PLANE;
import static io.github.jabrena.juno.games.redbaron.Entities.T_PYRAMID;
import static io.github.jabrena.juno.games.redbaron.Entities.T_TRACER;

/** The CPU pilot: chases targets, evades tracers and climbs over pyramids. */
final class AutopilotRB {
    private static final int CPU_FIRE_FRAMES = 3;
    private static final int CPU_WOBBLE = 60;
    private static final int CPU_WOBBLE_FRAMES = 25;
    private static final int CPU_EVADE_RANGE = 700;
    private static final int CPU_EVADE_WIDTH = 200;

    private static int aimWobbleX;
    private static int aimWobbleY;

    private AutopilotRB() {
    }

    static void fly(int[] ents) {
        if (Session.frame % CPU_WOBBLE_FRAMES == 0) {
            aimWobbleX = Random.nextInt(-CPU_WOBBLE, CPU_WOBBLE + 1);
            aimWobbleY = Random.nextInt(-CPU_WOBBLE / 2, CPU_WOBBLE / 2 + 1);
        }
        boolean ground = Session.round == Round.GROUND_ATTACK;
        int target = -1;
        int nearest = 1 << 30;
        for (int slot = 0; slot < ENTITIES; slot++) {
            int type = ents[slot * E_STRIDE + E_TYPE];
            int z = ents[slot * E_STRIDE + E_Z];
            boolean wanted = ground ? type == T_HANGAR || type == T_FLAK : type == T_PLANE || type == T_BLIMP;
            if (wanted && z > 60 && z < nearest) {
                target = slot;
                nearest = z;
            }
        }
        Controls.stickX = 0;
        Controls.stickY = 0;
        if (target >= 0) {
            int b = target * E_STRIDE;
            int x = ents[b + E_X] + aimWobbleX;
            int y = (ground ? GroundAttackRound.HANGAR_HEIGHT / 2 : ents[b + E_Y])
                    + Combat.GUN_OFFSET_Y + aimWobbleY;
            Controls.stickX = Camera.clamp(x * 400 / Math.max(nearest, 1), -100, 100);
            Controls.stickY = Camera.clamp((y - Camera.altitude) * 4, -100, 100);
            if (Math.abs(x) < 40 + nearest / 12 && Math.abs(y - Camera.altitude) < 50 && nearest < 900
                    && Session.frame % CPU_FIRE_FRAMES == 0) {
                Combat.fire(ents);
            }
        }
        for (int slot = 0; slot < ENTITIES; slot++) {
            int b = slot * E_STRIDE;
            if (ents[b + E_TYPE] == T_TRACER && ents[b + E_Z] < CPU_EVADE_RANGE
                    && Math.abs(ents[b + E_X]) < CPU_EVADE_WIDTH) {
                Controls.stickX = ents[b + E_X] >= 0 ? -100 : 100;
                Controls.stickY = ents[b + E_Y] >= Camera.altitude ? -60 : 60;
            }
        }
        for (int slot = 0; slot < ENTITIES; slot++) {
            int b = slot * E_STRIDE;
            if (ents[b + E_TYPE] == T_PYRAMID && ents[b + E_Z] < 500 && Math.abs(ents[b + E_X]) < 120
                    && Camera.altitude < GroundAttackRound.PYRAMID_HEIGHT + 40) {
                Controls.stickY = 100;
            }
        }
    }
}
