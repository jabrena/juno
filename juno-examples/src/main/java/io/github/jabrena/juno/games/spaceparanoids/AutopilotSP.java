package io.github.jabrena.juno.games.spaceparanoids;

import static io.github.jabrena.juno.games.spaceparanoids.Entities.ENTITIES;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.F_STRIDE;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.F_X;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.F_Z;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.I_STRIDE;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.I_TYPE;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.T_HUNTER;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.T_POOL;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.T_TANK;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.T_TURRET;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.TO_CELLS;

import io.github.jabrena.juno.api.Random;

/** The CPU driver: hunts enemies, seeks energy when damaged, aims and fires. */
final class AutopilotSP {
    private static final int CPU_WOBBLE = 20;
    private static final int CPU_WOBBLE_FRAMES = 20;
    private static final float CPU_SIGHT = 6f;
    private static final int CPU_LOW_SHIELD = 40;

    private static float aimWobble;

    private AutopilotSP() {
    }

    static void fly(byte[] maze, short[] route, int[] fs, int[] is) {
        if (Session.frame % CPU_WOBBLE_FRAMES == 0) {
            aimWobble = Random.nextInt(-CPU_WOBBLE, CPU_WOBBLE + 1) / 100f;
        }
        int aim = visibleTarget(maze, fs, is);
        if (aim >= 0) {
            aimAndFire(fs, is, aim);
            return;
        }
        int target = destination(is);
        if (target < 0) {
            return;
        }
        driveTo(maze, route, fs, target);
    }

    private static int visibleTarget(byte[] maze, int[] fs, int[] is) {
        int aim = -1;
        float nearest = CPU_SIGHT;
        for (int slot = 0; slot < ENTITIES; slot++) {
            int type = is[slot * I_STRIDE + I_TYPE];
            if (type != T_HUNTER && type != T_TANK && type != T_TURRET) {
                continue;
            }
            float ex = fs[slot * F_STRIDE + F_X] * TO_CELLS;
            float ez = fs[slot * F_STRIDE + F_Z] * TO_CELLS;
            float distance = (float) Math.sqrt((ex - Camera.posX) * (ex - Camera.posX)
                    + (ez - Camera.posZ) * (ez - Camera.posZ));
            if (distance < nearest && Combat.lineOfSight(maze, Camera.posX, Camera.posZ, ex, ez)) {
                nearest = distance;
                aim = slot;
            }
        }
        return aim;
    }

    private static void aimAndFire(int[] fs, int[] is, int aim) {
        float ex = fs[aim * F_STRIDE + F_X] * TO_CELLS;
        float ez = fs[aim * F_STRIDE + F_Z] * TO_CELLS;
        float turn = wrapAngle((float) Math.atan2(ez - Camera.posZ, ex - Camera.posX)
                + aimWobble - Camera.angle);
        if (Math.abs(turn) > 0.05f) {
            turnBy(turn);
        } else {
            Combat.fire(fs, is);
        }
    }

    private static int destination(int[] is) {
        int target = -1;
        if (Session.shield < CPU_LOW_SHIELD) {
            target = Entities.firstOf(is, T_POOL);
        }
        for (int type = T_HUNTER; type <= T_TURRET && target < 0; type++) {
            target = Entities.firstOf(is, type);
        }
        return target;
    }

    private static void driveTo(byte[] maze, short[] route, int[] fs, int target) {
        Maze.bfs(maze, route, Entities.entityCell(fs, target));
        int here = Maze.cellOf(Camera.posX, Camera.posZ);
        int next = nextCell(maze, route, here);
        float tx = next % Maze.SIZE + 0.5f;
        float tz = next / Maze.SIZE + 0.5f;
        if (next == here) {
            tx = fs[target * F_STRIDE + F_X] * TO_CELLS;
            tz = fs[target * F_STRIDE + F_Z] * TO_CELLS;
            if (Math.abs(tx - Camera.posX) + Math.abs(tz - Camera.posZ) < 0.1f) {
                return;
            }
        }
        float cx = here % Maze.SIZE + 0.5f;
        float cz = here / Maze.SIZE + 0.5f;
        boolean corner = Math.abs(tx - Camera.posX) > 0.2f && Math.abs(tz - Camera.posZ) > 0.2f;
        if (corner && Math.abs(cx - Camera.posX) + Math.abs(cz - Camera.posZ) > 0.1f) {
            tx = cx;
            tz = cz;
        }
        float turn = wrapAngle((float) Math.atan2(tz - Camera.posZ, tx - Camera.posX) - Camera.angle);
        if (Math.abs(turn) > 0.12f) {
            turnBy(turn);
        } else {
            Camera.drive(maze, Controls.SPEED);
        }
    }

    private static int nextCell(byte[] maze, short[] route, int here) {
        int next = here;
        for (int d = 0; d < 4; d++) {
            int cell = here + Maze.dx(d) + Maze.dz(d) * Maze.SIZE;
            if (maze[cell] == Maze.OPEN && route[cell] >= 0 && route[cell] < route[next]) {
                next = cell;
            }
        }
        return next;
    }

    private static void turnBy(float turn) {
        Camera.setAngle(Camera.angle
                + (turn > 0 ? Math.min(Controls.TURN, turn) : -Math.min(Controls.TURN, -turn)));
    }

    private static float wrapAngle(float value) {
        float twoPi = (float) (2 * Math.PI);
        while (value > Math.PI) {
            value = value - twoPi;
        }
        while (value < -Math.PI) {
            value = value + twoPi;
        }
        return value;
    }
}
