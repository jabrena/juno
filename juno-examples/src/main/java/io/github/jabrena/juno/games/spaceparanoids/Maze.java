package io.github.jabrena.juno.games.spaceparanoids;

import io.github.jabrena.juno.api.Random;

/** The generated maze and breadth-first paths through its open cells. */
final class Maze {
    static final int SIZE = 13;
    static final int CELLS = SIZE * SIZE;
    static final byte OPEN = 0;
    static final byte WALL = 1;

    private Maze() {
    }

    static void generate(byte[] maze, short[] stack) {
        for (int i = 0; i < CELLS; i++) {
            maze[i] = WALL;
        }
        int rooms = (SIZE - 1) / 2;
        int top = 0;
        stack[0] = (short) (SIZE + 1);
        maze[SIZE + 1] = OPEN;
        while (top >= 0) {
            int cell = stack[top];
            int x = cell % SIZE;
            int z = cell / SIZE;
            int options = 0;
            for (int d = 0; d < 4; d++) {
                int nx = x + 2 * dx(d);
                int nz = z + 2 * dz(d);
                if (nx > 0 && nz > 0 && nx < SIZE - 1 && nz < SIZE - 1 && maze[nz * SIZE + nx] == WALL) {
                    options = options + 1;
                }
            }
            if (options == 0) {
                top = top - 1;
                continue;
            }
            int pick = Random.nextInt(options);
            for (int d = 0; d < 4; d++) {
                int nx = x + 2 * dx(d);
                int nz = z + 2 * dz(d);
                if (nx > 0 && nz > 0 && nx < SIZE - 1 && nz < SIZE - 1 && maze[nz * SIZE + nx] == WALL) {
                    if (pick == 0) {
                        maze[(z + dz(d)) * SIZE + x + dx(d)] = OPEN;
                        maze[nz * SIZE + nx] = OPEN;
                        top = top + 1;
                        stack[top] = (short) (nz * SIZE + nx);
                        break;
                    }
                    pick = pick - 1;
                }
            }
        }
        int knocked = 0;
        int tries = 0;
        while (knocked < rooms + 2 && tries < 200) {
            tries = tries + 1;
            int x = Random.nextInt(1, SIZE - 1);
            int z = Random.nextInt(1, SIZE - 1);
            if (maze[z * SIZE + x] != WALL || ((x + z) & 1) == 0) {
                continue;
            }
            boolean across = (x & 1) == 0 && maze[z * SIZE + x - 1] == OPEN && maze[z * SIZE + x + 1] == OPEN;
            boolean along = (z & 1) == 0 && maze[(z - 1) * SIZE + x] == OPEN && maze[(z + 1) * SIZE + x] == OPEN;
            if (across || along) {
                maze[z * SIZE + x] = OPEN;
                knocked = knocked + 1;
            }
        }
    }

    static int dx(int direction) {
        return direction == 0 ? 1 : (direction == 2 ? -1 : 0);
    }

    static int dz(int direction) {
        return direction == 1 ? 1 : (direction == 3 ? -1 : 0);
    }

    static boolean isWall(byte[] maze, float x, float z) {
        if (x < 0f || z < 0f || x >= SIZE || z >= SIZE) {
            return true;
        }
        return maze[(int) z * SIZE + (int) x] != OPEN;
    }

    static int cellOf(float x, float z) {
        return (int) z * SIZE + (int) x;
    }

    static void bfs(byte[] maze, short[] paths, int start) {
        for (int i = 0; i < CELLS; i++) {
            paths[i] = -1;
        }
        paths[start] = 0;
        paths[CELLS] = (short) start;
        int head = 0;
        int tail = 1;
        while (head < tail) {
            int cell = paths[CELLS + head];
            head = head + 1;
            for (int d = 0; d < 4; d++) {
                int next = cell + dx(d) + dz(d) * SIZE;
                if (maze[next] == OPEN && paths[next] < 0) {
                    paths[next] = (short) (paths[cell] + 1);
                    paths[CELLS + tail] = (short) next;
                    tail = tail + 1;
                }
            }
        }
    }
}
