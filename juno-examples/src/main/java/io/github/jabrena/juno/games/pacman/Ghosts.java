package io.github.jabrena.juno.games.pacman;

import io.github.jabrena.juno.api.Random;

/**
 * The ghosts' own rules: leaving the house, stepping through the maze, and the arcade's targeting
 * rules (Blinky chases, Pinky ambushes four tiles ahead, Inky mirrors Blinky around a point two
 * tiles ahead of Pac-Man, and Clyde chases only while more than eight tiles away).
 */
final class Ghosts {
    private Ghosts() {
    }

    /** Pinky leaves at once, Inky after 30 dots, Clyde after 90, or any of them after 4 s without a dot. */
    static void releaseGhosts(int[] ghosts) {
        for (int g = 1; g < Session.GHOSTS; g++) {
            int base = g * Session.G_STRIDE;
            if (ghosts[base + Session.G_STATE] != Session.IN_HOUSE) {
                continue;
            }
            int needed = 0;
            if (g == 2) {
                needed = 30;
            } else if (g == 3) {
                needed = 90;
            }
            if (Session.dotsEaten >= needed || Session.sinceLastDot > 4000 / Session.FRAME_MILLIS) {
                ghosts[base + Session.G_STATE] = Session.LEAVING;
                Session.sinceLastDot = 0;
            }
            // Ghosts leave one at a time.
            return;
        }
    }

    static void moveGhost(byte[] tiles, int[] ghosts, int g) {
        int base = g * Session.G_STRIDE;
        int state = ghosts[base + Session.G_STATE];
        int speed = Math.min(15 + Session.level, 20);
        int row = Math.floorDiv(ghosts[base + Session.G_Y], Maze.TILE);
        int column = Math.floorDiv(ghosts[base + Session.G_X], Maze.TILE);
        if (state == Session.EYES || state == Session.ENTERING) {
            speed = 32;
        } else if (state == Session.IN_HOUSE) {
            return;
        } else if (state == Session.LEAVING) {
            speed = 8;
        } else if (row == Maze.TUNNEL_ROW && (column <= 5 || column >= Maze.COLUMNS - 6)) {
            speed = 8;
        } else if (ghosts[base + Session.G_FRIGHTENED] != 0) {
            speed = 10;
        }
        int acc = ghosts[base + Session.G_ACC] + speed;
        while (acc >= 16) {
            acc = acc - 16;
            stepGhost(tiles, ghosts, g);
        }
        ghosts[base + Session.G_ACC] = acc;
    }

    private static void stepGhost(byte[] tiles, int[] ghosts, int g) {
        int base = g * Session.G_STRIDE;
        int state = ghosts[base + Session.G_STATE];
        int x = ghosts[base + Session.G_X];
        int y = ghosts[base + Session.G_Y];
        if (state == Session.LEAVING || state == Session.ENTERING) {
            stepThroughDoor(ghosts, base, state, x, y);
            return;
        }
        if (Session.atTileCenter(x, y)) {
            int column = Math.floorDiv(x, Maze.TILE);
            int row = Math.floorDiv(y, Maze.TILE);
            if (state == Session.EYES && row == 11 && (column == 13 || column == 14)) {
                ghosts[base + Session.G_STATE] = Session.ENTERING;
                return;
            }
            ghosts[base + Session.G_DIR] = chooseDirection(tiles, ghosts, g, column, row);
        }
        int dir = ghosts[base + Session.G_DIR];
        ghosts[base + Session.G_X] = Session.wrapX(x + Session.dx(dir));
        ghosts[base + Session.G_Y] = y + Session.dy(dir);
    }

    /** Lines up with the door, then goes through it: up to leave, down to revive. */
    private static void stepThroughDoor(int[] ghosts, int base, int state, int x, int y) {
        if (x != Session.HOUSE_X) {
            ghosts[base + Session.G_X] = x < Session.HOUSE_X ? x + 1 : x - 1;
        } else if (state == Session.LEAVING) {
            if (y > Session.EXIT_Y) {
                ghosts[base + Session.G_Y] = y - 1;
                ghosts[base + Session.G_DIR] = Session.UP;
            } else {
                ghosts[base + Session.G_STATE] = Session.ACTIVE;
                ghosts[base + Session.G_DIR] = Session.LEFT;
            }
        } else if (y < Session.HOUSE_Y) {
            ghosts[base + Session.G_Y] = y + 1;
            ghosts[base + Session.G_DIR] = Session.DOWN;
        } else {
            ghosts[base + Session.G_STATE] = Session.LEAVING;
            ghosts[base + Session.G_FRIGHTENED] = 0;
        }
    }

