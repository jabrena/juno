package io.github.jabrena.juno.api.tft;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;

/**
 * Red Baron on the ELEGOO 2.8" TFT touch screen shield, after Atari's 1981 vector arcade game, in
 * landscape. You fly a First World War biplane seen from the cockpit, over a landscape ringed by
 * mountains, and every wave alternates two rounds:
 *
 * <ol>
 *   <li><b>Dogfight</b>: enemy biplanes circle ahead and make firing passes at you. Shoot down the
 *       number shown in the header. Halfway through, a blimp drifts by; it takes several hits and is
 *       worth a lot more.</li>
 *   <li><b>Ground attack</b>: fly low over enemy hangars and flak guns and strafe them. Your guns fire
 *       level, so you have to come down to hit anything on the ground, where the pyramids you fly
 *       past can be crashed into. Destroying every target pays a bonus.</li>
 * </ol>
 *
 * <p>The whole screen is a joystick: press anywhere and drag from that point. Left and right bank
 * the plane and turn it, as the horizon rolls; up and down climb and dive. Release to center the
 * stick. A quick tap fires both machine guns; their tracers converge on the sight in the middle of
 * the screen. You start with {@value #START_PLANES} planes and win another every
 * {@value #EXTRA_PLANE_SCORE} points; a hit from enemy fire or a crash costs one.
 *
 * <p>The game opens in the cockpit: "CONTACT!", the propeller is swung and spins up into a blur,
 * the plane races down the runway and pulls up into the sky, then the title is spelled out letter
 * by letter; after each game over it starts again from there. Tap to start, then choose who flies
 * the plane: <b>HUMAN</b> (you) or <b>CPU</b>, an autopilot that chases the nearest target, climbs
 * over the pyramids and fires when its aim, which wanders a little, is on target. Tap the header
 * during the game to switch between the two ({@code CPU} shows in the header).
 *
 * <p>Everything is drawn in vector style, like the arcade's: the world is kept relative to the
 * plane, so turning rotates it around you and flying moves it towards you; 3D points are
 * perspective-projected ({@code x' = cx + x·f/z}) and then rotated about the center of the view by
 * the bank angle, while the sight and the guns stay fixed. Each frame's lines go into one of two
 * display lists, and only lines that changed since the previous frame are erased and drawn again.
 */
@Board(ArduinoUnoQ.class)
public final class RedBaron {
    private static final int FRAME_MILLIS = 40;

    // Screen: landscape, a text header over the 3D view.
    private static final int WIDTH = 320;
    private static final int HEIGHT = 240;
    private static final int HEADER = 20;
    private static final int CENTER_X = 160;
    private static final int CENTER_Y = 132;

    // Camera: looks along +z; world z is the distance ahead of it, world y the height above ground.
    private static final int FOCAL = 160;
    private static final int NEAR = 30;
    private static final int FAR = 3000;
    private static final int FLIGHT_SPEED = 24;
    private static final int MIN_ALTITUDE = 40;
    private static final int MAX_ALTITUDE = 520;
    private static final int CLIMB = 6;
    private static final int MAX_BANK = 35;
    private static final int BANK_STEP = 3;
    /** Degrees of turn per frame for each degree of bank. */
    private static final float TURN_RATE = 0.06f;

    // Display lists: two of MAX_LINES lines (x0, y0, x1, y1, color), the one on screen and the next.
    private static final int MAX_LINES = 180;
    private static final int L_STRIDE = 5;
    private static final int LIST_SIZE = MAX_LINES * L_STRIDE;

    // Entities, in the plane's frame of reference.
    private static final int ENTITIES = 26;
    private static final int E_TYPE = 0;
    private static final int E_X = 1;
    private static final int E_Y = 2;
    private static final int E_Z = 3;
    private static final int E_VX = 4;
    private static final int E_VY = 5;
    private static final int E_VZ = 6;
    private static final int E_TIMER = 7;
    /** Direction of travel in degrees, clockwise from straight ahead (+z). */
    private static final int E_HEADING = 8;
    private static final int E_HP = 9;
    /** Enemy plane: 1 while on an attack run. Debris: its size. */
    private static final int E_MODE = 10;
    /** Enemy plane: its place in the formation ahead. Tracer: 1 when fired from the ground. */
    private static final int E_AUX = 11;
    private static final int E_STRIDE = 12;

    private static final int T_NONE = 0;
    private static final int T_PLANE = 1;
    private static final int T_BLIMP = 2;
    private static final int T_FALLING = 3;
    private static final int T_BULLET = 4;
    private static final int T_TRACER = 5;
    private static final int T_HANGAR = 6;
    private static final int T_FLAK = 7;
    private static final int T_PYRAMID = 8;
    private static final int T_MARKER = 9;
    private static final int T_DEBRIS = 10;

    // Rounds of a wave.
    private static final int DOGFIGHT = 0;
    private static final int GROUND_ATTACK = 1;

    // Guns: twin tracers from under the sight, converging on it CONVERGE units ahead.
    private static final int GUN_OFFSET_X = 34;
    private static final int GUN_OFFSET_Y = 26;
    private static final int CONVERGE = 560;
    private static final int BULLET_SPEED = 80;
    private static final int BULLET_FRAMES = 12;
    private static final int MAX_BULLETS = 4;
    /** A touch this short (in frames) that barely moved is a tap, and fires on release. */
    private static final int TAP_FRAMES = 7;
    private static final int DRAG_PIXELS = 14;
    /** Pixels of drag for full stick. */
    private static final int STICK_TRAVEL = 70;

    // Enemies.
    private static final int PLANE_SPEED = 30;
    private static final int PLANE_RADIUS = 60;
    private static final int BLIMP_RADIUS = 130;
    private static final int BLIMP_HITS = 4;
    private static final int TRACER_SPEED = 36;
    /** How close to the plane an enemy tracer must arrive to hit it. */
    private static final int TRACER_REACH = 40;
    /** How far off an enemy's aim may be, in world units, so that not every burst is deadly. */
    private static final int AIM_SCATTER = 70;
    private static final int MAX_TRACERS = 3;
    private static final int GROUND_RADIUS = 70;
    private static final int HANGAR_HEIGHT = 70;
    private static final int FLAK_HEIGHT = 26;
    private static final int PYRAMID_HEIGHT = 110;
    private static final int MARKERS = 8;

    // Player.
    private static final int START_PLANES = 3;
    private static final int EXTRA_PLANE_SCORE = 20000;

    // Scoring.
    private static final int PLANE_POINTS = 1000;
    private static final int BLIMP_POINTS = 5000;
    private static final int HANGAR_POINTS = 500;
    private static final int FLAK_POINTS = 800;
    private static final int ALL_TARGETS_BONUS = 5000;

    // Colors: white vectors like the arcade, with a few accents.
    private static final int SKY = TftTouchShield.BLACK;
    private static final int LINE = TftTouchShield.WHITE;
    private static final int HORIZON = 0x8410;
    private static final int MOUNTAIN = 0x5AEB;
    private static final int GROUND_MARK = 0x4208;
    private static final int ENEMY = TftTouchShield.WHITE;
    private static final int BLIMP = TftTouchShield.CYAN;
    private static final int TARGET = TftTouchShield.YELLOW;
    private static final int OBSTACLE = 0x8C51;
    private static final int BULLET = TftTouchShield.YELLOW;
    private static final int TRACER = TftTouchShield.RED;
    private static final int COCKPIT = 0xAD55;
    private static final int SIGHT = TftTouchShield.GREEN;
    private static final int FIRE = TftTouchShield.ORANGE;
    private static final int PROPELLER = 0xC618;
    private static final int RUNWAY = 0x8410;

