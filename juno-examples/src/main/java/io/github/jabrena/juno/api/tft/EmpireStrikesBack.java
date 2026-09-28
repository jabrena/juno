package io.github.jabrena.juno.api.tft;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;

/**
 * The Empire Strikes Back on the ELEGOO 2.8" TFT touch screen shield, after Atari's 1985 vector
 * arcade game, in landscape. Every wave plays three rounds, a little faster each time:
 *
 * <ol>
 *   <li><b>Probe droids</b>: fly your snowspeeder low over the snowfields of Hoth and destroy the
 *       Imperial probe droids hovering ahead before their shots wear your shields down.</li>
 *   <li><b>The walkers</b>: AT-AT walkers stride towards the rebel base. Their armor stops your
 *       lasers except at the head, which takes three hits; the smaller AT-STs fall to one. Both fire
 *       back. Bringing down every AT-AT pays a bonus.</li>
 *   <li><b>The asteroid field</b>: at the controls of the Millennium Falcon, weave through tumbling
 *       asteroids while TIE fighters close in. An asteroid you fly into takes a shield.</li>
 * </ol>
 *
 * <p>Drag to move the crosshair and tap to fire: the twin lasers converge on the point you tapped.
 * Your craft follows the crosshair, so steering and aiming are one: move away from incoming fire.
 * You start with {@value #START_SHIELDS} shields; a hit with none left ends the game, and each
 * completed wave restores one. Finish a round without losing a shield to earn the next letter of
 * <b>JEDI</b>; spell the whole word for a bonus.
 *
 * <p>The game opens like the film: snow falls on Hoth as an Imperial probe streaks down and lands,
 * two AT-ATs stride in from the distance, and the title assembles letter by letter; after each game
 * over it starts again from there. Tap to start, then choose who flies: <b>HUMAN</b> (you) or
 * <b>CPU</b>, an autopilot that lets targets close in, sweeps the crosshair onto them, dodges what
 * is about to hit it and misses now and then, like a person would. Tap the header during the game to switch between the
 * two ({@code CPU} shows in the header).
 *
 * <p>Everything is drawn in vector style from 3D points perspective-projected onto the screen
 * ({@code x' = cx + x·f/z}), with 3D lines clipped at the near plane and 2D lines clipped to the view,
 * using the same double display lists as {@link StarWars}: only lines that changed since the
 * previous frame are erased and drawn again.
 */
@Board(ArduinoUnoQ.class)
public final class EmpireStrikesBack {
    private static final int FRAME_MILLIS = 40;

    // Screen: landscape, a text header over the 3D view.
    private static final int WIDTH = 320;
    private static final int HEIGHT = 240;
    private static final int HEADER = 20;
    private static final int CENTER_X = 160;
    private static final int CENTER_Y = 130;

    // Camera: looks along +z; world z is the distance ahead of it.
    private static final int FOCAL = 160;
    private static final int NEAR = 40;
    private static final int FAR = 2600;
    private static final int CAMERA_LIMIT_X = 260;
    private static final int CAMERA_LOW = -70;
    private static final int CAMERA_HIGH = 150;
    private static final int CAMERA_STEP = 12;

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
    /** Where the entity's target was last drawn and how close a shot must land to hit it (0: none). */
    private static final int E_SX = 8;
    private static final int E_SY = 9;
    private static final int E_SR = 10;
    /** Asteroid: radius. Debris: size. Shot: the target slot. TIE: 1 once it breaks off. */
    private static final int E_AUX = 11;
    /** Asteroid: shape seed. Shot: the target's type. */
    private static final int E_FLAG = 12;
    private static final int E_HP = 13;
    private static final int E_STRIDE = 14;

    private static final int T_NONE = 0;
    private static final int T_PROBE = 1;
    private static final int T_ATAT = 2;
    private static final int T_ATST = 3;
    private static final int T_ASTEROID = 4;
    private static final int T_TIE = 5;
    private static final int T_FIREBALL = 6;
    private static final int T_DEBRIS = 7;
    private static final int T_SHOT = 8;

    // Rounds of a wave.
    private static final int PROBES = 0;
    private static final int WALKERS = 1;
    private static final int ASTEROIDS = 2;

    // World geometry.
    private static final int GROUND = -150;
    private static final int GRID = 320;
    private static final int ATAT_HEAD_Y = GROUND + 190;
    private static final int ATST_HEAD_Y = GROUND + 150;
    private static final int ATAT_HITS = 3;
    /** How close to the camera a fireball or asteroid must arrive to hit. */
    private static final int FIREBALL_REACH = 50;
    /** How much a fireball may correct its course towards you each frame. */
    private static final int HOMING = 2;

    // Player.
    private static final int START_SHIELDS = 6;
    private static final int MAX_SHIELDS = 9;
    private static final int SHOTS = 3;
    private static final int SHOT_FRAMES = 4;
    /** A touch this short (in frames) that barely moved is a tap, and fires on release. */
    private static final int TAP_FRAMES = 8;
    private static final int DRAG_PIXELS = 20;

    // Scoring.
    private static final int FIREBALL_POINTS = 33;
    private static final int PROBE_POINTS = 500;
    private static final int ATST_POINTS = 1000;
    private static final int ATAT_POINTS = 5000;
    private static final int ASTEROID_POINTS = 100;
    private static final int TIE_POINTS = 1000;
    private static final int ALL_ATATS_BONUS = 20000;
    private static final int JEDI_BONUS = 50000;

    // Colors.
    private static final int SPACE = TftTouchShield.BLACK;
    private static final int SNOW = 0x6DDF;
    private static final int SNOW_GRID = 0x3252;
    private static final int MOUNTAIN = 0xBDF7;
    private static final int PROBE = TftTouchShield.WHITE;
    private static final int PROBE_EYE = TftTouchShield.RED;
    private static final int WALKER = 0xC618;
    private static final int WALKER_HEAD = TftTouchShield.YELLOW;
    private static final int ROCK = 0xA4C8;
    private static final int STAR = 0x8410;
    private static final int TIE = TftTouchShield.GREEN;
    private static final int CROSSHAIR = TftTouchShield.WHITE;
    private static final int LASER = TftTouchShield.RED;
    private static final int FIRE_A = TftTouchShield.ORANGE;
    private static final int FIRE_B = TftTouchShield.RED;
    private static final int HUD = 0x3A7F;

