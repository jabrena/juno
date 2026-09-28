package io.github.jabrena.juno.games.spaceparanoids;

import io.github.jabrena.juno.api.Random;

/** Packed fixed-point entity records and their movement through the maze. */
final class Entities {
    static final int ONE = 1024;
    static final float TO_CELLS = 1f / ONE;
    static final int ENTITIES = 30;
    static final int F_X = 0;
    static final int F_Z = 1;
    static final int F_VX = 2;
    static final int F_VZ = 3;
    static final int F_TX = 4;
    static final int F_TZ = 5;
    static final int F_STRIDE = 6;
    static final int I_TYPE = 0;
    static final int I_TIMER = 1;
    static final int I_COOLDOWN = 2;
    static final int I_AUX = 3;
    static final int I_STRIDE = 4;

    static final int T_NONE = 0;
    static final int T_HUNTER = 1;
    static final int T_TANK = 2;
    static final int T_TURRET = 3;
    static final int T_POOL = 4;
    static final int T_SHOT = 5;
    static final int T_ENEMY_SHOT = 6;
    static final int T_BLAST = 7;

    private Entities() {
    }

    static void place(byte[] maze, short[] paths, int[] fs, int[] is, int type, int distance) {
        int slot = freeSlot(is);
        if (slot < 0) {
            return;
        }
        for (int tries = 0; tries < 300; tries++) {
            int x = 1 + 2 * Random.nextInt((Maze.SIZE - 1) / 2);
            int z = 1 + 2 * Random.nextInt((Maze.SIZE - 1) / 2);
            int cell = z * Maze.SIZE + x;
            if (paths[cell] < distance || occupied(fs, is, x, z)) {
                continue;
            }
            int f = slot * F_STRIDE;
            is[slot * I_STRIDE + I_TYPE] = type;
            is[slot * I_STRIDE + I_COOLDOWN] = Random.nextInt(30, 90);
            is[slot * I_STRIDE + I_AUX] = cell;
            fs[f + F_X] = x * ONE + ONE / 2;
            fs[f + F_Z] = z * ONE + ONE / 2;
            fs[f + F_TX] = fs[f + F_X];
            fs[f + F_TZ] = fs[f + F_Z];
            return;
        }
    }

    private static boolean occupied(int[] fs, int[] is, int x, int z) {
        for (int slot = 0; slot < ENTITIES; slot++) {
            if (is[slot * I_STRIDE + I_TYPE] != T_NONE && fs[slot * F_STRIDE + F_X] / ONE == x
                    && fs[slot * F_STRIDE + F_Z] / ONE == z) {
                return true;
            }
        }
        return false;
    }

    static void move(byte[] maze, short[] paths, int[] fs, int[] is) {
        if (Session.frame % 8 == 0) {
            Maze.bfs(maze, paths, Maze.cellOf(Camera.posX, Camera.posZ));
        }
        for (int slot = 0; slot < ENTITIES; slot++) {
            act(maze, paths, fs, is, slot);
        }
    }

    private static void act(byte[] maze, short[] paths, int[] fs, int[] is, int slot) {
        int type = is[slot * I_STRIDE + I_TYPE];
        if (type == T_HUNTER || type == T_TANK) {
            EnemyMovement.patrol(maze, paths, fs, is, slot);
            Combat.enemyFires(maze, fs, is, slot);
        } else if (type == T_TURRET) {
            Combat.enemyFires(maze, fs, is, slot);
        } else if (type == T_POOL) {
            collectPool(fs, is, slot);
        } else if (type == T_SHOT || type == T_ENEMY_SHOT) {
            Combat.moveShot(maze, fs, is, slot);
        } else if (type == T_BLAST) {
            is[slot * I_STRIDE + I_TIMER] = is[slot * I_STRIDE + I_TIMER] + 1;
            if (is[slot * I_STRIDE + I_TIMER] > 9) {
                is[slot * I_STRIDE + I_TYPE] = T_NONE;
            }
        }
    }

    private static void collectPool(int[] fs, int[] is, int slot) {
        if (nearPlayer(fs, slot, 0.4f)) {
            is[slot * I_STRIDE + I_TYPE] = T_NONE;
            Session.shield = Math.min(Session.MAX_SHIELD, Session.shield + 35);
            Session.addScore(Session.POOL_POINTS);
            Hud.drawHeader();
        }
    }

    static int freeSlot(int[] is) {
        for (int slot = 0; slot < ENTITIES; slot++) {
            if (is[slot * I_STRIDE + I_TYPE] == T_NONE) {
                return slot;
            }
        }
        return -1;
    }

    static void clear(int[] fs, int[] is) {
        for (int i = 0; i < ENTITIES * F_STRIDE; i++) {
            fs[i] = 0;
        }
        for (int i = 0; i < ENTITIES * I_STRIDE; i++) {
            is[i] = 0;
        }
    }

    static void clearTransient(int[] is) {
        for (int slot = 0; slot < ENTITIES; slot++) {
            int type = is[slot * I_STRIDE + I_TYPE];
            if (type == T_SHOT || type == T_ENEMY_SHOT || type == T_BLAST) {
                is[slot * I_STRIDE + I_TYPE] = T_NONE;
            }
        }
    }

    static int count(int[] is, int type) {
        int n = 0;
        for (int slot = 0; slot < ENTITIES; slot++) {
            if (is[slot * I_STRIDE + I_TYPE] == type) {
                n = n + 1;
            }
        }
        return n;
    }

    static int firstOf(int[] is, int type) {
        for (int slot = 0; slot < ENTITIES; slot++) {
            if (is[slot * I_STRIDE + I_TYPE] == type) {
                return slot;
            }
        }
        return -1;
    }

    static boolean near(int[] fs, int slot, int x, int z, int range) {
        int ddx = fs[slot * F_STRIDE + F_X] - x;
        int ddz = fs[slot * F_STRIDE + F_Z] - z;
        return ddx * ddx + ddz * ddz <= range * range;
    }

    static boolean nearPlayer(int[] fs, int slot, float range) {
        return near(fs, slot, (int) (Camera.posX * ONE), (int) (Camera.posZ * ONE), (int) (range * ONE));
    }

    static int entityCell(int[] fs, int slot) {
        return (fs[slot * F_STRIDE + F_Z] / ONE) * Maze.SIZE + fs[slot * F_STRIDE + F_X] / ONE;
    }

}
