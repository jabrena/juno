package io.github.jabrena.juno.games.spaceparanoids;

import io.github.jabrena.juno.api.Random;

import static io.github.jabrena.juno.games.spaceparanoids.Entities.ENTITIES;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.F_STRIDE;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.F_VX;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.F_VZ;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.F_X;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.F_Z;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.I_AUX;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.I_COOLDOWN;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.I_STRIDE;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.I_TIMER;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.I_TYPE;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.ONE;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.TO_CELLS;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.T_BLAST;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.T_ENEMY_SHOT;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.T_HUNTER;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.T_NONE;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.T_SHOT;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.T_TANK;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.T_TURRET;

/** Player and enemy shots, collision detection, damage and scoring. */
final class Combat {
    private static final float SHOT_SPEED = 0.35f;
    private static final float ENEMY_SHOT_SPEED = 0.12f;
    private static final float HIT_RANGE = 0.32f;
    private static final int FIRE_FRAMES = 7;

    static int fireCooldown;

    private Combat() {
    }

    static void tickCooldown() {
        if (fireCooldown > 0) {
            fireCooldown = fireCooldown - 1;
        }
    }

    static void fire(int[] fs, int[] is) {
        if (fireCooldown > 0 || Entities.count(is, T_SHOT) >= 3) {
            return;
        }
        int slot = Entities.freeSlot(is);
        if (slot < 0) {
            return;
        }
        fireCooldown = FIRE_FRAMES;
        int f = slot * F_STRIDE;
        is[slot * I_STRIDE + I_TYPE] = T_SHOT;
        is[slot * I_STRIDE + I_TIMER] = 40;
        fs[f + F_X] = (int) ((Camera.posX + Camera.dirX * 0.2f) * ONE);
        fs[f + F_Z] = (int) ((Camera.posZ + Camera.dirZ * 0.2f) * ONE);
        fs[f + F_VX] = (int) (Camera.dirX * SHOT_SPEED * ONE);
        fs[f + F_VZ] = (int) (Camera.dirZ * SHOT_SPEED * ONE);
    }

    static void enemyFires(byte[] maze, int[] fs, int[] is, int slot) {
        int b = slot * I_STRIDE;
        is[b + I_COOLDOWN] = is[b + I_COOLDOWN] - 1;
        if (is[b + I_COOLDOWN] > 0) {
            return;
        }
        int f = slot * F_STRIDE;
        float x = fs[f + F_X] * TO_CELLS;
        float z = fs[f + F_Z] * TO_CELLS;
        float ddx = Camera.posX - x;
        float ddz = Camera.posZ - z;
        float distance = (float) Math.sqrt(ddx * ddx + ddz * ddz);
        if (distance > 6f || distance < 0.3f || !lineOfSight(maze, x, z, Camera.posX, Camera.posZ)) {
            is[b + I_COOLDOWN] = 10;
            return;
        }
        int type = is[b + I_TYPE];
        int pause = type == T_HUNTER ? 70 : (type == T_TANK ? 90 : 60);
        is[b + I_COOLDOWN] = Math.max(25, pause - 4 * Session.level + Random.nextInt(30));
        int shot = Entities.freeSlot(is);
        if (shot < 0) {
            return;
        }
        int g = shot * F_STRIDE;
        is[shot * I_STRIDE + I_TYPE] = T_ENEMY_SHOT;
        is[shot * I_STRIDE + I_TIMER] = 120;
        is[shot * I_STRIDE + I_AUX] = type == T_TANK ? 25 : (type == T_TURRET ? 20 : 15);
        fs[g + F_X] = fs[f + F_X];
        fs[g + F_Z] = fs[f + F_Z];
        fs[g + F_VX] = (int) (ddx / distance * ENEMY_SHOT_SPEED * ONE);
        fs[g + F_VZ] = (int) (ddz / distance * ENEMY_SHOT_SPEED * ONE);
    }

    static boolean lineOfSight(byte[] maze, float x0, float z0, float x1, float z1) {
        float ddx = x1 - x0;
        float ddz = z1 - z0;
        int steps = (int) ((Math.abs(ddx) + Math.abs(ddz)) * 4f) + 1;
        for (int i = 1; i < steps; i++) {
            float t = (float) i / steps;
            if (Maze.isWall(maze, x0 + ddx * t, z0 + ddz * t)) {
                return false;
            }
        }
        return true;
    }

    static void moveShot(byte[] maze, int[] fs, int[] is, int slot) {
        int f = slot * F_STRIDE;
        int b = slot * I_STRIDE;
        is[b + I_TIMER] = is[b + I_TIMER] - 1;
        for (int half = 0; half < 2; half++) {
            fs[f + F_X] = fs[f + F_X] + fs[f + F_VX] / 2;
            fs[f + F_Z] = fs[f + F_Z] + fs[f + F_VZ] / 2;
            int x = fs[f + F_X];
            int z = fs[f + F_Z];
            if (is[b + I_TIMER] <= 0 || Maze.isWall(maze, x * TO_CELLS, z * TO_CELLS)) {
                is[b + I_TYPE] = T_NONE;
                return;
            }
            if (is[b + I_TYPE] == T_ENEMY_SHOT) {
                if (Entities.nearPlayer(fs, slot, Camera.RADIUS + 0.08f)) {
                    is[b + I_TYPE] = T_NONE;
                    Session.damage(is[b + I_AUX]);
                    return;
                }
            } else if (hitsTarget(fs, is, b, x, z)) {
                return;
            }
        }
    }

    private static boolean hitsTarget(int[] fs, int[] is, int shotBase, int x, int z) {
        for (int target = 0; target < ENTITIES; target++) {
            int type = is[target * I_STRIDE + I_TYPE];
            if ((type == T_HUNTER || type == T_TANK || type == T_TURRET)
                    && Entities.near(fs, target, x, z, (int) (HIT_RANGE * ONE))) {
                destroy(is, target);
                is[shotBase + I_TYPE] = T_NONE;
                return true;
            }
        }
        return false;
    }

    private static void destroy(int[] is, int slot) {
        int type = is[slot * I_STRIDE + I_TYPE];
        if (type == T_HUNTER) {
            Session.huntersLeft = Session.huntersLeft - 1;
            Session.addScore(Session.HUNTER_POINTS);
        } else if (type == T_TANK) {
            Session.addScore(Session.TANK_POINTS);
        } else {
            Session.addScore(Session.TURRET_POINTS);
        }
        is[slot * I_STRIDE + I_TYPE] = T_BLAST;
        is[slot * I_STRIDE + I_TIMER] = 0;
        is[slot * I_STRIDE + I_AUX] = type;
        Hud.drawHeader();
    }
}
