package io.github.jabrena.juno.games.starwars;

import io.github.jabrena.juno.api.Random;

import static io.github.jabrena.juno.games.starwars.Entities.ENTITIES;
import static io.github.jabrena.juno.games.starwars.Entities.E_SR;
import static io.github.jabrena.juno.games.starwars.Entities.E_STRIDE;
import static io.github.jabrena.juno.games.starwars.Entities.E_SX;
import static io.github.jabrena.juno.games.starwars.Entities.E_SY;
import static io.github.jabrena.juno.games.starwars.Entities.E_TYPE;
import static io.github.jabrena.juno.games.starwars.Entities.E_Y;
import static io.github.jabrena.juno.games.starwars.Entities.E_Z;
import static io.github.jabrena.juno.games.starwars.Entities.T_CATWALK;
import static io.github.jabrena.juno.games.starwars.Entities.T_NONE;
import static io.github.jabrena.juno.games.starwars.Entities.T_PORT;
import static io.github.jabrena.juno.games.starwars.Entities.T_SHOT;
import static io.github.jabrena.juno.games.starwars.Entities.T_TIE;
import static io.github.jabrena.juno.games.starwars.Entities.T_TOWER;
import static io.github.jabrena.juno.games.starwars.Entities.T_TURRET;

/**
 * The CPU at the controls, flying like a person rather than a machine: it lets targets come within
 * {@value #CPU_RANGE} units (the exhaust port within {@value #CPU_PORT_RANGE}) before shooting, glides
 * the crosshair towards the nearest one at {@value #CPU_AIM_SPEED} pixels a frame, and pulls the
 * trigger every {@value #CPU_FIRE_FRAMES} frames once it is on its aim point. About
 * {@value #CPU_MISS_PERCENT}% of its shots at TIE fighters, towers and turrets are aimed just outside
 * the target and miss; fireballs and the exhaust port it takes seriously. In the trench the crosshair
 * also steers, so there it aims only for the shot and then steers high, or low when the next catwalk
 * is high, to pass the catwalks.
 */
final class AutopilotSW {
    private static final int CPU_FIRE_FRAMES = 6;
    private static final int CPU_RANGE = 1800;
    private static final int CPU_PORT_RANGE = 900;
    private static final int CPU_AIM_SPEED = 24;
    private static final int CPU_MISS_PERCENT = 20;

    private static int aimOffsetX;
    private static int aimOffsetY;

    private AutopilotSW() {
    }

    static void fly(int[] ents) {
        int target = nearestTarget(ents);
        boolean trigger = Session.frame % CPU_FIRE_FRAMES == 0;
        if (trigger) {
            chooseAim(ents, target);
        }
        if (Session.phase == Phase.TRENCH) {
            if (target >= 0 && trigger) {
                Controls.crossX = ents[target * E_STRIDE + E_SX] + aimOffsetX;
                Controls.crossY = ents[target * E_STRIDE + E_SY] + aimOffsetY;
                Combat.fire(ents);
            }
            int catwalk = Entities.find(ents, T_CATWALK);
            Controls.crossX = Camera.CENTER_X;
            Controls.crossY = catwalk >= 0 && ents[catwalk * E_STRIDE + E_Y] > 0
                    ? DisplayList.HEIGHT - 30
                    : DisplayList.HEADER + 30;
            return;
        }
        int aimX = Camera.CENTER_X;
        int aimY = Camera.CENTER_Y;
        if (target >= 0) {
            aimX = ents[target * E_STRIDE + E_SX] + aimOffsetX;
            aimY = ents[target * E_STRIDE + E_SY] + aimOffsetY;
        }
        aimX = Camera.clamp(aimX, 8, DisplayList.WIDTH - 9);
        aimY = Camera.clamp(aimY, DisplayList.HEADER + 8, DisplayList.HEIGHT - 9);
        Controls.crossX = Controls.crossX + Camera.clamp(aimX - Controls.crossX, -CPU_AIM_SPEED, CPU_AIM_SPEED);
        Controls.crossY = Controls.crossY + Camera.clamp(aimY - Controls.crossY, -CPU_AIM_SPEED, CPU_AIM_SPEED);
        if (target >= 0 && trigger && Math.abs(aimX - Controls.crossX) + Math.abs(aimY - Controls.crossY) <= 4) {
            Combat.fire(ents);
        }
    }

    /** The nearest target within range, or -1. */
    private static int nearestTarget(int[] ents) {
        int target = -1;
        int nearest = 1 << 30;
        for (int slot = 0; slot < ENTITIES; slot++) {
            int b = slot * E_STRIDE;
            int type = ents[b + E_TYPE];
            int z = ents[b + E_Z];
            int range = type == T_PORT ? CPU_PORT_RANGE : CPU_RANGE;
            if (ents[b + E_SR] > 0 && type != T_SHOT && z < nearest && z < range) {
                nearest = z;
                target = slot;
            }
        }
        return target;
    }

    /** Each shot is either aimed true or, now and then, just past the edge of the target. */
    private static void chooseAim(int[] ents, int target) {
        aimOffsetX = 0;
        aimOffsetY = 0;
        int type = target >= 0 ? ents[target * E_STRIDE + E_TYPE] : T_NONE;
        boolean casual = type == T_TIE || type == T_TOWER || type == T_TURRET;
        if (casual && Random.nextInt(100) < CPU_MISS_PERCENT) {
            int off = ents[target * E_STRIDE + E_SR] + Random.nextInt(6, 20);
            aimOffsetX = Random.nextInt(2) == 0 ? -off : off;
            aimOffsetY = Random.nextInt(-off, off + 1);
        }
    }
}
