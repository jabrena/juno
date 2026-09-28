package io.github.jabrena.juno.games.spaceparanoids;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * Space Paranoids on the ELEGOO 2.8" TFT touch screen shield, after the arcade game from TRON, in
 * landscape: drive a tank through a wireframe maze seen from its turret and destroy every flying
 * hunter before the sector's timer runs out. Enemy tanks patrol the corridors and gun turrets guard
 * them; all of them fire back. Each hit drains your shield; green energy pools on the floor
 * recharge it, and a tank whose shield is gone, or that runs out of time, costs a life. Three
 * lives, one more every 10,000 points, and each sector is a new, larger-looking maze with more
 * enemies.
 *
 * <p>Hold the buttons along the bottom to turn ({@code <} {@code >}), drive ({@code ^} {@code v})
 * and fire ({@code FIRE}); tapping the view fires too. The radar between them shows the maze from
 * above: you in white, hunters in orange, tanks in red, turrets in yellow, pools in green.
 *
 * <p>The game opens like the film: a laser scan line digitizes the grid, which then rushes past as
 * a hunter spins in, and the title materializes letter by letter; after each game over it starts
 * again from there. Tap to start, then choose who drives the tank: <b>HUMAN</b> (you) or
 * <b>CPU</b>, an autopilot that hunts along the shortest path, heads for an energy pool when its
 * shield runs low and shoots what it sees, with an aim that wanders enough to miss now and then.
 * Tap the header during the game to switch between the two ({@code CPU} shows in the header).
 *
 * <p>The maze is drawn by ray casting with hidden lines removed: rays sampled every
 * {@value #SAMPLE} columns find which wall face each column sees, the boundaries between faces are
 * then found to the pixel by bisection, and each visible stretch of a face becomes its top and
 * bottom edge plus one vertical edge on the nearer side of each boundary. Enemies are 3D wireframes
 * hidden when a wall stands between them and you. Frames go through two display lists, so only
 * lines that moved are erased and redrawn.
 */
@Board(ArduinoUnoQ.class)
public final class SpaceParanoids {
    private static final int FRAME_MILLIS = 33;

    // Screen: a text header, the 3D view, and a bar of buttons with the radar.
    private static final int WIDTH = 320;
    private static final int HEIGHT = 240;
    private static final int HEADER = 20;
    private static final int VIEW_BOTTOM = 199;
    private static final int BAR_TOP = 200;
    private static final int CENTER_X = 160;
    private static final int CENTER_Y = 110;

    // Camera.
    /** Half the view's width at distance 1: a 90-degree field of view. */
    private static final float PLANE = 1f;
    /** Pixels per world unit at distance 1: half the width over the half field of view's tangent. */
    private static final float FOCAL = 160f / PLANE;
    private static final float EYE = 0.35f;
    private static final float WALL_HEIGHT = 0.7f;
    private static final float NEAR = 0.12f;
    private static final int SAMPLE = 4;
    private static final int SAMPLES = WIDTH / SAMPLE + 1;

    // Maze: SIZE x SIZE cells, odd coordinates are rooms, walls in between.
    private static final int SIZE = 13;
    private static final int CELLS = SIZE * SIZE;
    private static final byte OPEN = 0;
    private static final byte WALL = 1;

    // Display lists.
    private static final int MAX_LINES = 150;
    private static final int L_STRIDE = 5;
    private static final int LIST_SIZE = MAX_LINES * L_STRIDE;

    /** Entity positions and velocities are fixed point: {@value #ONE} per cell. */
    private static final int ONE = 1024;
    private static final float TO_CELLS = 1f / ONE;

    // Entities: position fields and other fields in two parallel arrays.
    private static final int ENTITIES = 30;
    private static final int F_X = 0;
    private static final int F_Z = 1;
    private static final int F_VX = 2;
    private static final int F_VZ = 3;
    private static final int F_TX = 4;
    private static final int F_TZ = 5;
    private static final int F_STRIDE = 6;
    private static final int I_TYPE = 0;
    /** Shots: frames left to live. Blasts: age. */
    private static final int I_TIMER = 1;
    private static final int I_COOLDOWN = 2;
    /** Enemies: the cell they came from. Enemy shots: damage. */
    private static final int I_AUX = 3;
    private static final int I_STRIDE = 4;

    private static final int T_NONE = 0;
    private static final int T_HUNTER = 1;
    private static final int T_TANK = 2;
    private static final int T_TURRET = 3;
    private static final int T_POOL = 4;
    private static final int T_SHOT = 5;
    private static final int T_ENEMY_SHOT = 6;
    private static final int T_BLAST = 7;

    // Player.
    private static final float RADIUS = 0.26f;
    private static final float TURN = 0.075f;
    private static final float SPEED = 0.07f;
    private static final float SHOT_SPEED = 0.35f;
    private static final float ENEMY_SHOT_SPEED = 0.12f;
    private static final float HIT_RANGE = 0.32f;
    private static final int FIRE_FRAMES = 7;
    private static final int MAX_SHIELD = 100;
    private static final int START_LIVES = 3;
    private static final int BONUS_EVERY = 10000;

    // Scoring.
    private static final int HUNTER_POINTS = 1000;
    private static final int TANK_POINTS = 500;
    private static final int TURRET_POINTS = 250;
    private static final int POOL_POINTS = 100;

    // Buttons along the bottom.
    private static final int B_NONE = 0;
    private static final int B_LEFT = 1;
    private static final int B_FORWARD = 2;
    private static final int B_FIRE = 3;
    private static final int B_BACK = 4;
    private static final int B_RIGHT = 5;
    private static final int RADAR_X = 179;
    private static final int RADAR_Y = 201;
    private static final int RADAR_CELL = 3;

    // Colors.
    private static final int SPACE = TftTouchShield.BLACK;
    private static final int WALL_TOP = TftTouchShield.CYAN;
    private static final int WALL_BOTTOM = 0x0410;
    private static final int WALL_SEAM = 0x0292;
    private static final int HUNTER = TftTouchShield.ORANGE;
    private static final int TANK = TftTouchShield.RED;
    private static final int TURRET = TftTouchShield.YELLOW;
    private static final int POOL = TftTouchShield.GREEN;
    private static final int SHOT = TftTouchShield.WHITE;
    private static final int ENEMY_SHOT = TftTouchShield.MAGENTA;
    private static final int RADAR_WALL = 0x2945;
    private static final int BUTTON = 0x3A7F;

    // The opening: the scan line digitizing the grid, then the flight over it.
    private static final int SCAN_FRAMES = 36;
    private static final int GRID_FRAMES = 64;
    private static final int GRID_HORIZON = 90;
    private static final int GRID_DEPTH = 436;
    private static final String TITLE = "SPACE PARANOIDS";

    // The HUMAN and CPU buttons of the pilot screen.
    private static final int CHOICE_X = 20;
    private static final int CHOICE_Y = 104;
    private static final int CHOICE_WIDTH = 130;
    private static final int CHOICE_HEIGHT = 72;
    private static final int CHOICE_GAP = 20;

    // The CPU driver: how far its aim wanders (hundredths of a radian), how often it changes, how
    // far it sees, and at what shield it goes for an energy pool.
    private static final int CPU_WOBBLE = 20;
    private static final int CPU_WOBBLE_FRAMES = 20;
    private static final float CPU_SIGHT = 6f;
    private static final int CPU_LOW_SHIELD = 40;

    private static int score;
    private static int best;
    private static int lives;
    private static int level;
    private static int nextBonus;
    private static int shield;
    private static int timeLeft;
    private static int huntersLeft;
    private static int frame;
    private static int fireCooldown;
    private static int held;
    private static boolean autopilot;
    private static boolean headerPressed;
    private static float aimWobble;

    // Camera state.
    private static float posX;
    private static float posZ;
    private static float angle;
    private static float dirX;
    private static float dirZ;
    private static float planeX;
    private static float planeZ;
    private static float inverse;

    // Last ray cast: the face hit, its distance, and where along the face.
    private static int rayFace;
    private static float rayDistance;
    private static float rayAlong;

    // Display list state.
    private static int front;
    private static int shown;
    private static int built;

    private SpaceParanoids() {
    }

    public static void main(String[] args) {
        byte[] maze = new byte[CELLS];
        short[] paths = new short[2 * CELLS];
        byte[] radar = new byte[CELLS];
        short[] lines = new short[2 * LIST_SIZE];
        int[] depth = new int[SAMPLES];
        int[] faces = new int[SAMPLES];
        int[] fs = new int[ENTITIES * F_STRIDE];
        int[] is = new int[ENTITIES * I_STRIDE];
        short[] route = new short[2 * CELLS];
        byte[] letter = new byte[1];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);

        while (true) {
            opening(lines);
            drawTitle(maze, paths, lines, depth, faces, fs, is, letter);
            waitForTap();
            Random.seed(Clock.micros());
            choosePilot();
            score = 0;
            lives = START_LIVES;
            level = 1;
            nextBonus = BONUS_EVERY;
            while (playLevel(maze, paths, route, radar, lines, depth, faces, fs, is)) {
                level = level + 1;
            }
            if (score > best) {
                best = score;
            }
            drawHeader();
            clearView();
            showCentered("GAME OVER", 90, 3, TftTouchShield.RED);
            Delay.millis(3000);
        }
    }

    // ---- Game flow ----

    /** Plays one sector; returns true when it is cleared, false when the last life is lost. */
    private static boolean playLevel(byte[] maze, short[] paths, short[] route, byte[] radar, short[] lines,
            int[] depth, int[] faces, int[] fs, int[] is) {
        startLevel(maze, paths, fs, is);
        TftTouchShield.fillScreen(SPACE);
        drawHeader();
        drawBar(maze, radar);
        showCentered("SECTOR", 76, 3, TftTouchShield.ORANGE);
        TftTouchShield.setCursor(level < 10 ? 151 : 142, 108);
        TftTouchShield.print(level);
        showCentered("Destroy the hunters", 140, 1, TftTouchShield.WHITE);
        Delay.millis(1400);
        clearView();

        int next = Clock.millis();
        int second = next + 1000;
        while (true) {
            while (Clock.millis() - next < 0) {
                Delay.millis(1);
            }
            next = next + FRAME_MILLIS;
            if (Clock.millis() - next > 4 * FRAME_MILLIS) {
                next = Clock.millis();
            }
            frame = frame + 1;
            handleTouch(maze, fs, is);
            if (autopilot) {
                flyAutopilot(maze, route, fs, is);
            }
            step(maze, paths, fs, is);
            render(maze, lines, depth, faces, fs, is);
            if (frame % 6 == 0) {
                updateRadar(radar, fs, is);
            }
            if (Clock.millis() - second >= 0) {
                second = second + 1000;
                timeLeft = timeLeft - 1;
                drawStatus();
            }
            if (huntersLeft == 0) {
                int bonus = 10 * timeLeft * level;
                addScore(bonus);
                drawHeader();
                clearView();
                showCentered("SECTOR CLEAR", 86, 2, TftTouchShield.GREEN);
                TftTouchShield.setTextSize(1);
                TftTouchShield.setTextColor(TftTouchShield.WHITE, SPACE);
                TftTouchShield.setCursor(118, 120);
                TftTouchShield.print("TIME BONUS ");
                TftTouchShield.print(bonus);
                Delay.millis(2000);
                return true;
            }
            if (shield <= 0 || timeLeft <= 0) {
                boolean timeUp = timeLeft <= 0;
                loseLife(timeUp);
                if (lives == 0) {
                    return false;
                }
                respawn(maze, fs, is);
                if (timeUp) {
                    timeLeft = levelTime();
                }
                clearView();
                drawHeader();
                next = Clock.millis();
                second = next + 1000;
            }
        }
    }

    private static int levelTime() {
        return Math.max(60, 130 - 10 * level);
    }

    /** A new maze with the sector's enemies and pools; the player starts in the top-left room. */
    private static void startLevel(byte[] maze, short[] paths, int[] fs, int[] is) {
        generateMaze(maze, paths);
        clearEntities(fs, is);
        placePlayer(maze);
        shield = MAX_SHIELD;
        timeLeft = levelTime();
        fireCooldown = 0;
        bfs(maze, paths, cellOf(posX, posZ));
        int hunters = Math.min(2 + level, 8);
        int tanks = Math.min(level - 1, 3);
        int turrets = Math.min(1 + level / 2, 4);
        for (int i = 0; i < hunters; i++) {
            placeEnemy(maze, paths, fs, is, T_HUNTER, 4);
        }
        for (int i = 0; i < tanks; i++) {
            placeEnemy(maze, paths, fs, is, T_TANK, 5);
        }
        for (int i = 0; i < turrets; i++) {
            placeEnemy(maze, paths, fs, is, T_TURRET, 4);
        }
        for (int i = 0; i < 3; i++) {
            placeEnemy(maze, paths, fs, is, T_POOL, 2);
        }
        huntersLeft = count(is, T_HUNTER);
    }

    /** The top-left room, facing down whichever corridor leads out of it. */
    private static void placePlayer(byte[] maze) {
        posX = 1.5f;
        posZ = 1.5f;
        setAngle(maze[SIZE + 2] == OPEN ? 0f : (float) (Math.PI / 2));
    }

    private static void respawn(byte[] maze, int[] fs, int[] is) {
        placePlayer(maze);
        shield = MAX_SHIELD;
        held = B_NONE;
        for (int slot = 0; slot < ENTITIES; slot++) {
            int type = is[slot * I_STRIDE + I_TYPE];
            if (type == T_SHOT || type == T_ENEMY_SHOT || type == T_BLAST) {
                is[slot * I_STRIDE + I_TYPE] = T_NONE;
            }
        }
    }

    private static void loseLife(boolean timeUp) {
        lives = lives - 1;
        for (int r = 4; r < CENTER_Y - HEADER; r = r + 6) {
            TftTouchShield.drawCircle(CENTER_X, CENTER_Y, r, (r & 4) == 0 ? TftTouchShield.RED : TftTouchShield.ORANGE);
            Delay.millis(15);
        }
        clearView();
        showCentered(timeUp ? "TIME UP" : "TANK DESTROYED", 90, 2, TftTouchShield.RED);
        drawHeader();
        Delay.millis(1500);
    }

    private static void addScore(int points) {
        score = score + points;
        if (score >= nextBonus) {
            lives = lives + 1;
            nextBonus = nextBonus + BONUS_EVERY;
        }
    }

    private static void damage(int amount) {
        shield = Math.max(0, shield - amount);
        TftTouchShield.fillRect(0, 0, WIDTH, HEADER - 1, TftTouchShield.RED);
        Delay.millis(40);
        drawHeader();
    }

    // ---- Maze ----

    /**
     * A perfect maze by randomized depth-first search over the rooms, then a few walls knocked out
     * so there are loops to circle around enemies.
     */
    private static void generateMaze(byte[] maze, short[] stack) {
        for (int i = 0; i < CELLS; i++) {
            maze[i] = WALL;
        }
        int rooms = (SIZE - 1) / 2;
        int top = 0;
        stack[0] = (short) (1 * SIZE + 1);
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
        // Loops: open walls that separate two rooms in a straight line.
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

    private static int dx(int d) {
        return d == 0 ? 1 : (d == 2 ? -1 : 0);
    }

    private static int dz(int d) {
        return d == 1 ? 1 : (d == 3 ? -1 : 0);
    }

    private static boolean isWall(byte[] maze, float x, float z) {
        if (x < 0f || z < 0f || x >= SIZE || z >= SIZE) {
            return true;
        }
        return maze[(int) z * SIZE + (int) x] != OPEN;
    }

    private static int cellOf(float x, float z) {
        return (int) z * SIZE + (int) x;
    }

    /** Breadth-first path lengths from {@code start} to every open cell ({@code -1} if unreachable). */
    private static void bfs(byte[] maze, short[] paths, int start) {
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

    /** Puts an entity in a random room at least {@code distance} steps from the player, alone there. */
    private static void placeEnemy(byte[] maze, short[] paths, int[] fs, int[] is, int type, int distance) {
        int slot = freeSlot(is);
        if (slot < 0) {
            return;
        }
        for (int tries = 0; tries < 300; tries++) {
            int x = 1 + 2 * Random.nextInt((SIZE - 1) / 2);
            int z = 1 + 2 * Random.nextInt((SIZE - 1) / 2);
            int cell = z * SIZE + x;
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

    // ---- Input ----

    /** The buttons and the view drive the tank; tapping the header hands it to the CPU or back. */
    private static void handleTouch(byte[] maze, int[] fs, int[] is) {
        if (fireCooldown > 0) {
            fireCooldown = fireCooldown - 1;
        }
        held = B_NONE;
        if (TftTouchShield.readTouch()) {
            int x = TftTouchShield.touchX();
            int y = TftTouchShield.touchY();
            if (y < HEADER) {
                if (!headerPressed) {
                    autopilot = !autopilot;
                    drawHeader();
                }
                headerPressed = true;
                return;
            }
            headerPressed = false;
            if (autopilot) {
                return;
            }
            held = y >= BAR_TOP ? buttonAt(x) : B_FIRE;
        } else {
            headerPressed = false;
        }
        if (held == B_LEFT) {
            setAngle(angle - TURN);
        } else if (held == B_RIGHT) {
            setAngle(angle + TURN);
        } else if (held == B_FORWARD) {
            drive(maze, SPEED);
        } else if (held == B_BACK) {
            drive(maze, -SPEED * 0.7f);
        } else if (held == B_FIRE) {
            fire(fs, is);
        }
    }

    private static int buttonAt(int x) {
        if (x < 50) {
            return B_LEFT;
        }
        if (x < 100) {
            return B_FORWARD;
        }
        if (x < 176) {
            return B_FIRE;
        }
        if (x < 222) {
            return B_NONE;
        }
        if (x < 271) {
            return B_BACK;
        }
        return B_RIGHT;
    }

    private static void setAngle(float value) {
        angle = value;
        dirX = (float) Math.cos(value);
        dirZ = (float) Math.sin(value);
        planeX = -dirZ * PLANE;
        planeZ = dirX * PLANE;
        inverse = 1f / (planeX * dirZ - dirX * planeZ);
    }

    /** Moves along the facing direction, sliding along walls. */
    private static void drive(byte[] maze, float speed) {
        float nx = posX + dirX * speed;
        if (isFree(maze, nx, posZ)) {
            posX = nx;
        }
        float nz = posZ + dirZ * speed;
        if (isFree(maze, posX, nz)) {
            posZ = nz;
        }
    }

    private static boolean isFree(byte[] maze, float x, float z) {
        return !isWall(maze, x - RADIUS, z - RADIUS) && !isWall(maze, x + RADIUS, z - RADIUS)
                && !isWall(maze, x - RADIUS, z + RADIUS) && !isWall(maze, x + RADIUS, z + RADIUS);
    }

    private static void fire(int[] fs, int[] is) {
        if (fireCooldown > 0 || count(is, T_SHOT) >= 3) {
            return;
        }
        int slot = freeSlot(is);
        if (slot < 0) {
            return;
        }
        fireCooldown = FIRE_FRAMES;
        int f = slot * F_STRIDE;
        is[slot * I_STRIDE + I_TYPE] = T_SHOT;
        is[slot * I_STRIDE + I_TIMER] = 40;
        fs[f + F_X] = (int) ((posX + dirX * 0.2f) * ONE);
        fs[f + F_Z] = (int) ((posZ + dirZ * 0.2f) * ONE);
        fs[f + F_VX] = (int) (dirX * SHOT_SPEED * ONE);
        fs[f + F_VZ] = (int) (dirZ * SHOT_SPEED * ONE);
    }

    /** The pilot screen: waits for a tap on HUMAN or CPU. */
    private static void choosePilot() {
        TftTouchShield.fillScreen(SPACE);
        showCentered("CHOOSE PILOT", 36, 3, TftTouchShield.ORANGE);
        showCentered("Who drives the tank?", 74, 1, TftTouchShield.WHITE);
        drawChoice(0, "HUMAN", "You drive and fire", false);
        drawChoice(1, "CPU", "Autopilot plays", false);
        showCentered("Tap the header in game to switch", 206, 1, WALL_SEAM);
        int choice = -1;
        while (choice < 0) {
            if (TftTouchShield.readTouch()) {
                choice = choiceAt(TftTouchShield.touchX(), TftTouchShield.touchY());
            }
            Delay.millis(10);
        }
        autopilot = choice == 1;
        drawChoice(choice, choice == 0 ? "HUMAN" : "CPU", choice == 0 ? "You drive and fire" : "Autopilot plays",
                true);
        waitForRelease();
    }

    /** The pilot button at (x, y): 0 for HUMAN, 1 for CPU, or -1. */
    private static int choiceAt(int x, int y) {
        if (y < CHOICE_Y || y >= CHOICE_Y + CHOICE_HEIGHT) {
            return -1;
        }
        for (int index = 0; index < 2; index++) {
            int left = CHOICE_X + index * (CHOICE_WIDTH + CHOICE_GAP);
            if (x >= left && x < left + CHOICE_WIDTH) {
                return index;
            }
        }
        return -1;
    }

    private static void drawChoice(int index, String label, String hint, boolean chosen) {
        int x = CHOICE_X + index * (CHOICE_WIDTH + CHOICE_GAP);
        int color = chosen ? BUTTON : 0x2124;
        TftTouchShield.fillRect(x, CHOICE_Y, CHOICE_WIDTH, CHOICE_HEIGHT, color);
        TftTouchShield.drawRect(x, CHOICE_Y, CHOICE_WIDTH, CHOICE_HEIGHT, chosen ? TftTouchShield.WHITE : WALL_TOP);
        TftTouchShield.setTextSize(3);
        TftTouchShield.setTextColor(TftTouchShield.ORANGE, color);
        TftTouchShield.setCursor(x + (CHOICE_WIDTH - label.length() * 18) / 2, CHOICE_Y + 16);
        TftTouchShield.print(label);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, color);
        TftTouchShield.setCursor(x + (CHOICE_WIDTH - hint.length() * 6) / 2, CHOICE_Y + 52);
        TftTouchShield.print(hint);
    }

    private static void waitForTap() {
        while (!TftTouchShield.readTouch()) {
            Delay.millis(10);
        }
        waitForRelease();
    }

    private static void waitForRelease() {
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

    // ---- The CPU driver ----

    /**
     * The CPU at the controls: it turns to face the nearest enemy it can see within
     * {@value #CPU_SIGHT} cells and fires once it is lined up, its aim off by up to
     * {@value #CPU_WOBBLE} hundredths of a radian (a new error every {@value #CPU_WOBBLE_FRAMES}
     * frames), so some shots miss. With nothing in sight it drives along the shortest path towards
     * the next hunter (then tanks, then turrets), or to an energy pool when its shield is below
     * {@value #CPU_LOW_SHIELD}.
     */
    private static void flyAutopilot(byte[] maze, short[] route, int[] fs, int[] is) {
        if (frame % CPU_WOBBLE_FRAMES == 0) {
            aimWobble = Random.nextInt(-CPU_WOBBLE, CPU_WOBBLE + 1) / 100f;
        }
        int aim = -1;
        float nearest = CPU_SIGHT;
        for (int slot = 0; slot < ENTITIES; slot++) {
            int type = is[slot * I_STRIDE + I_TYPE];
            if (type != T_HUNTER && type != T_TANK && type != T_TURRET) {
                continue;
            }
            float ex = fs[slot * F_STRIDE + F_X] * TO_CELLS;
            float ez = fs[slot * F_STRIDE + F_Z] * TO_CELLS;
            float distance = (float) Math.sqrt((ex - posX) * (ex - posX) + (ez - posZ) * (ez - posZ));
            if (distance < nearest && lineOfSight(maze, posX, posZ, ex, ez)) {
                nearest = distance;
                aim = slot;
            }
        }
        if (aim >= 0) {
            float ex = fs[aim * F_STRIDE + F_X] * TO_CELLS;
            float ez = fs[aim * F_STRIDE + F_Z] * TO_CELLS;
            float turn = wrapAngle((float) Math.atan2(ez - posZ, ex - posX) + aimWobble - angle);
            if (Math.abs(turn) > 0.05f) {
                turnBy(turn);
            } else {
                fire(fs, is);
            }
            return;
        }
        int target = -1;
        if (shield < CPU_LOW_SHIELD) {
            target = firstOf(is, T_POOL);
        }
        for (int type = T_HUNTER; type <= T_TURRET && target < 0; type++) {
            target = firstOf(is, type);
        }
        if (target < 0) {
            return;
        }
        bfs(maze, route, entityCell(fs, target));
        int here = cellOf(posX, posZ);
        int next = here;
        for (int d = 0; d < 4; d++) {
            int cell = here + dx(d) + dz(d) * SIZE;
            if (maze[cell] == OPEN && route[cell] >= 0 && route[cell] < route[next]) {
                next = cell;
            }
        }
        float tx = next % SIZE + 0.5f;
        float tz = next / SIZE + 0.5f;
        if (next == here) {
            // In the target's own cell: drive right up to it (a pool is only picked up close by).
            tx = fs[target * F_STRIDE + F_X] * TO_CELLS;
            tz = fs[target * F_STRIDE + F_Z] * TO_CELLS;
            if (Math.abs(tx - posX) + Math.abs(tz - posZ) < 0.1f) {
                return;
            }
        }
        float cx = here % SIZE + 0.5f;
        float cz = here / SIZE + 0.5f;
        // Round a corner from the middle of the cell, or the tank scrapes along the wall.
        boolean corner = Math.abs(tx - posX) > 0.2f && Math.abs(tz - posZ) > 0.2f;
        if (corner && Math.abs(cx - posX) + Math.abs(cz - posZ) > 0.1f) {
            tx = cx;
            tz = cz;
        }
        float turn = wrapAngle((float) Math.atan2(tz - posZ, tx - posX) - angle);
        if (Math.abs(turn) > 0.12f) {
            turnBy(turn);
        } else {
            drive(maze, SPEED);
        }
    }

    /** Turns towards {@code turn} radians away, by at most one frame's turn. */
    private static void turnBy(float turn) {
        setAngle(angle + (turn > 0 ? Math.min(TURN, turn) : -Math.min(TURN, -turn)));
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

    private static int firstOf(int[] is, int type) {
        for (int slot = 0; slot < ENTITIES; slot++) {
            if (is[slot * I_STRIDE + I_TYPE] == type) {
                return slot;
            }
        }
        return -1;
    }

    // ---- Simulation ----

    private static void step(byte[] maze, short[] paths, int[] fs, int[] is) {
        if (frame % 8 == 0) {
            bfs(maze, paths, cellOf(posX, posZ));
        }
        for (int slot = 0; slot < ENTITIES; slot++) {
            int type = is[slot * I_STRIDE + I_TYPE];
            if (type == T_HUNTER || type == T_TANK) {
                patrol(maze, paths, fs, is, slot);
                enemyFires(maze, fs, is, slot);
            } else if (type == T_TURRET) {
                enemyFires(maze, fs, is, slot);
            } else if (type == T_POOL) {
                if (nearPlayer(fs, slot, 0.4f)) {
                    is[slot * I_STRIDE + I_TYPE] = T_NONE;
                    shield = Math.min(MAX_SHIELD, shield + 35);
                    addScore(POOL_POINTS);
                    drawHeader();
                }
            } else if (type == T_SHOT || type == T_ENEMY_SHOT) {
                moveShot(maze, fs, is, slot);
            } else if (type == T_BLAST) {
                is[slot * I_STRIDE + I_TIMER] = is[slot * I_STRIDE + I_TIMER] + 1;
                if (is[slot * I_STRIDE + I_TIMER] > 9) {
                    is[slot * I_STRIDE + I_TYPE] = T_NONE;
                }
            }
        }
    }

    /**
     * Enemies move from room center to room center. Within reach of your trail they close in along
     * the shortest path, but hold off two steps away; otherwise they wander, never turning back
     * unless cornered.
     */
    private static void patrol(byte[] maze, short[] paths, int[] fs, int[] is, int slot) {
        int f = slot * F_STRIDE;
        int b = slot * I_STRIDE;
        int speed = is[b + I_TYPE] == T_HUNTER ? 46 + 4 * Math.min(level, 8) : 31;
        int ddx = fs[f + F_TX] - fs[f + F_X];
        int ddz = fs[f + F_TZ] - fs[f + F_Z];
        if (Math.abs(ddx) + Math.abs(ddz) > speed) {
            fs[f + F_X] = fs[f + F_X] + sign(ddx) * Math.min(speed, Math.abs(ddx));
            fs[f + F_Z] = fs[f + F_Z] + sign(ddz) * Math.min(speed, Math.abs(ddz));
            return;
        }
        fs[f + F_X] = fs[f + F_TX];
        fs[f + F_Z] = fs[f + F_TZ];
        int cell = entityCell(fs, slot);
        int here = paths[cell];
        int came = is[b + I_AUX];
        int choice = -1;
        if (here > 2 && here <= 9) {
            for (int d = 0; d < 4; d++) {
                int next = cell + dx(d) + dz(d) * SIZE;
                if (maze[next] == OPEN && paths[next] >= 0 && paths[next] < here) {
                    choice = next;
                }
            }
        }
        if (choice < 0) {
            int options = 0;
            for (int d = 0; d < 4; d++) {
                int next = cell + dx(d) + dz(d) * SIZE;
                if (maze[next] == OPEN && next != came && (here < 0 || paths[next] >= 2 || here > 9)) {
                    options = options + 1;
                }
            }
            if (options > 0) {
                int pick = Random.nextInt(options);
                for (int d = 0; d < 4 && choice < 0; d++) {
                    int next = cell + dx(d) + dz(d) * SIZE;
                    if (maze[next] == OPEN && next != came && (here < 0 || paths[next] >= 2 || here > 9)) {
                        if (pick == 0) {
                            choice = next;
                        }
                        pick = pick - 1;
                    }
                }
            } else {
                choice = came;
            }
        }
        is[b + I_AUX] = cell;
        if (choice >= 0 && maze[choice] == OPEN) {
            fs[f + F_TX] = (choice % SIZE) * ONE + ONE / 2;
            fs[f + F_TZ] = (choice / SIZE) * ONE + ONE / 2;
        }
    }

    /** Fires at you when in range with a clear line of sight. */
    private static void enemyFires(byte[] maze, int[] fs, int[] is, int slot) {
        int b = slot * I_STRIDE;
        is[b + I_COOLDOWN] = is[b + I_COOLDOWN] - 1;
        if (is[b + I_COOLDOWN] > 0) {
            return;
        }
        int f = slot * F_STRIDE;
        float x = fs[f + F_X] * TO_CELLS;
        float z = fs[f + F_Z] * TO_CELLS;
        float ddx = posX - x;
        float ddz = posZ - z;
        float distance = (float) Math.sqrt(ddx * ddx + ddz * ddz);
        if (distance > 6f || distance < 0.3f || !lineOfSight(maze, x, z, posX, posZ)) {
            is[b + I_COOLDOWN] = 10;
            return;
        }
        int type = is[b + I_TYPE];
        int pause = type == T_HUNTER ? 70 : (type == T_TANK ? 90 : 60);
        is[b + I_COOLDOWN] = Math.max(25, pause - 4 * level + Random.nextInt(30));
        int shot = freeSlot(is);
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

    private static boolean lineOfSight(byte[] maze, float x0, float z0, float x1, float z1) {
        float ddx = x1 - x0;
        float ddz = z1 - z0;
        int steps = (int) ((Math.abs(ddx) + Math.abs(ddz)) * 4f) + 1;
        for (int i = 1; i < steps; i++) {
            float t = (float) i / steps;
            if (isWall(maze, x0 + ddx * t, z0 + ddz * t)) {
                return false;
            }
        }
        return true;
    }

    private static void moveShot(byte[] maze, int[] fs, int[] is, int slot) {
        int f = slot * F_STRIDE;
        int b = slot * I_STRIDE;
        is[b + I_TIMER] = is[b + I_TIMER] - 1;
        // Two half steps, so a fast shot cannot skip through a corner or a target.
        for (int half = 0; half < 2; half++) {
            fs[f + F_X] = fs[f + F_X] + fs[f + F_VX] / 2;
            fs[f + F_Z] = fs[f + F_Z] + fs[f + F_VZ] / 2;
            int x = fs[f + F_X];
            int z = fs[f + F_Z];
            if (is[b + I_TIMER] <= 0 || isWall(maze, x * TO_CELLS, z * TO_CELLS)) {
                is[b + I_TYPE] = T_NONE;
                return;
            }
            if (is[b + I_TYPE] == T_ENEMY_SHOT) {
                if (nearPlayer(fs, slot, RADIUS + 0.08f)) {
                    is[b + I_TYPE] = T_NONE;
                    damage(is[b + I_AUX]);
                    return;
                }
            } else {
                for (int target = 0; target < ENTITIES; target++) {
                    int type = is[target * I_STRIDE + I_TYPE];
                    if ((type == T_HUNTER || type == T_TANK || type == T_TURRET)
                            && near(fs, target, x, z, (int) (HIT_RANGE * ONE))) {
                        destroy(fs, is, target);
                        is[b + I_TYPE] = T_NONE;
                        return;
                    }
                }
            }
        }
    }

    private static void destroy(int[] fs, int[] is, int slot) {
        int type = is[slot * I_STRIDE + I_TYPE];
        if (type == T_HUNTER) {
            huntersLeft = huntersLeft - 1;
            addScore(HUNTER_POINTS);
        } else if (type == T_TANK) {
            addScore(TANK_POINTS);
        } else {
            addScore(TURRET_POINTS);
        }
        is[slot * I_STRIDE + I_TYPE] = T_BLAST;
        is[slot * I_STRIDE + I_TIMER] = 0;
        is[slot * I_STRIDE + I_AUX] = type;
        drawHeader();
    }

    /** Whether entity {@code slot} is within {@code range} of (x, z), all in fixed point. */
    private static boolean near(int[] fs, int slot, int x, int z, int range) {
        int ddx = fs[slot * F_STRIDE + F_X] - x;
        int ddz = fs[slot * F_STRIDE + F_Z] - z;
        return ddx * ddx + ddz * ddz <= range * range;
    }

    private static boolean nearPlayer(int[] fs, int slot, float range) {
        return near(fs, slot, (int) (posX * ONE), (int) (posZ * ONE), (int) (range * ONE));
    }

    private static int entityCell(int[] fs, int slot) {
        return (fs[slot * F_STRIDE + F_Z] / ONE) * SIZE + fs[slot * F_STRIDE + F_X] / ONE;
    }

    // ---- Ray casting ----

    /** Casts the ray through screen column {@code column}; sets {@link #rayFace} and {@link #rayDistance}. */
    private static void castRay(byte[] maze, int column) {
        float camera = 2f * column / (WIDTH - 1) - 1f;
        float rayX = dirX + planeX * camera;
        float rayZ = dirZ + planeZ * camera;
        int mapX = (int) posX;
        int mapZ = (int) posZ;
        float deltaX = rayX == 0f ? 1e30f : Math.abs(1f / rayX);
        float deltaZ = rayZ == 0f ? 1e30f : Math.abs(1f / rayZ);
        int stepX;
        int stepZ;
        float sideX;
        float sideZ;
        if (rayX < 0f) {
            stepX = -1;
            sideX = (posX - mapX) * deltaX;
        } else {
            stepX = 1;
            sideX = (mapX + 1f - posX) * deltaX;
        }
        if (rayZ < 0f) {
            stepZ = -1;
            sideZ = (posZ - mapZ) * deltaZ;
        } else {
            stepZ = 1;
            sideZ = (mapZ + 1f - posZ) * deltaZ;
        }
        int side = 0;
        for (int guard = 0; guard < 4 * SIZE; guard++) {
            if (sideX < sideZ) {
                sideX = sideX + deltaX;
                mapX = mapX + stepX;
                side = 0;
            } else {
                sideZ = sideZ + deltaZ;
                mapZ = mapZ + stepZ;
                side = 1;
            }
            if (mapX < 0 || mapZ < 0 || mapX >= SIZE || mapZ >= SIZE || maze[mapZ * SIZE + mapX] != OPEN) {
                break;
            }
        }
        // Faces on the same line, facing the same way, share an id, so a long wall is one face.
        if (side == 0) {
            rayDistance = sideX - deltaX;
            rayAlong = posZ + rayDistance * rayZ;
            rayFace = ((stepX > 0 ? mapX : mapX + 1) * 2 + (stepX > 0 ? 1 : 0)) * 2;
        } else {
            rayDistance = sideZ - deltaZ;
            rayAlong = posX + rayDistance * rayX;
            rayFace = ((stepZ > 0 ? mapZ : mapZ + 1) * 2 + (stepZ > 0 ? 1 : 0)) * 2 + 1;
        }
        rayDistance = Math.max(rayDistance, 0.05f);
    }

    /** Draws the visible walls, with the boundaries between faces found to the pixel. */
    private static void drawWalls(byte[] maze, short[] lines, int[] depth, int[] faces) {
        for (int i = 0; i < SAMPLES; i++) {
            castRay(maze, sampleColumn(i));
            depth[i] = (int) (rayDistance * ONE);
            faces[i] = rayFace;
        }
        int face = faces[0];
        int start = 0;
        float startDistance = depth[0] * TO_CELLS;
        int last = 0;
        float lastDistance = startDistance;
        for (int i = 1; i < SAMPLES; i++) {
            int column = sampleColumn(i);
            while (faces[i] != face) {
                int lo = last;
                float loDistance = lastDistance;
                int hi = column;
                float hiDistance = depth[i] * TO_CELLS;
                int hiFace = faces[i];
                while (hi - lo > 1) {
                    int mid = (lo + hi) / 2;
                    castRay(maze, mid);
                    if (rayFace == face) {
                        lo = mid;
                        loDistance = rayDistance;
                    } else {
                        hi = mid;
                        hiDistance = rayDistance;
                        hiFace = rayFace;
                    }
                }
                wallRun(maze, lines, face, start, startDistance, lo, loDistance);
                if (loDistance <= hiDistance) {
                    addLine(lines, lo, wallTop(loDistance), lo, wallBottom(loDistance), WALL_TOP);
                } else {
                    addLine(lines, hi, wallTop(hiDistance), hi, wallBottom(hiDistance), WALL_TOP);
                }
                face = hiFace;
                start = hi;
                startDistance = hiDistance;
                last = hi;
                lastDistance = hiDistance;
            }
            last = column;
            lastDistance = depth[i] * TO_CELLS;
        }
        wallRun(maze, lines, face, start, startDistance, last, lastDistance);
    }

    private static int sampleColumn(int i) {
        return Math.min(i * SAMPLE, WIDTH - 1);
    }

    /**
     * The visible stretch of one face from column c0 to c1: its top and bottom edges, and a seam
     * where each cell boundary crosses it, projected exactly.
     */
    private static void wallRun(byte[] maze, short[] lines, int face, int c0, float d0, int c1, float d1) {
        addLine(lines, c0, wallTop(d0), c1, wallTop(d1), WALL_TOP);
        addLine(lines, c0, wallBottom(d0), c1, wallBottom(d1), WALL_BOTTOM);
        castRay(maze, c0);
        float a0 = rayAlong;
        castRay(maze, c1);
        float a1 = rayAlong;
        float plane = (face >> 2);
        int from = (int) Math.ceil(Math.min(a0, a1) + 0.02f);
        int to = (int) Math.floor(Math.max(a0, a1) - 0.02f);
        for (int k = from; k <= to; k++) {
            float sx = (face & 1) == 0 ? plane - posX : k - posX;
            float sz = (face & 1) == 0 ? k - posZ : plane - posZ;
            float distance = inverse * (-planeZ * sx + planeX * sz);
            if (distance < NEAR) {
                continue;
            }
            int column = screenX(inverse * (dirZ * sx - dirX * sz), distance);
            if (column > c0 && column < c1) {
                addLine(lines, column, wallTop(distance), column, wallBottom(distance), WALL_SEAM);
            }
        }
    }

    private static int wallTop(float distance) {
        return clamp(CENTER_Y - (int) (FOCAL * (WALL_HEIGHT - EYE) / distance), -20000, 20000);
    }

    private static int wallBottom(float distance) {
        return clamp(CENTER_Y + (int) (FOCAL * EYE / distance), -20000, 20000);
    }

    // ---- Scene ----

    private static void render(byte[] maze, short[] lines, int[] depth, int[] faces, int[] fs, int[] is) {
        built = 0;
        drawWalls(maze, lines, depth, faces);
        for (int slot = 0; slot < ENTITIES; slot++) {
            if (is[slot * I_STRIDE + I_TYPE] != T_NONE) {
                drawEntity(lines, depth, fs, is, slot);
            }
        }
        // Gun sight.
        addLine(lines, CENTER_X - 10, CENTER_Y, CENTER_X - 4, CENTER_Y, TftTouchShield.GREEN);
        addLine(lines, CENTER_X + 4, CENTER_Y, CENTER_X + 10, CENTER_Y, TftTouchShield.GREEN);
        addLine(lines, CENTER_X, CENTER_Y - 8, CENTER_X, CENTER_Y - 3, TftTouchShield.GREEN);
        present(lines);
    }

    /** Draws an entity unless a wall hides its center. */
    private static void drawEntity(short[] lines, int[] depth, int[] fs, int[] is, int slot) {
        int f = slot * F_STRIDE;
        int b = slot * I_STRIDE;
        float x = fs[f + F_X] * TO_CELLS;
        float z = fs[f + F_Z] * TO_CELLS;
        float sx = x - posX;
        float sz = z - posZ;
        float distance = inverse * (-planeZ * sx + planeX * sz);
        if (distance < NEAR) {
            return;
        }
        float lateral = inverse * (dirZ * sx - dirX * sz);
        int column = CENTER_X + (int) (CENTER_X * lateral / distance);
        if (column < -40 || column > WIDTH + 40) {
            return;
        }
        int sample = clamp((column + SAMPLE / 2) / SAMPLE, 0, SAMPLES - 1);
        if (distance > depth[sample] * TO_CELLS + 0.3f) {
            return;
        }
        int type = is[b + I_TYPE];
        float spin = frame * 0.12f + slot;
        if (type == T_HUNTER) {
            drawHunter(lines, x, z, 0.62f + 0.05f * (float) Math.sin(spin), spin);
        } else if (type == T_TANK) {
            drawTank(lines, x, z, fs[f + F_TX] * TO_CELLS - x, fs[f + F_TZ] * TO_CELLS - z);
        } else if (type == T_TURRET) {
            drawTurret(lines, x, z, spin * 0.5f);
        } else if (type == T_POOL) {
            float r = 0.18f + 0.04f * (float) Math.sin(spin * 2f);
            floorDiamond(lines, x, z, r, POOL);
            floorDiamond(lines, x, z, r * 0.5f, POOL);
        } else if (type == T_SHOT || type == T_ENEMY_SHOT) {
            int color = type == T_SHOT ? SHOT : ENEMY_SHOT;
            float y = type == T_SHOT ? 0.22f : 0.35f;
            float s = 0.05f;
            line3(lines, x - s, y, z, x + s, y, z, color);
            line3(lines, x, y - s, z, x, y + s, z, color);
            line3(lines, x, y, z - s, x, y, z + s, color);
        } else if (type == T_BLAST) {
            int age = is[b + I_TIMER];
            float r0 = 0.04f * age;
            float r1 = r0 + 0.12f;
            float y = is[b + I_AUX] == T_HUNTER ? 0.62f : 0.2f;
            for (int k = 0; k < 8; k++) {
                float c = (float) Math.cos(k * 0.785f);
                float s = (float) Math.sin(k * 0.785f);
                int color = (k & 1) == 0 ? TftTouchShield.YELLOW : TftTouchShield.ORANGE;
                line3(lines, x + c * r0, y + s * r0, z, x + c * r1, y + s * r1, z, color);
            }
        }
    }

    /** A hunter: a spinning wireframe octahedron hovering at eye level. */
    private static void drawHunter(short[] lines, float x, float z, float y, float spin) {
        float r = 0.2f;
        float c = (float) Math.cos(spin) * r;
        float s = (float) Math.sin(spin) * r;
        float ax = x + c;
        float az = z + s;
        float bx = x - s;
        float bz = z + c;
        float cx = x - c;
        float cz = z - s;
        float ex = x + s;
        float ez = z - c;
        float top = y + 0.24f;
        float bottom = y - 0.24f;
        line3(lines, ax, y, az, bx, y, bz, HUNTER);
        line3(lines, bx, y, bz, cx, y, cz, HUNTER);
        line3(lines, cx, y, cz, ex, y, ez, HUNTER);
        line3(lines, ex, y, ez, ax, y, az, HUNTER);
        line3(lines, x, top, z, ax, y, az, HUNTER);
        line3(lines, x, top, z, bx, y, bz, HUNTER);
        line3(lines, x, top, z, cx, y, cz, HUNTER);
        line3(lines, x, top, z, ex, y, ez, HUNTER);
        line3(lines, x, bottom, z, ax, y, az, TftTouchShield.YELLOW);
        line3(lines, x, bottom, z, cx, y, cz, TftTouchShield.YELLOW);
    }

    /** An enemy tank: a low wedge hull and a box turret with a barrel, pointing where it drives. */
    private static void drawTank(short[] lines, float x, float z, float hx, float hz) {
        float length = (float) Math.sqrt(hx * hx + hz * hz);
        float fx = 1f;
        float fz = 0f;
        if (length > 0.001f) {
            fx = hx / length;
            fz = hz / length;
        }
        float rx = -fz;
        float rz = fx;
        box(lines, x, z, fx, fz, rx, rz, 0.26f, 0.2f, 0f, 0.12f, TANK);
        box(lines, x, z, fx, fz, rx, rz, 0.12f, 0.1f, 0.12f, 0.22f, TANK);
        line3(lines, x + fx * 0.12f, 0.17f, z + fz * 0.12f, x + fx * 0.36f, 0.17f, z + fz * 0.36f, TANK);
    }

    /** A gun turret: a pyramid on a square pad, its tip slowly turning. */
    private static void drawTurret(short[] lines, float x, float z, float spin) {
        float c = (float) Math.cos(spin) * 0.2f;
        float s = (float) Math.sin(spin) * 0.2f;
        float tipY = 0.55f;
        line3(lines, x + c, 0f, z + s, x - s, 0f, z + c, TURRET);
        line3(lines, x - s, 0f, z + c, x - c, 0f, z - s, TURRET);
        line3(lines, x - c, 0f, z - s, x + s, 0f, z - c, TURRET);
        line3(lines, x + s, 0f, z - c, x + c, 0f, z + s, TURRET);
        line3(lines, x, tipY, z, x + c, 0f, z + s, TURRET);
        line3(lines, x, tipY, z, x - s, 0f, z + c, TURRET);
        line3(lines, x, tipY, z, x - c, 0f, z - s, TURRET);
        line3(lines, x, tipY, z, x + s, 0f, z - c, TURRET);
    }

    /** The eight edges of a box oriented along (fx, fz), half-sizes {@code l} by {@code w}. */
    private static void box(short[] lines, float x, float z, float fx, float fz, float rx, float rz, float l,
            float w, float y0, float y1, int color) {
        float ax = x + fx * l + rx * w;
        float az = z + fz * l + rz * w;
        float bx = x + fx * l - rx * w;
        float bz = z + fz * l - rz * w;
        float cx = x - fx * l - rx * w;
        float cz = z - fz * l - rz * w;
        float dx = x - fx * l + rx * w;
        float dz = z - fz * l + rz * w;
        for (int edge = 0; edge < 2; edge++) {
            float y = edge == 0 ? y0 : y1;
            line3(lines, ax, y, az, bx, y, bz, color);
            line3(lines, bx, y, bz, cx, y, cz, color);
            line3(lines, cx, y, cz, dx, y, dz, color);
            line3(lines, dx, y, dz, ax, y, az, color);
        }
    }

    private static void floorDiamond(short[] lines, float x, float z, float r, int color) {
        line3(lines, x + r, 0f, z, x, 0f, z + r, color);
        line3(lines, x, 0f, z + r, x - r, 0f, z, color);
        line3(lines, x - r, 0f, z, x, 0f, z - r, color);
        line3(lines, x, 0f, z - r, x + r, 0f, z, color);
    }

    /** A world line (x, height y, z), transformed to the camera, clipped at the near plane and projected. */
    private static void line3(short[] lines, float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        float ax = x0 - posX;
        float az = z0 - posZ;
        float bx = x1 - posX;
        float bz = z1 - posZ;
        float lat0 = inverse * (dirZ * ax - dirX * az);
        float dep0 = inverse * (-planeZ * ax + planeX * az);
        float lat1 = inverse * (dirZ * bx - dirX * bz);
        float dep1 = inverse * (-planeZ * bx + planeX * bz);
        if (dep0 < NEAR && dep1 < NEAR) {
            return;
        }
        if (dep0 < NEAR) {
            float t = (NEAR - dep0) / (dep1 - dep0);
            lat0 = lat0 + (lat1 - lat0) * t;
            y0 = y0 + (y1 - y0) * t;
            dep0 = NEAR;
        } else if (dep1 < NEAR) {
            float t = (NEAR - dep1) / (dep0 - dep1);
            lat1 = lat1 + (lat0 - lat1) * t;
            y1 = y1 + (y0 - y1) * t;
            dep1 = NEAR;
        }
        addLine(lines, screenX(lat0, dep0), screenY(y0, dep0), screenX(lat1, dep1), screenY(y1, dep1), color);
    }

    private static int screenX(float lateral, float distance) {
        return clamp(CENTER_X + (int) (CENTER_X * lateral / distance), -20000, 20000);
    }

    private static int screenY(float y, float distance) {
        return clamp(CENTER_Y - (int) (FOCAL * (y - EYE) / distance), -20000, 20000);
    }

    // ---- Display lists ----

    /** Clips a screen line to the view (Cohen-Sutherland) and appends it to the list being built. */
    private static void addLine(short[] lines, int x0, int y0, int x1, int y1, int color) {
        int code0 = outCode(x0, y0);
        int code1 = outCode(x1, y1);
        int rounds = 0;
        while ((code0 | code1) != 0) {
            if ((code0 & code1) != 0 || rounds == 4) {
                return;
            }
            rounds = rounds + 1;
            int code = code0 != 0 ? code0 : code1;
            int x;
            int y;
            if ((code & 8) != 0) {
                x = x0 + (x1 - x0) * (HEADER - y0) / (y1 - y0);
                y = HEADER;
            } else if ((code & 4) != 0) {
                x = x0 + (x1 - x0) * (VIEW_BOTTOM - y0) / (y1 - y0);
                y = VIEW_BOTTOM;
            } else if ((code & 2) != 0) {
                y = y0 + (y1 - y0) * (WIDTH - 1 - x0) / (x1 - x0);
                x = WIDTH - 1;
            } else {
                y = y0 + (y1 - y0) * (0 - x0) / (x1 - x0);
                x = 0;
            }
            if (code == code0) {
                x0 = x;
                y0 = y;
                code0 = outCode(x0, y0);
            } else {
                x1 = x;
                y1 = y;
                code1 = outCode(x1, y1);
            }
        }
        if (built == MAX_LINES) {
            return;
        }
        int at = (1 - front) * LIST_SIZE + built * L_STRIDE;
        lines[at] = (short) x0;
        lines[at + 1] = (short) y0;
        lines[at + 2] = (short) x1;
        lines[at + 3] = (short) y1;
        lines[at + 4] = (short) color;
        built = built + 1;
    }

    private static int outCode(int x, int y) {
        int code = 0;
        if (x < 0) {
            code = code | 1;
        } else if (x > WIDTH - 1) {
            code = code | 2;
        }
        if (y < HEADER) {
            code = code | 8;
        } else if (y > VIEW_BOTTOM) {
            code = code | 4;
        }
        return code;
    }

    /**
     * Shows the list just built: erases the previous frame's lines that changed, then draws the new
     * ones plus any unchanged line an erased one may have crossed.
     */
    private static void present(short[] lines) {
        int oldBase = front * LIST_SIZE;
        int newBase = (1 - front) * LIST_SIZE;
        for (int i = 0; i < shown; i++) {
            if (changed(lines, i)) {
                drawListed(lines, oldBase + i * L_STRIDE, SPACE);
            }
        }
        for (int j = 0; j < built; j++) {
            int at = newBase + j * L_STRIDE;
            boolean draw = changed(lines, j);
            for (int i = 0; i < shown && !draw; i++) {
                if (changed(lines, i) && overlaps(lines, oldBase + i * L_STRIDE, at)) {
                    draw = true;
                }
            }
            if (draw) {
                drawListed(lines, at, lines[at + 4] & 0xFFFF);
            }
        }
        front = 1 - front;
        shown = built;
    }

    private static boolean changed(short[] lines, int i) {
        if (i >= shown || i >= built) {
            return true;
        }
        int a = front * LIST_SIZE + i * L_STRIDE;
        int b = (1 - front) * LIST_SIZE + i * L_STRIDE;
        for (int k = 0; k < L_STRIDE; k++) {
            if (lines[a + k] != lines[b + k]) {
                return true;
            }
        }
        return false;
    }

    private static boolean overlaps(short[] lines, int a, int b) {
        return Math.min(lines[a], lines[a + 2]) <= Math.max(lines[b], lines[b + 2])
                && Math.min(lines[b], lines[b + 2]) <= Math.max(lines[a], lines[a + 2])
                && Math.min(lines[a + 1], lines[a + 3]) <= Math.max(lines[b + 1], lines[b + 3])
                && Math.min(lines[b + 1], lines[b + 3]) <= Math.max(lines[a + 1], lines[a + 3]);
    }

    private static void drawListed(short[] lines, int at, int color) {
        drawLine(lines[at], lines[at + 1], lines[at + 2], lines[at + 3], color);
    }

    /** Blanks the view and forgets what the display list had drawn there. */
    private static void clearView() {
        TftTouchShield.fillRect(0, HEADER, WIDTH, BAR_TOP - HEADER, SPACE);
        shown = 0;
    }

    /** Bresenham line; runs of pixels on the same row become one fill. */
    private static void drawLine(int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int stepX = x0 > x1 ? -1 : 1;
        int stepY = y0 > y1 ? -1 : 1;
        int error = dx + dy;
        int x = x0;
        int y = y0;
        int runStart = x0;
        while (x != x1 || y != y1) {
            int twice = 2 * error;
            int nextX = x;
            int nextY = y;
            if (twice >= dy) {
                error = error + dy;
                nextX = x + stepX;
            }
            if (twice <= dx) {
                error = error + dx;
                nextY = y + stepY;
            }
            if (nextY != y) {
                fillRun(runStart, x, y, color);
                runStart = nextX;
            }
            x = nextX;
            y = nextY;
        }
        fillRun(runStart, x, y, color);
    }

    private static void fillRun(int from, int to, int y, int color) {
        int left = Math.min(from, to);
        int right = Math.max(from, to);
        TftTouchShield.fillRect(left, y, right - left + 1, 1, color);
    }

    // ---- Button bar and radar ----

    private static void drawBar(byte[] maze, byte[] radar) {
        TftTouchShield.fillRect(0, BAR_TOP, WIDTH, HEIGHT - BAR_TOP, SPACE);
        button(0, 50, "<");
        button(50, 50, "^");
        button(100, 76, "FIRE");
        button(222, 49, "v");
        button(271, 49, ">");
        for (int cell = 0; cell < CELLS; cell++) {
            radar[cell] = 0;
            if (maze[cell] != OPEN) {
                radarCell(cell, RADAR_WALL);
            }
        }
    }

    private static void button(int x, int w, String label) {
        TftTouchShield.drawRect(x + 2, BAR_TOP + 3, w - 4, HEIGHT - BAR_TOP - 5, BUTTON);
        showAt(label, x + (w - label.length() * 12) / 2, BAR_TOP + 13, 2, TftTouchShield.WHITE);
    }

    private static void radarCell(int cell, int color) {
        TftTouchShield.fillRect(RADAR_X + (cell % SIZE) * RADAR_CELL, RADAR_Y + (cell / SIZE) * RADAR_CELL,
                RADAR_CELL, RADAR_CELL, color);
    }

    /** Clears last time's blips, then marks every entity's cell and yours. */
    private static void updateRadar(byte[] radar, int[] fs, int[] is) {
        for (int cell = 0; cell < CELLS; cell++) {
            if (radar[cell] != 0) {
                radar[cell] = 0;
                radarCell(cell, SPACE);
            }
        }
        for (int slot = 0; slot < ENTITIES; slot++) {
            int type = is[slot * I_STRIDE + I_TYPE];
            int color = -1;
            if (type == T_HUNTER) {
                color = HUNTER;
            } else if (type == T_TANK) {
                color = TANK;
            } else if (type == T_TURRET) {
                color = TURRET;
            } else if (type == T_POOL) {
                color = POOL;
            }
            if (color >= 0) {
                int cell = entityCell(fs, slot);
                radar[cell] = 1;
                radarCell(cell, color);
            }
        }
        int me = cellOf(posX, posZ);
        radar[me] = 1;
        radarCell(me, TftTouchShield.WHITE);
    }

    // ---- Opening and title ----

    /**
     * The opening, after the film: a laser scan line sweeps down the screen, digitizing the grid
     * behind it, then the grid rushes past as a hunter spins in from the horizon.
     */
    private static void opening(short[] lines) {
        TftTouchShield.fillScreen(SPACE);
        shown = 0;
        showCentered("DIGITIZING...", 212, 1, WALL_TOP);
        for (int f = 0; f <= SCAN_FRAMES; f++) {
            int scanY = HEADER + (VIEW_BOTTOM - HEADER) * f / SCAN_FRAMES;
            built = 0;
            drawGrid(lines, 0, scanY);
            addLine(lines, 0, scanY, WIDTH - 1, scanY, TftTouchShield.RED);
            present(lines);
            Delay.millis(30);
        }
        TftTouchShield.fillRect(0, BAR_TOP, WIDTH, HEIGHT - BAR_TOP, SPACE);
        for (int f = 0; f < GRID_FRAMES; f++) {
            built = 0;
            drawGrid(lines, f * 6, VIEW_BOTTOM);
            // A hunter spins in from the horizon.
            int size = 4 + f / 3;
            int cx = CENTER_X + Math.round((float) Math.sin(f * 0.09f) * 40);
            int cy = GRID_HORIZON - 12 - f / 2;
            float spin = f * 0.25f;
            int previousX = 0;
            int previousY = 0;
            for (int k = 0; k <= 4; k++) {
                float a = spin + k * 1.5708f;
                int x = cx + Math.round((float) Math.cos(a) * size);
                int y = cy + Math.round((float) Math.sin(a) * size * 0.3f);
                addLine(lines, cx, cy - size * 6 / 5, x, y, HUNTER);
                if (k > 0) {
                    addLine(lines, previousX, previousY, x, y, HUNTER);
                }
                if ((k & 1) == 0) {
                    addLine(lines, cx, cy + size * 6 / 5, x, y, TftTouchShield.YELLOW);
                }
                previousX = x;
                previousY = y;
            }
            present(lines);
            Delay.millis(30);
        }
        clearView();
        Delay.millis(200);
    }

    /**
     * The digital grid in perspective: lines across it, {@code travel} sixteenths of a square nearer
     * each step so it rushes past, and lines along it meeting at the horizon; nothing below
     * {@code limitY}.
     */
    private static void drawGrid(short[] lines, int travel, int limitY) {
        if (limitY <= GRID_HORIZON) {
            return;
        }
        addLine(lines, 0, GRID_HORIZON, WIDTH - 1, GRID_HORIZON, WALL_TOP);
        for (int k = 0; k < 12; k++) {
            int z = 16 + k * 96 + (96 - travel % 96);
            int y = GRID_HORIZON + GRID_DEPTH * 16 / z;
            if (y < limitY) {
                addLine(lines, 0, y, WIDTH - 1, y, WALL_BOTTOM);
            }
        }
        for (int col = -8; col <= 8; col++) {
            // Along the floor, x - center grows with y - horizon.
            int nearX = CENTER_X + col * 20 * (limitY - GRID_HORIZON) / 36;
            addLine(lines, CENTER_X + col * 2, GRID_HORIZON, nearX, limitY, WALL_BOTTOM);
        }
    }

    /**
     * The title screen: the first maze in view and "SPACE PARANOIDS" materializing letter by letter
     * in scrambled order, then lighting up.
     */
    private static void drawTitle(byte[] maze, short[] paths, short[] lines, int[] depth, int[] faces, int[] fs,
            int[] is, byte[] letter) {
        Random.seed(7);
        generateMaze(maze, paths);
        placePlayer(maze);
        clearEntities(fs, is);
        render(maze, lines, depth, faces, fs, is);
        int left = (WIDTH - TITLE.length() * 18) / 2;
        TftTouchShield.setTextSize(3);
        for (int k = 0; k < TITLE.length(); k++) {
            // 7 and the title's 15 letters share no factor, so this visits every letter once.
            int i = k * 7 % TITLE.length();
            letter[0] = (byte) TITLE.charAt(i);
            TftTouchShield.setTextColor(WALL_TOP, SPACE);
            TftTouchShield.setCursor(left + i * 18, 34);
            TftTouchShield.print(letter, 1);
            Delay.millis(70);
        }
        Delay.millis(150);
        showCentered(TITLE, 34, 3, TftTouchShield.WHITE);
        Delay.millis(80);
        showCentered(TITLE, 34, 3, TftTouchShield.ORANGE);
        showCentered("Destroy every hunter before time runs out", 206, 1, TftTouchShield.WHITE);
        showCentered("Tap to start", 222, 1, TftTouchShield.CYAN);
    }

    // ---- Records ----

    private static int freeSlot(int[] is) {
        for (int slot = 0; slot < ENTITIES; slot++) {
            if (is[slot * I_STRIDE + I_TYPE] == T_NONE) {
                return slot;
            }
        }
        return -1;
    }

    private static void clearEntities(int[] fs, int[] is) {
        for (int i = 0; i < ENTITIES * F_STRIDE; i++) {
            fs[i] = 0;
        }
        for (int i = 0; i < ENTITIES * I_STRIDE; i++) {
            is[i] = 0;
        }
    }

    private static int count(int[] is, int type) {
        int n = 0;
        for (int slot = 0; slot < ENTITIES; slot++) {
            if (is[slot * I_STRIDE + I_TYPE] == type) {
                n = n + 1;
            }
        }
        return n;
    }

    private static int sign(int value) {
        return value > 0 ? 1 : (value < 0 ? -1 : 0);
    }

    private static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(value, high));
    }

    // ---- Text ----

    private static void drawHeader() {
        TftTouchShield.fillRect(0, 0, WIDTH, HEADER - 1, SPACE);
        TftTouchShield.drawHorizontalLine(0, HEADER - 1, WIDTH, BUTTON);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.YELLOW, SPACE);
        TftTouchShield.setCursor(4, 2);
        TftTouchShield.print("SCORE ");
        TftTouchShield.print(score);
        TftTouchShield.setTextColor(TftTouchShield.CYAN, SPACE);
        TftTouchShield.setCursor(124, 2);
        TftTouchShield.print("HI ");
        TftTouchShield.print(best);
        TftTouchShield.setCursor(250, 2);
        TftTouchShield.print("SECTOR ");
        TftTouchShield.print(level);
        if (autopilot) {
            TftTouchShield.setTextColor(TftTouchShield.MAGENTA, SPACE);
            TftTouchShield.setCursor(208, 2);
            TftTouchShield.print("CPU");
        }
        TftTouchShield.setTextColor(TftTouchShield.WHITE, SPACE);
        TftTouchShield.setCursor(4, 11);
        TftTouchShield.print("LIVES ");
        TftTouchShield.print(lives);
        TftTouchShield.setTextColor(TftTouchShield.CYAN, SPACE);
        TftTouchShield.setCursor(196, 11);
        TftTouchShield.print("SHIELD");
        int color = TftTouchShield.GREEN;
        if (shield <= 25) {
            color = TftTouchShield.RED;
        } else if (shield <= 50) {
            color = TftTouchShield.YELLOW;
        }
        int bar = shield * 76 / MAX_SHIELD;
        TftTouchShield.drawRect(236, 10, 80, 8, BUTTON);
        TftTouchShield.fillRect(238, 12, bar, 4, color);
        drawStatus();
    }

    /** Time left and hunters left, in the header's second row. */
    private static void drawStatus() {
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(timeLeft <= 15 ? TftTouchShield.RED : TftTouchShield.WHITE, SPACE);
        TftTouchShield.setCursor(58, 11);
        TftTouchShield.print("TIME ");
        TftTouchShield.print(Math.max(0, timeLeft));
        TftTouchShield.print("  ");
        TftTouchShield.setTextColor(HUNTER, SPACE);
        TftTouchShield.setCursor(118, 11);
        TftTouchShield.print("HUNTERS ");
        TftTouchShield.print(huntersLeft);
        TftTouchShield.print(" ");
    }

    private static void showCentered(String text, int y, int size, int color) {
        showAt(text, (WIDTH - text.length() * 6 * size) / 2, y, size, color);
    }

    private static void showAt(String text, int x, int y, int size, int color) {
        TftTouchShield.setTextSize(size);
        TftTouchShield.setTextColor(color, SPACE);
        TftTouchShield.setCursor(x, y);
        TftTouchShield.print(text);
    }
}
