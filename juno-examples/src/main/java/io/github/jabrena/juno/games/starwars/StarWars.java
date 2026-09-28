package io.github.jabrena.juno.games.starwars;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * Star Wars on the ELEGOO 2.8" TFT touch screen shield, after Atari's 1983 vector arcade game, in
 * landscape. You fly an X-wing seen from the cockpit through the three phases of the attack on the
 * Death Star, and every wave repeats them a little faster:
 *
 * <ol>
 *   <li><b>Space</b>: TIE fighters swoop in and fire spinning fireballs. Destroy the number shown in
 *       the header. Darth Vader's TIE fighter joins halfway; it cannot be destroyed, and a hit only
 *       sends it spinning away.</li>
 *   <li><b>Surface</b>: laser towers stream past above the Death Star's surface. Shoot their yellow
 *       tops before they fire; destroying every top in the wave pays a bonus.</li>
 *   <li><b>Trench</b>: fly down the trench, where the X-wing follows the crosshair, so you steer as
 *       you aim. Wall turrets fire at you and, from wave 2, catwalks cross the trench: pass above or
 *       below them. At the end, hit the exhaust port. Miss it and you fly the trench again. Hit it
 *       with the only shot you fire in the trench and the Force is with you, for a bonus.</li>
 * </ol>
 *
 * <p>Drag to move the crosshair and tap to fire: the four wing cannons converge on the point you
 * tapped. You start with {@value #START_SHIELDS} shields; each fireball or catwalk that hits you takes
 * one, a hit with none left ends the game, and every destroyed Death Star restores one.
 *
 * <p>The game opens like the film: "A long time ago in a galaxy far, far away....", the logo
 * receding into the distance, and a Star Destroyer passing overhead in pursuit of a rebel ship,
 * then the title settles into place; after each game over it starts again from there. Tap to
 * start, then choose who flies the X-wing: <b>HUMAN</b> (you) or <b>CPU</b>, an autopilot that
 * locks on to the nearest target and fires, and in the trench steers above or below the catwalks.
 * Tap the header during the game to switch between the two ({@code CPU} shows in the header).
 *
 * <p>Everything is drawn in vector style from 3D points perspective-projected onto the screen
 * ({@code x' = cx + x·f/z}), with 3D lines clipped at the near plane and 2D lines clipped to the view.
 * Each frame's lines go into one of two display lists; only lines that changed since the previous
 * frame are erased, and only new lines and unchanged lines crossed by an erased one are drawn again,
 * so still parts of the scene (the horizon, far stars, a hovering fighter) cost nothing.
 */
@Board(ArduinoUnoQ.class)
public final class StarWars {
    private static final int FRAME_MILLIS = 30;

    // Screen: landscape, a text header over the 3D view.
    private static final int WIDTH = 320;
    private static final int HEIGHT = 240;
    private static final int HEADER = 20;
    private static final int CENTER_X = 160;
    private static final int CENTER_Y = 130;

    // Camera: looks along +z; world z is the distance ahead of it.
    private static final int FOCAL = 160;
    private static final int NEAR = 40;
    private static final int FAR = 2400;
    private static final int CAMERA_LIMIT = 110;
    private static final int CAMERA_STEP = 10;

    // Display lists: two of MAX_LINES lines (x0, y0, x1, y1, color), the one on screen and the next.
    private static final int MAX_LINES = 180;
    private static final int L_STRIDE = 5;
    private static final int LIST_SIZE = MAX_LINES * L_STRIDE;

    // Entities.
    private static final int ENTITIES = 24;
    private static final int E_TYPE = 0;
    private static final int E_X = 1;
    private static final int E_Y = 2;
    private static final int E_Z = 3;
    private static final int E_VX = 4;
    private static final int E_VY = 5;
    private static final int E_VZ = 6;
    private static final int E_TIMER = 7;
    /** Where the entity was last drawn and how close a shot must land to hit it (0: not a target). */
    private static final int E_SX = 8;
    private static final int E_SY = 9;
    private static final int E_SR = 10;
    /** TIE: 1 once it breaks off its attack. Tower: its height. Debris: its size. */
    private static final int E_AUX = 11;
    /** Tower: 1 while its top stands. */
    private static final int E_FLAG = 12;
    private static final int E_STRIDE = 13;

    private static final int T_NONE = 0;
    private static final int T_TIE = 1;
    private static final int T_VADER = 2;
    private static final int T_FIREBALL = 3;
    private static final int T_TOWER = 4;
    private static final int T_TURRET = 5;
    private static final int T_CATWALK = 6;
    private static final int T_PORT = 7;
    private static final int T_DEBRIS = 8;
    private static final int T_SHOT = 9;

    // Phases of a wave.
    private static final int SPACE_PHASE = 0;
    private static final int SURFACE_PHASE = 1;
    private static final int TRENCH_PHASE = 2;

    // World geometry.
    private static final int GROUND = -140;
    private static final int GRID = 300;
    private static final int TRENCH_HALF_WIDTH = 170;
    private static final int TRENCH_FLOOR = -150;
    private static final int TRENCH_TOP = 150;
    private static final int SEGMENT = 320;
    private static final int TIE_WING = 55;
    private static final int TOWER_HALF_WIDTH = 26;
    private static final int CAP_HALF_WIDTH = 16;
    private static final int CAP_HEIGHT = 26;
    private static final int CATWALK_HALF_HEIGHT = 12;
    /** How far above or below a catwalk's middle the X-wing must pass to clear it. */
    private static final int CATWALK_CLEARANCE = 44;
    /** How close to the camera a fireball must arrive to hit. */
    private static final int FIREBALL_REACH = 50;

    // Player.
    private static final int START_SHIELDS = 6;
    private static final int MAX_SHIELDS = 9;
    private static final int SHOTS = 3;
    private static final int SHOT_FRAMES = 4;
    /** A touch this short (in frames) that barely moved is a tap, and fires on release. */
    private static final int TAP_FRAMES = 8;
    private static final int DRAG_PIXELS = 20;

    // Scoring, in the arcade's style.
    private static final int FIREBALL_POINTS = 33;
    private static final int TURRET_POINTS = 100;
    private static final int TOWER_POINTS = 200;
    private static final int TIE_POINTS = 1000;
    private static final int PORT_POINTS = 25000;
    private static final int ALL_TOWERS_BONUS = 50000;
    private static final int SHIELD_BONUS = 5000;
    private static final int FORCE_BONUS = 50000;

    // Colors.
    private static final int SPACE = TftTouchShield.BLACK;
    private static final int STAR = 0x8410;
    private static final int TIE = TftTouchShield.GREEN;
    private static final int VADER = TftTouchShield.WHITE;
    private static final int CROSSHAIR = TftTouchShield.WHITE;
    private static final int LASER = TftTouchShield.RED;
    private static final int FIRE_A = TftTouchShield.ORANGE;
    private static final int FIRE_B = TftTouchShield.RED;
    private static final int STRUCTURE = 0x3A7F;
    private static final int GRID_LINE = 0x2112;
    private static final int TOWER_TOP = TftTouchShield.YELLOW;
    private static final int TURRET = TftTouchShield.MAGENTA;
    private static final int CATWALK = 0xFBE0;
    private static final int PORT_OUTER = TftTouchShield.RED;
    private static final int PORT_INNER = TftTouchShield.YELLOW;
    private static final int OPENING = 0x4D7F;

    // The opening scene: the Star Destroyer passing over the planet after the rebel ship.
    private static final int SCENE_FRAMES = 70;
    private static final int PLANET_Y = 640;
    private static final int PLANET_RADIUS = 470;
    private static final int DESTROYER = 0xBDF7;
    private static final int RUNNER = TftTouchShield.WHITE;
    private static final String OPENING_LINE_1 = "A long time ago in a galaxy";
    private static final String OPENING_LINE_2 = "far, far away....";

    // The HUMAN and CPU buttons of the pilot screen.
    private static final int CHOICE_X = 20;
    private static final int CHOICE_Y = 104;
    private static final int CHOICE_WIDTH = 130;
    private static final int CHOICE_HEIGHT = 72;
    private static final int CHOICE_GAP = 20;

    // The CPU pilot: how often it fires, how near targets (and the exhaust port) must come before it
    // shoots, how fast it moves the crosshair, and how often it deliberately aims a little off.
    private static final int CPU_FIRE_FRAMES = 6;
    private static final int CPU_RANGE = 1800;
    private static final int CPU_PORT_RANGE = 900;
    private static final int CPU_AIM_SPEED = 24;
    private static final int CPU_MISS_PERCENT = 20;

    private static int score;
    private static int best;
    private static int shields;
    private static int wave;
    private static int phase;
    private static int frame;
    private static boolean dead;

    private static int camX;
    private static int camY;
    private static int crossX = CENTER_X;
    private static int crossY = CENTER_Y;
    private static boolean touching;
    private static boolean dragged;
    private static int touchFrames;
    private static int releaseMisses;
    private static int startX;
    private static int startY;
    private static boolean autopilot;
    private static boolean headerPressed;
    private static int aimOffsetX;
    private static int aimOffsetY;

    // Display list state.
    private static int front;
    private static int shown;
    private static int built;

    // Phase state.
    private static int spawnCountdown;
    private static int kills;
    private static int killTarget;
    private static boolean vaderSpawned;
    private static int towersToSpawn;
    private static int towersTotal;
    private static int topsHit;
    private static int travel;
    private static int trenchLength;
    private static boolean portSpawned;
    private static boolean portDestroyed;
    private static boolean portMissed;
    private static int trenchShots;
    private static int status;

    private StarWars() {
    }

    public static void main(String[] args) {
        short[] lines = new short[2 * LIST_SIZE];
        int[] ents = new int[ENTITIES * E_STRIDE];
        byte[] letter = new byte[1];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);

        while (true) {
            opening(lines, letter);
            drawTitle(lines);
            waitForTap();
            Random.seed(Clock.micros());
            choosePilot();
            score = 0;
            shields = START_SHIELDS;
            wave = 1;
            boolean alive = true;
            while (alive) {
                alive = playWave(lines, ents);
                if (alive) {
                    wave = wave + 1;
                }
            }
            if (score > best) {
                best = score;
            }
            drawHeader();
            clearView();
            showCentered("GAME OVER", 100, 3, TftTouchShield.RED);
            Delay.millis(3000);
        }
    }

    // ---- Game flow ----

    /** Plays the three phases of one wave; returns false when the last shield is lost. */
    private static boolean playWave(short[] lines, int[] ents) {
        if (!runPhase(SPACE_PHASE, lines, ents)) {
            return false;
        }
        approachDeathStar();
        if (!runPhase(SURFACE_PHASE, lines, ents)) {
            return false;
        }
        if (topsHit == towersTotal) {
            score = score + ALL_TOWERS_BONUS;
            drawHeader();
            clearView();
            showCentered("ALL TOWER TOPS DESTROYED", 110, 1, TOWER_TOP);
            showCentered("BONUS 50000", 126, 1, TftTouchShield.WHITE);
            Delay.millis(1500);
        }
        while (true) {
            if (!runPhase(TRENCH_PHASE, lines, ents)) {
                return false;
            }
            if (portDestroyed) {
                break;
            }
            clearView();
            showCentered("YOU MISSED", 100, 2, TftTouchShield.RED);
            showCentered("Fly the trench again", 130, 1, TftTouchShield.WHITE);
            Delay.millis(1500);
        }
        destroyDeathStar();
        return true;
    }

    /** Runs one phase frame by frame; returns false when the X-wing is destroyed. */
    private static boolean runPhase(int which, short[] lines, int[] ents) {
        startPhase(which, ents);
        drawHeader();
        clearView();
        if (which == SPACE_PHASE) {
            showCentered("WAVE", 88, 3, TftTouchShield.YELLOW);
            TftTouchShield.setCursor(wave < 10 ? 151 : 142, 120);
            TftTouchShield.print(wave);
            showCentered("Destroy the TIE fighters", 156, 1, TftTouchShield.WHITE);
        } else if (which == SURFACE_PHASE) {
            showCentered("DEATH STAR SURFACE", 104, 2, TftTouchShield.YELLOW);
            showCentered("Shoot the tower tops", 136, 1, TftTouchShield.WHITE);
        } else {
            showCentered("THE TRENCH", 104, 2, TftTouchShield.YELLOW);
            showCentered("Steer with the crosshair and hit the port", 136, 1, TftTouchShield.WHITE);
        }
        Delay.millis(1400);
        clearView();

        int next = Clock.millis();
        while (true) {
            while (Clock.millis() - next < 0) {
                Delay.millis(1);
            }
            next = next + FRAME_MILLIS;
            if (Clock.millis() - next > 4 * FRAME_MILLIS) {
                // A slow frame: carry on from now rather than rushing to catch up.
                next = Clock.millis();
            }
            frame = frame + 1;
            handleTouch(ents);
            if (autopilot) {
                flyAutopilot(ents);
            }
            step(ents);
            resolveShots(ents);
            render(lines, ents);
            if (dead) {
                return false;
            }
            if (phaseOver(ents)) {
                return true;
            }
            if (phase == TRENCH_PHASE && (frame & 7) == 0) {
                drawStatus();
            }
        }
    }

    private static void startPhase(int which, int[] ents) {
        phase = which;
        dead = false;
        clear(ents, ENTITIES * E_STRIDE);
        camX = 0;
        camY = 0;
        travel = 0;
        spawnCountdown = 10;
        if (which == SPACE_PHASE) {
            kills = 0;
            killTarget = Math.min(4 + 2 * wave, 12);
            vaderSpawned = false;
        } else if (which == SURFACE_PHASE) {
            towersTotal = Math.min(4 + 2 * wave, 14);
            towersToSpawn = towersTotal;
            topsHit = 0;
        } else {
            trenchLength = 8000 + 1500 * Math.min(wave, 6);
            portSpawned = false;
            portDestroyed = false;
            portMissed = false;
            trenchShots = 0;
        }
    }

    private static boolean phaseOver(int[] ents) {
        if (phase == SPACE_PHASE) {
            return kills >= killTarget && count(ents, T_TIE) + count(ents, T_VADER) + count(ents, T_FIREBALL)
                    + count(ents, T_DEBRIS) == 0;
        }
        if (phase == SURFACE_PHASE) {
            return towersToSpawn == 0 && count(ents, T_TOWER) + count(ents, T_FIREBALL) + count(ents, T_DEBRIS) == 0;
        }
        return portMissed || (portDestroyed && count(ents, T_DEBRIS) == 0);
    }

    /** One hit on the X-wing: a shield is lost, or the game when none is left. */
    private static void shieldHit() {
        if (shields == 0) {
            dead = true;
            return;
        }
        shields = shields - 1;
        TftTouchShield.fillRect(0, 0, WIDTH, HEADER - 1, TftTouchShield.RED);
        Delay.millis(60);
        drawHeader();
    }

    // ---- Input ----

    /**
     * Drag moves the crosshair; a short tap that did not move fires where it landed, on release.
     * Tapping the header hands the X-wing to the CPU or back.
     */
    private static void handleTouch(int[] ents) {
        boolean down = TftTouchShield.readTouch();
        if (down && TftTouchShield.touchY() < HEADER) {
            if (!headerPressed) {
                autopilot = !autopilot;
                touching = false;
                drawHeader();
            }
            headerPressed = true;
            return;
        }
        headerPressed = false;
        if (autopilot) {
            return;
        }
        if (down) {
            int x = Math.max(8, Math.min(TftTouchShield.touchX(), WIDTH - 9));
            int y = Math.max(HEADER + 8, Math.min(TftTouchShield.touchY(), HEIGHT - 9));
            crossX = x;
            crossY = y;
            if (!touching) {
                touching = true;
                dragged = false;
                touchFrames = 0;
                startX = x;
                startY = y;
            } else {
                touchFrames = touchFrames + 1;
                if (Math.abs(x - startX) + Math.abs(y - startY) > DRAG_PIXELS) {
                    dragged = true;
                }
            }
            releaseMisses = 0;
        } else if (touching) {
            // Two frames without contact, so a flicker of the resistive panel is not a release.
            releaseMisses = releaseMisses + 1;
            if (releaseMisses >= 2) {
                touching = false;
                if (touchFrames <= TAP_FRAMES && !dragged) {
                    fire(ents);
                }
            }
        }
    }

    private static void fire(int[] ents) {
        if (count(ents, T_SHOT) >= SHOTS) {
            return;
        }
        int slot = freeSlot(ents);
        if (slot < 0) {
            return;
        }
        int b = slot * E_STRIDE;
        ents[b + E_TYPE] = T_SHOT;
        ents[b + E_X] = crossX;
        ents[b + E_Y] = crossY;
        ents[b + E_TIMER] = SHOT_FRAMES;
        // The target is what is under the crosshair now; it is hit when the beams converge.
        int target = targetAt(ents, crossX, crossY);
        ents[b + E_AUX] = target;
        if (target >= 0) {
            ents[b + E_FLAG] = ents[target * E_STRIDE + E_TYPE];
        }
        if (phase == TRENCH_PHASE) {
            trenchShots = trenchShots + 1;
        }
    }

    /** The pilot screen: waits for a tap on HUMAN or CPU. */
    private static void choosePilot() {
        TftTouchShield.fillScreen(SPACE);
        showCentered("CHOOSE PILOT", 36, 3, TftTouchShield.YELLOW);
        showCentered("Who flies the X-wing?", 74, 1, TftTouchShield.WHITE);
        drawChoice(0, "HUMAN", "You aim and fire", false);
        drawChoice(1, "CPU", "Autopilot plays", false);
        showCentered("Tap the header in game to switch", 206, 1, STAR);
        int choice = -1;
        while (choice < 0) {
            if (TftTouchShield.readTouch()) {
                choice = choiceAt(TftTouchShield.touchX(), TftTouchShield.touchY());
            }
            Delay.millis(10);
        }
        autopilot = choice == 1;
        touching = false;
        drawChoice(choice, choice == 0 ? "HUMAN" : "CPU", choice == 0 ? "You aim and fire" : "Autopilot plays", true);
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
        int color = chosen ? STRUCTURE : 0x2124;
        TftTouchShield.fillRect(x, CHOICE_Y, CHOICE_WIDTH, CHOICE_HEIGHT, color);
        TftTouchShield.drawRect(x, CHOICE_Y, CHOICE_WIDTH, CHOICE_HEIGHT, chosen ? TftTouchShield.WHITE : STRUCTURE);
        TftTouchShield.setTextSize(3);
        TftTouchShield.setTextColor(TftTouchShield.YELLOW, color);
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

    // ---- The CPU pilot ----

    /**
     * The CPU at the controls, flying like a person rather than a machine: it lets targets come
     * within {@value #CPU_RANGE} units (the exhaust port within {@value #CPU_PORT_RANGE}) before
     * shooting, glides the crosshair towards the nearest one at {@value #CPU_AIM_SPEED} pixels a
     * frame, and pulls the trigger every {@value #CPU_FIRE_FRAMES} frames once it is on its aim
     * point. About {@value #CPU_MISS_PERCENT}% of its shots at TIE fighters, towers and turrets are
     * aimed just outside the target and miss; fireballs and the exhaust port it takes seriously. In the trench the crosshair also steers, so there it aims only for the shot and then
     * steers high, or low when the next catwalk is high, to pass the catwalks.
     */
    private static void flyAutopilot(int[] ents) {
        int target = -1;
        int nearest = 1 << 30;
        for (int slot = 0; slot < ENTITIES; slot++) {
            int b = slot * E_STRIDE;
            int type = ents[b + E_TYPE];
            int z = ents[b + E_Z];
            int range = type == T_PORT ? CPU_PORT_RANGE : CPU_RANGE;
            if (ents[b + E_SR] > 0 && type != T_SHOT && z < nearest && z < range) {
                nearest = z;
                target = slot;
            }
        }
        boolean trigger = frame % CPU_FIRE_FRAMES == 0;
        if (trigger) {
            // Each shot is either aimed true or, now and then, just past the edge of the target.
            aimOffsetX = 0;
            aimOffsetY = 0;
            int type = target >= 0 ? ents[target * E_STRIDE + E_TYPE] : T_NONE;
            boolean casual = type == T_TIE || type == T_TOWER || type == T_TURRET;
            if (casual && Random.nextInt(100) < CPU_MISS_PERCENT) {
                int off = ents[target * E_STRIDE + E_SR] + Random.nextInt(6, 20);
                aimOffsetX = Random.nextInt(2) == 0 ? -off : off;
                aimOffsetY = Random.nextInt(-off, off + 1);
            }
        }
        if (phase == TRENCH_PHASE) {
            if (target >= 0 && trigger) {
                crossX = ents[target * E_STRIDE + E_SX] + aimOffsetX;
                crossY = ents[target * E_STRIDE + E_SY] + aimOffsetY;
                fire(ents);
            }
            int catwalk = find(ents, T_CATWALK);
            crossX = CENTER_X;
            crossY = catwalk >= 0 && ents[catwalk * E_STRIDE + E_Y] > 0 ? HEIGHT - 30 : HEADER + 30;
            return;
        }
        int aimX = CENTER_X;
        int aimY = CENTER_Y;
        if (target >= 0) {
            aimX = ents[target * E_STRIDE + E_SX] + aimOffsetX;
            aimY = ents[target * E_STRIDE + E_SY] + aimOffsetY;
        }
        aimX = clamp(aimX, 8, WIDTH - 9);
        aimY = clamp(aimY, HEADER + 8, HEIGHT - 9);
        crossX = crossX + clamp(aimX - crossX, -CPU_AIM_SPEED, CPU_AIM_SPEED);
        crossY = crossY + clamp(aimY - crossY, -CPU_AIM_SPEED, CPU_AIM_SPEED);
        if (target >= 0 && trigger && Math.abs(aimX - crossX) + Math.abs(aimY - crossY) <= 4) {
            fire(ents);
        }
    }

    // ---- Simulation ----

    /** How far the world moves towards the camera each frame. */
    private static int flightSpeed() {
        if (phase == SURFACE_PHASE) {
            return 26 + 2 * Math.min(wave, 8);
        }
        if (phase == TRENCH_PHASE) {
            return 34 + 3 * Math.min(wave, 8);
        }
        return 0;
    }

    private static void step(int[] ents) {
        int speed = flightSpeed();
        travel = travel + speed;
        if (phase == TRENCH_PHASE) {
            steer();
        }
        spawn(ents);
        for (int slot = 0; slot < ENTITIES; slot++) {
            int b = slot * E_STRIDE;
            int type = ents[b + E_TYPE];
            if (type == T_NONE || type == T_SHOT) {
                continue;
            }
            ents[b + E_X] = ents[b + E_X] + ents[b + E_VX];
            ents[b + E_Y] = ents[b + E_Y] + ents[b + E_VY];
            ents[b + E_Z] = ents[b + E_Z] + ents[b + E_VZ] - speed;
            int z = ents[b + E_Z];
            if (type == T_TIE || type == T_VADER) {
                flyTie(ents, b);
            } else if (type == T_FIREBALL) {
                if (z <= NEAR) {
                    ents[b + E_TYPE] = T_NONE;
                    if (Math.abs(ents[b + E_X] - camX) < FIREBALL_REACH
                            && Math.abs(ents[b + E_Y] - camY) < FIREBALL_REACH) {
                        shieldHit();
                    }
                }
            } else if (type == T_TOWER || type == T_TURRET) {
                if (z < NEAR) {
                    ents[b + E_TYPE] = T_NONE;
                } else {
                    gunnerFires(ents, b);
                }
            } else if (type == T_CATWALK) {
                if (z <= NEAR) {
                    ents[b + E_TYPE] = T_NONE;
                    if (Math.abs(ents[b + E_Y] - camY) < CATWALK_CLEARANCE) {
                        shieldHit();
                    }
                }
            } else if (type == T_PORT) {
                if (z < NEAR) {
                    ents[b + E_TYPE] = T_NONE;
                    portMissed = true;
                }
            } else if (type == T_DEBRIS) {
                ents[b + E_TIMER] = ents[b + E_TIMER] - 1;
                if (ents[b + E_TIMER] <= 0 || z < NEAR) {
                    ents[b + E_TYPE] = T_NONE;
                }
            }
        }
    }

    /** In the trench the X-wing drifts towards where the crosshair points. */
    private static void steer() {
        int targetX = clamp((crossX - CENTER_X) * CAMERA_LIMIT / 150, -CAMERA_LIMIT, CAMERA_LIMIT);
        int targetY = clamp((CENTER_Y - crossY) * CAMERA_LIMIT / 100, -CAMERA_LIMIT, CAMERA_LIMIT);
        camX = camX + clamp(targetX - camX, -CAMERA_STEP, CAMERA_STEP);
        camY = camY + clamp(targetY - camY, -CAMERA_STEP, CAMERA_STEP);
    }

    private static void spawn(int[] ents) {
        if (phase == SPACE_PHASE) {
            spawnFighters(ents);
        } else if (phase == SURFACE_PHASE) {
            spawnTowers(ents);
        } else {
            spawnTrench(ents);
        }
    }

    private static void spawnFighters(int[] ents) {
        if (kills >= killTarget) {
            return;
        }
        spawnCountdown = spawnCountdown - 1;
        int attacking = count(ents, T_TIE) + count(ents, T_VADER);
        if (spawnCountdown > 0 || attacking >= Math.min(2 + wave / 2, 4)) {
            return;
        }
        int slot = freeSlot(ents);
        if (slot < 0) {
            return;
        }
        spawnCountdown = Random.nextInt(15, 45);
        int b = slot * E_STRIDE;
        int type = T_TIE;
        if (!vaderSpawned && kills * 2 >= killTarget) {
            type = T_VADER;
            vaderSpawned = true;
        }
        ents[b + E_TYPE] = type;
        ents[b + E_X] = Random.nextInt(-600, 601);
        ents[b + E_Y] = Random.nextInt(-280, 281);
        ents[b + E_Z] = FAR - Random.nextInt(400);
        ents[b + E_VZ] = -Math.min(12 + 2 * wave, 30);
        ents[b + E_TIMER] = 1;
    }

    /** Attacks, weaving towards the camera and firing, then breaks off and flies out of view. */
    private static void flyTie(int[] ents, int b) {
        int x = ents[b + E_X];
        int y = ents[b + E_Y];
        int z = ents[b + E_Z];
        if (ents[b + E_AUX] == 0) {
            int timer = ents[b + E_TIMER] - 1;
            if (timer <= 0) {
                int weave = 6 + Math.min(wave, 6);
                ents[b + E_VX] = Random.nextInt(-weave, weave + 1);
                ents[b + E_VY] = Random.nextInt(-5, 6);
                timer = Random.nextInt(15, 40);
            }
            ents[b + E_TIMER] = timer;
            // Stay inside the view while attacking.
            if (x > z * 3 / 5) {
                ents[b + E_VX] = -Math.abs(ents[b + E_VX]) - 1;
            } else if (x < -z * 3 / 5) {
                ents[b + E_VX] = Math.abs(ents[b + E_VX]) + 1;
            }
            if (y > z * 2 / 5) {
                ents[b + E_VY] = -Math.abs(ents[b + E_VY]) - 1;
            } else if (y < -z * 2 / 5) {
                ents[b + E_VY] = Math.abs(ents[b + E_VY]) + 1;
            }
            if (z < 320 || kills >= killTarget) {
                breakOff(ents, b);
            } else if (z > 450 && z < 1700 && Random.nextInt(1000) < 10 + 3 * Math.min(wave, 10)) {
                fireball(ents, x, y, z);
            }
        } else if (z > FAR + 200 || x > z + 200 || x < -z - 200 || y > z + 200 || y < -z - 200) {
            ents[b + E_TYPE] = T_NONE;
        }
    }

    private static void breakOff(int[] ents, int b) {
        ents[b + E_AUX] = 1;
        ents[b + E_VZ] = 18 + wave;
        ents[b + E_VX] = ents[b + E_X] >= 0 ? 16 : -16;
        ents[b + E_VY] = ents[b + E_Y] >= 0 ? 8 : -8;
    }

    private static void spawnTowers(int[] ents) {
        if (towersToSpawn == 0) {
            return;
        }
        spawnCountdown = spawnCountdown - 1;
        if (spawnCountdown > 0) {
            return;
        }
        int slot = freeSlot(ents);
        if (slot < 0) {
            return;
        }
        spawnCountdown = Math.max(18, Random.nextInt(30, 60) - 2 * wave);
        int b = slot * E_STRIDE;
        int x = Random.nextInt(140, 620);
        if (Random.nextInt(2) == 0) {
            x = -x;
        }
        ents[b + E_TYPE] = T_TOWER;
        ents[b + E_X] = x;
        ents[b + E_Y] = GROUND;
        ents[b + E_Z] = FAR;
        ents[b + E_AUX] = Random.nextInt(170, 330);
        ents[b + E_FLAG] = 1;
        ents[b + E_TIMER] = Random.nextInt(10, 50);
        towersToSpawn = towersToSpawn - 1;
        drawStatus();
    }

    private static void spawnTrench(int[] ents) {
        if (portSpawned) {
            return;
        }
        int slot = freeSlot(ents);
        if (slot < 0) {
            return;
        }
        int b = slot * E_STRIDE;
        if (travel >= trenchLength) {
            portSpawned = true;
            ents[b + E_TYPE] = T_PORT;
            ents[b + E_Y] = TRENCH_FLOOR;
            ents[b + E_Z] = FAR;
            return;
        }
        spawnCountdown = spawnCountdown - 1;
        if (spawnCountdown > 0) {
            return;
        }
        spawnCountdown = Math.max(14, Random.nextInt(24, 50) - 2 * wave);
        ents[b + E_Z] = FAR;
        if (wave >= 2 && Random.nextInt(3) == 0) {
            ents[b + E_TYPE] = T_CATWALK;
            ents[b + E_Y] = Random.nextInt(-90, 91);
        } else {
            ents[b + E_TYPE] = T_TURRET;
            ents[b + E_X] = Random.nextInt(2) == 0 ? -TRENCH_HALF_WIDTH : TRENCH_HALF_WIDTH;
            ents[b + E_Y] = Random.nextInt(-110, 111);
            ents[b + E_TIMER] = Random.nextInt(10, 60);
        }
    }

    /** Towers (from their tops, while they stand) and trench turrets fire now and then. */
    private static void gunnerFires(int[] ents, int b) {
        int z = ents[b + E_Z];
        if (z < 500 || z > 1900) {
            return;
        }
        int y = ents[b + E_Y];
        if (ents[b + E_TYPE] == T_TOWER) {
            if (ents[b + E_FLAG] == 0) {
                return;
            }
            y = GROUND + ents[b + E_AUX] + CAP_HEIGHT / 2;
        }
        ents[b + E_TIMER] = ents[b + E_TIMER] - 1;
        if (ents[b + E_TIMER] <= 0) {
            // Turrets line the whole trench, so each fires less often than a tower.
            int low = ents[b + E_TYPE] == T_TURRET ? 70 : 40;
            ents[b + E_TIMER] = Math.max(24, Random.nextInt(low, low + 60) - 4 * wave);
            fireball(ents, ents[b + E_X], y, z);
        }
    }

    /** Launches a fireball from (x, y, z) aimed at where the camera is now. */
    private static void fireball(int[] ents, int x, int y, int z) {
        int slot = freeSlot(ents);
        if (slot < 0) {
            return;
        }
        int b = slot * E_STRIDE;
        int speed = Math.min(20 + 2 * wave, 36);
        int frames = Math.max(1, (z - NEAR) / (speed + flightSpeed()));
        ents[b + E_TYPE] = T_FIREBALL;
        ents[b + E_X] = x;
        ents[b + E_Y] = y;
        ents[b + E_Z] = z;
        ents[b + E_VX] = (camX - x) / frames;
        ents[b + E_VY] = (camY - y) / frames;
        ents[b + E_VZ] = -speed;
    }

    // ---- Shots ----

    /**
     * Lasers take {@value #SHOT_FRAMES} frames to converge, then hit the target that was under the
     * crosshair when they were fired, if it is still there (a tower top still standing).
     */
    private static void resolveShots(int[] ents) {
        for (int slot = 0; slot < ENTITIES; slot++) {
            int b = slot * E_STRIDE;
            if (ents[b + E_TYPE] != T_SHOT) {
                continue;
            }
            ents[b + E_TIMER] = ents[b + E_TIMER] - 1;
            if (ents[b + E_TIMER] > 0) {
                continue;
            }
            ents[b + E_TYPE] = T_NONE;
            int target = ents[b + E_AUX];
            if (target >= 0) {
                int t = target * E_STRIDE;
                boolean standing = ents[t + E_TYPE] != T_TOWER || ents[t + E_FLAG] != 0;
                if (ents[t + E_TYPE] == ents[b + E_FLAG] && standing) {
                    hit(ents, target);
                }
            }
        }
    }

    /** The nearest entity whose target circle contains the screen point, or -1. */
    private static int targetAt(int[] ents, int x, int y) {
        int found = -1;
        int nearest = 1 << 30;
        for (int slot = 0; slot < ENTITIES; slot++) {
            int b = slot * E_STRIDE;
            int type = ents[b + E_TYPE];
            int r = ents[b + E_SR];
            if (type == T_NONE || type == T_SHOT || r <= 0) {
                continue;
            }
            int dx = ents[b + E_SX] - x;
            int dy = ents[b + E_SY] - y;
            if (dx * dx + dy * dy <= r * r && ents[b + E_Z] < nearest) {
                nearest = ents[b + E_Z];
                found = slot;
            }
        }
        return found;
    }

    private static void hit(int[] ents, int slot) {
        int b = slot * E_STRIDE;
        int type = ents[b + E_TYPE];
        int x = ents[b + E_X];
        int y = ents[b + E_Y];
        int z = ents[b + E_Z];
        if (type == T_VADER) {
            // Vader cannot be destroyed: the hit sends him spinning out of the fight.
            breakOff(ents, b);
            ents[b + E_VZ] = 48;
            return;
        }
        if (type == T_TOWER) {
            ents[b + E_FLAG] = 0;
            ents[b + E_SR] = 0;
            topsHit = topsHit + 1;
            score = score + TOWER_POINTS;
            debris(ents, x, GROUND + ents[b + E_AUX] + CAP_HEIGHT / 2, z, 40);
            drawHeader();
            return;
        }
        ents[b + E_TYPE] = T_NONE;
        if (type == T_TIE) {
            kills = kills + 1;
            score = score + TIE_POINTS;
            debris(ents, x, y, z, 90);
        } else if (type == T_FIREBALL) {
            score = score + FIREBALL_POINTS;
            debris(ents, x, y, z, 30);
        } else if (type == T_TURRET) {
            score = score + TURRET_POINTS;
            debris(ents, x, y, z, 40);
        } else if (type == T_PORT) {
            portDestroyed = true;
            score = score + PORT_POINTS;
            debris(ents, x, y, z, 120);
        }
        drawHeader();
    }

    private static void debris(int[] ents, int x, int y, int z, int size) {
        int slot = freeSlot(ents);
        if (slot < 0) {
            return;
        }
        int b = slot * E_STRIDE;
        ents[b + E_TYPE] = T_DEBRIS;
        ents[b + E_X] = x;
        ents[b + E_Y] = y;
        ents[b + E_Z] = z;
        ents[b + E_TIMER] = 10;
        ents[b + E_AUX] = size;
    }

    // ---- Scene ----

    private static void render(short[] lines, int[] ents) {
        built = 0;
        if (phase == SPACE_PHASE) {
            drawStars(lines);
        } else if (phase == SURFACE_PHASE) {
            drawSurface(lines);
        } else {
            drawTrench(lines);
        }
        // Farthest first, so what is near is drawn last.
        for (int pass = 0; pass < 2; pass++) {
            for (int slot = 0; slot < ENTITIES; slot++) {
                int b = slot * E_STRIDE;
                int type = ents[b + E_TYPE];
                boolean far = ents[b + E_Z] > FAR / 2;
                if (type != T_NONE && type != T_SHOT && far == (pass == 0)) {
                    drawEntity(lines, ents, slot);
                }
            }
        }
        for (int slot = 0; slot < ENTITIES; slot++) {
            if (ents[slot * E_STRIDE + E_TYPE] == T_SHOT) {
                drawShot(lines, ents, slot);
            }
        }
        drawCrosshair(lines);
        present(lines);
    }

    private static void drawStars(short[] lines) {
        for (int i = 0; i < 28; i++) {
            int x = (i * 97 + 13) % WIDTH;
            int y = HEADER + 2 + (i * 61 + 7 * i * i) % (HEIGHT - HEADER - 4);
            addLine(lines, x, y, x, y, STAR);
        }
    }

    private static void drawSurface(short[] lines) {
        addLine(lines, 0, CENTER_Y, WIDTH - 1, CENTER_Y, STRUCTURE);
        int offset = travel % GRID;
        for (int k = 0; k < 8; k++) {
            int z = GRID * (k + 1) - offset;
            if (z < NEAR || z > FAR) {
                continue;
            }
            for (int c = 0; c < 4; c++) {
                int x = -540 + c * 360 + ((k & 1) == 0 ? 0 : 180);
                line3(lines, x - 50, GROUND, z, x + 50, GROUND, z, GRID_LINE);
            }
        }
    }

    private static void drawTrench(short[] lines) {
        int w = TRENCH_HALF_WIDTH;
        line3(lines, -w, TRENCH_TOP, NEAR, -w, TRENCH_TOP, FAR, STRUCTURE);
        line3(lines, -w, TRENCH_FLOOR, NEAR, -w, TRENCH_FLOOR, FAR, STRUCTURE);
        line3(lines, w, TRENCH_FLOOR, NEAR, w, TRENCH_FLOOR, FAR, STRUCTURE);
        line3(lines, w, TRENCH_TOP, NEAR, w, TRENCH_TOP, FAR, STRUCTURE);
        int offset = travel % SEGMENT;
        for (int k = 0; k < 8; k++) {
            int z = SEGMENT * (k + 1) - offset;
            if (z < NEAR || z > FAR) {
                continue;
            }
            line3(lines, -w, TRENCH_FLOOR, z, -w, TRENCH_TOP, z, GRID_LINE);
            line3(lines, -w, TRENCH_FLOOR, z, w, TRENCH_FLOOR, z, GRID_LINE);
            line3(lines, w, TRENCH_FLOOR, z, w, TRENCH_TOP, z, GRID_LINE);
        }
    }

    private static void drawEntity(short[] lines, int[] ents, int slot) {
        int b = slot * E_STRIDE;
        int type = ents[b + E_TYPE];
        int x = ents[b + E_X];
        int y = ents[b + E_Y];
        int z = ents[b + E_Z];
        ents[b + E_SR] = 0;
        if (z < NEAR) {
            return;
        }
        int px = projectX(x, z);
        int py = projectY(y, z);
        ents[b + E_SX] = px;
        ents[b + E_SY] = py;
        if (type == T_TIE || type == T_VADER) {
            drawTie(lines, px, py, z, type == T_VADER);
            ents[b + E_SR] = scale(80, z) + 4;
        } else if (type == T_FIREBALL) {
            int r = Math.max(2, Math.min(scale(28, z), 40));
            int spin = (frame + slot) & 1;
            int straight = spin == 0 ? FIRE_A : FIRE_B;
            int diagonal = spin == 0 ? FIRE_B : FIRE_A;
            int d = r * 7 / 10;
            addLine(lines, px - r, py, px + r, py, straight);
            addLine(lines, px, py - r, px, py + r, straight);
            addLine(lines, px - d, py - d, px + d, py + d, diagonal);
            addLine(lines, px - d, py + d, px + d, py - d, diagonal);
            ents[b + E_SR] = r + 6;
        } else if (type == T_TOWER) {
            drawTower(lines, ents, b, px, py, z);
        } else if (type == T_TURRET) {
            line3(lines, x, y - 16, z - 16, x, y + 16, z - 16, TURRET);
            line3(lines, x, y + 16, z - 16, x, y + 16, z + 16, TURRET);
            line3(lines, x, y + 16, z + 16, x, y - 16, z + 16, TURRET);
            line3(lines, x, y - 16, z + 16, x, y - 16, z - 16, TURRET);
            ents[b + E_SR] = scale(22, z) + 5;
        } else if (type == T_CATWALK) {
            int w = TRENCH_HALF_WIDTH;
            int top = y + CATWALK_HALF_HEIGHT;
            int bottom = y - CATWALK_HALF_HEIGHT;
            line3(lines, -w, top, z, w, top, z, CATWALK);
            line3(lines, -w, bottom, z, w, bottom, z, CATWALK);
            line3(lines, -w, top, z, -w, bottom, z, CATWALK);
            line3(lines, w, top, z, w, bottom, z, CATWALK);
        } else if (type == T_PORT) {
            floorSquare(lines, x, y, z, 50, PORT_OUTER);
            floorSquare(lines, x, y, z, 22, PORT_INNER);
            ents[b + E_SR] = scale(45, z) + 6;
        } else if (type == T_DEBRIS) {
            int age = 10 - ents[b + E_TIMER];
            int size = ents[b + E_AUX];
            int r0 = size * age / 10;
            int r1 = r0 + size / 4 + 4;
            for (int k = 0; k < 8; k++) {
                int color = (k & 1) == 0 ? TftTouchShield.YELLOW : TftTouchShield.ORANGE;
                part(lines, px, py, z, dirX(k) * r0 / 10, dirY(k) * r0 / 10, dirX(k) * r1 / 10, dirY(k) * r1 / 10,
                        color);
            }
        }
    }

    /**
     * A TIE fighter seen head-on: two hexagonal wings joined to a hexagonal cockpit. Vader's has
     * bent wings. Far away, a wing is one stroke.
     */
    private static void drawTie(short[] lines, int px, int py, int z, boolean vader) {
        int color = vader ? VADER : TIE;
        if (scale(70, z) < 7) {
            part(lines, px, py, z, -TIE_WING, 70, -TIE_WING, -70, color);
            part(lines, px, py, z, TIE_WING, 70, TIE_WING, -70, color);
            part(lines, px, py, z, -TIE_WING, 0, TIE_WING, 0, color);
            return;
        }
        for (int side = -1; side <= 1; side = side + 2) {
            int wx = side * TIE_WING;
            int o = side * 14;
            if (vader) {
                part(lines, px, py, z, wx - side * 20, 74, wx + o, 34, color);
                part(lines, px, py, z, wx + o, 34, wx + o, -34, color);
                part(lines, px, py, z, wx + o, -34, wx - side * 20, -74, color);
                part(lines, px, py, z, wx - side * 20, 74, wx - side * 20, -74, color);
                part(lines, px, py, z, side * 16, 0, wx - side * 20, 0, color);
            } else {
                part(lines, px, py, z, wx, 70, wx + o, 35, color);
                part(lines, px, py, z, wx + o, 35, wx + o, -35, color);
                part(lines, px, py, z, wx + o, -35, wx, -70, color);
                part(lines, px, py, z, wx, -70, wx - o, -35, color);
                part(lines, px, py, z, wx - o, -35, wx - o, 35, color);
                part(lines, px, py, z, wx - o, 35, wx, 70, color);
                part(lines, px, py, z, wx, 70, wx, -70, color);
                part(lines, px, py, z, side * 16, 0, wx - o, 0, color);
            }
        }
        int cockpit = vader ? TftTouchShield.RED : color;
        part(lines, px, py, z, 16, 0, 8, 14, cockpit);
        part(lines, px, py, z, 8, 14, -8, 14, cockpit);
        part(lines, px, py, z, -8, 14, -16, 0, cockpit);
        part(lines, px, py, z, -16, 0, -8, -14, cockpit);
        part(lines, px, py, z, -8, -14, 8, -14, cockpit);
        part(lines, px, py, z, 8, -14, 16, 0, cockpit);
    }

    /** A laser tower on the surface; (px, py) is its foot. Its top is the target. */
    private static void drawTower(short[] lines, int[] ents, int b, int px, int py, int z) {
        int h = ents[b + E_AUX];
        int w = TOWER_HALF_WIDTH;
        part(lines, px, py, z, -w, 0, -w, h, STRUCTURE);
        part(lines, px, py, z, w, 0, w, h, STRUCTURE);
        part(lines, px, py, z, -w, h, w, h, STRUCTURE);
        if (ents[b + E_FLAG] != 0) {
            int c = CAP_HALF_WIDTH;
            part(lines, px, py, z, -c, h, -c, h + CAP_HEIGHT, TOWER_TOP);
            part(lines, px, py, z, -c, h + CAP_HEIGHT, c, h + CAP_HEIGHT, TOWER_TOP);
            part(lines, px, py, z, c, h + CAP_HEIGHT, c, h, TOWER_TOP);
            part(lines, px, py, z, -c, h + CAP_HEIGHT / 2, c, h + CAP_HEIGHT / 2, TOWER_TOP);
            ents[b + E_SX] = px;
            ents[b + E_SY] = py - scale(h + CAP_HEIGHT / 2, z);
            ents[b + E_SR] = scale(CAP_HALF_WIDTH + 6, z) + 5;
        }
    }

    private static void floorSquare(short[] lines, int x, int y, int z, int half, int color) {
        line3(lines, x - half, y, z - half, x + half, y, z - half, color);
        line3(lines, x + half, y, z - half, x + half, y, z + half, color);
        line3(lines, x + half, y, z + half, x - half, y, z + half, color);
        line3(lines, x - half, y, z + half, x - half, y, z - half, color);
    }

    /** The four wing cannons' beams, travelling from the screen's corners to the crosshair. */
    private static void drawShot(short[] lines, int[] ents, int slot) {
        int b = slot * E_STRIDE;
        int tx = ents[b + E_X];
        int ty = ents[b + E_Y];
        int progress = SHOT_FRAMES - ents[b + E_TIMER];
        int from = progress * 22;
        int to = from + 34;
        for (int corner = 0; corner < 4; corner++) {
            int cx = (corner & 1) == 0 ? 4 : WIDTH - 5;
            int cy = (corner & 2) == 0 ? HEADER + 4 : HEIGHT - 5;
            addLine(lines, cx + (tx - cx) * from / 100, cy + (ty - cy) * from / 100, cx + (tx - cx) * to / 100,
                    cy + (ty - cy) * to / 100, LASER);
        }
    }

    private static void drawCrosshair(short[] lines) {
        int x = crossX;
        int y = crossY;
        addLine(lines, x - 11, y, x - 4, y, CROSSHAIR);
        addLine(lines, x + 4, y, x + 11, y, CROSSHAIR);
        addLine(lines, x, y - 11, x, y - 4, CROSSHAIR);
        addLine(lines, x, y + 4, x, y + 11, CROSSHAIR);
    }

    private static int dirX(int k) {
        if (k == 0) {
            return 10;
        }
        if (k == 1 || k == 7) {
            return 7;
        }
        if (k == 3 || k == 5) {
            return -7;
        }
        if (k == 4) {
            return -10;
        }
        return 0;
    }

    private static int dirY(int k) {
        return dirX((k + 6) % 8);
    }

    // ---- Projection ----

    private static int projectX(int x, int z) {
        return clamp(CENTER_X + (x - camX) * FOCAL / z, -20000, 20000);
    }

    private static int projectY(int y, int z) {
        return clamp(CENTER_Y - (y - camY) * FOCAL / z, -20000, 20000);
    }

    /** Pixels spanned by {@code size} world units at depth {@code z}. */
    private static int scale(int size, int z) {
        return size * FOCAL / z;
    }

    /** A line of a flat object whose points all lie at depth {@code z}, around its projection (px, py). */
    private static void part(short[] lines, int px, int py, int z, int dx0, int dy0, int dx1, int dy1, int color) {
        addLine(lines, px + dx0 * FOCAL / z, py - dy0 * FOCAL / z, px + dx1 * FOCAL / z, py - dy1 * FOCAL / z, color);
    }

    /** A 3D line, clipped at the near plane and projected. */
    private static void line3(short[] lines, int x0, int y0, int z0, int x1, int y1, int z1, int color) {
        if (z0 < NEAR && z1 < NEAR) {
            return;
        }
        if (z0 < NEAR) {
            x0 = x0 + (x1 - x0) * (NEAR - z0) / (z1 - z0);
            y0 = y0 + (y1 - y0) * (NEAR - z0) / (z1 - z0);
            z0 = NEAR;
        } else if (z1 < NEAR) {
            x1 = x1 + (x0 - x1) * (NEAR - z1) / (z0 - z1);
            y1 = y1 + (y0 - y1) * (NEAR - z1) / (z0 - z1);
            z1 = NEAR;
        }
        addLine(lines, projectX(x0, z0), projectY(y0, z0), projectX(x1, z1), projectY(y1, z1), color);
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
                x = x0 + (x1 - x0) * (HEIGHT - 1 - y0) / (y1 - y0);
                y = HEIGHT - 1;
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
        } else if (y > HEIGHT - 1) {
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

    /** Whether entry {@code i} differs between the list on screen and the one just built. */
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
        TftTouchShield.fillRect(0, HEADER, WIDTH, HEIGHT - HEADER, SPACE);
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

    // ---- Interludes ----

    /**
     * The opening, after the film: "A long time ago in a galaxy far, far away....", the STAR WARS
     * logo receding into the distance until it is gone, then the first shot of the film.
     */
    private static void opening(short[] lines, byte[] letter) {
        TftTouchShield.fillScreen(SPACE);
        shown = 0;
        typeCentered(OPENING_LINE_1, 104, letter);
        typeCentered(OPENING_LINE_2, 120, letter);
        Delay.millis(900);
        TftTouchShield.fillScreen(SPACE);
        Delay.millis(300);
        for (int size = 5; size >= 1; size--) {
            TftTouchShield.fillRect(0, 60, WIDTH, 60, SPACE);
            showCentered("STAR WARS", 90 - 4 * size, size, TftTouchShield.YELLOW);
            Delay.millis(size == 5 ? 500 : 260);
        }
        TftTouchShield.fillRect(0, 60, WIDTH, 60, SPACE);
        Delay.millis(300);
        flyover(lines);
        TftTouchShield.fillScreen(SPACE);
        shown = 0;
        Delay.millis(250);
    }

    /**
     * The first shot of the film: above a planet's horizon a small rebel ship flees into the
     * distance, and the wedge of an Imperial Star Destroyer slides in overhead after it, firing.
     */
    private static void flyover(short[] lines) {
        TftTouchShield.fillScreen(SPACE);
        shown = 0;
        for (int f = 0; f < SCENE_FRAMES; f++) {
            built = 0;
            drawStars(lines);
            // The planet's horizon, an arc across the bottom of the screen.
            int previousX = 0;
            int previousY = 0;
            for (int a = -24; a <= 24; a = a + 4) {
                float radians = (float) Math.toRadians(a);
                int x = CENTER_X + Math.round((float) Math.sin(radians) * PLANET_RADIUS);
                int y = PLANET_Y - Math.round((float) Math.cos(radians) * PLANET_RADIUS);
                if (a > -24) {
                    addLine(lines, previousX, previousY, x, y, OPENING);
                }
                previousX = x;
                previousY = y;
            }
            // The rebel ship, running for the horizon and getting smaller.
            int runnerX = CENTER_X + 30 - f / 3;
            int runnerY = 80 + f * 3 / 2;
            int r = Math.max(1, 6 - f / 14);
            addLine(lines, runnerX - r, runnerY, runnerX + r, runnerY, RUNNER);
            addLine(lines, runnerX, runnerY - r, runnerX, runnerY + r / 2, RUNNER);
            // The Star Destroyer: a wedge whose point slides down from the top, its hull behind it.
            int apexX = CENTER_X + 10 - f / 4;
            int apexY = HEADER - 10 + f * 11 / 5;
            int span = 40 + f * 5;
            int back = HEADER - 40;
            addLine(lines, apexX, apexY, apexX - span, back, DESTROYER);
            addLine(lines, apexX, apexY, apexX + span, back, DESTROYER);
            addLine(lines, apexX, apexY, apexX, back, DESTROYER);
            for (int k = 1; k <= 3; k++) {
                int y = apexY - (apexY - back) * k / 4;
                int half = span * k / 4;
                addLine(lines, apexX - half, y, apexX + half, y, STRUCTURE);
            }
            // Laser bolts from its forward guns at the fleeing ship.
            if (f > 10 && f % 6 < 3) {
                int step = f % 6;
                int fromX = apexX + (runnerX - apexX) * (step * 30 + 10) / 100;
                int fromY = apexY + (runnerY - apexY) * (step * 30 + 10) / 100;
                int toX = apexX + (runnerX - apexX) * (step * 30 + 25) / 100;
                int toY = apexY + (runnerY - apexY) * (step * 30 + 25) / 100;
                addLine(lines, fromX, fromY, toX, toY, LASER);
            }
            present(lines);
            Delay.millis(30);
        }
        Delay.millis(400);
    }

    /** Types a line of the opening letter by letter, centered. */
    private static void typeCentered(String text, int y, byte[] letter) {
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(OPENING, SPACE);
        TftTouchShield.setCursor((WIDTH - text.length() * 6) / 2, y);
        for (int i = 0; i < text.length(); i++) {
            letter[0] = (byte) text.charAt(i);
            TftTouchShield.print(letter, 1);
            Delay.millis(35);
        }
    }

    /**
     * The title screen: yellow frames close in on "STAR WARS" as the logo settles into place, then
     * the TIE fighters.
     */
    private static void drawTitle(short[] lines) {
        int textLeft = (WIDTH - 9 * 24) / 2;
        for (int k = 10; k >= 0; k--) {
            int left = textLeft - 8 - k * (textLeft - 8) / 10;
            int top = 26 - k * 26 / 10;
            int right = WIDTH - 1 - left;
            int bottom = 74 + k * (HEIGHT - 75) / 10;
            TftTouchShield.drawRect(left, top, right - left + 1, bottom - top + 1, TftTouchShield.YELLOW);
            Delay.millis(45);
            TftTouchShield.drawRect(left, top, right - left + 1, bottom - top + 1, SPACE);
        }
        showCentered("STAR WARS", 34, 4, TftTouchShield.WHITE);
        Delay.millis(120);
        showCentered("STAR WARS", 34, 4, TftTouchShield.YELLOW);
        Delay.millis(300);
        phase = SPACE_PHASE;
        built = 0;
        drawStars(lines);
        drawTie(lines, projectX(0, 420), projectY(-10, 420), 420, false);
        drawTie(lines, projectX(-520, 1500), projectY(160, 1500), 1500, false);
        drawTie(lines, projectX(560, 1800), projectY(120, 1800), 1800, true);
        present(lines);
        showCentered("Drag to aim, tap to fire", 198, 1, TftTouchShield.WHITE);
        showCentered("Tap to start", 214, 1, TftTouchShield.CYAN);
    }

    private static void drawDeathStar(int color) {
        TftTouchShield.drawCircle(CENTER_X, CENTER_Y, 62, color);
        TftTouchShield.drawHorizontalLine(CENTER_X - 62, CENTER_Y + 4, 124, color);
        TftTouchShield.drawCircle(CENTER_X - 24, CENTER_Y - 24, 14, color);
    }

    private static void approachDeathStar() {
        clearView();
        for (int step = 0; step < 3; step++) {
            drawDeathStar(TftTouchShield.GRAY);
            Delay.millis(250);
            drawDeathStar(TftTouchShield.WHITE);
            Delay.millis(250);
        }
        showCentered("APPROACHING THE DEATH STAR", 212, 1, TftTouchShield.WHITE);
        Delay.millis(900);
    }

    private static void destroyDeathStar() {
        clearView();
        drawDeathStar(TftTouchShield.GRAY);
        Delay.millis(300);
        for (int r = 4; r < 150; r = r + 5) {
            int color = TftTouchShield.WHITE;
            if (r % 15 == 9) {
                color = TftTouchShield.YELLOW;
            } else if (r % 15 == 14) {
                color = TftTouchShield.ORANGE;
            }
            TftTouchShield.drawCircle(CENTER_X, CENTER_Y, r, color);
            Delay.millis(20);
        }
        int shieldBonus = shields * SHIELD_BONUS;
        boolean force = trenchShots == 1;
        score = score + shieldBonus;
        if (force) {
            score = score + FORCE_BONUS;
        }
        shields = Math.min(shields + 1, MAX_SHIELDS);
        drawHeader();
        clearView();
        showCentered("DEATH STAR DESTROYED", 80, 2, TftTouchShield.YELLOW);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, SPACE);
        TftTouchShield.setCursor(106, 120);
        TftTouchShield.print("SHIELD BONUS ");
        TftTouchShield.print(shieldBonus);
        if (force) {
            showCentered("THE FORCE IS WITH YOU  +50000", 140, 1, TftTouchShield.CYAN);
        }
        Delay.millis(2500);
    }

    // ---- Records ----

    private static int freeSlot(int[] ents) {
        for (int slot = 0; slot < ENTITIES; slot++) {
            if (ents[slot * E_STRIDE + E_TYPE] == T_NONE) {
                clearSlot(ents, slot);
                return slot;
            }
        }
        return -1;
    }

    private static void clearSlot(int[] ents, int slot) {
        for (int k = 0; k < E_STRIDE; k++) {
            ents[slot * E_STRIDE + k] = 0;
        }
    }

    private static int find(int[] ents, int type) {
        for (int slot = 0; slot < ENTITIES; slot++) {
            if (ents[slot * E_STRIDE + E_TYPE] == type) {
                return slot;
            }
        }
        return -1;
    }

    private static int count(int[] ents, int type) {
        int n = 0;
        for (int slot = 0; slot < ENTITIES; slot++) {
            if (ents[slot * E_STRIDE + E_TYPE] == type) {
                n = n + 1;
            }
        }
        return n;
    }

    private static void clear(int[] records, int length) {
        for (int i = 0; i < length; i++) {
            records[i] = 0;
        }
    }

    private static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(value, high));
    }

    // ---- Text ----

    private static void drawHeader() {
        TftTouchShield.fillRect(0, 0, WIDTH, HEADER - 1, SPACE);
        TftTouchShield.drawHorizontalLine(0, HEADER - 1, WIDTH, STRUCTURE);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.YELLOW, SPACE);
        TftTouchShield.setCursor(4, 2);
        TftTouchShield.print("SCORE ");
        TftTouchShield.print(score);
        TftTouchShield.setTextColor(TftTouchShield.CYAN, SPACE);
        TftTouchShield.setCursor(136, 2);
        TftTouchShield.print("HI ");
        TftTouchShield.print(best);
        TftTouchShield.setCursor(262, 2);
        TftTouchShield.print("WAVE ");
        TftTouchShield.print(wave);
        if (autopilot) {
            TftTouchShield.setTextColor(TftTouchShield.MAGENTA, SPACE);
            TftTouchShield.setCursor(226, 2);
            TftTouchShield.print("CPU");
        }
        int color = TftTouchShield.GREEN;
        if (shields == 0) {
            color = TftTouchShield.RED;
        } else if (shields <= 2) {
            color = TftTouchShield.YELLOW;
        }
        TftTouchShield.setTextColor(color, SPACE);
        TftTouchShield.setCursor(4, 11);
        TftTouchShield.print("SHIELDS ");
        TftTouchShield.print(shields);
        status = -1;
        drawStatus();
    }

    /** The phase's goal in the header's second row: fighters or towers left, or distance to the port. */
    private static void drawStatus() {
        int value;
        if (phase == SPACE_PHASE) {
            value = Math.max(0, killTarget - kills);
        } else if (phase == SURFACE_PHASE) {
            value = towersToSpawn;
        } else {
            value = Math.max(0, (trenchLength - travel) / 10);
        }
        if (value == status) {
            return;
        }
        status = value;
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, SPACE);
        TftTouchShield.setCursor(136, 11);
        if (phase == SPACE_PHASE) {
            TftTouchShield.print("TIE FIGHTERS LEFT ");
        } else if (phase == SURFACE_PHASE) {
            TftTouchShield.print("TOWERS AHEAD ");
        } else {
            TftTouchShield.print("EXHAUST PORT ");
        }
        TftTouchShield.print(value);
        TftTouchShield.print("   ");
    }

    private static void showCentered(String text, int y, int size, int color) {
        TftTouchShield.setTextSize(size);
        TftTouchShield.setTextColor(color, SPACE);
        TftTouchShield.setCursor((WIDTH - text.length() * 6 * size) / 2, y);
        TftTouchShield.print(text);
    }
}
