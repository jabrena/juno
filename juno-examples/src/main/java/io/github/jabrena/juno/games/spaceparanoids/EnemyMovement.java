package io.github.jabrena.juno.games.spaceparanoids;

import io.github.jabrena.juno.api.Random;

/** Corridor navigation for moving hunters and tanks. */
final class EnemyMovement {
    private EnemyMovement() {
    }

    static void patrol(byte[] maze, short[] paths, int[] fs, int[] is, int slot) {
        int f = slot * Entities.F_STRIDE;
        int b = slot * Entities.I_STRIDE;
        int speed = is[b + Entities.I_TYPE] == Entities.T_HUNTER
                ? 46 + 4 * Math.min(Session.level, 8) : 31;
        int ddx = fs[f + Entities.F_TX] - fs[f + Entities.F_X];
        int ddz = fs[f + Entities.F_TZ] - fs[f + Entities.F_Z];
        if (Math.abs(ddx) + Math.abs(ddz) > speed) {
            fs[f + Entities.F_X] = fs[f + Entities.F_X] + sign(ddx) * Math.min(speed, Math.abs(ddx));
            fs[f + Entities.F_Z] = fs[f + Entities.F_Z] + sign(ddz) * Math.min(speed, Math.abs(ddz));
            return;
        }
        fs[f + Entities.F_X] = fs[f + Entities.F_TX];
        fs[f + Entities.F_Z] = fs[f + Entities.F_TZ];
        int cell = Entities.entityCell(fs, slot);
        int choice = chooseNextCell(maze, paths, cell, is[b + Entities.I_AUX]);
        is[b + Entities.I_AUX] = cell;
        if (choice >= 0 && maze[choice] == Maze.OPEN) {
            fs[f + Entities.F_TX] = (choice % Maze.SIZE) * Entities.ONE + Entities.ONE / 2;
            fs[f + Entities.F_TZ] = (choice / Maze.SIZE) * Entities.ONE + Entities.ONE / 2;
        }
    }

    private static int chooseNextCell(byte[] maze, short[] paths, int cell, int came) {
        int here = paths[cell];
        int choice = closerCell(maze, paths, cell, here);
        if (choice >= 0) {
            return choice;
        }
        int options = countOptions(maze, paths, cell, came, here);
        if (options == 0) {
            return came;
        }
        int pick = Random.nextInt(options);
        for (int d = 0; d < 4; d++) {
            int next = cell + Maze.dx(d) + Maze.dz(d) * Maze.SIZE;
            if (isOption(maze, paths, next, came, here)) {
                if (pick == 0) {
                    return next;
                }
                pick = pick - 1;
            }
        }
        return -1;
    }

    private static int countOptions(byte[] maze, short[] paths, int cell, int came, int here) {
        int options = 0;
        for (int d = 0; d < 4; d++) {
            int next = cell + Maze.dx(d) + Maze.dz(d) * Maze.SIZE;
            if (isOption(maze, paths, next, came, here)) {
                options = options + 1;
            }
        }
        return options;
    }

    private static boolean isOption(byte[] maze, short[] paths, int next, int came, int here) {
        return maze[next] == Maze.OPEN && next != came && (here < 0 || paths[next] >= 2 || here > 9);
    }

    private static int closerCell(byte[] maze, short[] paths, int cell, int here) {
        int choice = -1;
        if (here > 2 && here <= 9) {
            for (int d = 0; d < 4; d++) {
                int next = cell + Maze.dx(d) + Maze.dz(d) * Maze.SIZE;
                if (maze[next] == Maze.OPEN && paths[next] >= 0 && paths[next] < here) {
                    choice = next;
                }
            }
        }
        return choice;
    }

    private static int sign(int value) {
        return value > 0 ? 1 : (value < 0 ? -1 : 0);
    }
}
