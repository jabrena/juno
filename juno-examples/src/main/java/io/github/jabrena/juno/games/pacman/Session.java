package io.github.jabrena.juno.games.pacman;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * Score, lives, level progress, the ghost record layout, screen geometry, and the rules governing
 * Pac-Man's movement, eating, scoring and the scatter/chase/frightened phases. Ghost movement and
 * targeting themselves live in {@link Ghosts}.
 */
final class Session {
    static final int WIDTH = 240;
    static final int HEIGHT = 320;
    static final int FRAME_MILLIS = 20;
    static final int EXTRA_LIFE_SCORE = 10000;

    // Directions, in the arcade's tie-break order.
    static final int UP = 0;
    static final int LEFT = 1;
    static final int DOWN = 2;
    static final int RIGHT = 3;

    // Sprites are 13x13 pixels around their center.
    static final int HALF = 6;

    // Ghosts: fixed-stride records.
    static final int GHOSTS = 4;
    static final int G_X = 0;
    static final int G_Y = 1;
    static final int G_DIR = 2;
    static final int G_STATE = 3;
    static final int G_ACC = 4;
    static final int G_SHOWN_X = 5;
    static final int G_SHOWN_Y = 6;
    static final int G_SHOWN_LOOK = 7;
    static final int G_FRIGHTENED = 8;
    static final int G_STRIDE = 9;

    static final int IN_HOUSE = 0;
    static final int LEAVING = 1;
    static final int ACTIVE = 2;
    static final int EYES = 3;
    static final int ENTERING = 4;

    // Ghost house: its door and the tile row just above it.
    static final int HOUSE_X = 112;
    static final int HOUSE_Y = 116;
    static final int EXIT_Y = 92;

    static int score;
    static int best;
    static int lives;
    static int level;
    static int dotsEaten;
    static int nextExtraLife;

    static int pacX;
    static int pacY;
    static int pacDir;
    static int pacNext;
    static int pacAcc;
    static int pacSteps;
    static int pacShownX;
    static int pacShownY;
    static int pacShownLook;
    static int pacDeath;
    static boolean pacVisible;
    static boolean ghostsVisible;

    static int frightTimer;
    static int ghostsEatenInFright;
    static int phase;
    static int phaseTimer;
    static int sinceLastDot;

    private Session() {
    }

    static void newGame() {
        score = 0;
        lives = 3;
        level = 1;
        nextExtraLife = EXTRA_LIFE_SCORE;
    }

    static void finishGame() {
        if (score > best) {
            best = score;
        }
    }

    static void resetActors(int[] ghosts) {
        pacX = 14 * Maze.TILE;
        pacY = 23 * Maze.TILE + 4;
        pacDir = LEFT;
        pacNext = LEFT;
        pacAcc = 0;
        pacSteps = 0;
        pacDeath = 0;
        pacVisible = true;
        ghostsVisible = true;
        pacShownX = -1000;
        frightTimer = 0;
        phase = 0;
        phaseTimer = phaseLength(0);
        sinceLastDot = 0;
        for (int g = 0; g < GHOSTS; g++) {
            int base = g * G_STRIDE;
            ghosts[base + G_STATE] = IN_HOUSE;
            ghosts[base + G_X] = g == 2 ? HOUSE_X - 16 : g == 3 ? HOUSE_X + 16 : HOUSE_X;
            ghosts[base + G_Y] = HOUSE_Y;
            ghosts[base + G_DIR] = UP;
            ghosts[base + G_ACC] = 0;
            ghosts[base + G_SHOWN_X] = -1000;
            ghosts[base + G_FRIGHTENED] = 0;
        }
        // Blinky starts outside, Pinky in the middle of the house.
        ghosts[G_STATE] = ACTIVE;
        ghosts[G_X] = HOUSE_X;
        ghosts[G_Y] = EXIT_Y;
        ghosts[G_DIR] = LEFT;
    }

    // ---- Scatter / chase / frightened ----

    /** Scatter 7 s, chase 20 s, scatter 7, chase 20, scatter 5, chase 20, scatter 5, then chase. */
    static int phaseLength(int index) {
        int seconds;
        switch (index) {
            case 0:
            case 2:
                seconds = 7;
                break;
            case 4:
            case 6:
                seconds = 5;
                break;
            case 7:
                return -1;
            default:
                seconds = 20;
                break;
        }
        return seconds * 1000 / FRAME_MILLIS;
    }

    static boolean chasing() {
        return (phase & 1) != 0;
    }

    static void updatePhase(int[] ghosts) {
        if (frightTimer > 0) {
            frightTimer = frightTimer - 1;
            if (frightTimer == 0) {
                for (int g = 0; g < GHOSTS; g++) {
                    ghosts[g * G_STRIDE + G_FRIGHTENED] = 0;
                }
            }
            return;
        }
        if (phaseTimer < 0) {
            return;
        }
        phaseTimer = phaseTimer - 1;
        if (phaseTimer == 0) {
            phase = phase + 1;
            phaseTimer = phaseLength(phase);
            for (int g = 0; g < GHOSTS; g++) {
                int base = g * G_STRIDE;
                if (ghosts[base + G_STATE] == ACTIVE) {
                    ghosts[base + G_DIR] = (ghosts[base + G_DIR] + 2) & 3;
                }
            }
        }
    }