    // The opening: the probe's fall, then the walkers' advance.
    private static final int POD_FRAMES = 44;
    private static final int WALK_FRAMES = 70;
    private static final String TITLE_TOP = "THE EMPIRE";
    private static final String TITLE_BOTTOM = "STRIKES BACK";

    // The HUMAN and CPU buttons of the pilot screen.
    private static final int CHOICE_X = 20;
    private static final int CHOICE_Y = 104;
    private static final int CHOICE_WIDTH = 130;
    private static final int CHOICE_HEIGHT = 72;
    private static final int CHOICE_GAP = 20;

    // The CPU pilot: how fast it moves the crosshair, how often it may fire, how near a target must
    // come before it engages, and how often it aims a shot just off the target.
    private static final int CPU_AIM_SPEED = 20;
    private static final int CPU_FIRE_FRAMES = 8;
    private static final int CPU_RANGE = 2000;
    private static final int CPU_MISS_PERCENT = 10;
    /** How often the CPU is slow to notice a new target, and lets it come much closer first. */
    private static final int CPU_LATE_PERCENT = 30;
    /** How near a fireball or asteroid on a collision course must be before the CPU dodges it. */
    private static final int CPU_EVADE_Z = 700;

    private static int score;
    private static int best;
    private static int shields;
    private static int wave;
    private static int round;
    private static int frame;
    private static boolean dead;
    /** JEDI letters earned so far (0-4). */
    private static int jedi;
    private static boolean hitThisRound;

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
    /** The target the CPU last picked, and how near it must come before the CPU reacts. */
    private static int cpuTarget = -1;
    private static int cpuReaction;

    // Display list state.
    private static int front;
    private static int shown;
    private static int built;

    // Round state.
    private static int spawnCountdown;
    private static int kills;
    private static int killTarget;
    private static int walkersToSpawn;
    private static int atatsTotal;
    private static int atatsDown;
    private static int travel;
    private static int fieldLength;
    private static int status;

    private EmpireStrikesBack() {
    }

    public static void main(String[] args) {
        short[] lines = new short[2 * LIST_SIZE];
        int[] ents = new int[ENTITIES * E_STRIDE];
        byte[] letter = new byte[1];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);