    // The opening: propeller spin-up, then the take-off run.
    private static final int SPIN_FRAMES = 60;
    private static final int PROP_LENGTH = 78;
    private static final int TAKEOFF_FRAMES = 56;
    private static final int RUNWAY_HALF_WIDTH = 60;
    private static final int EYE_HEIGHT = 30;
    private static final String TITLE = "RED BARON";

    // The HUMAN and CPU buttons of the pilot screen.
    private static final int CHOICE_X = 20;
    private static final int CHOICE_Y = 104;
    private static final int CHOICE_WIDTH = 130;
    private static final int CHOICE_HEIGHT = 72;
    private static final int CHOICE_GAP = 20;
    private static final int CHOICE = 0x2124;
    private static final int CHOICE_CHOSEN = 0x7800;

    // The CPU pilot: how often it may fire, and how far and how often its aim wanders off target.
    private static final int CPU_FIRE_FRAMES = 3;
    private static final int CPU_WOBBLE = 60;
    private static final int CPU_WOBBLE_FRAMES = 25;
    /** How near an enemy tracer must be, and how close to the line of flight, before the CPU dodges. */
    private static final int CPU_EVADE_RANGE = 700;
    private static final int CPU_EVADE_WIDTH = 200;

    private static int score;
    private static int best;
    private static int planes;
    private static int nextExtraPlane;
    private static int wave;
    private static int round;
    private static int frame;
    private static boolean dead;
    private static boolean shotDown;

    // Flight.
    private static int altitude;
    private static float bank;
    private static float heading;
    private static int bankCos = 1024;
    private static int bankSin;

    // Virtual joystick.
    private static int stickX;
    private static int stickY;
    private static boolean touching;
    private static boolean dragged;
    private static int touchFrames;
    private static int releaseMisses;
    private static int originX;
    private static int originY;
    private static boolean autopilot;
    private static boolean headerPressed;
    private static int aimWobbleX;
    private static int aimWobbleY;

    // Display list state.
    private static int front;
    private static int shown;
    private static int built;

    // Round state.
    private static int spawnCountdown;
    private static int kills;
    private static int killTarget;
    private static boolean blimpSpawned;
    private static int targetsToSpawn;
    private static int targetsTotal;
    private static int targetsHit;
    private static int status;

    private RedBaron() {
    }

    public static void main(String[] args) {
        short[] lines = new short[2 * LIST_SIZE];
        int[] ents = new int[ENTITIES * E_STRIDE];
        byte[] letter = new byte[1];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);