    static void frighten(int[] ghosts) {
        frightTimer = Math.max(60, 6000 / FRAME_MILLIS - (level - 1) * 40);
        ghostsEatenInFright = 0;
        for (int g = 0; g < GHOSTS; g++) {
            int base = g * G_STRIDE;
            int state = ghosts[base + G_STATE];
            if (state == ACTIVE || state == IN_HOUSE || state == LEAVING) {
                ghosts[base + G_FRIGHTENED] = 1;
            }
            if (state == ACTIVE) {
                ghosts[base + G_DIR] = (ghosts[base + G_DIR] + 2) & 3;
            }
        }
    }

    // ---- Pac-Man ----

    static void movePac(byte[] tiles, int[] ghosts) {
        int speed = Math.min(16 + level, 21);
        if (frightTimer > 0) {
            speed = speed + 2;
        }
        pacAcc = pacAcc + speed;
        while (pacAcc >= 16) {
            pacAcc = pacAcc - 16;
            stepPac(tiles, ghosts);
        }
    }

    private static void stepPac(byte[] tiles, int[] ghosts) {
        if (pacNext == ((pacDir + 2) & 3)) {
            pacDir = pacNext;
        }
        if (atTileCenter(pacX, pacY)) {
            int column = Math.floorDiv(pacX, Maze.TILE);
            int row = Math.floorDiv(pacY, Maze.TILE);
            eat(tiles, ghosts, column, row);
            if (!blockedForPac(tiles, column + dx(pacNext), row + dy(pacNext))) {
                pacDir = pacNext;
            }
            if (blockedForPac(tiles, column + dx(pacDir), row + dy(pacDir))) {
                return;
            }
        }
        pacX = wrapX(pacX + dx(pacDir));
        pacY = pacY + dy(pacDir);
        pacSteps = pacSteps + 1;
    }

    private static void eat(byte[] tiles, int[] ghosts, int column, int row) {
        int tile = Maze.tileAt(tiles, column, row);
        if (tile != Maze.DOT && tile != Maze.ENERGIZER) {
            return;
        }
        tiles[row * Maze.COLUMNS + column] = (byte) Maze.EMPTY;
        dotsEaten = dotsEaten + 1;
        sinceLastDot = 0;
        if (tile == Maze.ENERGIZER) {
            addScore(50);
            frighten(ghosts);
        } else {
            addScore(10);
        }
        Hud.drawScore();
    }

    static void addScore(int points) {
        score = score + points;
        if (score >= nextExtraLife) {
            nextExtraLife = nextExtraLife + EXTRA_LIFE_SCORE;
            lives = lives + 1;
            Hud.drawFooter();
        }
    }

    static boolean blockedForPac(byte[] tiles, int column, int row) {
        int tile = Maze.tileAt(tiles, column, row);
        return tile == Maze.WALL || tile == Maze.DOOR;
    }

    /** Returns -1 when a ghost catches Pac-Man, otherwise the number of ghosts eaten this frame. */
    static int checkCollisions(byte[] tiles, int[] ghosts) {
        int eaten = 0;
        for (int g = 0; g < GHOSTS; g++) {
            int base = g * G_STRIDE;
            if (ghosts[base + G_STATE] != ACTIVE) {
                continue;
            }
            if (Math.abs(ghosts[base + G_X] - pacX) >= 6 || Math.abs(ghosts[base + G_Y] - pacY) >= 6) {
                continue;
            }
            if (ghosts[base + G_FRIGHTENED] == 0) {
                return -1;
            }
            eaten = eaten + eatGhost(tiles, ghosts, base);
        }
        return eaten;
    }

    private static int eatGhost(byte[] tiles, int[] ghosts, int base) {
        ghostsEatenInFright = ghostsEatenInFright + 1;
        int points = 100 << ghostsEatenInFright;
        addScore(points);
        Hud.drawHeader();
        ghosts[base + G_STATE] = EYES;
        ghosts[base + G_FRIGHTENED] = 0;
        // Show the points where the ghost was, for a moment.
        ghostsVisible = false;
        pacVisible = false;
        SceneRenderer.redrawActors(tiles, ghosts, false);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.CYAN, SceneRenderer.SPACE);
        TftTouchShield.setCursor(Maze.MAZE_X + ghosts[base + G_X] - 9, Maze.MAZE_Y + ghosts[base + G_Y] - 3);
        TftTouchShield.print(points);
        Delay.millis(600);
        SceneRenderer.redrawArea(tiles, ghosts, ghosts[base + G_X] - 10, ghosts[base + G_Y] - 4, 21, 9);
        ghostsVisible = true;
        pacVisible = true;
        return 1;
    }

    // ---- Geometry ----

    static boolean atTileCenter(int x, int y) {
        int column = Math.floorDiv(x, Maze.TILE);
        return Math.floorMod(x, Maze.TILE) == 4 && Math.floorMod(y, Maze.TILE) == 4 && column >= 0
                && column < Maze.COLUMNS;
    }

    /** Wraps through the side tunnel once a sprite has left the screen. */
    static int wrapX(int x) {
        if (x < -Maze.TILE) {
            return Maze.COLUMNS * Maze.TILE + Maze.TILE - 1;
        }
        if (x >= Maze.COLUMNS * Maze.TILE + Maze.TILE) {
            return -Maze.TILE;
        }
        return x;
    }

    static int dx(int dir) {
        if (dir == LEFT) {
            return -1;
        }
        return dir == RIGHT ? 1 : 0;
    }

    static int dy(int dir) {
        if (dir == UP) {
            return -1;
        }
        return dir == DOWN ? 1 : 0;
    }
}