        while (true) {
            opening(lines, ents);
            drawTitle(letter);
            waitForTap();
            Random.seed(Clock.micros());
            choosePilot();
            score = 0;
            shields = START_SHIELDS;
            wave = 1;
            jedi = 0;
            boolean alive = true;
            while (alive) {
                alive = runRound(PROBES, lines, ents) && runRound(WALKERS, lines, ents)
                        && runRound(ASTEROIDS, lines, ents);
                if (alive) {
                    shields = Math.min(shields + 1, MAX_SHIELDS);
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

    /** Runs one round frame by frame; returns false when the last shield is lost. */
    private static boolean runRound(int which, short[] lines, int[] ents) {
        startRound(which, ents);
        drawHeader();
        clearView();
        if (which == PROBES) {
            showCentered("WAVE", 72, 3, TftTouchShield.YELLOW);
            TftTouchShield.setCursor(wave < 10 ? 151 : 142, 102);
            TftTouchShield.print(wave);
            showCentered("HOTH", 138, 2, SNOW);
            showCentered("Destroy the probe droids", 164, 1, TftTouchShield.WHITE);
        } else if (which == WALKERS) {
            showCentered("THE WALKERS", 104, 2, TftTouchShield.YELLOW);
            showCentered("Hit the AT-AT heads", 136, 1, TftTouchShield.WHITE);
        } else {
            showCentered("THE ASTEROID FIELD", 104, 2, TftTouchShield.YELLOW);
            showCentered("Never tell me the odds!", 136, 1, TftTouchShield.WHITE);
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
            if (roundOver(ents)) {
                finishRound();
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
        hitThisRound = false;
        clear(ents, ENTITIES * E_STRIDE);
        camX = 0;
        camY = 0;
        travel = 0;
        spawnCountdown = 10;
        kills = 0;
        if (which == PROBES) {
            killTarget = Math.min(8 + 2 * wave, 18);
        } else if (which == WALKERS) {
            atatsTotal = Math.min(2 + wave, 5);
            walkersToSpawn = atatsTotal + Math.min(2 + wave, 6);
            atatsDown = 0;
        } else {
            fieldLength = 30000 + 4000 * Math.min(wave, 6);
        }
    }

    private static boolean roundOver(int[] ents) {
        int threats = count(ents, T_FIREBALL) + count(ents, T_DEBRIS);
        if (round == PROBES) {
            return kills >= killTarget && count(ents, T_PROBE) + threats == 0;
        }
        if (round == WALKERS) {
            return walkersToSpawn == 0 && count(ents, T_ATAT) + count(ents, T_ATST) + threats == 0;
        }
        return travel >= fieldLength && count(ents, T_ASTEROID) + count(ents, T_TIE) + threats == 0;
    }

    /** Round bonuses and the JEDI letter for a round flown without losing a shield. */
    private static void finishRound() {
        drawHeader();
        clearView();
        int y = 90;
        if (round == WALKERS && atatsDown == atatsTotal) {
            score = score + ALL_ATATS_BONUS;
            showCentered("ALL AT-ATS DOWN  +20000", y, 1, WALKER_HEAD);
            y = y + 18;
        }
        if (!hitThisRound) {
            jedi = jedi + 1;
            showCentered("NO SHIELDS LOST", y, 1, TftTouchShield.WHITE);
            showCentered(jediWord(), y + 14, 2, TftTouchShield.CYAN);
            if (jedi == 4) {
                score = score + JEDI_BONUS;
                showCentered("JEDI BONUS  +50000", y + 40, 1, TftTouchShield.CYAN);
                jedi = 0;
            }
            y = y + 60;
        }
        drawHeader();
        if (y > 90) {
            Delay.millis(1600);
        }
    }

    /** The letters of JEDI earned so far, the rest as dashes. */
    private static String jediWord() {
        if (jedi >= 4) {
            return "J E D I";
        }
        if (jedi == 3) {
            return "J E D -";
        }
        if (jedi == 2) {
            return "J E - -";
        }
        if (jedi == 1) {
            return "J - - -";
        }
        return "- - - -";
    }

    /** One hit on your craft: a shield is lost, or the game when none is left. */
    private static void shieldHit() {
        hitThisRound = true;
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
     * Tapping the header hands the controls to the CPU or back.
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
    }

    /** The pilot screen: waits for a tap on HUMAN or CPU. */
    private static void choosePilot() {
        TftTouchShield.fillScreen(SPACE);
        showCentered("CHOOSE PILOT", 36, 3, TftTouchShield.YELLOW);
        showCentered("Who flies for the Rebellion?", 74, 1, TftTouchShield.WHITE);
        drawChoice(0, "HUMAN", "You steer and fire", false);
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
        drawChoice(choice, choice == 0 ? "HUMAN" : "CPU", choice == 0 ? "You steer and fire" : "Autopilot plays",
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
        int color = chosen ? HUD : 0x2124;
        TftTouchShield.fillRect(x, CHOICE_Y, CHOICE_WIDTH, CHOICE_HEIGHT, color);
        TftTouchShield.drawRect(x, CHOICE_Y, CHOICE_WIDTH, CHOICE_HEIGHT, chosen ? TftTouchShield.WHITE : SNOW);
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
     * within {@value #CPU_RANGE} before engaging the nearest (a probe droid, walker, TIE fighter,
     * fireball, or an asteroid within 900), sweeps the crosshair towards it at
     * {@value #CPU_AIM_SPEED} pixels a frame, and fires, at most every {@value #CPU_FIRE_FRAMES}
     * frames, once the crosshair is on its aim point. About {@value #CPU_LATE_PERCENT}% of the time
     * it is slow to notice a new target (anything but a fireball) and only reacts once it is within
     * 600 to 1100. About {@value #CPU_MISS_PERCENT}% of its
     * shots at anything but a fireball are aimed just past the target and miss. The crosshair also
     * steers the craft, so when a fireball or asteroid on a collision course comes within
     * {@value #CPU_EVADE_Z}, it dodges first (see {@link #dodge}).
     */
    private static void flyAutopilot(int[] ents) {
        int target = -1;
        int nearest = CPU_RANGE;
        for (int slot = 0; slot < ENTITIES; slot++) {
            int b = slot * E_STRIDE;
            int type = ents[b + E_TYPE];
            boolean wanted = type == T_PROBE || type == T_ATAT || type == T_ATST || type == T_TIE
                    || type == T_FIREBALL || (type == T_ASTEROID && ents[b + E_Z] < 900);
            if (wanted && ents[b + E_SR] > 0 && ents[b + E_Z] < nearest) {
                target = slot;
                nearest = ents[b + E_Z];
            }
        }
        if (target >= 0 && target != cpuTarget) {
            // A new target: now and then the CPU is slow to notice it, as a person would be.
            cpuTarget = target;
            boolean late = ents[target * E_STRIDE + E_TYPE] != T_FIREBALL && Random.nextInt(100) < CPU_LATE_PERCENT;
            cpuReaction = late ? Random.nextInt(600, 1100) : CPU_RANGE;
        }
        if (target >= 0 && nearest >= cpuReaction) {
            target = -1;
        }
        boolean trigger = frame % CPU_FIRE_FRAMES == 0;
        if (trigger) {
            // Each shot is aimed true or, now and then, just past the edge of the target.
            aimOffsetX = 0;
            aimOffsetY = 0;
            if (target >= 0 && ents[target * E_STRIDE + E_TYPE] != T_FIREBALL
                    && Random.nextInt(100) < CPU_MISS_PERCENT) {
                int off = ents[target * E_STRIDE + E_SR] + Random.nextInt(6, 20);
                aimOffsetX = Random.nextInt(2) == 0 ? -off : off;
                aimOffsetY = Random.nextInt(-off, off + 1);
            }
        }
        int aimX = CENTER_X;
        int aimY = CENTER_Y;
        if (target >= 0) {
            aimX = ents[target * E_STRIDE + E_SX] + aimOffsetX;
            aimY = ents[target * E_STRIDE + E_SY] + aimOffsetY;
        }
        // Dodge first: the crosshair steers the craft, so swing it away from whatever is about to hit.
        int threat = -1;
        int closest = CPU_EVADE_Z;
        for (int slot = 0; slot < ENTITIES; slot++) {
            int b = slot * E_STRIDE;
            int type = ents[b + E_TYPE];
            int reach = type == T_FIREBALL ? FIREBALL_REACH : ents[b + E_AUX];
            if ((type == T_FIREBALL || type == T_ASTEROID) && ents[b + E_Z] < closest
                    && Math.abs(ents[b + E_X] - camX) < reach + 60 && Math.abs(ents[b + E_Y] - camY) < reach + 60) {
                threat = slot;
                closest = ents[b + E_Z];
            }
        }
        if (threat >= 0) {
            // A dodge is a flick of the finger to wherever steers clear of every close threat.
            dodge(ents);
            return;
        }
        if (target < 0) {
            return;
        }
        aimX = clamp(aimX, 8, WIDTH - 9);
        aimY = clamp(aimY, HEADER + 8, HEIGHT - 9);
        crossX = crossX + clamp(aimX - crossX, -CPU_AIM_SPEED, CPU_AIM_SPEED);
        crossY = crossY + clamp(aimY - crossY, -CPU_AIM_SPEED, CPU_AIM_SPEED);
        if (trigger && Math.abs(aimX - crossX) + Math.abs(aimY - crossY) <= 4) {
            fire(ents);
        }
    }

    /**
     * Tries a grid of crosshair positions and flicks the crosshair to the one that steers the craft
     * furthest from every fireball and asteroid within {@value #CPU_EVADE_Z}.
     */
    private static void dodge(int[] ents) {
        int bestX = crossX;
        int bestY = crossY;
        int bestClearance = Integer.MIN_VALUE;
        for (int column = 0; column < 5; column++) {
            for (int row = 0; row < 3; row++) {
                int x = 8 + column * (WIDTH - 17) / 4;
                int y = HEADER + 8 + row * (HEIGHT - HEADER - 17) / 2;
                // Where the craft heads with the crosshair there (see steer()).
                int headX = clamp((x - CENTER_X) * CAMERA_LIMIT_X / 150, -CAMERA_LIMIT_X, CAMERA_LIMIT_X);
                int headY = clamp((CENTER_Y - y) * CAMERA_HIGH / 100, CAMERA_LOW, CAMERA_HIGH);
                int clearance = Integer.MAX_VALUE;
                for (int slot = 0; slot < ENTITIES; slot++) {
                    int b = slot * E_STRIDE;
                    int type = ents[b + E_TYPE];
                    if ((type != T_FIREBALL && type != T_ASTEROID) || ents[b + E_Z] >= CPU_EVADE_Z) {
                        continue;
                    }
                    int reach = type == T_FIREBALL ? FIREBALL_REACH : ents[b + E_AUX];
                    int gap = Math.max(Math.abs(ents[b + E_X] - headX), Math.abs(ents[b + E_Y] - headY)) - reach;
                    clearance = Math.min(clearance, gap);
                }
                // Prefer staying close to where the crosshair already is, all else equal.
                int score = clearance * 4 - Math.abs(x - crossX) - Math.abs(y - crossY);
                if (score > bestClearance) {
                    bestClearance = score;
                    bestX = x;
                    bestY = y;
                }
            }
        }
        crossX = bestX;
        crossY = bestY;
    }

    // ---- Simulation ----

    /** How far the world moves towards the camera each frame. */
    private static int flightSpeed() {
        if (round == ASTEROIDS) {
            return 30 + 2 * Math.min(wave, 8);
        }
        return 18 + Math.min(wave, 8);
    }

    private static void step(int[] ents) {
        int speed = flightSpeed();
        travel = travel + speed;
        steer();
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
            if (type == T_PROBE) {
                flyProbe(ents, b);
            } else if (type == T_ATAT || type == T_ATST) {
                if (z < NEAR) {
                    ents[b + E_TYPE] = T_NONE;
                } else {
                    walkerFires(ents, b);
                }
            } else if (type == T_TIE) {
                flyTie(ents, b);
            } else if (type == T_FIREBALL || type == T_ASTEROID) {
                if (type == T_FIREBALL && z > NEAR) {
                    home(ents, b, speed);
                }
                if (z <= NEAR) {
                    ents[b + E_TYPE] = T_NONE;
                    int reach = type == T_FIREBALL ? FIREBALL_REACH : ents[b + E_AUX];
                    if (Math.abs(ents[b + E_X] - camX) < reach && Math.abs(ents[b + E_Y] - camY) < reach) {
                        shieldHit();
                    }
                }
            } else if (type == T_DEBRIS) {
                ents[b + E_TIMER] = ents[b + E_TIMER] - 1;
                if (ents[b + E_TIMER] <= 0 || z < NEAR) {
                    ents[b + E_TYPE] = T_NONE;
                }
            }
        }
    }

    /** Your craft drifts towards where the crosshair points: steering and aiming are one. */
    private static void steer() {
        int targetX = clamp((crossX - CENTER_X) * CAMERA_LIMIT_X / 150, -CAMERA_LIMIT_X, CAMERA_LIMIT_X);
        int targetY = clamp((CENTER_Y - crossY) * CAMERA_HIGH / 100, CAMERA_LOW, CAMERA_HIGH);
        camX = camX + clamp(targetX - camX, -CAMERA_STEP, CAMERA_STEP);
        camY = camY + clamp(targetY - camY, -CAMERA_STEP, CAMERA_STEP);
    }

    private static void spawn(int[] ents) {
        spawnCountdown = spawnCountdown - 1;
        if (spawnCountdown > 0) {
            return;
        }
        if (round == PROBES) {
            spawnCountdown = 10;
            if (kills + count(ents, T_PROBE) < killTarget && count(ents, T_PROBE) < Math.min(2 + wave / 2, 4)) {
                spawnProbe(ents);
                spawnCountdown = Random.nextInt(20, 50);
            }
        } else if (round == WALKERS) {
            spawnCountdown = 10;
            int walking = count(ents, T_ATAT) + count(ents, T_ATST);
            if (walkersToSpawn > 0 && walking < 3) {
                spawnWalker(ents);
                spawnCountdown = Math.max(40, Random.nextInt(60, 110) - 4 * wave);
            }
        } else if (travel < fieldLength) {
            spawnCountdown = Math.max(8, Random.nextInt(14, 30) - wave);
            if (Random.nextInt(5) == 0 && count(ents, T_TIE) < Math.min(1 + wave / 2, 3)) {
                spawnTie(ents);
            } else {
                spawnAsteroid(ents);
            }
        } else {
            spawnCountdown = 10;
        }
    }

    private static void spawnProbe(int[] ents) {
        int slot = freeSlot(ents);
        if (slot < 0) {
            return;
        }
        int b = slot * E_STRIDE;
        ents[b + E_TYPE] = T_PROBE;
        ents[b + E_X] = camX + Random.nextInt(-500, 501);
        ents[b + E_Y] = Random.nextInt(-40, 160);
        ents[b + E_Z] = FAR - Random.nextInt(300);
        ents[b + E_TIMER] = Random.nextInt(8, 30);
    }

    /**
     * A probe droid hovers ahead, keeping its distance while it drifts from side to side, and fires
     * now and then; it bobs as it floats.
     */
    private static void flyProbe(int[] ents, int b) {
        int z = ents[b + E_Z];
        // Hold station at 700-1400 ahead: match the flight speed there.
        if (z < 900 + (b % 5) * 100) {
            ents[b + E_VZ] = flightSpeed() - 2;
        } else {
            ents[b + E_VZ] = 0;
        }
        ents[b + E_TIMER] = ents[b + E_TIMER] - 1;
        if (ents[b + E_TIMER] <= 0) {
            ents[b + E_VX] = Random.nextInt(-8, 9);
            ents[b + E_VY] = Random.nextInt(-3, 4);
            ents[b + E_TIMER] = Random.nextInt(20, 50);
            if (z < 2500 && Random.nextInt(100) < 16 + 4 * Math.min(wave, 8)) {
                fireball(ents, ents[b + E_X], ents[b + E_Y], z);
            }
        }
        ents[b + E_Y] = clamp(ents[b + E_Y], GROUND + 90, 220);
        int dx = ents[b + E_X] - camX;
        if (dx > z * 3 / 5) {
            ents[b + E_VX] = -Math.abs(ents[b + E_VX]) - 1;
        } else if (dx < -z * 3 / 5) {
            ents[b + E_VX] = Math.abs(ents[b + E_VX]) + 1;
        }
    }

    private static void spawnWalker(int[] ents) {
        int slot = freeSlot(ents);
        if (slot < 0) {
            return;
        }
        int b = slot * E_STRIDE;
        // AT-ATs first, then AT-STs mixed in.
        boolean atat = atatsTotal - atatsDown - count(ents, T_ATAT) > 0
                && (walkersToSpawn > Math.min(wave, 4) || Random.nextInt(2) == 0);
        ents[b + E_TYPE] = atat ? T_ATAT : T_ATST;
        int side = Random.nextInt(2) == 0 ? -1 : 1;
        ents[b + E_X] = camX + side * Random.nextInt(150, 520);
        if (atat) {
            // AT-ATs stride across your path, towards the middle.
            ents[b + E_FLAG] = -side;
            ents[b + E_VX] = -side * 3;
        }
        ents[b + E_Y] = GROUND;
        ents[b + E_Z] = FAR;
        // Walkers advance slowly; the snowspeeder closes in on them.
        ents[b + E_VZ] = flightSpeed() - (atat ? 6 : 10);
        ents[b + E_HP] = atat ? ATAT_HITS : 1;
        ents[b + E_TIMER] = Random.nextInt(10, 40);
        walkersToSpawn = walkersToSpawn - 1;
    }

    private static void walkerFires(int[] ents, int b) {
        int z = ents[b + E_Z];
        if (z < 450 || z > 2500) {
            return;
        }
        ents[b + E_TIMER] = ents[b + E_TIMER] - 1;
        if (ents[b + E_TIMER] <= 0) {
            boolean atat = ents[b + E_TYPE] == T_ATAT;
            ents[b + E_TIMER] = Math.max(34, Random.nextInt(atat ? 70 : 90, 140) - 4 * wave);
            int headX = atat ? ents[b + E_X] + (ents[b + E_FLAG] >= 0 ? 175 : -175) : ents[b + E_X];
            fireball(ents, headX, atat ? ATAT_HEAD_Y : ATST_HEAD_Y, z);
        }
    }

    private static void spawnAsteroid(int[] ents) {
        int slot = freeSlot(ents);
        if (slot < 0) {
            return;
        }
        int b = slot * E_STRIDE;
        ents[b + E_TYPE] = T_ASTEROID;
        ents[b + E_X] = camX + Random.nextInt(-700, 701);
        ents[b + E_Y] = camY + Random.nextInt(-450, 451);
        ents[b + E_Z] = FAR;
        ents[b + E_VX] = Random.nextInt(-3, 4);
        ents[b + E_VY] = Random.nextInt(-3, 4);
        ents[b + E_VZ] = -Random.nextInt(0, 6 + wave);
        ents[b + E_AUX] = Random.nextInt(45, 120);
        ents[b + E_FLAG] = Random.nextInt(1000);
    }

    private static void spawnTie(int[] ents) {
        int slot = freeSlot(ents);
        if (slot < 0) {
            return;
        }
        int b = slot * E_STRIDE;
        ents[b + E_TYPE] = T_TIE;
        ents[b + E_X] = camX + Random.nextInt(-500, 501);
        ents[b + E_Y] = camY + Random.nextInt(-250, 251);
        ents[b + E_Z] = FAR - Random.nextInt(400);
        ents[b + E_VZ] = flightSpeed() - Math.min(10 + 2 * wave, 26);
        ents[b + E_TIMER] = 1;
    }

    /** Weaves towards the camera firing, then breaks off and flies out of view. */
    private static void flyTie(int[] ents, int b) {
        int x = ents[b + E_X] - camX;
        int y = ents[b + E_Y] - camY;
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
            if (x > z * 3 / 5) {
                ents[b + E_VX] = -Math.abs(ents[b + E_VX]) - 1;
            } else if (x < -z * 3 / 5) {
                ents[b + E_VX] = Math.abs(ents[b + E_VX]) + 1;
            }
            if (z < 350) {
                ents[b + E_AUX] = 1;
                ents[b + E_VZ] = flightSpeed() + 20;
                ents[b + E_VX] = x >= 0 ? 16 : -16;
                ents[b + E_VY] = y >= 0 ? 8 : -8;
            } else if (z > 500 && z < 1700 && Random.nextInt(1000) < 12 + 3 * Math.min(wave, 10)) {
                fireball(ents, ents[b + E_X], ents[b + E_Y], z);
            }
        } else if (z > FAR + 200 || Math.abs(x) > z + 300 || Math.abs(y) > z + 300) {
            ents[b + E_TYPE] = T_NONE;
        }
    }

    /**
     * Fireballs home in on your craft, but can only correct their course a little each frame, so a
     * quick move still dodges them.
     */
    private static void home(int[] ents, int b, int speed) {
        int frames = Math.max(1, (ents[b + E_Z] - NEAR) / (speed - ents[b + E_VZ]));
        int wantX = (camX - ents[b + E_X]) / frames;
        int wantY = (camY - ents[b + E_Y]) / frames;
        ents[b + E_VX] = ents[b + E_VX] + clamp(wantX - ents[b + E_VX], -HOMING, HOMING);
        ents[b + E_VY] = ents[b + E_VY] + clamp(wantY - ents[b + E_VY], -HOMING, HOMING);
    }

    /** Launches a fireball from (x, y, z) aimed at where your craft is now. */
    private static void fireball(int[] ents, int x, int y, int z) {
        int slot = freeSlot(ents);
        if (slot < 0) {
            return;
        }
        int b = slot * E_STRIDE;
        int speed = Math.min(18 + 2 * wave, 34);
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
     * crosshair when they were fired, if it is still there.
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
            if (target >= 0 && ents[target * E_STRIDE + E_TYPE] == ents[b + E_FLAG]) {
                hit(ents, target);
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
        if (type == T_ATAT) {
            ents[b + E_HP] = ents[b + E_HP] - 1;
            int headX = x + (ents[b + E_FLAG] >= 0 ? 175 : -175);
            debris(ents, headX, ATAT_HEAD_Y, z, 30);
            if (ents[b + E_HP] > 0) {
                return;
            }
            atatsDown = atatsDown + 1;
            score = score + ATAT_POINTS;
            ents[b + E_TYPE] = T_NONE;
            debris(ents, x, GROUND + 130, z, 160);
            drawHeader();
            return;
        }
        ents[b + E_TYPE] = T_NONE;
        if (type == T_PROBE) {
            kills = kills + 1;
            score = score + PROBE_POINTS;
            debris(ents, x, y, z, 70);
        } else if (type == T_ATST) {
            score = score + ATST_POINTS;
            debris(ents, x, ATST_HEAD_Y, z, 70);
        } else if (type == T_ASTEROID) {
            score = score + ASTEROID_POINTS;
            debris(ents, x, y, z, ents[b + E_AUX]);
        } else if (type == T_TIE) {
            score = score + TIE_POINTS;
            debris(ents, x, y, z, 90);
        } else if (type == T_FIREBALL) {
            score = score + FIREBALL_POINTS;
            debris(ents, x, y, z, 30);
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
        if (round == ASTEROIDS) {
            drawStars(lines);
        } else {
            drawHoth(lines);
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
            int x = (i * 97 + 13 - camX / 8 + 3200) % WIDTH;
            int y = HEADER + 2 + (i * 61 + 7 * i * i + camY / 8 + 2200) % (HEIGHT - HEADER - 4);
            addLine(lines, x, y, x, y, STAR);
        }
    }

    /** The horizon, a ridge of snowy mountains on it, and snow drifts streaming past on the ground. */
    private static void drawHoth(short[] lines) {
        addLine(lines, 0, CENTER_Y, WIDTH - 1, CENTER_Y, SNOW);
        int previousX = -1;
        int previousY = CENTER_Y;
        for (int k = 0; k <= 16; k++) {
            int x = k * 20 - 1;
            int y = CENTER_Y - ridge(k);
            addLine(lines, previousX, previousY, x, y, MOUNTAIN);
            previousX = x;
            previousY = y;
        }
        int offset = travel % GRID;
        for (int k = 0; k < 7; k++) {
            int z = GRID * (k + 1) - offset;
            if (z < NEAR || z > FAR) {
                continue;
            }
            for (int c = 0; c < 4; c++) {
                int x = -600 + c * 400 + ((k & 1) == 0 ? 0 : 200);
                line3(lines, x - 60, GROUND, z, x + 60, GROUND, z, SNOW_GRID);
            }
        }
    }

    /** Height in pixels of the mountain ridge at each 20-pixel step along the horizon. */
    private static int ridge(int k) {
        // One character per point, '0' = 0 pixels; a string keeps the table out of the arena.
        return "05<8@6;3?9A57=48;0".charAt(k) - '0';
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
        if (type == T_PROBE) {
            drawProbe(lines, px, py, z, slot);
            ents[b + E_SR] = scale(60, z) + 5;
        } else if (type == T_ATAT) {
            drawAtat(lines, ents, b);
        } else if (type == T_ATST) {
            drawAtst(lines, ents, b);
        } else if (type == T_ASTEROID) {
            drawAsteroid(lines, px, py, z, ents[b + E_AUX], ents[b + E_FLAG]);
            ents[b + E_SR] = scale(ents[b + E_AUX], z) + 4;
        } else if (type == T_TIE) {
            drawTie(lines, px, py, z);
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

    /** A probe droid: an octagonal head with a red eye, and legs dangling (and swaying) below. */
    private static void drawProbe(short[] lines, int px, int py, int z, int slot) {
        for (int k = 0; k < 8; k++) {
            part(lines, px, py, z, dirX(k) * 4, dirY(k) * 4, dirX(k + 1) * 4, dirY(k + 1) * 4, PROBE);
        }
        part(lines, px, py, z, -40, 0, 40, 0, PROBE);
        part(lines, px, py, z, -6, 12, 6, 12, PROBE_EYE);
        int sway = ((frame + slot * 3) / 4) % 3 - 1;
        for (int leg = -2; leg <= 2; leg++) {
            int top = leg * 12;
            part(lines, px, py, z, top, -40, top + leg * 6 + sway * 6, -95 - Math.abs(leg) * 8, PROBE);
        }
    }

    /**
     * An AT-AT seen side-on as it strides across your path: a long box body on four legs that step
     * in turn, and a boxy head out in front, its only weak spot. {@code E_FLAG} is the direction it
     * walks (+1 or -1 along x).
     */
    private static void drawAtat(short[] lines, int[] ents, int b) {
        int x = ents[b + E_X];
        int z = ents[b + E_Z];
        int dir = ents[b + E_FLAG] >= 0 ? 1 : -1;
        int bodyLow = GROUND + 150;
        int bodyHigh = GROUND + 230;
        box(lines, x - 110, bodyLow, z - 45, x + 110, bodyHigh, z + 45, WALKER);
        // Neck and head, in front of the body.
        int headX = x + dir * 175;
        line3(lines, x + dir * 110, bodyLow + 40, z, headX - dir * 30, ATAT_HEAD_Y, z, WALKER);
        int headColor = ents[b + E_HP] < ATAT_HITS && (frame & 2) == 0 ? TftTouchShield.RED : WALKER_HEAD;
        box(lines, headX - 32, ATAT_HEAD_Y - 22, z - 24, headX + 32, ATAT_HEAD_Y + 22, z + 24, headColor);
        int step = (frame + b) % 32;
        for (int leg = 0; leg < 4; leg++) {
            int legX = x + ((leg & 1) == 0 ? -80 : 80);
            int legZ = z + ((leg & 2) == 0 ? -40 : 40);
            // Diagonal pairs step together, half a cycle apart.
            int phase = (step + ((leg == 0 || leg == 3) ? 0 : 16)) % 32;
            int swing = dir * (phase < 16 ? phase * 3 - 24 : (32 - phase) * 3 - 24);
            int knee = GROUND + 75 + (phase < 16 ? 12 : 0);
            line3(lines, legX, bodyLow, legZ, legX + swing / 2, knee, legZ, WALKER);
            line3(lines, legX + swing / 2, knee, legZ, legX + swing, GROUND, legZ, WALKER);
            line3(lines, legX + swing - 12, GROUND, legZ, legX + swing + 12, GROUND, legZ, WALKER);
        }
        ents[b + E_SX] = projectX(headX, z);
        ents[b + E_SY] = projectY(ATAT_HEAD_Y, z);
        ents[b + E_SR] = scale(44, z) + 5;
    }

    /** An AT-ST: a small box head on two bird-like legs. One hit brings it down. */
    private static void drawAtst(short[] lines, int[] ents, int b) {
        int x = ents[b + E_X];
        int z = ents[b + E_Z];
        box(lines, x - 26, ATST_HEAD_Y - 20, z - 26, x + 26, ATST_HEAD_Y + 20, z + 26, WALKER_HEAD);
        int step = (frame + b) % 20;
        for (int leg = -1; leg <= 1; leg = leg + 2) {
            int phase = (step + (leg < 0 ? 0 : 10)) % 20;
            int swing = phase < 10 ? phase * 5 - 25 : (20 - phase) * 5 - 25;
            int kneeZ = z + 26;
            line3(lines, x + leg * 18, ATST_HEAD_Y - 20, z, x + leg * 22, GROUND + 60, kneeZ, WALKER);
            line3(lines, x + leg * 22, GROUND + 60, kneeZ, x + leg * 18, GROUND, z + swing, WALKER);
            line3(lines, x + leg * 18 - 12, GROUND, z + swing, x + leg * 18 + 12, GROUND, z + swing, WALKER);
        }
        ents[b + E_SX] = projectX(x, z);
        ents[b + E_SY] = projectY(ATST_HEAD_Y, z);
        ents[b + E_SR] = scale(40, z) + 5;
    }

    /** A tumbling asteroid: an irregular heptagon, turning, with a crease across it. */
    private static void drawAsteroid(short[] lines, int px, int py, int z, int radius, int seed) {
        float turn = (frame + seed) * 0.08f * ((seed & 1) == 0 ? 1 : -1);
        int firstX = 0;
        int firstY = 0;
        int previousX = 0;
        int previousY = 0;
        for (int k = 0; k < 7; k++) {
            float angle = turn + k * 0.8976f;
            int r = radius * (7 + (seed >> k) % 4) / 10;
            int vx = Math.round((float) Math.cos(angle) * r);
            int vy = Math.round((float) Math.sin(angle) * r);
            if (k == 0) {
                firstX = vx;
                firstY = vy;
            } else {
                part(lines, px, py, z, previousX, previousY, vx, vy, ROCK);
            }
            if (k == 3) {
                part(lines, px, py, z, firstX / 2, firstY / 2, vx / 2, vy / 2, ROCK);
            }
            previousX = vx;
            previousY = vy;
        }
        part(lines, px, py, z, previousX, previousY, firstX, firstY, ROCK);
    }

    /** A TIE fighter seen head-on: two hexagonal wings joined to a hexagonal cockpit. */
    private static void drawTie(short[] lines, int px, int py, int z) {
        int wing = 55;
        if (scale(70, z) < 7) {
            part(lines, px, py, z, -wing, 70, -wing, -70, TIE);
            part(lines, px, py, z, wing, 70, wing, -70, TIE);
            part(lines, px, py, z, -wing, 0, wing, 0, TIE);
            return;
        }
        for (int side = -1; side <= 1; side = side + 2) {
            int wx = side * wing;
            int o = side * 14;
            part(lines, px, py, z, wx, 70, wx + o, 35, TIE);
            part(lines, px, py, z, wx + o, 35, wx + o, -35, TIE);
            part(lines, px, py, z, wx + o, -35, wx, -70, TIE);
            part(lines, px, py, z, wx, -70, wx - o, -35, TIE);
            part(lines, px, py, z, wx - o, -35, wx - o, 35, TIE);
            part(lines, px, py, z, wx - o, 35, wx, 70, TIE);
            part(lines, px, py, z, side * 16, 0, wx - o, 0, TIE);
        }
        part(lines, px, py, z, 16, 0, 8, 14, TIE);
        part(lines, px, py, z, 8, 14, -8, 14, TIE);
        part(lines, px, py, z, -8, 14, -16, 0, TIE);
        part(lines, px, py, z, -16, 0, -8, -14, TIE);
        part(lines, px, py, z, -8, -14, 8, -14, TIE);
        part(lines, px, py, z, 8, -14, 16, 0, TIE);
    }

    /** The visible edges of an axis-aligned box: its front face, back face and the four sides. */
    private static void box(short[] lines, int x0, int y0, int z0, int x1, int y1, int z1, int color) {
        line3(lines, x0, y0, z0, x1, y0, z0, color);
        line3(lines, x1, y0, z0, x1, y1, z0, color);
        line3(lines, x1, y1, z0, x0, y1, z0, color);
        line3(lines, x0, y1, z0, x0, y0, z0, color);
        line3(lines, x0, y1, z0, x0, y1, z1, color);
        line3(lines, x1, y1, z0, x1, y1, z1, color);
        line3(lines, x0, y1, z1, x1, y1, z1, color);
        line3(lines, x0, y0, z0, x0, y0, z1, color);
        line3(lines, x1, y0, z0, x1, y0, z1, color);
    }

    /** The twin lasers' beams, travelling from the lower corners to the crosshair. */
    private static void drawShot(short[] lines, int[] ents, int slot) {
        int b = slot * E_STRIDE;
        int tx = ents[b + E_X];
        int ty = ents[b + E_Y];
        int progress = SHOT_FRAMES - ents[b + E_TIMER];
        int from = progress * 22;
        int to = from + 34;
        for (int corner = 0; corner < 2; corner++) {
            int cx = corner == 0 ? 4 : WIDTH - 5;
            int cy = HEIGHT - 5;
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
        int step = (k % 8 + 8) % 8;
        if (step == 0) {
            return 10;
        }
        if (step == 1 || step == 7) {
            return 7;
        }
        if (step == 3 || step == 5) {
            return -7;
        }
        if (step == 4) {
            return -10;
        }
        return 0;
    }

    private static int dirY(int k) {
        return dirX(k + 6);
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

    // ---- Opening and title ----

    /**
     * The opening, after the film: snow falls on Hoth as an Imperial probe streaks down and lands in
     * a flash, then two AT-ATs stride in from the distance to where the title screen shows them.
     */
    private static void opening(short[] lines, int[] ents) {
        TftTouchShield.fillScreen(SPACE);
        shown = 0;
        round = PROBES;
        camX = 0;
        camY = 0;
        travel = 0;
        clear(ents, ENTITIES * E_STRIDE);
        int impactX = 0;
        for (int f = 0; f < POD_FRAMES; f++) {
            frame = f;
            built = 0;
            drawHoth(lines);
            drawSnow(lines, f);
            // The probe's pod: a white-hot head with a fiery tail, falling towards the horizon.
            int x = 300 - f * 5;
            int y = HEADER + 4 + f * (CENTER_Y - HEADER - 6) / POD_FRAMES;
            addLine(lines, x, y, x + 30, y - 16, FIRE_A);
            addLine(lines, x + 1, y + 1, x + 22, y - 10, FIRE_B);
            addLine(lines, x - 1, y, x + 1, y, TftTouchShield.WHITE);
            impactX = x;
            present(lines);
            Delay.millis(30);
        }
        for (int r = 3; r <= 36; r = r + 3) {
            TftTouchShield.drawCircle(impactX, CENTER_Y, r, (r & 4) == 0 ? TftTouchShield.WHITE : FIRE_A);
            Delay.millis(25);
        }
        Delay.millis(200);
        clearView();
        // The walkers: the near one ends at z 1500, the far one at 2300, as on the title screen.
        ents[E_TYPE] = T_ATAT;
        ents[E_X] = 120;
        ents[E_HP] = ATAT_HITS;
        ents[E_FLAG] = -1;
        ents[E_STRIDE + E_TYPE] = T_ATAT;
        ents[E_STRIDE + E_X] = -520;
        ents[E_STRIDE + E_HP] = ATAT_HITS;
        ents[E_STRIDE + E_FLAG] = -1;
        for (int f = 0; f <= WALK_FRAMES; f++) {
            frame = POD_FRAMES + f;
            built = 0;
            drawHoth(lines);
            drawSnow(lines, POD_FRAMES + f);
            ents[E_Z] = FAR + 1400 - (FAR + 1400 - 1500) * f / WALK_FRAMES;
            ents[E_STRIDE + E_Z] = FAR + 900 - (FAR + 900 - 2300) * f / WALK_FRAMES;
            drawAtat(lines, ents, E_STRIDE);
            drawAtat(lines, ents, 0);
            present(lines);
            Delay.millis(30);
        }
        clear(ents, ENTITIES * E_STRIDE);
    }

    /** Snowflakes drifting down and a little sideways, each at its own pace. */
    private static void drawSnow(short[] lines, int f) {
        for (int i = 0; i < 30; i++) {
            int x = (i * 97 + f * (1 + i % 3) / 2) % WIDTH;
            int y = HEADER + (i * 53 + f * (2 + i % 3)) % (HEIGHT - HEADER);
            addLine(lines, x, y, x, y, TftTouchShield.WHITE);
        }
    }

    /**
     * The title screen, over the last frame of the opening: the two title lines assemble letter by
     * letter, their spacing tightening until they lock into place, then flash from white to yellow.
     */
    private static void drawTitle(byte[] letter) {
        // At 27 pixels apart "STRIKES BACK" still fits the screen; any wider and it would wrap.
        for (int spacing = 27; spacing >= 18; spacing = spacing - 3) {
            TftTouchShield.fillRect(0, 28, WIDTH, 56, SPACE);
            spaceOut(TITLE_TOP, 30, spacing, letter);
            spaceOut(TITLE_BOTTOM, 58, spacing, letter);
            Delay.millis(110);
        }
        showCentered(TITLE_TOP, 30, 3, TftTouchShield.WHITE);
        showCentered(TITLE_BOTTOM, 58, 3, TftTouchShield.WHITE);
        Delay.millis(100);
        showCentered(TITLE_TOP, 30, 3, TftTouchShield.YELLOW);
        showCentered(TITLE_BOTTOM, 58, 3, TftTouchShield.YELLOW);
        showCentered("Drag to steer and aim, tap to fire", 204, 1, TftTouchShield.WHITE);
        showCentered("Tap to start", 218, 1, TftTouchShield.CYAN);
    }

    /** One title line at text size 3 with its letters {@code spacing} pixels apart, centered. */
    private static void spaceOut(String text, int y, int spacing, byte[] letter) {
        int left = (WIDTH - (text.length() - 1) * spacing - 18) / 2;
        TftTouchShield.setTextSize(3);
        TftTouchShield.setTextColor(SNOW, SPACE);
        for (int i = 0; i < text.length(); i++) {
            letter[0] = (byte) text.charAt(i);
            TftTouchShield.setCursor(left + i * spacing, y);
            TftTouchShield.print(letter, 1);
        }
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
        TftTouchShield.fillRect(0, 0, WIDTH, HEADER - 1, SPACE);
        TftTouchShield.drawHorizontalLine(0, HEADER - 1, WIDTH, HUD);
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
        TftTouchShield.setTextColor(TftTouchShield.CYAN, SPACE);
        TftTouchShield.setCursor(262, 11);
        TftTouchShield.print(jediWord());
        status = -1;
        drawStatus();
    }

    /** The round's goal in the header's second row. */
    private static void drawStatus() {
        int value;
        if (round == PROBES) {
            value = Math.max(0, killTarget - kills);
        } else if (round == WALKERS) {
            value = Math.max(0, atatsTotal - atatsDown);
        } else {
            value = Math.max(0, (fieldLength - travel) / 100);
        }
        if (value == status) {
            return;
        }
        status = value;
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, SPACE);
        TftTouchShield.setCursor(86, 11);
        if (round == PROBES) {
            TftTouchShield.print("PROBE DROIDS ");
        } else if (round == WALKERS) {
            TftTouchShield.print("AT-ATS LEFT ");
        } else {
            TftTouchShield.print("OUT OF THE FIELD ");
        }
        TftTouchShield.print(value);
        TftTouchShield.print("  ");
    }

    private static void showCentered(String text, int y, int size, int color) {
        TftTouchShield.setTextSize(size);
        TftTouchShield.setTextColor(color, SPACE);
        TftTouchShield.setCursor((WIDTH - text.length() * 6 * size) / 2, y);
        TftTouchShield.print(text);
    }
}