        while (true) {
            opening(lines);
            drawTitle(lines, letter);
            waitForTap();
            Random.seed(Clock.micros());
            choosePilot();
            score = 0;
            planes = START_PLANES;
            nextExtraPlane = EXTRA_PLANE_SCORE;
            wave = 1;
            boolean alive = true;
            while (alive) {
                alive = runRound(DOGFIGHT, lines, ents) && runRound(GROUND_ATTACK, lines, ents);
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

    /** Runs one round frame by frame; returns false when the last plane is lost. */
    private static boolean runRound(int which, short[] lines, int[] ents) {
        startRound(which, ents);
        drawHeader();
        clearView();
        if (which == DOGFIGHT) {
            showCentered("WAVE", 84, 3, TftTouchShield.YELLOW);
            TftTouchShield.setCursor(wave < 10 ? 151 : 142, 116);
            TftTouchShield.print(wave);
            showCentered("DOGFIGHT", 152, 2, TftTouchShield.WHITE);
        } else {
            showCentered("GROUND ATTACK", 100, 2, TftTouchShield.YELLOW);
            showCentered("Fly low and strafe the targets", 134, 1, TftTouchShield.WHITE);
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
            fly(ents);
            spawn(ents);
            step(ents);
            resolveBullets(ents);
            render(lines, ents);
            if (shotDown) {
                if (!loseAPlane(ents)) {
                    return false;
                }
                next = Clock.millis();
            }
            if (roundOver(ents)) {
                if (which == GROUND_ATTACK && targetsHit == targetsTotal) {
                    score = score + ALL_TARGETS_BONUS;
                    drawHeader();
                    clearView();
                    showCentered("ALL TARGETS DESTROYED", 104, 1, TARGET);
                    showCentered("BONUS 5000", 122, 1, TftTouchShield.WHITE);
                    Delay.millis(1500);
                }
                return true;
            }
            if ((frame & 7) == 0) {
                drawStatus();
            }
        }
    }

    private static void startRound(int which, int[] ents) {
        round = which;
        dead = false;
        shotDown = false;
        clear(ents, ENTITIES * E_STRIDE);
        altitude = which == DOGFIGHT ? 300 : 200;
        bank = 0;
        spawnCountdown = 20;
        stickX = 0;
        stickY = 0;
        touching = false;
        if (which == DOGFIGHT) {
            kills = 0;
            killTarget = Math.min(3 + wave, 10);
            blimpSpawned = false;
        } else {
            targetsTotal = Math.min(4 + wave, 12);
            targetsToSpawn = targetsTotal;
            targetsHit = 0;
        }
        for (int i = 0; i < MARKERS; i++) {
            int slot = freeSlot(ents);
            int b = slot * E_STRIDE;
            ents[b + E_TYPE] = T_MARKER;
            ents[b + E_X] = Random.nextInt(-1600, 1601);
            ents[b + E_Z] = NEAR + 200 + i * (FAR - 400) / MARKERS;
        }
    }

    private static boolean roundOver(int[] ents) {
        if (round == DOGFIGHT) {
            return kills >= killTarget && count(ents, T_PLANE) + count(ents, T_FALLING) + count(ents, T_TRACER)
                    + count(ents, T_DEBRIS) == 0;
        }
        return targetsToSpawn == 0 && count(ents, T_HANGAR) + count(ents, T_FLAK) + count(ents, T_TRACER)
                + count(ents, T_DEBRIS) == 0;
    }

    /** After a hit or a crash: one plane fewer, and the round goes on. Returns false at the last. */
    private static boolean loseAPlane(int[] ents) {
        shotDown = false;
        planes = planes - 1;
        TftTouchShield.fillRect(0, HEADER, WIDTH, HEIGHT - HEADER, TftTouchShield.RED);
        Delay.millis(80);
        clearView();
        drawHeader();
        if (planes == 0) {
            dead = true;
            return false;
        }
        showCentered("SHOT DOWN", 104, 2, TftTouchShield.RED);
        showCentered("Planes left", 130, 1, TftTouchShield.WHITE);
        TftTouchShield.setCursor(158, 146);
        TftTouchShield.print(planes);
        Delay.millis(1300);
        clearView();
        // A fresh start: enemy fire cleared, level flight, obstacles out of the way.
        for (int slot = 0; slot < ENTITIES; slot++) {
            int b = slot * E_STRIDE;
            int type = ents[b + E_TYPE];
            boolean close = ents[b + E_Z] < 400;
            if (type == T_TRACER || type == T_BULLET || (close && (type == T_PYRAMID || type == T_HANGAR
                    || type == T_FLAK))) {
                ents[b + E_TYPE] = T_NONE;
            }
        }
        bank = 0;
        altitude = Math.max(altitude, round == DOGFIGHT ? 300 : 200);
        return true;
    }

    private static void addScore(int points) {
        score = score + points;
        if (score >= nextExtraPlane) {
            planes = planes + 1;
            nextExtraPlane = nextExtraPlane + EXTRA_PLANE_SCORE;
        }
        drawHeader();
    }

    // ---- Input ----

    /**
     * The touch is a joystick centered where it started; a short tap that barely moved fires.
     * Tapping the header hands the plane to the CPU or back.
     */
    private static void handleTouch(int[] ents) {
        boolean down = TftTouchShield.readTouch();
        if (down && TftTouchShield.touchY() < HEADER) {
            if (!headerPressed) {
                autopilot = !autopilot;
                touching = false;
                stickX = 0;
                stickY = 0;
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
            int x = TftTouchShield.touchX();
            int y = TftTouchShield.touchY();
            if (!touching) {
                touching = true;
                dragged = false;
                touchFrames = 0;
                originX = x;
                originY = y;
            } else {
                touchFrames = touchFrames + 1;
                if (Math.abs(x - originX) + Math.abs(y - originY) > DRAG_PIXELS) {
                    dragged = true;
                }
            }
            stickX = clamp((x - originX) * 100 / STICK_TRAVEL, -100, 100);
            // Drag up to climb.
            stickY = clamp((originY - y) * 100 / STICK_TRAVEL, -100, 100);
            releaseMisses = 0;
        } else if (touching) {
            // Two frames without contact, so a flicker of the resistive panel is not a release.
            releaseMisses = releaseMisses + 1;
            if (releaseMisses >= 2) {
                touching = false;
                stickX = 0;
                stickY = 0;
                if (touchFrames <= TAP_FRAMES && !dragged) {
                    fire(ents);
                }
            }
        }
    }

    /** Fires both guns: two tracers from under the sight that converge on it ahead. */
    private static void fire(int[] ents) {
        if (count(ents, T_BULLET) + 2 > MAX_BULLETS) {
            return;
        }
        for (int side = -1; side <= 1; side = side + 2) {
            int slot = freeSlot(ents);
            if (slot < 0) {
                return;
            }
            int b = slot * E_STRIDE;
            ents[b + E_TYPE] = T_BULLET;
            ents[b + E_X] = side * GUN_OFFSET_X;
            ents[b + E_Y] = altitude - GUN_OFFSET_Y;
            ents[b + E_Z] = NEAR;
            ents[b + E_VX] = -side * GUN_OFFSET_X * BULLET_SPEED / CONVERGE;
            ents[b + E_VY] = GUN_OFFSET_Y * BULLET_SPEED / CONVERGE;
            ents[b + E_VZ] = BULLET_SPEED;
            ents[b + E_TIMER] = BULLET_FRAMES;
        }
    }

    /** The pilot screen: waits for a tap on HUMAN or CPU. */
    private static void choosePilot() {
        TftTouchShield.fillScreen(SKY);
        showCentered("CHOOSE PILOT", 36, 3, TftTouchShield.RED);
        showCentered("Who flies the biplane?", 74, 1, TftTouchShield.WHITE);
        drawChoice(0, "HUMAN", "You fly and fire", false);
        drawChoice(1, "CPU", "Autopilot plays", false);
        showCentered("Tap the header in game to switch", 206, 1, HORIZON);
        int choice = -1;
        while (choice < 0) {
            if (TftTouchShield.readTouch()) {
                choice = choiceAt(TftTouchShield.touchX(), TftTouchShield.touchY());
            }
            Delay.millis(10);
        }
        autopilot = choice == 1;
        touching = false;
        drawChoice(choice, choice == 0 ? "HUMAN" : "CPU", choice == 0 ? "You fly and fire" : "Autopilot plays", true);
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
        int color = chosen ? CHOICE_CHOSEN : CHOICE;
        TftTouchShield.fillRect(x, CHOICE_Y, CHOICE_WIDTH, CHOICE_HEIGHT, color);
        TftTouchShield.drawRect(x, CHOICE_Y, CHOICE_WIDTH, CHOICE_HEIGHT, chosen ? TftTouchShield.WHITE : HORIZON);
        TftTouchShield.setTextSize(3);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, color);
        TftTouchShield.setCursor(x + (CHOICE_WIDTH - label.length() * 18) / 2, CHOICE_Y + 16);
        TftTouchShield.print(label);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.YELLOW, color);
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
     * The CPU at the stick: it steers towards the nearest target (enemy planes and the blimp in a
     * dogfight, hangars and flak on the ground, where it flies low enough for its guns to reach),
     * breaks away from enemy tracers closing in, climbs over pyramids in its way, and fires when the
     * target is in the sight. Its aim wanders by up to {@value #CPU_WOBBLE} units, a new offset every
     * {@value #CPU_WOBBLE_FRAMES} frames, so it misses now and then like a person would.
     */
    private static void flyAutopilot(int[] ents) {
        if (frame % CPU_WOBBLE_FRAMES == 0) {
            aimWobbleX = Random.nextInt(-CPU_WOBBLE, CPU_WOBBLE + 1);
            aimWobbleY = Random.nextInt(-CPU_WOBBLE / 2, CPU_WOBBLE / 2 + 1);
        }
        boolean ground = round == GROUND_ATTACK;
        int target = -1;
        int nearest = 1 << 30;
        for (int slot = 0; slot < ENTITIES; slot++) {
            int type = ents[slot * E_STRIDE + E_TYPE];
            int z = ents[slot * E_STRIDE + E_Z];
            boolean wanted = ground ? type == T_HANGAR || type == T_FLAK : type == T_PLANE || type == T_BLIMP;
            if (wanted && z > 60 && z < nearest) {
                target = slot;
                nearest = z;
            }
        }
        stickX = 0;
        stickY = 0;
        if (target >= 0) {
            int b = target * E_STRIDE;
            int x = ents[b + E_X] + aimWobbleX;
            int y = (ground ? HANGAR_HEIGHT / 2 : ents[b + E_Y]) + GUN_OFFSET_Y + aimWobbleY;
            stickX = clamp(x * 400 / Math.max(nearest, 1), -100, 100);
            stickY = clamp((y - altitude) * 4, -100, 100);
            if (Math.abs(x) < 40 + nearest / 12 && Math.abs(y - altitude) < 50 && nearest < 900
                    && frame % CPU_FIRE_FRAMES == 0) {
                fire(ents);
            }
        }
        // Break away from enemy fire closing in: hard over, and up or down, away from the tracer.
        for (int slot = 0; slot < ENTITIES; slot++) {
            int b = slot * E_STRIDE;
            if (ents[b + E_TYPE] == T_TRACER && ents[b + E_Z] < CPU_EVADE_RANGE
                    && Math.abs(ents[b + E_X]) < CPU_EVADE_WIDTH) {
                stickX = ents[b + E_X] >= 0 ? -100 : 100;
                stickY = ents[b + E_Y] >= altitude ? -60 : 60;
            }
        }
        // Climb over pyramids in the way.
        for (int slot = 0; slot < ENTITIES; slot++) {
            int b = slot * E_STRIDE;
            if (ents[b + E_TYPE] == T_PYRAMID && ents[b + E_Z] < 500 && Math.abs(ents[b + E_X]) < 120
                    && altitude < PYRAMID_HEIGHT + 40) {
                stickY = 100;
            }
        }
    }

    // ---- Flight ----

    /**
     * Banks towards the stick, turns with the bank (the world rotates around the plane), climbs or
     * dives, and flies forward (the world moves towards the plane).
     */
    private static void fly(int[] ents) {
        float target = stickX * MAX_BANK / 100.0f;
        bank = bank + Math.max(-BANK_STEP, Math.min(BANK_STEP, target - bank));
        float radians = (float) Math.toRadians(bank);
        bankCos = Math.round((float) Math.cos(radians) * 1024);
        bankSin = Math.round((float) Math.sin(radians) * 1024);
        altitude = clamp(altitude + stickY * CLIMB / 100, MIN_ALTITUDE, MAX_ALTITUDE);

        float turn = bank * TURN_RATE;
        heading = heading + turn;
        if (heading >= 360) {
            heading = heading - 360;
        } else if (heading < 0) {
            heading = heading + 360;
        }
        if (turn != 0) {
            turnWorld(ents, turn);
        }
    }

    /** Rotates everything around the plane by {@code degrees} (a right turn swings the world left). */
    private static void turnWorld(int[] ents, float degrees) {
        float radians = (float) Math.toRadians(degrees);
        int c = Math.round((float) Math.cos(radians) * 4096);
        int s = Math.round((float) Math.sin(radians) * 4096);
        int turn = Math.round(degrees);
        for (int slot = 0; slot < ENTITIES; slot++) {
            int b = slot * E_STRIDE;
            if (ents[b + E_TYPE] == T_NONE) {
                continue;
            }
            int x = ents[b + E_X];
            int z = ents[b + E_Z];
            ents[b + E_X] = (x * c - z * s) / 4096;
            ents[b + E_Z] = (x * s + z * c) / 4096;
            int vx = ents[b + E_VX];
            int vz = ents[b + E_VZ];
            ents[b + E_VX] = (vx * c - vz * s) / 4096;
            ents[b + E_VZ] = (vx * s + vz * c) / 4096;
            ents[b + E_HEADING] = ents[b + E_HEADING] - turn;
        }
    }

    // ---- Simulation ----

    private static void spawn(int[] ents) {
        spawnCountdown = spawnCountdown - 1;
        if (spawnCountdown > 0) {
            return;
        }
        if (round == DOGFIGHT) {
            int flying = count(ents, T_PLANE) + count(ents, T_FALLING);
            int atOnce = Math.min(1 + (wave + 1) / 2, 3);
            if (kills + flying < killTarget && count(ents, T_PLANE) < atOnce) {
                spawnPlane(ents);
                spawnCountdown = 50;
            } else if (!blimpSpawned && kills * 2 >= killTarget) {
                spawnBlimp(ents);
                spawnCountdown = 60;
            } else {
                spawnCountdown = 10;
            }
        } else {
            if (targetsToSpawn > 0) {
                spawnGroundTarget(ents);
                targetsToSpawn = targetsToSpawn - 1;
                spawnCountdown = Math.max(26, 44 - 2 * wave);
            } else {
                spawnCountdown = 20;
            }
            if (Random.nextInt(3) == 0) {
                int slot = freeSlot(ents);
                if (slot >= 0) {
                    int b = slot * E_STRIDE;
                    ents[b + E_TYPE] = T_PYRAMID;
                    ents[b + E_X] = Random.nextInt(-900, 901);
                    ents[b + E_Z] = FAR - 200;
                }
            }
        }
    }

    private static void spawnPlane(int[] ents) {
        int slot = freeSlot(ents);
        if (slot < 0) {
            return;
        }
        int b = slot * E_STRIDE;
        ents[b + E_TYPE] = T_PLANE;
        ents[b + E_X] = Random.nextInt(-1400, 1401);
        ents[b + E_Y] = Random.nextInt(200, 480);
        ents[b + E_Z] = FAR - 400;
        ents[b + E_HEADING] = 180;
        ents[b + E_VZ] = -PLANE_SPEED;
        ents[b + E_TIMER] = attackDelay();
        ents[b + E_AUX] = Random.nextInt(8);
    }

    private static void spawnBlimp(int[] ents) {
        int slot = freeSlot(ents);
        if (slot < 0) {
            return;
        }
        blimpSpawned = true;
        int b = slot * E_STRIDE;
        int side = Random.nextInt(2) == 0 ? -1 : 1;
        ents[b + E_TYPE] = T_BLIMP;
        ents[b + E_X] = side * 900;
        ents[b + E_Y] = 420;
        ents[b + E_Z] = 2000;
        ents[b + E_VX] = -side * 5;
        ents[b + E_HEADING] = -side * 90;
        ents[b + E_HP] = BLIMP_HITS;
    }

    private static void spawnGroundTarget(int[] ents) {
        int slot = freeSlot(ents);
        if (slot < 0) {
            return;
        }
        int b = slot * E_STRIDE;
        ents[b + E_TYPE] = Random.nextInt(3) == 0 ? T_FLAK : T_HANGAR;
        ents[b + E_X] = Random.nextInt(-500, 501);
        ents[b + E_Z] = FAR - 100;
        ents[b + E_TIMER] = 30 + Random.nextInt(40);
    }

    private static void step(int[] ents) {
        for (int slot = 0; slot < ENTITIES; slot++) {
            int b = slot * E_STRIDE;
            int type = ents[b + E_TYPE];
            if (type == T_NONE) {
                continue;
            }
            // Everything moves by its own velocity, and towards the plane at its speed.
            ents[b + E_X] = ents[b + E_X] + ents[b + E_VX];
            ents[b + E_Y] = ents[b + E_Y] + ents[b + E_VY];
            ents[b + E_Z] = ents[b + E_Z] + ents[b + E_VZ] - (type == T_BULLET ? 0 : FLIGHT_SPEED);
            int z = ents[b + E_Z];
            if (type == T_PLANE) {
                flyEnemy(ents, b);
            } else if (type == T_FALLING) {
                ents[b + E_HEADING] = ents[b + E_HEADING] + 17;
                if (ents[b + E_Y] <= 0 || z < NEAR) {
                    explode(ents, slot, 60);
                }
            } else if (type == T_BLIMP) {
                if (z < NEAR || Math.abs(ents[b + E_X]) > 2600) {
                    ents[b + E_TYPE] = T_NONE;
                }
            } else if (type == T_BULLET) {
                ents[b + E_TIMER] = ents[b + E_TIMER] - 1;
                if (ents[b + E_TIMER] <= 0) {
                    ents[b + E_TYPE] = T_NONE;
                }
            } else if (type == T_TRACER) {
                if (z <= NEAR) {
                    ents[b + E_TYPE] = T_NONE;
                    if (Math.abs(ents[b + E_X]) < TRACER_REACH
                            && Math.abs(ents[b + E_Y] - altitude) < TRACER_REACH) {
                        shotDown = true;
                    }
                }
            } else if (type == T_HANGAR || type == T_FLAK || type == T_PYRAMID) {
                if (z < NEAR) {
                    ents[b + E_TYPE] = T_NONE;
                    int height = type == T_PYRAMID ? PYRAMID_HEIGHT : type == T_FLAK ? FLAK_HEIGHT : HANGAR_HEIGHT;
                    if (Math.abs(ents[b + E_X]) < GROUND_RADIUS && altitude < height + 20) {
                        shotDown = true;
                    }
                } else if (type == T_FLAK) {
                    flakFires(ents, b);
                }
            } else if (type == T_MARKER) {
                if (z < NEAR || z > FAR || Math.abs(ents[b + E_X]) > 2000) {
                    ents[b + E_X] = Random.nextInt(-1600, 1601);
                    ents[b + E_Z] = FAR - Random.nextInt(300);
                }
            } else if (type == T_DEBRIS) {
                ents[b + E_TIMER] = ents[b + E_TIMER] - 1;
                if (ents[b + E_TIMER] <= 0 || z < NEAR) {
                    ents[b + E_TYPE] = T_NONE;
                }
            }
        }
    }

    /**
     * An enemy biplane holds a place in a loose formation ahead of you, weaving, until its timer runs
     * out; then it dives at you, firing when it has you in its sights, and passes. Once behind you it
     * comes round again from far ahead.
     */
    private static void flyEnemy(int[] ents, int b) {
        int x = ents[b + E_X];
        int y = ents[b + E_Y];
        int z = ents[b + E_Z];
        if (z < -300 || z > FAR + 600 || Math.abs(x) > 3000) {
            ents[b + E_X] = Random.nextInt(-1400, 1401);
            ents[b + E_Y] = Random.nextInt(200, 480);
            ents[b + E_Z] = FAR - 400;
            ents[b + E_VX] = 0;
            ents[b + E_VY] = 0;
            ents[b + E_VZ] = -PLANE_SPEED;
            ents[b + E_MODE] = 0;
            ents[b + E_TIMER] = attackDelay();
            return;
        }
        int targetX;
        int targetY;
        int targetZ;
        if (ents[b + E_MODE] == 1) {
            // Attack run: straight at you, then on past.
            targetX = 0;
            targetY = altitude;
            targetZ = -400;
            if (ents[b + E_TIMER] > 0) {
                ents[b + E_TIMER] = ents[b + E_TIMER] - 1;
            } else if (z > 200 && z < 1100 && Math.abs(x) < z / 4 && Math.abs(y - altitude) < z / 4) {
                enemyFires(ents, x, y, z, false);
                ents[b + E_TIMER] = 30;
            }
        } else {
            int place = ents[b + E_AUX];
            float t = (frame + place * 40) * 0.035f;
            targetX = (place - 4) * 180 + Math.round((float) Math.sin(t) * 260);
            targetY = 260 + Math.round((float) Math.cos(t * 0.7f) * 110);
            targetZ = 900 + place * 60;
            ents[b + E_TIMER] = ents[b + E_TIMER] - 1;
            if (ents[b + E_TIMER] <= 0) {
                ents[b + E_MODE] = 1;
                ents[b + E_TIMER] = 10;
            }
        }
        // Steer: bend the velocity towards the target at the plane's speed (plus the camera's, so it
        // can keep station ahead of you).
        int dx = targetX - x;
        int dy = targetY - y;
        int dz = targetZ - z;
        float length = (float) Math.sqrt((float) dx * dx + (float) dy * dy + (float) dz * dz);
        if (length > 1) {
            float speed = PLANE_SPEED + FLIGHT_SPEED;
            int wantX = Math.round(dx * speed / length);
            int wantY = Math.round(dy * speed / length);
            int wantZ = Math.round(dz * speed / length) + FLIGHT_SPEED;
            ents[b + E_VX] = ents[b + E_VX] + (wantX - ents[b + E_VX]) / 6;
            ents[b + E_VY] = ents[b + E_VY] + (wantY - ents[b + E_VY]) / 6;
            ents[b + E_VZ] = ents[b + E_VZ] + (wantZ - ents[b + E_VZ]) / 6;
        }
        // Its heading follows its motion through the air (its velocity relative to the ground).
        int airX = ents[b + E_VX];
        int airZ = ents[b + E_VZ] - FLIGHT_SPEED;
        if (airX != 0 || airZ != 0) {
            ents[b + E_HEADING] = Math.round((float) Math.toDegrees(Math.atan2(airX, airZ)));
        }
    }

    /** Frames an enemy biplane keeps station before its next attack run; shorter in later waves. */
    private static int attackDelay() {
        return Math.max(60, 150 - 10 * wave) + Random.nextInt(100);
    }

    private static void flakFires(int[] ents, int b) {
        ents[b + E_TIMER] = ents[b + E_TIMER] - 1;
        int z = ents[b + E_Z];
        if (ents[b + E_TIMER] <= 0 && z > 450 && z < 1600) {
            enemyFires(ents, ents[b + E_X], FLAK_HEIGHT, z, true);
            ents[b + E_TIMER] = Math.max(34, 70 - 4 * wave);
        }
    }

    /** A tracer from (x, y, z) aimed, give or take {@link #AIM_SCATTER}, at where the plane is now. */
    private static void enemyFires(int[] ents, int x, int y, int z, boolean fromGround) {
        if (count(ents, T_TRACER) >= MAX_TRACERS) {
            return;
        }
        int slot = freeSlot(ents);
        if (slot < 0) {
            return;
        }
        int b = slot * E_STRIDE;
        int dx = Random.nextInt(-AIM_SCATTER, AIM_SCATTER + 1) - x;
        int dy = altitude + Random.nextInt(-AIM_SCATTER, AIM_SCATTER + 1) - y;
        int dz = NEAR - z;
        float length = (float) Math.sqrt((float) dx * dx + (float) dy * dy + (float) dz * dz);
        ents[b + E_TYPE] = T_TRACER;
        ents[b + E_X] = x;
        ents[b + E_Y] = y;
        ents[b + E_Z] = z;
        // The plane flies into the tracer too, so aim for the relative closing speed.
        float speed = TRACER_SPEED;
        ents[b + E_VX] = Math.round(dx * speed / length);
        ents[b + E_VY] = Math.round(dy * speed / length);
        ents[b + E_VZ] = Math.round(dz * speed / length) + FLIGHT_SPEED;
        ents[b + E_AUX] = fromGround ? 1 : 0;
    }

    /** Bullets that reach a target this frame hit it. */
    private static void resolveBullets(int[] ents) {
        for (int slot = 0; slot < ENTITIES; slot++) {
            int b = slot * E_STRIDE;
            if (ents[b + E_TYPE] != T_BULLET) {
                continue;
            }
            int target = targetHitBy(ents, ents[b + E_X], ents[b + E_Y], ents[b + E_Z]);
            if (target >= 0) {
                ents[b + E_TYPE] = T_NONE;
                hit(ents, target);
            }
        }
    }

    /** The target a bullet at (x, y, z) is inside, or -1. */
    private static int targetHitBy(int[] ents, int x, int y, int z) {
        for (int slot = 0; slot < ENTITIES; slot++) {
            int b = slot * E_STRIDE;
            int type = ents[b + E_TYPE];
            int radius;
            int middle = ents[b + E_Y];
            if (type == T_PLANE) {
                radius = PLANE_RADIUS;
            } else if (type == T_BLIMP) {
                radius = BLIMP_RADIUS;
            } else if (type == T_HANGAR || type == T_FLAK) {
                radius = GROUND_RADIUS;
                middle = HANGAR_HEIGHT / 2;
            } else {
                continue;
            }
            // The bullet moves BULLET_SPEED a frame, so allow for that much depth.
            if (Math.abs(x - ents[b + E_X]) < radius && Math.abs(y - middle) < radius
                    && Math.abs(z - ents[b + E_Z]) < radius + BULLET_SPEED / 2) {
                return slot;
            }
        }
        return -1;
    }

    private static void hit(int[] ents, int slot) {
        int b = slot * E_STRIDE;
        int type = ents[b + E_TYPE];
        if (type == T_PLANE) {
            // Shot down: it spins and falls, and explodes when it reaches the ground.
            ents[b + E_TYPE] = T_FALLING;
            ents[b + E_VX] = ents[b + E_VX] / 2;
            ents[b + E_VY] = -12;
            ents[b + E_VZ] = FLIGHT_SPEED;
            kills = kills + 1;
            addScore(PLANE_POINTS);
        } else if (type == T_BLIMP) {
            ents[b + E_HP] = ents[b + E_HP] - 1;
            ents[b + E_TIMER] = 4;
            if (ents[b + E_HP] <= 0) {
                explode(ents, slot, 140);
                addScore(BLIMP_POINTS);
            }
        } else if (type == T_HANGAR || type == T_FLAK) {
            targetsHit = targetsHit + 1;
            explode(ents, slot, 70);
            addScore(type == T_FLAK ? FLAK_POINTS : HANGAR_POINTS);
        }
    }

    /** Turns the entity in {@code slot} into a burst of debris of the given size. */
    private static void explode(int[] ents, int slot, int size) {
        int b = slot * E_STRIDE;
        ents[b + E_TYPE] = T_DEBRIS;
        ents[b + E_VX] = 0;
        ents[b + E_VY] = 0;
        ents[b + E_VZ] = 0;
        ents[b + E_TIMER] = 10;
        ents[b + E_MODE] = size;
    }

    // ---- Rendering ----

    private static void render(short[] lines, int[] ents) {
        built = 0;
        drawLandscape(lines);
        // Farthest first, so what is near is drawn last.
        for (int pass = 0; pass < 2; pass++) {
            for (int slot = 0; slot < ENTITIES; slot++) {
                int b = slot * E_STRIDE;
                int type = ents[b + E_TYPE];
                boolean far = ents[b + E_Z] > FAR / 3;
                if (type != T_NONE && far == (pass == 0)) {
                    drawEntity(lines, ents, slot);
                }
            }
        }
        drawCockpit(lines);
        present(lines);
    }

    /** The horizon and the ring of mountains, which scroll with the heading and roll with the bank. */
    private static void drawLandscape(short[] lines) {
        worldLine(lines, CENTER_X - 400, CENTER_Y, CENTER_X + 400, CENTER_Y, HORIZON);
        int previousX = 0;
        int previousY = 0;
        boolean previousVisible = false;
        for (int i = 0; i <= 24; i++) {
            float relative = i * 15 - heading;
            while (relative > 180) {
                relative = relative - 360;
            }
            while (relative < -180) {
                relative = relative + 360;
            }
            boolean visible = relative > -70 && relative < 70;
            int x = 0;
            int y = 0;
            if (visible) {
                x = CENTER_X + Math.round(FOCAL * (float) Math.tan(Math.toRadians(relative)));
                y = CENTER_Y - mountainHeight(i % 24);
                if (previousVisible) {
                    worldLine(lines, previousX, previousY, x, y, MOUNTAIN);
                }
            }
            previousX = x;
            previousY = y;
            previousVisible = visible;
        }
    }

    /** Height in pixels of the mountain ridge at each 15-degree bearing. */
    private static int mountainHeight(int i) {
        // One character per bearing, '&' = 0 pixels; a string keeps the table out of the arena.
        return "0H4R<,8NZ@2D+>T6.LX:2F/*".charAt(i) - '&';
    }

    private static void drawEntity(short[] lines, int[] ents, int slot) {
        int b = slot * E_STRIDE;
        int type = ents[b + E_TYPE];
        int x = ents[b + E_X];
        int y = ents[b + E_Y];
        int z = ents[b + E_Z];
        if (z < NEAR || z > FAR) {
            return;
        }
        if (type == T_PLANE || type == T_FALLING) {
            drawBiplane(lines, x, y, z, ents[b + E_HEADING], type == T_FALLING ? FIRE : ENEMY);
        } else if (type == T_BLIMP) {
            int color = BLIMP;
            if (ents[b + E_TIMER] > 0) {
                ents[b + E_TIMER] = ents[b + E_TIMER] - 1;
                color = FIRE;
            }
            drawBlimp(lines, x, y, z, ents[b + E_HEADING], color);
        } else if (type == T_BULLET) {
            line3(lines, x, y, z, x - ents[b + E_VX], y - ents[b + E_VY], z - ents[b + E_VZ] / 2, BULLET);
        } else if (type == T_TRACER) {
            line3(lines, x, y, z, x + ents[b + E_VX], y + ents[b + E_VY], z + ents[b + E_VZ] / 2, TRACER);
        } else if (type == T_HANGAR) {
            drawBox(lines, x, z, 55, HANGAR_HEIGHT, TARGET);
            line3(lines, x - 55, HANGAR_HEIGHT, z, x, HANGAR_HEIGHT + 30, z, TARGET);
            line3(lines, x, HANGAR_HEIGHT + 30, z, x + 55, HANGAR_HEIGHT, z, TARGET);
        } else if (type == T_FLAK) {
            drawBox(lines, x, z, 30, FLAK_HEIGHT, TARGET);
            line3(lines, x, FLAK_HEIGHT, z, x, 70, z + 30, TARGET);
        } else if (type == T_PYRAMID) {
            drawPyramid(lines, x, z);
        } else if (type == T_MARKER) {
            line3(lines, x - 30, 0, z, x + 30, 0, z, GROUND_MARK);
            line3(lines, x, 0, z - 30, x, 0, z + 30, GROUND_MARK);
        } else if (type == T_DEBRIS) {
            int size = ents[b + E_MODE] * (12 - ents[b + E_TIMER]) / 8;
            for (int k = 0; k < 6; k++) {
                float angle = (float) (k * Math.PI / 3 + ents[b + E_TIMER] * 0.3);
                int ex = Math.round((float) Math.cos(angle) * size);
                int ey = Math.round((float) Math.sin(angle) * size);
                line3(lines, x + ex / 2, y + ey / 2, z, x + ex, y + ey, z, k % 2 == 0 ? FIRE : LINE);
            }
        }
    }

    /**
     * A biplane in wireframe: fuselage, upper and lower wings with struts, tailplane and fin, turned
     * to {@code heading}. Model units: x across, y up, z forward.
     */
    private static void drawBiplane(short[] lines, int x, int y, int z, int heading, int color) {
        float radians = (float) Math.toRadians(heading);
        int c = Math.round((float) Math.cos(radians) * 1024);
        int s = Math.round((float) Math.sin(radians) * 1024);
        // Fuselage.
        modelLine(lines, x, y, z, c, s, 0, 0, 42, 0, 0, -52, color);
        // Upper wing.
        modelLine(lines, x, y, z, c, s, -62, 16, 12, 62, 16, 12, color);
        modelLine(lines, x, y, z, c, s, -62, 16, -8, 62, 16, -8, color);
        modelLine(lines, x, y, z, c, s, -62, 16, 12, -62, 16, -8, color);
        modelLine(lines, x, y, z, c, s, 62, 16, 12, 62, 16, -8, color);
        // Lower wing.
        modelLine(lines, x, y, z, c, s, -54, -6, 10, 54, -6, 10, color);
        modelLine(lines, x, y, z, c, s, -54, -6, -6, 54, -6, -6, color);
        // Struts.
        modelLine(lines, x, y, z, c, s, -40, -6, 2, -40, 16, 2, color);
        modelLine(lines, x, y, z, c, s, 40, -6, 2, 40, 16, 2, color);
        // Tailplane and fin.
        modelLine(lines, x, y, z, c, s, -20, 0, -48, 20, 0, -48, color);
        modelLine(lines, x, y, z, c, s, 0, 0, -44, 0, 18, -52, color);
        // Propeller disc, as a cross at the nose.
        modelLine(lines, x, y, z, c, s, -12, 0, 44, 12, 0, 44, color);
        modelLine(lines, x, y, z, c, s, 0, -12, 44, 0, 12, 44, color);
    }

    /** A blimp: an elongated envelope outline, tail fins and a gondola, turned to {@code heading}. */
    private static void drawBlimp(short[] lines, int x, int y, int z, int heading, int color) {
        float radians = (float) Math.toRadians(heading);
        int c = Math.round((float) Math.cos(radians) * 1024);
        int s = Math.round((float) Math.sin(radians) * 1024);
        int previousLength = 0;
        int previousHeight = 0;
        for (int k = 0; k <= 12; k++) {
            float angle = (float) (k * Math.PI / 6);
            int length = Math.round((float) Math.cos(angle) * 170);
            int height = Math.round((float) Math.sin(angle) * 60);
            if (k > 0) {
                modelLine(lines, x, y, z, c, s, 0, previousHeight, previousLength, 0, height, length, color);
            }
            previousLength = length;
            previousHeight = height;
        }
        modelLine(lines, x, y, z, c, s, 0, 30, -140, 0, 80, -175, color);
        modelLine(lines, x, y, z, c, s, 0, 80, -175, 0, 40, -175, color);
        modelLine(lines, x, y, z, c, s, 0, -60, 30, 0, -78, 30, color);
        modelLine(lines, x, y, z, c, s, 0, -78, 30, 0, -78, -30, color);
        modelLine(lines, x, y, z, c, s, 0, -78, -30, 0, -60, -30, color);
    }

    private static void drawBox(short[] lines, int x, int z, int half, int height, int color) {
        line3(lines, x - half, 0, z - half, x + half, 0, z - half, color);
        line3(lines, x - half, height, z - half, x + half, height, z - half, color);
        line3(lines, x - half, 0, z - half, x - half, height, z - half, color);
        line3(lines, x + half, 0, z - half, x + half, height, z - half, color);
        line3(lines, x - half, height, z - half, x - half, height, z + half, color);
        line3(lines, x + half, height, z - half, x + half, height, z + half, color);
        line3(lines, x - half, height, z + half, x + half, height, z + half, color);
    }

    private static void drawPyramid(short[] lines, int x, int z) {
        int half = 80;
        line3(lines, x - half, 0, z - half, x + half, 0, z - half, OBSTACLE);
        line3(lines, x - half, 0, z - half, x, PYRAMID_HEIGHT, z, OBSTACLE);
        line3(lines, x + half, 0, z - half, x, PYRAMID_HEIGHT, z, OBSTACLE);
        line3(lines, x - half, 0, z + half, x, PYRAMID_HEIGHT, z, OBSTACLE);
        line3(lines, x + half, 0, z + half, x, PYRAMID_HEIGHT, z, OBSTACLE);
        line3(lines, x - half, 0, z - half, x - half, 0, z + half, OBSTACLE);
        line3(lines, x + half, 0, z - half, x + half, 0, z + half, OBSTACLE);
    }

    /** The parts of the plane that do not roll with the view: gun barrels, cowling and the sight. */
    private static void drawCockpit(short[] lines) {
        addLine(lines, 128, HEIGHT - 1, 146, 196, COCKPIT);
        addLine(lines, 140, HEIGHT - 1, 152, 196, COCKPIT);
        addLine(lines, 146, 196, 152, 196, COCKPIT);
        addLine(lines, 192, HEIGHT - 1, 174, 196, COCKPIT);
        addLine(lines, 180, HEIGHT - 1, 168, 196, COCKPIT);
        addLine(lines, 174, 196, 168, 196, COCKPIT);
        addLine(lines, 70, HEIGHT - 1, 110, 222, COCKPIT);
        addLine(lines, 110, 222, 210, 222, COCKPIT);
        addLine(lines, 210, 222, 250, HEIGHT - 1, COCKPIT);
        // The sight: a ring with a center dot.
        for (int k = 0; k < 8; k++) {
            addLine(lines, CENTER_X + sightX(k), CENTER_Y + sightY(k), CENTER_X + sightX(k + 1),
                    CENTER_Y + sightY(k + 1), SIGHT);
        }
        addLine(lines, CENTER_X, CENTER_Y, CENTER_X, CENTER_Y, SIGHT);
    }

    private static int sightX(int k) {
        int step = k % 8;
        if (step == 0) {
            return 12;
        }
        if (step == 1 || step == 7) {
            return 8;
        }
        if (step == 3 || step == 5) {
            return -8;
        }
        if (step == 4) {
            return -12;
        }
        return 0;
    }

    private static int sightY(int k) {
        return sightX(k + 6);
    }

    // ---- Projection ----

    /** A line of a model, from model coordinates turned by (c, s)/1024 and placed at (x, y, z). */
    private static void modelLine(short[] lines, int x, int y, int z, int c, int s, int ax, int ay, int az, int bx,
                                  int by, int bz, int color) {
        line3(lines, x + (ax * c + az * s) / 1024, y + ay, z + (az * c - ax * s) / 1024,
                x + (bx * c + bz * s) / 1024, y + by, z + (bz * c - bx * s) / 1024, color);
    }

    /** A 3D line, clipped at the near plane, projected, and rolled by the bank. */
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
        worldLine(lines, projectX(x0, z0), projectY(y0, z0), projectX(x1, z1), projectY(y1, z1), color);
    }

    private static int projectX(int x, int z) {
        return clamp(CENTER_X + x * FOCAL / z, -20000, 20000);
    }

    private static int projectY(int y, int z) {
        return clamp(CENTER_Y - (y - altitude) * FOCAL / z, -20000, 20000);
    }

    /** A screen line of the outside world, rolled about the center of the view by the bank. */
    private static void worldLine(short[] lines, int x0, int y0, int x1, int y1, int color) {
        addLine(lines, rollX(x0, y0), rollY(x0, y0), rollX(x1, y1), rollY(x1, y1), color);
    }

    // Banking right rolls the world counter-clockwise on the screen (y points down).
    private static int rollX(int x, int y) {
        return CENTER_X + ((x - CENTER_X) * bankCos + (y - CENTER_Y) * bankSin) / 1024;
    }

    private static int rollY(int x, int y) {
        return CENTER_Y + ((y - CENTER_Y) * bankCos - (x - CENTER_X) * bankSin) / 1024;
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
                drawListed(lines, oldBase + i * L_STRIDE, SKY);
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
        TftTouchShield.fillRect(0, HEADER, WIDTH, HEIGHT - HEADER, SKY);
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

    // ---- Opening and title ----

    /**
     * The opening, from the cockpit: "CONTACT!", the propeller is swung and spins up until it is a
     * blur, then the take-off run down the runway and the climb into the sky.
     */
    private static void opening(short[] lines) {
        TftTouchShield.fillScreen(SKY);
        shown = 0;
        bankCos = 1024;
        bankSin = 0;
        showCentered("CONTACT!", 28, 2, TftTouchShield.YELLOW);
        int angle = 0;
        for (int f = 0; f < SPIN_FRAMES; f++) {
            int speed = 2 + f * f / 45;
            angle = (angle + speed) % 360;
            built = 0;
            // The engine cowling around the hub.
            for (int k = 0; k < 8; k++) {
                addLine(lines, CENTER_X + sightX(k) * 2, CENTER_Y + sightY(k) * 2, CENTER_X + sightX(k + 1) * 2,
                        CENTER_Y + sightY(k + 1) * 2, COCKPIT);
            }
            if (speed > 40) {
                // Too fast to follow: the blades blur into a disc.
                for (int k = 0; k < 16; k++) {
                    float a0 = (float) Math.toRadians(k * 22.5f);
                    float a1 = (float) Math.toRadians(k * 22.5f + 22.5f);
                    addLine(lines, CENTER_X + Math.round((float) Math.cos(a0) * PROP_LENGTH),
                            CENTER_Y + Math.round((float) Math.sin(a0) * PROP_LENGTH),
                            CENTER_X + Math.round((float) Math.cos(a1) * PROP_LENGTH),
                            CENTER_Y + Math.round((float) Math.sin(a1) * PROP_LENGTH), HORIZON);
                }
            }
            if (speed <= 60) {
                float radians = (float) Math.toRadians(angle);
                int dx = Math.round((float) Math.cos(radians) * PROP_LENGTH);
                int dy = Math.round((float) Math.sin(radians) * PROP_LENGTH);
                addLine(lines, CENTER_X - dx, CENTER_Y - dy, CENTER_X + dx, CENTER_Y + dy, PROPELLER);
            }
            present(lines);
            Delay.millis(30);
        }
        Delay.millis(200);
        takeOff(lines);
        TftTouchShield.fillScreen(SKY);
        shown = 0;
        Delay.millis(250);
    }

    /**
     * The take-off run: the runway's center line rushes past faster and faster, then the nose comes
     * up, the horizon sinks and the runway drops away below.
     */
    private static void takeOff(short[] lines) {
        TftTouchShield.fillScreen(SKY);
        shown = 0;
        int pitch = 0;
        int travel = 0;
        for (int f = 0; f < TAKEOFF_FRAMES; f++) {
            built = 0;
            travel = travel + 8 + 2 * f;
            if (f > TAKEOFF_FRAMES - 22) {
                pitch = pitch + 7;
            }
            int horizon = CENTER_Y + pitch;
            addLine(lines, 0, horizon, WIDTH - 1, horizon, HORIZON);
            for (int side = -1; side <= 1; side = side + 2) {
                addLine(lines, CENTER_X + side * RUNWAY_HALF_WIDTH * FOCAL / NEAR, horizon + EYE_HEIGHT * FOCAL / NEAR,
                        CENTER_X + side * RUNWAY_HALF_WIDTH * FOCAL / FAR, horizon + EYE_HEIGHT * FOCAL / FAR, RUNWAY);
            }
            for (int k = 0; k < 12; k++) {
                int z = NEAR + k * 120 + (120 - travel % 120);
                addLine(lines, CENTER_X, horizon + EYE_HEIGHT * FOCAL / z, CENTER_X,
                        horizon + EYE_HEIGHT * FOCAL / (z + 50), LINE);
            }
            drawCockpit(lines);
            present(lines);
            Delay.millis(30);
        }
        Delay.millis(200);
    }

    /**
     * The title screen: the landscape and two enemy biplanes, and "RED BARON" spelled out letter by
     * letter, each one flashing white like a burst of fire before it turns red.
     */
    private static void drawTitle(short[] lines, byte[] letter) {
        built = 0;
        altitude = 300;
        heading = 30;
        bankCos = 1024;
        bankSin = 0;
        drawLandscape(lines);
        drawBiplane(lines, -120, 330, 520, 150, ENEMY);
        drawBiplane(lines, 260, 420, 1400, 200, ENEMY);
        present(lines);
        int left = (WIDTH - TITLE.length() * 24) / 2;
        TftTouchShield.setTextSize(4);
        for (int i = 0; i < TITLE.length(); i++) {
            letter[0] = (byte) TITLE.charAt(i);
            TftTouchShield.setTextColor(TftTouchShield.WHITE, SKY);
            TftTouchShield.setCursor(left + i * 24, 34);
            TftTouchShield.print(letter, 1);
            Delay.millis(50);
            TftTouchShield.setTextColor(TftTouchShield.RED, SKY);
            TftTouchShield.setCursor(left + i * 24, 34);
            TftTouchShield.print(letter, 1);
            Delay.millis(70);
        }
        Delay.millis(200);
        showCentered("Drag to fly, tap to fire", 198, 1, TftTouchShield.WHITE);
        showCentered("Tap to start", 214, 1, TftTouchShield.CYAN);
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
        TftTouchShield.fillRect(0, 0, WIDTH, HEADER - 1, SKY);
        TftTouchShield.drawHorizontalLine(0, HEADER - 1, WIDTH, HORIZON);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.YELLOW, SKY);
        TftTouchShield.setCursor(4, 2);
        TftTouchShield.print("SCORE ");
        TftTouchShield.print(score);
        TftTouchShield.setTextColor(TftTouchShield.CYAN, SKY);
        TftTouchShield.setCursor(136, 2);
        TftTouchShield.print("HI ");
        TftTouchShield.print(best);
        TftTouchShield.setCursor(262, 2);
        TftTouchShield.print("WAVE ");
        TftTouchShield.print(wave);
        if (autopilot) {
            TftTouchShield.setTextColor(TftTouchShield.MAGENTA, SKY);
            TftTouchShield.setCursor(226, 2);
            TftTouchShield.print("CPU");
        }
        TftTouchShield.setTextColor(planes <= 1 ? TftTouchShield.RED : TftTouchShield.GREEN, SKY);
        TftTouchShield.setCursor(4, 11);
        TftTouchShield.print("PLANES ");
        TftTouchShield.print(planes);
        status = -1;
        drawStatus();
    }

    /** The round's goal in the header's second row, and the altitude. */
    private static void drawStatus() {
        int value = round == DOGFIGHT ? Math.max(0, killTarget - kills) : targetsToSpawn;
        int combined = value * 1000 + altitude / 10;
        if (combined == status) {
            return;
        }
        status = combined;
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, SKY);
        TftTouchShield.setCursor(96, 11);
        if (round == DOGFIGHT) {
            TftTouchShield.print("ENEMY PLANES ");
        } else {
            TftTouchShield.print("TARGETS AHEAD ");
        }
        TftTouchShield.print(value);
        TftTouchShield.print("  ");
        TftTouchShield.setCursor(250, 11);
        TftTouchShield.print("ALT ");
        TftTouchShield.print(altitude);
        TftTouchShield.print("  ");
    }

    private static void showCentered(String text, int y, int size, int color) {
        TftTouchShield.setTextSize(size);
        TftTouchShield.setTextColor(color, SKY);
        TftTouchShield.setCursor((WIDTH - text.length() * 6 * size) / 2, y);
        TftTouchShield.print(text);
    }
}