    private static int chooseDirection(byte[] tiles, int[] ghosts, int g, int column, int row) {
        int base = g * Session.G_STRIDE;
        int current = ghosts[base + Session.G_DIR];
        int reverse = (current + 2) & 3;
        boolean frightened = ghosts[base + Session.G_FRIGHTENED] != 0 && ghosts[base + Session.G_STATE] == Session.ACTIVE;
        int targetColumn = targetColumn(ghosts, g);
        int targetRow = targetRow(ghosts, g);
        int bestDir = current;
        int bestDistance = Integer.MAX_VALUE;
        int randomPick = -1;
        if (frightened) {
            randomPick = Random.nextInt(4);
        }
        for (int dir = Session.UP; dir <= Session.RIGHT; dir++) {
            if (dir == reverse) {
                continue;
            }
            int nextColumn = column + Session.dx(dir);
            int nextRow = row + Session.dy(dir);
            int tile = Maze.tileAt(tiles, nextColumn, nextRow);
            if (tile == Maze.WALL || tile == Maze.DOOR) {
                continue;
            }
            if (frightened) {
                // A random turn: the first open one at or after a random direction.
                int order = (dir - randomPick + 4) & 3;
                if (order < bestDistance) {
                    bestDistance = order;
                    bestDir = dir;
                }
                continue;
            }
            int ddx = nextColumn - targetColumn;
            int ddy = nextRow - targetRow;
            int distance = ddx * ddx + ddy * ddy;
            if (distance < bestDistance) {
                bestDistance = distance;
                bestDir = dir;
            }
        }
        return bestDir;
    }

    private static int targetColumn(int[] ghosts, int g) {
        return target(ghosts, g, true);
    }

    private static int targetRow(int[] ghosts, int g) {
        return target(ghosts, g, false);
    }

    /** The column (or row) of ghost {@code g}'s target tile under the current mode. */
    private static int target(int[] ghosts, int g, boolean wantColumn) {
        int base = g * Session.G_STRIDE;
        if (ghosts[base + Session.G_STATE] == Session.EYES) {
            return wantColumn ? 13 : 11;
        }
        int pacColumn = Math.floorDiv(Session.pacX, Maze.TILE);
        int pacRow = Math.floorDiv(Session.pacY, Maze.TILE);
        int column;
        int row;
        boolean scatter = !Session.chasing();
        if (g == 3 && !scatter) {
            int gx = Math.floorDiv(ghosts[base + Session.G_X], Maze.TILE) - pacColumn;
            int gy = Math.floorDiv(ghosts[base + Session.G_Y], Maze.TILE) - pacRow;
            scatter = gx * gx + gy * gy < 64;
        }
        if (scatter) {
            if (g == 0) {
                column = Maze.COLUMNS - 3;
                row = -3;
            } else if (g == 1) {
                column = 2;
                row = -3;
            } else if (g == 2) {
                column = Maze.COLUMNS - 1;
                row = Maze.ROWS;
            } else {
                column = 0;
                row = Maze.ROWS;
            }
        } else if (g == 0 || g == 3) {
            column = pacColumn;
            row = pacRow;
        } else if (g == 1) {
            column = pacColumn + 4 * Session.dx(Session.pacDir);
            row = pacRow + 4 * Session.dy(Session.pacDir);
        } else {
            int aheadColumn = pacColumn + 2 * Session.dx(Session.pacDir);
            int aheadRow = pacRow + 2 * Session.dy(Session.pacDir);
            column = 2 * aheadColumn - Math.floorDiv(ghosts[Session.G_X], Maze.TILE);
            row = 2 * aheadRow - Math.floorDiv(ghosts[Session.G_Y], Maze.TILE);
        }
        return wantColumn ? column : row;
    }
}
