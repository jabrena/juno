package io.github.jabrena.juno.api.tft;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;

/**
 * Pac-Man on the ELEGOO 2.8" TFT touch screen shield, on the arcade's 28x31-tile maze with 8-pixel
 * tiles. Touch and hold beside, above or below Pac-Man to steer: the turn is taken at the next
 * junction where it fits. Eat all 244 dots to clear the level; an energizer turns the ghosts blue
 * for a while so you can eat them (200, 400, 800, 1600). Three lives, an extra one at
 * {@value #EXTRA_LIFE_SCORE} points.
 *
 * <p>The ghosts follow the arcade's rules: at every tile they turn towards a target tile, never
 * reversing; Blinky targets Pac-Man, Pinky four tiles ahead of him, Inky the point that mirrors
 * Blinky around the tile two ahead of him, and Clyde Pac-Man only while more than eight tiles away.
 * Scatter and chase phases alternate (each switch reverses the ghosts), the ghosts slow down in the
 * side tunnel, and they leave the house as Pac-Man eats dots.
 *
 * <p>Only the rectangles around moving sprites are redrawn. Each is streamed with {@code
 * beginPixels}/{@code pushPixel}, compositing ghosts and Pac-Man over the maze pixel computed from
 * the tile map — walls are outlines traced at run time from each tile's neighbours — so dots and
 * walls are restored exactly where a sprite has passed.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class PacMan {
    private static final int FRAME_MILLIS = 20;
    private static final int EXTRA_LIFE_SCORE = 10000;

    // Maze.
    private static final int COLUMNS = 28;
    private static final int ROWS = 31;
    private static final int TILES = COLUMNS * ROWS;
    private static final int TILE = 8;
    private static final int MAZE_X = 8;
    private static final int MAZE_Y = 32;
    private static final int TUNNEL_ROW = 14;
    private static final int TOTAL_DOTS = 244;

    private static final int EMPTY = 0;
    private static final int WALL = 1;
    private static final int DOT = 2;
    private static final int ENERGIZER = 3;
    private static final int DOOR = 4;

    // Directions, in the arcade's tie-break order.
    private static final int UP = 0;
    private static final int LEFT = 1;
    private static final int DOWN = 2;
    private static final int RIGHT = 3;

    // Sprites are 13x13 pixels around their center.
    private static final int HALF = 6;

    // Ghosts: fixed-stride records.
    private static final int GHOSTS = 4;
    private static final int G_X = 0;
    private static final int G_Y = 1;
    private static final int G_DIR = 2;
    private static final int G_STATE = 3;
    private static final int G_ACC = 4;
    private static final int G_SHOWN_X = 5;
    private static final int G_SHOWN_Y = 6;
    private static final int G_SHOWN_LOOK = 7;
    private static final int G_FRIGHTENED = 8;
    private static final int G_STRIDE = 9;

    private static final int IN_HOUSE = 0;
    private static final int LEAVING = 1;
    private static final int ACTIVE = 2;
    private static final int EYES = 3;
    private static final int ENTERING = 4;

    // Ghost house: its door and the tile row just above it.
    private static final int HOUSE_X = 112;
    private static final int HOUSE_Y = 116;
    private static final int EXIT_Y = 92;

    private static final int SPACE = TftTouchShield.BLACK;
    private static final int WALL_COLOR = 0x211F;
    private static final int DOT_COLOR = 0xFDD5;
    private static final int DOOR_COLOR = 0xFDDF;
    private static final int PAC_COLOR = TftTouchShield.YELLOW;
    private static final int FRIGHT_COLOR = 0x211F;
    private static final int FRIGHT_FACE = 0xFDD5;
    private static final int EYE_PUPIL = 0x211F;

    private static int score;
    private static int best;
    private static int lives;
    private static int level;
    private static int dotsEaten;
    private static int nextExtraLife;
    private static int wallColor;

    private static int pacX;
    private static int pacY;
    private static int pacDir;
    private static int pacNext;
    private static int pacAcc;
    private static int pacSteps;
    private static int pacShownX;
    private static int pacShownY;
    private static int pacShownLook;
    private static int pacDeath;
    private static boolean pacVisible;
    private static boolean ghostsVisible;

    private static int frightTimer;
    private static int ghostsEatenInFright;
    private static int phase;
    private static int phaseTimer;
    private static int sinceLastDot;

    private PacMan() {
    }

    public static void main(String[] args) {
        byte[] tiles = new byte[TILES];
        int[] ghosts = new int[GHOSTS * G_STRIDE];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        TftTouchShield.fillScreen(SPACE);
        wallColor = WALL_COLOR;
        loadMaze(tiles);
        drawMaze(tiles, ghosts);
        showCentered("PAC-MAN", 14 * TILE + MAZE_Y + 1, 1, PAC_COLOR);
        showCentered("Hold beside Pac-Man to steer", 292, 1, TftTouchShield.WHITE);
        showCentered("Tap to start", 306, 1, TftTouchShield.YELLOW);
        waitForTap();
        Random.seed(Clock.micros());

        while (true) {
            score = 0;
            lives = 3;
            level = 1;
            nextExtraLife = EXTRA_LIFE_SCORE;
            TftTouchShield.fillScreen(SPACE);
            while (lives > 0) {
                loadMaze(tiles);
                dotsEaten = 0;
                drawMaze(tiles, ghosts);
                drawHeader();
                boolean cleared = false;
                while (lives > 0 && !cleared) {
                    cleared = playLife(tiles, ghosts);
                    if (!cleared) {
                        lives = lives - 1;
                    }
                }
                if (cleared) {
                    flashMaze(tiles, ghosts);
                    level = level + 1;
                }
            }
            if (score > best) {
                best = score;
            }
            drawHeader();
            showCentered("GAME  OVER", 17 * TILE + MAZE_Y, 1, TftTouchShield.RED);
            Delay.millis(1500);
            waitForTap();
        }
    }

    // ---- Game flow ----

    /** Plays from the starting positions until Pac-Man dies (false) or the maze is cleared (true). */
    private static boolean playLife(byte[] tiles, int[] ghosts) {
        resetActors(ghosts);
        drawFooter();
        redrawActors(tiles, ghosts, true);
        showCentered("READY!", 17 * TILE + MAZE_Y, 1, PAC_COLOR);
        Delay.millis(1800);
        redrawArea(tiles, ghosts, 80, 17 * TILE, 72, TILE);

        int next = Clock.millis();
        int frame = 0;
        while (true) {
            while (Clock.millis() - next < 0) {
                Delay.millis(1);
            }
            next = next + FRAME_MILLIS;
            frame = frame + 1;

            steer();
            sinceLastDot = sinceLastDot + 1;
            updatePhase(ghosts);
            movePac(tiles, ghosts);
            releaseGhosts(ghosts);
            for (int g = 0; g < GHOSTS; g++) {
                moveGhost(tiles, ghosts, g);
            }
            int collision = checkCollisions(tiles, ghosts);
            redrawActors(tiles, ghosts, false);
            if (collision < 0) {
                dieAnimation(tiles, ghosts);
                return false;
            }
            if (collision > 0) {
                // Eating a ghost paused the game: do not rush to catch up.
                next = Clock.millis();
            }
            if (dotsEaten == TOTAL_DOTS) {
                Delay.millis(1000);
                return true;
            }
        }
    }

    private static void resetActors(int[] ghosts) {
        pacX = 14 * TILE;
        pacY = 23 * TILE + 4;
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

    private static void dieAnimation(byte[] tiles, int[] ghosts) {
        Delay.millis(800);
        ghostsVisible = false;
        pacDir = UP;
        redrawActors(tiles, ghosts, true);
        for (int step = 1; step <= 12; step++) {
            pacDeath = step;
            redrawActors(tiles, ghosts, true);
            Delay.millis(90);
        }
        pacVisible = false;
        redrawActors(tiles, ghosts, true);
        Delay.millis(600);
    }

    private static void flashMaze(byte[] tiles, int[] ghosts) {
        ghostsVisible = false;
        pacVisible = false;
        redrawActors(tiles, ghosts, true);
        for (int flash = 0; flash < 2; flash++) {
            wallColor = TftTouchShield.WHITE;
            if ((flash & 1) != 0) {
                wallColor = WALL_COLOR;
            }
            drawMaze(tiles, ghosts);
            Delay.millis(200);
        }
        wallColor = WALL_COLOR;
    }

    // ---- Scatter / chase / frightened ----

    /** Scatter 7 s, chase 20 s, scatter 7, chase 20, scatter 5, chase 20, scatter 5, then chase. */
    private static int phaseLength(int index) {
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

    private static boolean chasing() {
        return (phase & 1) != 0;
    }

    private static void updatePhase(int[] ghosts) {
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

    private static void frighten(int[] ghosts) {
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

    /** While touched, turns towards the finger along the dominant axis from Pac-Man. */
    private static void steer() {
        if (!TftTouchShield.readTouch()) {
            return;
        }
        int dx = TftTouchShield.touchX() - (MAZE_X + pacX);
        int dy = TftTouchShield.touchY() - (MAZE_Y + pacY);
        if (Math.abs(dx) < 6 && Math.abs(dy) < 6) {
            return;
        }
        if (Math.abs(dx) > Math.abs(dy)) {
            pacNext = dx < 0 ? LEFT : RIGHT;
        } else {
            pacNext = dy < 0 ? UP : DOWN;
        }
    }

    private static void movePac(byte[] tiles, int[] ghosts) {
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
            int column = Math.floorDiv(pacX, TILE);
            int row = Math.floorDiv(pacY, TILE);
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
        int tile = tileAt(tiles, column, row);
        if (tile != DOT && tile != ENERGIZER) {
            return;
        }
        tiles[row * COLUMNS + column] = (byte) EMPTY;
        dotsEaten = dotsEaten + 1;
        sinceLastDot = 0;
        if (tile == ENERGIZER) {
            addScore(50);
            frighten(ghosts);
        } else {
            addScore(10);
        }
        drawScore();
    }

    private static void addScore(int points) {
        score = score + points;
        if (score >= nextExtraLife) {
            nextExtraLife = nextExtraLife + EXTRA_LIFE_SCORE;
            lives = lives + 1;
            drawFooter();
        }
    }

    private static boolean blockedForPac(byte[] tiles, int column, int row) {
        int tile = tileAt(tiles, column, row);
        return tile == WALL || tile == DOOR;
    }

    // ---- Ghosts ----

    /** Pinky leaves at once, Inky after 30 dots, Clyde after 90, or any of them after 4 s without a dot. */
    private static void releaseGhosts(int[] ghosts) {
        for (int g = 1; g < GHOSTS; g++) {
            int base = g * G_STRIDE;
            if (ghosts[base + G_STATE] != IN_HOUSE) {
                continue;
            }
            int needed = 0;
            if (g == 2) {
                needed = 30;
            } else if (g == 3) {
                needed = 90;
            }
            if (dotsEaten >= needed || sinceLastDot > 4000 / FRAME_MILLIS) {
                ghosts[base + G_STATE] = LEAVING;
                sinceLastDot = 0;
            }
            // Ghosts leave one at a time.
            return;
        }
    }

    private static void moveGhost(byte[] tiles, int[] ghosts, int g) {
        int base = g * G_STRIDE;
        int state = ghosts[base + G_STATE];
        int speed = Math.min(15 + level, 20);
        int row = Math.floorDiv(ghosts[base + G_Y], TILE);
        int column = Math.floorDiv(ghosts[base + G_X], TILE);
        if (state == EYES || state == ENTERING) {
            speed = 32;
        } else if (state == IN_HOUSE) {
            return;
        } else if (state == LEAVING) {
            speed = 8;
        } else if (row == TUNNEL_ROW && (column <= 5 || column >= COLUMNS - 6)) {
            speed = 8;
        } else if (ghosts[base + G_FRIGHTENED] != 0) {
            speed = 10;
        }
        int acc = ghosts[base + G_ACC] + speed;
        while (acc >= 16) {
            acc = acc - 16;
            stepGhost(tiles, ghosts, g);
        }
        ghosts[base + G_ACC] = acc;
    }

    private static void stepGhost(byte[] tiles, int[] ghosts, int g) {
        int base = g * G_STRIDE;
        int state = ghosts[base + G_STATE];
        int x = ghosts[base + G_X];
        int y = ghosts[base + G_Y];
        if (state == LEAVING || state == ENTERING) {
            // Line up with the door, then go through it: up to leave, down to revive.
            if (x != HOUSE_X) {
                ghosts[base + G_X] = x < HOUSE_X ? x + 1 : x - 1;
            } else if (state == LEAVING) {
                if (y > EXIT_Y) {
                    ghosts[base + G_Y] = y - 1;
                    ghosts[base + G_DIR] = UP;
                } else {
                    ghosts[base + G_STATE] = ACTIVE;
                    ghosts[base + G_DIR] = LEFT;
                }
            } else if (y < HOUSE_Y) {
                ghosts[base + G_Y] = y + 1;
                ghosts[base + G_DIR] = DOWN;
            } else {
                ghosts[base + G_STATE] = LEAVING;
                ghosts[base + G_FRIGHTENED] = 0;
            }
            return;
        }
        if (atTileCenter(x, y)) {
            int column = Math.floorDiv(x, TILE);
            int row = Math.floorDiv(y, TILE);
            if (state == EYES && row == 11 && (column == 13 || column == 14)) {
                ghosts[base + G_STATE] = ENTERING;
                return;
            }
            ghosts[base + G_DIR] = chooseDirection(tiles, ghosts, g, column, row);
        }
        int dir = ghosts[base + G_DIR];
        ghosts[base + G_X] = wrapX(x + dx(dir));
        ghosts[base + G_Y] = y + dy(dir);
    }

    private static int chooseDirection(byte[] tiles, int[] ghosts, int g, int column, int row) {
        int base = g * G_STRIDE;
        int current = ghosts[base + G_DIR];
        int reverse = (current + 2) & 3;
        boolean frightened = ghosts[base + G_FRIGHTENED] != 0 && ghosts[base + G_STATE] == ACTIVE;
        int targetColumn = targetColumn(ghosts, g);
        int targetRow = targetRow(ghosts, g);
        int bestDir = current;
        int bestDistance = Integer.MAX_VALUE;
        int randomPick = -1;
        if (frightened) {
            randomPick = Random.nextInt(4);
        }
        for (int dir = UP; dir <= RIGHT; dir++) {
            if (dir == reverse) {
                continue;
            }
            int nextColumn = column + dx(dir);
            int nextRow = row + dy(dir);
            int tile = tileAt(tiles, nextColumn, nextRow);
            if (tile == WALL || tile == DOOR) {
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
        int base = g * G_STRIDE;
        if (ghosts[base + G_STATE] == EYES) {
            return wantColumn ? 13 : 11;
        }
        int pacColumn = Math.floorDiv(pacX, TILE);
        int pacRow = Math.floorDiv(pacY, TILE);
        int column;
        int row;
        boolean scatter = !chasing();
        if (g == 3 && !scatter) {
            int gx = Math.floorDiv(ghosts[base + G_X], TILE) - pacColumn;
            int gy = Math.floorDiv(ghosts[base + G_Y], TILE) - pacRow;
            scatter = gx * gx + gy * gy < 64;
        }
        if (scatter) {
            if (g == 0) {
                column = COLUMNS - 3;
                row = -3;
            } else if (g == 1) {
                column = 2;
                row = -3;
            } else if (g == 2) {
                column = COLUMNS - 1;
                row = ROWS;
            } else {
                column = 0;
                row = ROWS;
            }
        } else if (g == 0 || g == 3) {
            column = pacColumn;
            row = pacRow;
        } else if (g == 1) {
            column = pacColumn + 4 * dx(pacDir);
            row = pacRow + 4 * dy(pacDir);
        } else {
            int aheadColumn = pacColumn + 2 * dx(pacDir);
            int aheadRow = pacRow + 2 * dy(pacDir);
            column = 2 * aheadColumn - Math.floorDiv(ghosts[G_X], TILE);
            row = 2 * aheadRow - Math.floorDiv(ghosts[G_Y], TILE);
        }
        return wantColumn ? column : row;
    }

    /** Returns -1 when a ghost catches Pac-Man, otherwise the number of ghosts eaten this frame. */
    private static int checkCollisions(byte[] tiles, int[] ghosts) {
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
            ghostsEatenInFright = ghostsEatenInFright + 1;
            int points = 100 << ghostsEatenInFright;
            addScore(points);
            drawHeader();
            ghosts[base + G_STATE] = EYES;
            ghosts[base + G_FRIGHTENED] = 0;
            // Show the points where the ghost was, for a moment.
            ghostsVisible = false;
            pacVisible = false;
            redrawActors(tiles, ghosts, false);
            TftTouchShield.setTextSize(1);
            TftTouchShield.setTextColor(TftTouchShield.CYAN, SPACE);
            TftTouchShield.setCursor(MAZE_X + ghosts[base + G_X] - 9, MAZE_Y + ghosts[base + G_Y] - 3);
            TftTouchShield.print(points);
            Delay.millis(600);
            redrawArea(tiles, ghosts, ghosts[base + G_X] - 10, ghosts[base + G_Y] - 4, 21, 9);
            ghostsVisible = true;
            pacVisible = true;
            eaten = eaten + 1;
        }
        return eaten;
    }

    // ---- Maze ----

    private static void loadMaze(byte[] tiles) {
        for (int row = 0; row < ROWS; row++) {
            String line = mazeRow(row);
            for (int column = 0; column < COLUMNS; column++) {
                char c = line.charAt(column);
                int tile = EMPTY;
                if (c == '#') {
                    tile = WALL;
                } else if (c == '.') {
                    tile = DOT;
                } else if (c == 'o') {
                    tile = ENERGIZER;
                } else if (c == '-') {
                    tile = DOOR;
                }
                tiles[row * COLUMNS + column] = (byte) tile;
            }
        }
    }

    private static String mazeRow(int row) {
        switch (row) {
            case 0:
            case 30:
                return "############################";
            case 1:
            case 20:
                return "#............##............#";
            case 2:
            case 4:
            case 21:
            case 22:
                return "#.####.#####.##.#####.####.#";
            case 3:
                return "#o####.#####.##.#####.####o#";
            case 5:
            case 29:
                return "#..........................#";
            case 6:
            case 7:
                return "#.####.##.########.##.####.#";
            case 8:
            case 26:
                return "#......##....##....##......#";
            case 9:
                return "######.##### ## #####.######";
            case 10:
                return "     #.##### ## #####.#     ";
            case 11:
            case 17:
                return "     #.##          ##.#     ";
            case 12:
                return "     #.## ###--### ##.#     ";
            case 13:
            case 15:
                return "######.## #      # ##.######";
            case 14:
                return "      .   #      #   .      ";
            case 16:
            case 18:
                return "     #.## ######## ##.#     ";
            case 19:
                return "######.## ######## ##.######";
            case 23:
                return "#o..##.......  .......##..o#";
            case 24:
            case 25:
                return "###.##.##.########.##.##.###";
            default:
                return "#.##########.##.##########.#";
        }
    }

    /** The tile at a column and row; past the side edges only the tunnel row is open. */
    private static int tileAt(byte[] tiles, int column, int row) {
        if (row < 0 || row >= ROWS) {
            return WALL;
        }
        if (column < 0 || column >= COLUMNS) {
            return row == TUNNEL_ROW ? EMPTY : WALL;
        }
        return tiles[row * COLUMNS + column];
    }

    private static boolean isWall(byte[] tiles, int column, int row) {
        return tileAt(tiles, column, row) == WALL;
    }

    /**
     * Whether maze pixel ({@code px}, {@code py}) lies inside the shrunken wall shape: a wall tile
     * minus a 3-pixel margin along each side (and corner) that faces an open tile.
     */
    private static boolean insideWall(byte[] tiles, int px, int py) {
        int column = Math.floorDiv(px, TILE);
        int row = Math.floorDiv(py, TILE);
        if (!isWall(tiles, column, row)) {
            return false;
        }
        int lx = px - column * TILE;
        int ly = py - row * TILE;
        boolean near = lx < 3;
        boolean far = lx > 4;
        boolean top = ly < 3;
        boolean bottom = ly > 4;
        if ((top && !isWall(tiles, column, row - 1)) || (bottom && !isWall(tiles, column, row + 1))
                || (near && !isWall(tiles, column - 1, row)) || (far && !isWall(tiles, column + 1, row))) {
            return false;
        }
        if ((top && near && !isWall(tiles, column - 1, row - 1)) || (top && far && !isWall(tiles, column + 1, row - 1))
                || (bottom && near && !isWall(tiles, column - 1, row + 1))
                || (bottom && far && !isWall(tiles, column + 1, row + 1))) {
            return false;
        }
        return true;
    }

    /** The maze's own color at a pixel: wall outline, door, dot, energizer or black. */
    private static int backgroundAt(byte[] tiles, int px, int py) {
        int column = Math.floorDiv(px, TILE);
        int row = Math.floorDiv(py, TILE);
        int tile = tileAt(tiles, column, row);
        int lx = px - column * TILE;
        int ly = py - row * TILE;
        if (tile == WALL) {
            if (insideWall(tiles, px, py) && (!insideWall(tiles, px - 1, py) || !insideWall(tiles, px + 1, py)
                    || !insideWall(tiles, px, py - 1) || !insideWall(tiles, px, py + 1))) {
                return wallColor;
            }
            return SPACE;
        }
        if (tile == DOOR) {
            return ly == 3 || ly == 4 ? DOOR_COLOR : SPACE;
        }
        if (tile == DOT) {
            return (lx == 3 || lx == 4) && (ly == 3 || ly == 4) ? DOT_COLOR : SPACE;
        }
        if (tile == ENERGIZER) {
            int ex = 2 * lx - 7;
            int ey = 2 * ly - 7;
            return ex * ex + ey * ey <= 50 ? DOT_COLOR : SPACE;
        }
        return SPACE;
    }

    // ---- Drawing ----

    private static void drawMaze(byte[] tiles, int[] ghosts) {
        redrawArea(tiles, ghosts, -MAZE_X, 0, COLUMNS * TILE + 2 * MAZE_X, ROWS * TILE);
    }

    /** Redraws every actor that moved or changed looks; with {@code force}, all of them. */
    private static void redrawActors(byte[] tiles, int[] ghosts, boolean force) {
        for (int g = 0; g < GHOSTS; g++) {
            int base = g * G_STRIDE;
            int x = ghosts[base + G_X];
            int y = ghosts[base + G_Y];
            int look = ghostLook(ghosts, g);
            if (force || x != ghosts[base + G_SHOWN_X] || y != ghosts[base + G_SHOWN_Y]
                    || look != ghosts[base + G_SHOWN_LOOK]) {
                redrawMoved(tiles, ghosts, ghosts[base + G_SHOWN_X], ghosts[base + G_SHOWN_Y], x, y);
                ghosts[base + G_SHOWN_X] = x;
                ghosts[base + G_SHOWN_Y] = y;
                ghosts[base + G_SHOWN_LOOK] = look;
            }
        }
        int look = pacLook();
        if (force || pacX != pacShownX || pacY != pacShownY || look != pacShownLook) {
            redrawMoved(tiles, ghosts, pacShownX, pacShownY, pacX, pacY);
            pacShownX = pacX;
            pacShownY = pacY;
            pacShownLook = look;
        }
    }

    /** Redraws the union of a sprite's old and new rectangles (just the new one when far apart). */
    private static void redrawMoved(byte[] tiles, int[] ghosts, int oldX, int oldY, int x, int y) {
        if (Math.abs(oldX - x) > 2 * HALF || Math.abs(oldY - y) > 2 * HALF) {
            if (oldX > -1000) {
                redrawArea(tiles, ghosts, oldX - HALF, oldY - HALF, 2 * HALF + 1, 2 * HALF + 1);
            }
            redrawArea(tiles, ghosts, x - HALF, y - HALF, 2 * HALF + 1, 2 * HALF + 1);
            return;
        }
        int left = Math.min(oldX, x) - HALF;
        int top = Math.min(oldY, y) - HALF;
        redrawArea(tiles, ghosts, left, top, Math.max(oldX, x) + HALF + 1 - left, Math.max(oldY, y) + HALF + 1 - top);
    }

    /** Streams a maze-space rectangle, clipped to the screen, compositing the actors over the maze. */
    private static void redrawArea(byte[] tiles, int[] ghosts, int x, int y, int w, int h) {
        int left = Math.max(x, -MAZE_X);
        int top = Math.max(y, 0);
        int right = Math.min(x + w, COLUMNS * TILE + MAZE_X);
        int bottom = Math.min(y + h, ROWS * TILE);
        if (right <= left || bottom <= top) {
            return;
        }
        if (!TftTouchShield.beginPixels(MAZE_X + left, MAZE_Y + top, right - left, bottom - top)) {
            return;
        }
        for (int py = top; py < bottom; py++) {
            for (int px = left; px < right; px++) {
                TftTouchShield.pushPixel(pixelAt(tiles, ghosts, px, py));
            }
        }
    }

    private static int pixelAt(byte[] tiles, int[] ghosts, int px, int py) {
        if (ghostsVisible) {
            for (int g = 0; g < GHOSTS; g++) {
                int base = g * G_STRIDE;
                int dx = px - ghosts[base + G_X];
                int dy = py - ghosts[base + G_Y];
                if (dx >= -HALF && dx <= HALF && dy >= -HALF && dy <= HALF) {
                    int color = ghostPixel(ghosts, g, dx, dy);
                    if (color >= 0) {
                        return color;
                    }
                }
            }
        }
        if (pacVisible) {
            int dx = px - pacX;
            int dy = py - pacY;
            if (dx >= -HALF && dx <= HALF && dy >= -HALF && dy <= HALF && pacPixel(dx, dy)) {
                return PAC_COLOR;
            }
        }
        return backgroundAt(tiles, px, py);
    }

    private static int pacLook() {
        return mouth() * 4 + pacDir + pacDeath * 64 + (pacVisible ? 0 : 1024);
    }

    /** Mouth opening 0 (closed) to 2 (wide), cycling with the distance Pac-Man has moved. */
    private static int mouth() {
        int cycle = (pacSteps / 2) & 3;
        return cycle == 3 ? 1 : cycle;
    }

    private static boolean pacPixel(int dx, int dy) {
        if (dx * dx + dy * dy > HALF * HALF + HALF) {
            return false;
        }
        int forward;
        int side;
        if (pacDir == RIGHT) {
            forward = dx;
            side = Math.abs(dy);
        } else if (pacDir == LEFT) {
            forward = -dx;
            side = Math.abs(dy);
        } else if (pacDir == UP) {
            forward = -dy;
            side = Math.abs(dx);
        } else {
            forward = dy;
            side = Math.abs(dx);
        }
        if (pacDeath > 0) {
            // Dying, the mouth opens from 45 degrees until nothing is left.
            if (dx == 0 && dy == 0) {
                return pacDeath < 12;
            }
            double angle = Math.toDegrees(Math.atan2((double) side, (double) forward));
            return angle > 45.0 + pacDeath * 135.0 / 12.0;
        }
        // The mouth is a wedge around the direction of travel, 0, 1 or 2 units open.
        return forward <= 0 || side * 2 > forward * mouth();
    }

    private static int ghostLook(int[] ghosts, int g) {
        int base = g * G_STRIDE;
        int state = ghosts[base + G_STATE];
        int kind = 0;
        if (state == EYES || state == ENTERING) {
            kind = 3;
        } else if (ghosts[base + G_FRIGHTENED] != 0) {
            kind = flashing() ? 2 : 1;
        }
        int feet = ((ghosts[base + G_X] + ghosts[base + G_Y]) >> 2) & 1;
        return ghosts[base + G_DIR] + kind * 4 + feet * 16 + (ghostsVisible ? 0 : 32);
    }

    private static boolean flashing() {
        return frightTimer < 2000 / FRAME_MILLIS && ((frightTimer / 10) & 1) != 0;
    }

    /** The ghost's color at an offset from its center, or -1 where it is transparent. */
    private static int ghostPixel(int[] ghosts, int g, int dx, int dy) {
        int base = g * G_STRIDE;
        int state = ghosts[base + G_STATE];
        int dir = ghosts[base + G_DIR];
        boolean eyesOnly = state == EYES || state == ENTERING;
        boolean frightened = !eyesOnly && ghosts[base + G_FRIGHTENED] != 0;

        if (!frightened) {
            // Eyes: 4x4 whites with 2x2 pupils looking where the ghost is heading.
            int ox = dx(dir);
            int oy = dy(dir);
            int ex = dx - ox;
            int ey = dy - oy;
            if (ey >= -4 && ey <= -1 && ((ex >= -5 && ex <= -2) || (ex >= 1 && ex <= 4))) {
                int pupilX = ex + 1;
                if (ex >= 1) {
                    pupilX = ex - 2;
                }
                if (pupilX + 4 >= 1 + ox && pupilX + 4 <= 2 + ox && ey + 4 >= 1 + oy && ey + 4 <= 2 + oy) {
                    return EYE_PUPIL;
                }
                return TftTouchShield.WHITE;
            }
            if (eyesOnly) {
                return -1;
            }
        }

        // Body: a round top, straight sides and a wavy hem.
        boolean body;
        if (dy <= 0) {
            body = dx * dx + dy * dy <= HALF * HALF + HALF;
        } else if (dy < HALF) {
            body = true;
        } else {
            int feet = ((ghosts[base + G_X] + ghosts[base + G_Y]) >> 2) & 1;
            body = ((dx + HALF + feet * 2) & 3) < 2;
        }
        if (!body) {
            return -1;
        }
        if (!frightened) {
            return ghostColor(g);
        }
        boolean flash = flashing();
        int skin = flash ? TftTouchShield.WHITE : FRIGHT_COLOR;
        int face = flash ? TftTouchShield.RED : FRIGHT_FACE;
        // Frightened face: two small eyes and a zig-zag mouth.
        if (dy >= -3 && dy <= -2 && (dx == -3 || dx == -2 || dx == 2 || dx == 3)) {
            return face;
        }
        if ((dy == 2 && (dx & 1) == 0 && Math.abs(dx) <= 4) || (dy == 3 && (dx & 1) != 0 && Math.abs(dx) <= 5)) {
            return face;
        }
        return skin;
    }

    private static int ghostColor(int g) {
        if (g == 0) {
            return 0xF800;
        }
        if (g == 1) {
            return 0xFDDF;
        }
        if (g == 2) {
            return 0x07FF;
        }
        return 0xFDCA;
    }

    // ---- Geometry ----

    private static boolean atTileCenter(int x, int y) {
        int column = Math.floorDiv(x, TILE);
        return Math.floorMod(x, TILE) == 4 && Math.floorMod(y, TILE) == 4 && column >= 0 && column < COLUMNS;
    }

    /** Wraps through the side tunnel once a sprite has left the screen. */
    private static int wrapX(int x) {
        if (x < -TILE) {
            return COLUMNS * TILE + TILE - 1;
        }
        if (x >= COLUMNS * TILE + TILE) {
            return -TILE;
        }
        return x;
    }

    private static int dx(int dir) {
        if (dir == LEFT) {
            return -1;
        }
        return dir == RIGHT ? 1 : 0;
    }

    private static int dy(int dir) {
        if (dir == UP) {
            return -1;
        }
        return dir == DOWN ? 1 : 0;
    }

    // ---- Input ----

    private static void waitForTap() {
        while (!TftTouchShield.readTouch()) {
            Delay.millis(10);
        }
        int misses = 0;
        while (misses < 3) {
            if (TftTouchShield.readTouch()) {
                misses = 0;
            } else {
                misses = misses + 1;
            }
            Delay.millis(10);
        }
    }

    // ---- Text ----

    private static void drawHeader() {
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, SPACE);
        TftTouchShield.setCursor(24, 4);
        TftTouchShield.print("1UP");
        TftTouchShield.setCursor(150, 4);
        TftTouchShield.print("HIGH SCORE");
        TftTouchShield.setTextSize(2);
        TftTouchShield.fillRect(0, 13, 240, 16, SPACE);
        TftTouchShield.setCursor(24, 13);
        TftTouchShield.print(score);
        TftTouchShield.setCursor(150, 13);
        TftTouchShield.print(Math.max(best, score));
    }

    /** Just the numbers, over the previous ones: the score only grows, so nothing is left behind. */
    private static void drawScore() {
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, SPACE);
        TftTouchShield.setCursor(24, 13);
        TftTouchShield.print(score);
        if (score > best) {
            TftTouchShield.setCursor(150, 13);
            TftTouchShield.print(score);
        }
    }

    private static void drawFooter() {
        int y = MAZE_Y + ROWS * TILE + 8;
        TftTouchShield.fillRect(0, y - 2, 240, 320 - y + 2, SPACE);
        for (int life = 0; life < Math.min(lives - 1, 6); life++) {
            int cx = 24 + life * 18;
            TftTouchShield.fillCircle(cx, y + 7, 6, PAC_COLOR);
            // A mouth facing left, like the arcade's spare lives.
            for (int i = 0; i < 6; i++) {
                TftTouchShield.fillRect(cx - 6, y + 7 - i / 2, 6 - i, 1 + i, SPACE);
            }
        }
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, SPACE);
        TftTouchShield.setCursor(170, y + 4);
        TftTouchShield.print("LEVEL ");
        TftTouchShield.print(level);
    }

    private static void showCentered(String text, int y, int size, int color) {
        TftTouchShield.setTextSize(size);
        TftTouchShield.setTextColor(color, SPACE);
        TftTouchShield.setCursor((240 - text.length() * 6 * size) / 2, y);
        TftTouchShield.print(text);
    }
}
