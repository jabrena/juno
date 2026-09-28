package io.github.jabrena.juno.games.redbaron;

import static io.github.jabrena.juno.games.redbaron.Entities.ENTITIES;
import static io.github.jabrena.juno.games.redbaron.Entities.E_HP;
import static io.github.jabrena.juno.games.redbaron.Entities.E_STRIDE;
import static io.github.jabrena.juno.games.redbaron.Entities.E_TIMER;
import static io.github.jabrena.juno.games.redbaron.Entities.E_TYPE;
import static io.github.jabrena.juno.games.redbaron.Entities.E_VX;
import static io.github.jabrena.juno.games.redbaron.Entities.E_VY;
import static io.github.jabrena.juno.games.redbaron.Entities.E_VZ;
import static io.github.jabrena.juno.games.redbaron.Entities.E_X;
import static io.github.jabrena.juno.games.redbaron.Entities.E_Y;
import static io.github.jabrena.juno.games.redbaron.Entities.E_Z;
import static io.github.jabrena.juno.games.redbaron.Entities.FLIGHT_SPEED;
import static io.github.jabrena.juno.games.redbaron.Entities.T_BLIMP;
import static io.github.jabrena.juno.games.redbaron.Entities.T_BULLET;
import static io.github.jabrena.juno.games.redbaron.Entities.T_FALLING;
import static io.github.jabrena.juno.games.redbaron.Entities.T_FLAK;
import static io.github.jabrena.juno.games.redbaron.Entities.T_HANGAR;
import static io.github.jabrena.juno.games.redbaron.Entities.T_NONE;
import static io.github.jabrena.juno.games.redbaron.Entities.T_PLANE;

/** The twin guns, their bullets, and damage to air and ground targets. */
final class Combat {
    static final int GUN_OFFSET_X = 34;
    static final int GUN_OFFSET_Y = 26;
    private static final int CONVERGE = 560;
    static final int BULLET_SPEED = 80;
    private static final int BULLET_FRAMES = 12;
    private static final int MAX_BULLETS = 4;

    private Combat() {
    }

    static void fire(int[] ents) {
        if (Entities.count(ents, T_BULLET) + 2 > MAX_BULLETS) {
            return;
        }
        for (int side = -1; side <= 1; side = side + 2) {
            int slot = Entities.freeSlot(ents);
            if (slot < 0) {
                return;
            }
            int b = slot * E_STRIDE;
            ents[b + E_TYPE] = T_BULLET;
            ents[b + E_X] = side * GUN_OFFSET_X;
            ents[b + E_Y] = Camera.altitude - GUN_OFFSET_Y;
            ents[b + E_Z] = Camera.NEAR;
            ents[b + E_VX] = -side * GUN_OFFSET_X * BULLET_SPEED / CONVERGE;
            ents[b + E_VY] = GUN_OFFSET_Y * BULLET_SPEED / CONVERGE;
            ents[b + E_VZ] = BULLET_SPEED;
            ents[b + E_TIMER] = BULLET_FRAMES;
        }
    }

    static void resolveBullets(int[] ents) {
        for (int slot = 0; slot < ENTITIES; slot++) {
            int b = slot * E_STRIDE;
            if (ents[b + E_TYPE] != T_BULLET) {
                continue;
            }
            int target = targetHitBy(ents, ents[b + E_X], ents[b + E_Y], ents[b + E_Z]);
            if (target >= 0) {
                ents[b + E_TYPE] = T_NONE;
                hit(ents, target);
            }
        }
    }

    static int targetHitBy(int[] ents, int x, int y, int z) {
        for (int slot = 0; slot < ENTITIES; slot++) {
            int b = slot * E_STRIDE;
            int type = ents[b + E_TYPE];
            int radius;
            int middle = ents[b + E_Y];
            if (type == T_PLANE) {
                radius = DogfightRound.PLANE_RADIUS;
            } else if (type == T_BLIMP) {
                radius = DogfightRound.BLIMP_RADIUS;
            } else if (type == T_HANGAR || type == T_FLAK) {
                radius = GroundAttackRound.GROUND_RADIUS;
                middle = GroundAttackRound.HANGAR_HEIGHT / 2;
            } else {
                continue;
            }
            if (Math.abs(x - ents[b + E_X]) < radius && Math.abs(y - middle) < radius
                    && Math.abs(z - ents[b + E_Z]) < radius + BULLET_SPEED / 2) {
                return slot;
            }
        }
        return -1;
    }

    static void hit(int[] ents, int slot) {
        int b = slot * E_STRIDE;
        int type = ents[b + E_TYPE];
        if (type == T_PLANE) {
            ents[b + E_TYPE] = T_FALLING;
            ents[b + E_VX] = ents[b + E_VX] / 2;
            ents[b + E_VY] = -12;
            ents[b + E_VZ] = FLIGHT_SPEED;
            DogfightRound.kills = DogfightRound.kills + 1;
            Session.addScore(Session.PLANE_POINTS);
        } else if (type == T_BLIMP) {
            ents[b + E_HP] = ents[b + E_HP] - 1;
            ents[b + E_TIMER] = 4;
            if (ents[b + E_HP] <= 0) {
                Entities.debris(ents, slot, 140);
                Session.addScore(Session.BLIMP_POINTS);
            }
        } else if (type == T_HANGAR || type == T_FLAK) {
            GroundAttackRound.targetsHit = GroundAttackRound.targetsHit + 1;
            Entities.debris(ents, slot, 70);
            Session.addScore(type == T_FLAK ? Session.FLAK_POINTS : Session.HANGAR_POINTS);
        }
    }
}
