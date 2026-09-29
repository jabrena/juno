package io.github.jabrena.juno.games.pacman;

import io.github.jabrena.juno.api.Random;

/**
 * The CPU pilot: at every junction it favors dots and energizers, flees any active ghost within
 * {@value #DANGER_RADIUS} tiles and instead hunts down a frightened one, and occasionally picks a
 * direction at random so it misses now and then like a person would.
 */
final class AutopilotPacMan {
    private static final int DANGER_RADIUS = 4;
    private static final int CPU_MISTAKE_PERCENT = 8;

    private AutopilotPacMan() {
    }

    static void fly(byte[] tiles, int[] ghosts) {
        if (!Session.atTileCenter(Session.pacX, Session.pacY)) {
            return;
        }
        if (Random.nextInt(100) < CPU_MISTAKE_PERCENT) {
            Session.pacNext = Random.nextInt(4);
            return;
        }
        int column = Math.floorDiv(Session.pacX, Maze.TILE);
        int row = Math.floorDiv(Session.pacY, Maze.TILE);
        int chosen = bestDirection(tiles, ghosts, column, row, false);
        if (chosen < 0) {
            // Every other direction is blocked: only reversing (a dead end) will do.
            chosen = bestDirection(tiles, ghosts, column, row, true);
        }
        if (chosen >= 0) {
            Session.pacNext = chosen;
        }
    }

    private static int bestDirection(byte[] tiles, int[] ghosts, int column, int row, boolean allowReverse) {
        int reverse = (Session.pacDir + 2) & 3;
        int chosen = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int dir = Session.UP; dir <= Session.RIGHT; dir++) {
            if (!allowReverse && dir == reverse) {
                continue;
            }
            int nextColumn = column + Session.dx(dir);
            int nextRow = row + Session.dy(dir);
            if (Session.blockedForPac(tiles, nextColumn, nextRow)) {
                continue;
            }
            int score = desirability(ghosts, nextColumn, nextRow, Maze.tileAt(tiles, nextColumn, nextRow));
            if (score > bestScore) {
                bestScore = score;
                chosen = dir;
            }
        }
        return chosen;
    }

    /** Dots and energizers pull in, a nearby active ghost pushes away, a frightened one pulls in hard. */
    private static int desirability(int[] ghosts, int column, int row, int tile) {
        int score = 0;
        if (tile == Maze.DOT) {
            score = score + 5;
        } else if (tile == Maze.ENERGIZER) {
            score = score + 20;
        }
        for (int g = 0; g < Session.GHOSTS; g++) {
            int base = g * Session.G_STRIDE;
            if (ghosts[base + Session.G_STATE] != Session.ACTIVE) {
                continue;
            }
            int ghostColumn = Math.floorDiv(ghosts[base + Session.G_X], Maze.TILE);
            int ghostRow = Math.floorDiv(ghosts[base + Session.G_Y], Maze.TILE);
            int distance = Math.abs(ghostColumn - column) + Math.abs(ghostRow - row);
            if (distance > DANGER_RADIUS) {
                continue;
            }
            int urgency = (DANGER_RADIUS - distance) * 30;
            score = score + (ghosts[base + Session.G_FRIGHTENED] != 0 ? urgency : -2 * urgency);
        }
        return score;
    }
}
