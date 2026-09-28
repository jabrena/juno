package io.github.jabrena.juno.api.tft;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;

/**
 * Star Trek on the ELEGOO 2.8" TFT touch screen shield, after Sega's 1982 color vector arcade game,
 * in landscape. You command the Enterprise through sector after sector: destroy every Klingon
 * battlecruiser in the sector to move on to the next.
 *
 * <p>The big tactical view on the left shows the sector from above, with the Enterprise in the
 * middle; the small window on the right looks ahead from the bridge. Press and hold anywhere in the
 * tactical view: the Enterprise turns towards your finger and moves ahead on impulse power. The
 * buttons on the right fire the <b>phasers</b> straight ahead (they need a moment to recharge between
 * shots), launch a <b>photon torpedo</b> whose blast destroys everything near it, and <b>warp</b>
 * you far ahead, out of trouble. Photon torpedoes and warp jumps are limited; fly into the starbase
 * to dock, which also restores your shields.
 *
 * <p>The game opens with a starfield rushing past as the Enterprise goes to warp, then the title
 * zooms in and <i>Strategic Operations Simulator</i> types out beneath it; after each game over it
 * starts again from there. Tap to start, then choose who commands the Enterprise: <b>HUMAN</b>
 * (you) or <b>CPU</b>, an autopilot that hunts the nearest enemy, docks when its shields run low
 * and uses every weapon. Tap the header during the game to switch between the two ({@code CPU}
 * shows in the header).
 *
 * <p>Klingons circle you, and sometimes your starbase, firing torpedoes, and a starbase that takes
 * too many hits is lost. From sector 2, anti-matter saucers home in on the Enterprise and drain its
 * shields if they touch it; every fourth sector the space probe Nomad roams the sector laying mines.
 * Every hit takes some of your shields; when they are gone, so is the Enterprise.
 *
 * <p>Everything is drawn in vector style with the double display lists of {@link StarWars}, clipped
 * to whichever view is being drawn: only lines that changed since the previous frame are erased and
 * drawn again. Positions are fixed point (1/16 unit) in a sector that wraps around at the edges.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class StarTrek {
    private static final int FRAME_MILLIS = 40;
    private static final int FIX = 16;

    // Screen: landscape, header on top, tactical view on the left, bridge view and buttons on the right.
    private static final int WIDTH = 320;
    private static final int HEIGHT = 240;
    private static final int HEADER = 20;
    private static final int TACTICAL_RIGHT = 219;
    private static final int TACTICAL_X = 110;
    private static final int TACTICAL_Y = 130;
    /** World units per pixel in the tactical view. */
    private static final int ZOOM = 2;
    private static final int PANEL_X = 222;
    private static final int BRIDGE_TOP = 21;
    private static final int BRIDGE_BOTTOM = 98;
    private static final int BRIDGE_X = 271;
    private static final int BRIDGE_Y = 60;
    private static final int BRIDGE_FOCAL = 60;
    private static final int BUTTON_Y = 102;
    private static final int BUTTON_HEIGHT = 44;
    // The opening: stars (x, y, depth, and the streak drawn last frame) rushing out from the center.
    private static final int INTRO_STARS = 32;
    private static final int S_X = 0;
    private static final int S_Y = 1;
    private static final int S_Z = 2;
    private static final int S_TAIL_X = 3;
    private static final int S_TAIL_Y = 4;
    private static final int S_HEAD_X = 5;
    private static final int S_HEAD_Y = 6;
    private static final int S_STRIDE = 7;
    private static final int INTRO_FRAMES = 70;
    private static final int INTRO_DEPTH = 1024;
    private static final int INTRO_FOCAL = 64;
    private static final String SUBTITLE = "STRATEGIC OPERATIONS SIMULATOR";
    /** The HUMAN and CPU buttons of the pilot screen. */
    private static final int CHOICE_X = 20;
    private static final int CHOICE_Y = 104;
    private static final int CHOICE_WIDTH = 130;
    private static final int CHOICE_HEIGHT = 72;
    private static final int CHOICE_GAP = 20;
    /** {@link #pressed} while a finger is on the header. */
    private static final int HEADER_PRESSED = 3;

    /** The sector wraps around: its size in world units. */
    private static final int SECTOR = 2048;
    private static final int SECTOR_FIX = SECTOR * FIX;

    // Display lists: two of MAX_LINES lines (x0, y0, x1, y1, color), the one on screen and the next.
    private static final int MAX_LINES = 200;
    private static final int L_STRIDE = 5;
    private static final int LIST_SIZE = MAX_LINES * L_STRIDE;

    // Entities. Positions and velocities in world units x FIX; heading in degrees clockwise from up.
    private static final int ENTITIES = 24;
    private static final int E_TYPE = 0;
    private static final int E_X = 1;
    private static final int E_Y = 2;
    private static final int E_VX = 3;
    private static final int E_VY = 4;
    private static final int E_HEADING = 5;
    private static final int E_TIMER = 6;
    private static final int E_HP = 7;
    /** Klingon: 1 when attacking the starbase. Debris: its size. */
    private static final int E_AUX = 8;
    /** Klingon and Nomad: frames until the next torpedo or mine. */
    private static final int E_COOL = 9;
    private static final int E_STRIDE = 10;

    private static final int T_NONE = 0;
    private static final int T_KLINGON = 1;
    private static final int T_TORPEDO = 2;
    private static final int T_PHOTON = 3;
    private static final int T_STARBASE = 4;
    private static final int T_SAUCER = 5;
    private static final int T_NOMAD = 6;
    private static final int T_MINE = 7;
    private static final int T_DEBRIS = 8;

    // The Enterprise.
    private static final int MAX_SPEED = 4 * FIX;
    private static final int THRUST = 5;
    private static final int TURN = 6;
    private static final int PHASER_RANGE = 320;
    private static final int PHASER_COOLDOWN = 9;
    private static final int PHASER_FRAMES = 3;
    private static final int PHOTON_SPEED = 11;
    private static final int PHOTON_FRAMES = 55;
    private static final int PHOTON_BLAST = 70;
    private static final int MAX_PHOTONS = 5;
    private static final int MAX_WARPS = 3;
    private static final int WARP_DISTANCE = 700;
    private static final int DOCK_RANGE = 40;

    // The CPU pilot.
    private static final int DOCK_SHIELDS = 50;
    private static final int HOLD_OFF = 200;
    private static final int AIM = 12;
    private static final int PHOTON_EVERY = 90;
    private static final int WARP_SHIELDS = 30;
    private static final int WARP_THREAT = 80;

    // Enemies.
    private static final int KLINGON_HP = 2;
    private static final int KLINGON_SPEED = 38;
    private static final int KLINGON_TURN = 4;
    private static final int BREAK_DISTANCE = 110;
    private static final int BREAK_FRAMES = 45;
    private static final int TORPEDO_SPEED = 5;
    private static final int TORPEDO_FRAMES = 110;
    private static final int STARBASE_HP = 6;
    private static final int NOMAD_HP = 4;
    private static final int HIT_RADIUS = 14;

    // Shield damage, in percent.
    private static final int TORPEDO_DAMAGE = 10;
    private static final int SAUCER_DAMAGE = 8;
    private static final int MINE_DAMAGE = 12;

    // Scoring.
    private static final int KLINGON_POINTS = 1000;
    private static final int SAUCER_POINTS = 500;
    private static final int MINE_POINTS = 100;
    private static final int TORPEDO_POINTS = 50;
    private static final int NOMAD_POINTS = 5000;
    private static final int SECTOR_BONUS = 5000;

    // Colors.
    private static final int SPACE = TftTouchShield.BLACK;
    private static final int STAR = 0x7BEF;
    private static final int ENTERPRISE = TftTouchShield.WHITE;
    private static final int KLINGON = TftTouchShield.GREEN;
    private static final int TORPEDO = TftTouchShield.RED;
    private static final int PHOTON = TftTouchShield.ORANGE;
    private static final int PHASER = TftTouchShield.YELLOW;
    private static final int STARBASE = TftTouchShield.CYAN;
    private static final int SAUCER = TftTouchShield.MAGENTA;
    private static final int NOMAD = 0xFD20;
    private static final int MINE = TftTouchShield.RED;
    private static final int FRAME = 0x3A7F;
    private static final int BUTTON = 0x2124;
    private static final int BUTTON_EMPTY = 0x18C3;

    private static int score;
    private static int best;
    private static int sector;
    private static int frame;
    private static boolean dead;

    // The Enterprise, in world units x FIX.
    private static int shipX;
    private static int shipY;
    private static int shipVX;
    private static int shipVY;
    private static int heading;
    private static int shields;
    private static int photons;
    private static int warps;
    private static int phaserCooldown;
    private static int phaserFrames;
    private static int phaserLength;
    private static boolean docked;
    private static boolean baseLost;

    // Input.
    private static boolean steering;
    private static int steerX;
    private static int steerY;
    private static int pressed = -1;
    private static boolean autopilot;

    // Display list state and the clip rectangle of the view being drawn.
    private static int front;
    private static int shown;
    private static int built;
    private static int clipLeft;
    private static int clipTop;
    private static int clipRight;
    private static int clipBottom;
    private static int status;
    private static int shownScore;
    private static boolean headerFlashed;

    private StarTrek() {
    }

    public static void main(String[] args) {
        short[] lines = new short[2 * LIST_SIZE];
        int[] ents = new int[ENTITIES * E_STRIDE];
        byte[] letter = new byte[1];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);

        while (true) {
            warpIntro(ents);
            drawTitle(lines, ents, letter);
            waitForTap();
            Random.seed(Clock.micros());
            choosePilot();
            score = 0;
            sector = 1;
            shields = 100;
            photons = MAX_PHOTONS;
            warps = MAX_WARPS;
            while (playSector(lines, ents)) {
                sector = sector + 1;
            }
            if (score > best) {
                best = score;
            }
            drawHeader(ents);
            clearView();
            showCentered("GAME OVER", 100, 3, TftTouchShield.RED);
            Delay.millis(3000);
        }
    }

    // ---- Game flow ----

    /** Plays one sector; returns false when the Enterprise is destroyed. */
    private static boolean playSector(short[] lines, int[] ents) {
        startSector(ents);
        drawHeader(ents);
        clearView();
        showCentered("SECTOR", 84, 3, TftTouchShield.YELLOW);
        TftTouchShield.setCursor(sector < 10 ? 151 : 142, 114);
        TftTouchShield.print(sector);
        if (sector % 4 == 0) {
            showCentered("Nomad is laying mines", 150, 1, NOMAD);
        } else {
            showCentered("Destroy the Klingons", 150, 1, TftTouchShield.WHITE);
        }
        Delay.millis(1500);
        clearView();
        drawPanel();

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
            render(lines, ents);
            if (dead) {
                explodeEnterprise();
                return false;
            }
            if (sectorCleared(ents)) {
                finishSector();
                return true;
            }
            if (headerFlashed) {
                drawHeader(ents);
            } else {
                drawStatus(ents);
            }
        }
    }

    private static void startSector(int[] ents) {
        dead = false;
        docked = false;
        baseLost = false;
        clear(ents, ENTITIES * E_STRIDE);
        shipX = SECTOR_FIX / 2;
        shipY = SECTOR_FIX / 2;
        shipVX = 0;
        shipVY = 0;
        heading = 0;
        phaserCooldown = 0;
        phaserFrames = 0;
        steering = false;
        // The starbase, some way from where the Enterprise arrives.
        spawn(ents, T_STARBASE, shipX + Random.nextInt(-300, 301) * FIX, shipY + 350 * FIX, STARBASE_HP);
        int klingons = Math.min(2 + sector, 8);
        if (sector % 4 == 0) {
            klingons = Math.min(1 + sector / 4, 4);
            spawnAway(ents, T_NOMAD, NOMAD_HP);
        }
        for (int i = 0; i < klingons; i++) {
            int slot = spawnAway(ents, T_KLINGON, KLINGON_HP);
            if (slot >= 0) {
                int b = slot * E_STRIDE;
                ents[b + E_AUX] = Random.nextInt(100) < 25 ? 1 : 0;
                ents[b + E_COOL] = Random.nextInt(40, 100);
                ents[b + E_HEADING] = Random.nextInt(360);
                ents[b + E_TIMER] = 0;
            }
        }
        if (sector >= 2) {
            for (int i = 0; i < Math.min(sector / 2, 4); i++) {
                spawnAway(ents, T_SAUCER, 1);
            }
        }
    }

    private static boolean sectorCleared(int[] ents) {
        return count(ents, T_KLINGON) + count(ents, T_NOMAD) == 0;
    }

    private static void finishSector() {
        int bonus = SECTOR_BONUS + shields * 50;
        score = score + bonus;
        clearView();
        showCentered("SECTOR CLEARED", 90, 2, TftTouchShield.YELLOW);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, SPACE);
        TftTouchShield.setCursor(118, 122);
        TftTouchShield.print("BONUS ");
        TftTouchShield.print(bonus);
        showCentered("Warping to the next sector", 142, 1, STARBASE);
        // The warp: streaks rushing out from the center.
        for (int k = 0; k < 12; k++) {
            int r = 10 + k * 10;
            for (int d = 0; d < 8; d++) {
                int dx = dirX(d);
                int dy = dirY(d);
                drawLine(160 + dx * r / 10, 180 + dy * r / 30, 160 + dx * (r + 8) / 10, 180 + dy * (r + 8) / 30, STAR);
            }
            Delay.millis(40);
        }
        Delay.millis(900);
    }

    private static void explodeEnterprise() {
        for (int r = 4; r < 60; r = r + 4) {
            TftTouchShield.drawCircle(TACTICAL_X, TACTICAL_Y, r, (r & 4) == 0 ? TftTouchShield.YELLOW : TftTouchShield.RED);
            Delay.millis(30);
        }
        Delay.millis(600);
    }

    /** One hit on the Enterprise: shields drop, and at zero the Enterprise is lost. */
    private static void damage(int percent) {
        shields = Math.max(0, shields - percent);
        if (shields == 0) {
            dead = true;
        }
        TftTouchShield.fillRect(0, 0, WIDTH, HEADER - 1, TftTouchShield.RED);
        Delay.millis(40);
        headerFlashed = true;
    }

    // ---- Input ----

    /**
     * Holding a finger in the tactical view turns the Enterprise towards it and runs the impulse
     * engines; the buttons act when pressed. Tapping the header hands the helm to the CPU or back.
     */
    private static void handleTouch(int[] ents) {
        if (!TftTouchShield.readTouch()) {
            if (!autopilot) {
                steering = false;
            }
            pressed = -1;
            return;
        }
        int x = TftTouchShield.touchX();
        int y = TftTouchShield.touchY();
        if (y < HEADER) {
            if (pressed != HEADER_PRESSED) {
                autopilot = !autopilot;
                steering = false;
                status = -1;
            }
            pressed = HEADER_PRESSED;
            return;
        }
        if (autopilot) {
            pressed = -1;
            return;
        }
        if (x <= TACTICAL_RIGHT) {
            steering = true;
            steerX = x;
            steerY = y;
            pressed = -1;
            return;
        }
        steering = false;
        int button = -1;
        if (x >= PANEL_X && y >= BUTTON_Y) {
            button = Math.min(2, (y - BUTTON_Y) / BUTTON_HEIGHT);
        }
        if (button >= 0 && button != pressed) {
            if (button == 0) {
                firePhasers(ents);
            } else if (button == 1) {
                firePhoton(ents);
            } else {
                warp(ents);
            }
        }
        pressed = button;
    }

    /** The pilot screen: waits for a tap on HUMAN or CPU. */
    private static void choosePilot() {
        TftTouchShield.fillScreen(SPACE);
        showCentered("CHOOSE PILOT", 36, 3, TftTouchShield.YELLOW);
        showCentered("Who commands the Enterprise?", 74, 1, TftTouchShield.WHITE);
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
        int color = chosen ? FRAME : BUTTON;
        TftTouchShield.fillRect(x, CHOICE_Y, CHOICE_WIDTH, CHOICE_HEIGHT, color);
        TftTouchShield.drawRect(x, CHOICE_Y, CHOICE_WIDTH, CHOICE_HEIGHT, chosen ? TftTouchShield.WHITE : FRAME);
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
     * The CPU at the helm: it steers at the nearest enemy ship, or at the starbase to dock when the
     * shields run low, but holds off at phaser range instead of ramming it. It fires phasers when the
     * target is dead ahead, adds a photon torpedo now and then, and warps away when a Klingon
     * torpedo is about to finish off the shields.
     */
    private static void flyAutopilot(int[] ents) {
        if (warps > 0 && shields <= WARP_SHIELDS && nearestDistance(ents, T_TORPEDO) < WARP_THREAT) {
            warp(ents);
            return;
        }
        int base = find(ents, T_STARBASE);
        boolean needDock = shields < DOCK_SHIELDS && base >= 0 && !docked;
        int target = -1;
        int nearest = SECTOR;
        for (int slot = 0; slot < ENTITIES; slot++) {
            int type = ents[slot * E_STRIDE + E_TYPE];
            boolean wanted = needDock ? slot == base : type == T_KLINGON || type == T_NOMAD || type == T_SAUCER;
            if (wanted) {
                int d = distanceToShip(ents, slot * E_STRIDE);
                if (d < nearest) {
                    target = slot;
                    nearest = d;
                }
            }
        }
        if (target < 0) {
            steering = false;
            return;
        }
        int b = target * E_STRIDE;
        int dx = wrap(ents[b + E_X] - shipX) / FIX;
        int dy = wrap(ents[b + E_Y] - shipY) / FIX;
        int off = Math.abs(angleBetween(heading, bearing(dx, dy)));
        steering = needDock || nearest > HOLD_OFF || off > 20;
        steerX = TACTICAL_X + dx;
        steerY = TACTICAL_Y + dy;
        if (!needDock && off < AIM && nearest < PHASER_RANGE) {
            firePhasers(ents);
            if (nearest > 120 && frame % PHOTON_EVERY == 0) {
                firePhoton(ents);
            }
        }
    }

    /** The distance to the nearest object of a type, or the size of the sector when there is none. */
    private static int nearestDistance(int[] ents, int type) {
        int nearest = SECTOR;
        for (int slot = 0; slot < ENTITIES; slot++) {
            if (ents[slot * E_STRIDE + E_TYPE] == type) {
                nearest = Math.min(nearest, distanceToShip(ents, slot * E_STRIDE));
            }
        }
        return nearest;
    }

    // ---- The Enterprise ----

    private static void steer() {
        if (!steering) {
            // Without impulse power the Enterprise coasts to a stop.
            shipVX = shipVX * 15 / 16;
            shipVY = shipVY * 15 / 16;
            return;
        }
        int want = bearing(steerX - TACTICAL_X, steerY - TACTICAL_Y);
        heading = normalize(heading + clamp(angleBetween(heading, want), -TURN, TURN));
        float radians = (float) Math.toRadians(heading);
        shipVX = shipVX + Math.round((float) Math.sin(radians) * THRUST);
        shipVY = shipVY - Math.round((float) Math.cos(radians) * THRUST);
        int speed = (int) Math.sqrt((float) shipVX * shipVX + (float) shipVY * shipVY);
        if (speed > MAX_SPEED) {
            shipVX = shipVX * MAX_SPEED / speed;
            shipVY = shipVY * MAX_SPEED / speed;
        }
    }

    /**
     * Phasers strike the nearest enemy in a narrow beam straight ahead, within range, and need
     * {@value #PHASER_COOLDOWN} frames to recharge.
     */
    private static void firePhasers(int[] ents) {
        if (phaserCooldown > 0) {
            return;
        }
        phaserCooldown = PHASER_COOLDOWN;
        phaserFrames = PHASER_FRAMES;
        phaserLength = PHASER_RANGE;
        float radians = (float) Math.toRadians(heading);
        float hx = (float) Math.sin(radians);
        float hy = -(float) Math.cos(radians);
        int target = -1;
        int nearest = PHASER_RANGE + 1;
        for (int slot = 0; slot < ENTITIES; slot++) {
            int b = slot * E_STRIDE;
            if (!isHostile(ents[b + E_TYPE])) {
                continue;
            }
            float dx = wrap(ents[b + E_X] - shipX) / (float) FIX;
            float dy = wrap(ents[b + E_Y] - shipY) / (float) FIX;
            int ahead = Math.round(dx * hx + dy * hy);
            int side = Math.round(dx * -hy + dy * hx);
            if (ahead > 0 && ahead < nearest && Math.abs(side) < HIT_RADIUS + radius(ents[b + E_TYPE])) {
                nearest = ahead;
                target = slot;
            }
        }
        if (target >= 0) {
            phaserLength = nearest;
            hit(ents, target, 1);
        }
    }

    private static void firePhoton(int[] ents) {
        if (photons == 0) {
            return;
        }
        float radians = (float) Math.toRadians(heading);
        int slot = spawn(ents, T_PHOTON, shipX, shipY, 1);
        if (slot < 0) {
            return;
        }
        photons = photons - 1;
        int b = slot * E_STRIDE;
        ents[b + E_VX] = shipVX + Math.round((float) Math.sin(radians) * PHOTON_SPEED * FIX);
        ents[b + E_VY] = shipVY - Math.round((float) Math.cos(radians) * PHOTON_SPEED * FIX);
        ents[b + E_TIMER] = PHOTON_FRAMES;
        drawPanel();
    }

    /** Jumps the Enterprise far ahead, out of trouble. */
    private static void warp(int[] ents) {
        if (warps == 0) {
            return;
        }
        warps = warps - 1;
        float radians = (float) Math.toRadians(heading);
        shipX = wrapPosition(shipX + Math.round((float) Math.sin(radians) * WARP_DISTANCE * FIX));
        shipY = wrapPosition(shipY - Math.round((float) Math.cos(radians) * WARP_DISTANCE * FIX));
        TftTouchShield.fillRect(0, HEADER, TACTICAL_RIGHT + 1, HEIGHT - HEADER, TftTouchShield.WHITE);
        Delay.millis(40);
        clearView();
        drawPanel();
    }

    // ---- Simulation ----

    private static void step(int[] ents) {
        steer();
        shipX = wrapPosition(shipX + shipVX);
        shipY = wrapPosition(shipY + shipVY);
        if (phaserCooldown > 0) {
            phaserCooldown = phaserCooldown - 1;
        }
        if (phaserFrames > 0) {
            phaserFrames = phaserFrames - 1;
        }
        for (int slot = 0; slot < ENTITIES; slot++) {
            int b = slot * E_STRIDE;
            int type = ents[b + E_TYPE];
            if (type == T_NONE) {
                continue;
            }
            ents[b + E_X] = wrapPosition(ents[b + E_X] + ents[b + E_VX]);
            ents[b + E_Y] = wrapPosition(ents[b + E_Y] + ents[b + E_VY]);
            if (type == T_KLINGON) {
                flyKlingon(ents, b);
            } else if (type == T_SAUCER) {
                flySaucer(ents, b);
            } else if (type == T_NOMAD) {
                flyNomad(ents, b);
            } else if (type == T_TORPEDO) {
                flyTorpedo(ents, b);
            } else if (type == T_PHOTON) {
                flyPhoton(ents, slot);
            } else if (type == T_MINE) {
                if (distanceToShip(ents, b) < HIT_RADIUS + 6) {
                    ents[b + E_TYPE] = T_NONE;
                    damage(MINE_DAMAGE);
                }
            } else if (type == T_STARBASE) {
                if (!docked && distanceToShip(ents, b) < DOCK_RANGE) {
                    dock();
                }
            } else if (type == T_DEBRIS) {
                ents[b + E_TIMER] = ents[b + E_TIMER] - 1;
                if (ents[b + E_TIMER] <= 0) {
                    ents[b + E_TYPE] = T_NONE;
                }
            }
        }
    }

    /** Docking restores shields, photon torpedoes and warp jumps, once per sector. */
    private static void dock() {
        docked = true;
        shields = 100;
        photons = MAX_PHOTONS;
        warps = MAX_WARPS;
        status = -1;
        drawPanel();
    }

    /**
     * A Klingon makes attack passes at its target (the Enterprise, or sometimes the starbase): it
     * closes in, breaks away once close, and comes round again, firing a torpedo whenever it is
     * facing its target and its tubes are loaded.
     */
    private static void flyKlingon(int[] ents, int b) {
        int target = ents[b + E_AUX] == 1 ? find(ents, T_STARBASE) : -1;
        int tx = shipX;
        int ty = shipY;
        if (target >= 0) {
            tx = ents[target * E_STRIDE + E_X];
            ty = ents[target * E_STRIDE + E_Y];
        }
        int dx = wrap(tx - ents[b + E_X]) / FIX;
        int dy = wrap(ty - ents[b + E_Y]) / FIX;
        int distance = (int) Math.sqrt((float) dx * dx + (float) dy * dy);
        int toTarget = bearing(dx, dy);
        int want = toTarget;
        if (ents[b + E_TIMER] > 0) {
            // Breaking away after a pass, before coming round for the next one.
            ents[b + E_TIMER] = ents[b + E_TIMER] - 1;
            want = normalize(toTarget + 150);
        } else if (distance < BREAK_DISTANCE) {
            ents[b + E_TIMER] = BREAK_FRAMES;
        } else if (distance < 260) {
            want = normalize(toTarget + 20);
        }
        int h = normalize(ents[b + E_HEADING] + clamp(angleBetween(ents[b + E_HEADING], want), -KLINGON_TURN,
                KLINGON_TURN));
        ents[b + E_HEADING] = h;
        float radians = (float) Math.toRadians(h);
        int speed = KLINGON_SPEED + 2 * Math.min(sector, 8);
        ents[b + E_VX] = Math.round((float) Math.sin(radians) * speed);
        ents[b + E_VY] = -Math.round((float) Math.cos(radians) * speed);
        ents[b + E_COOL] = ents[b + E_COOL] - 1;
        boolean facing = Math.abs(angleBetween(h, toTarget)) < 35;
        if (ents[b + E_COOL] <= 0 && facing && distance < 420) {
            fireTorpedo(ents, ents[b + E_X], ents[b + E_Y], toTarget);
            ents[b + E_COOL] = Math.max(35, Random.nextInt(60, 110) - 4 * sector);
        }
    }

    private static void fireTorpedo(int[] ents, int x, int y, int direction) {
        int slot = spawn(ents, T_TORPEDO, x, y, 1);
        if (slot < 0) {
            return;
        }
        int b = slot * E_STRIDE;
        float radians = (float) Math.toRadians(direction);
        ents[b + E_VX] = Math.round((float) Math.sin(radians) * TORPEDO_SPEED * FIX);
        ents[b + E_VY] = -Math.round((float) Math.cos(radians) * TORPEDO_SPEED * FIX);
        ents[b + E_TIMER] = TORPEDO_FRAMES;
    }

    /** Klingon torpedoes hit the Enterprise or the starbase, or burn out. */
    private static void flyTorpedo(int[] ents, int b) {
        ents[b + E_TIMER] = ents[b + E_TIMER] - 1;
        if (ents[b + E_TIMER] <= 0) {
            ents[b + E_TYPE] = T_NONE;
            return;
        }
        if (distanceToShip(ents, b) < HIT_RADIUS) {
            ents[b + E_TYPE] = T_NONE;
            damage(TORPEDO_DAMAGE);
            return;
        }
        int base = find(ents, T_STARBASE);
        if (base >= 0 && distance(ents, b, base * E_STRIDE) < radius(T_STARBASE)) {
            ents[b + E_TYPE] = T_NONE;
            int s = base * E_STRIDE;
            ents[s + E_HP] = ents[s + E_HP] - 1;
            if (ents[s + E_HP] <= 0) {
                baseLost = true;
                debris(ents, ents[s + E_X], ents[s + E_Y], 40);
                ents[s + E_TYPE] = T_NONE;
                status = -1;
            }
        }
    }

    /** Anti-matter saucers home in on the Enterprise; touching it drains its shields. */
    private static void flySaucer(int[] ents, int b) {
        int dx = wrap(shipX - ents[b + E_X]);
        int dy = wrap(shipY - ents[b + E_Y]);
        float length = (float) Math.sqrt((float) dx * dx + (float) dy * dy);
        if (length > 1) {
            float speed = 22 + 3 * Math.min(sector, 8);
            ents[b + E_VX] = Math.round(dx * speed / length);
            ents[b + E_VY] = Math.round(dy * speed / length);
        }
        if (length / FIX < HIT_RADIUS + 6) {
            ents[b + E_TYPE] = T_NONE;
            damage(SAUCER_DAMAGE);
        }
    }

    /** Nomad wanders the sector, turning now and then, and lays a mine every so often. */
    private static void flyNomad(int[] ents, int b) {
        ents[b + E_TIMER] = ents[b + E_TIMER] - 1;
        if (ents[b + E_TIMER] <= 0) {
            ents[b + E_HEADING] = Random.nextInt(360);
            ents[b + E_TIMER] = Random.nextInt(40, 100);
        }
        float radians = (float) Math.toRadians(ents[b + E_HEADING]);
        ents[b + E_VX] = Math.round((float) Math.sin(radians) * 24);
        ents[b + E_VY] = -Math.round((float) Math.cos(radians) * 24);
        ents[b + E_COOL] = ents[b + E_COOL] - 1;
        if (ents[b + E_COOL] <= 0 && count(ents, T_MINE) < 8) {
            spawn(ents, T_MINE, ents[b + E_X], ents[b + E_Y], 1);
            ents[b + E_COOL] = Math.max(30, 70 - 3 * sector);
        }
    }

    /** A photon torpedo flies straight until it meets an enemy or burns out, then its blast hits all around. */
    private static void flyPhoton(int[] ents, int slot) {
        int b = slot * E_STRIDE;
        ents[b + E_TIMER] = ents[b + E_TIMER] - 1;
        boolean contact = ents[b + E_TIMER] <= 0;
        for (int other = 0; other < ENTITIES && !contact; other++) {
            int o = other * E_STRIDE;
            if (isHostile(ents[o + E_TYPE]) && distance(ents, b, o) < HIT_RADIUS + radius(ents[o + E_TYPE])) {
                contact = true;
            }
        }
        if (!contact) {
            return;
        }
        int x = ents[b + E_X];
        int y = ents[b + E_Y];
        ents[b + E_TYPE] = T_NONE;
        for (int other = 0; other < ENTITIES; other++) {
            int o = other * E_STRIDE;
            if (isHostile(ents[o + E_TYPE]) && distance(ents, b, o) < PHOTON_BLAST + radius(ents[o + E_TYPE])) {
                hit(ents, other, 3);
            }
        }
        debris(ents, x, y, PHOTON_BLAST);
    }

    /** Damages a hostile; destroyed ones score and leave debris. */
    private static void hit(int[] ents, int slot, int damage) {
        int b = slot * E_STRIDE;
        int type = ents[b + E_TYPE];
        ents[b + E_HP] = ents[b + E_HP] - damage;
        if (ents[b + E_HP] > 0) {
            return;
        }
        ents[b + E_TYPE] = T_NONE;
        int points = TORPEDO_POINTS;
        int size = 12;
        if (type == T_KLINGON) {
            points = KLINGON_POINTS;
            size = 30;
        } else if (type == T_SAUCER) {
            points = SAUCER_POINTS;
            size = 16;
        } else if (type == T_NOMAD) {
            points = NOMAD_POINTS;
            size = 40;
        } else if (type == T_MINE) {
            points = MINE_POINTS;
        }
        score = score + points;
        debris(ents, ents[b + E_X], ents[b + E_Y], size);
        status = -1;
    }

    private static void debris(int[] ents, int x, int y, int size) {
        int slot = spawn(ents, T_DEBRIS, x, y, 1);
        if (slot >= 0) {
            ents[slot * E_STRIDE + E_TIMER] = 8;
            ents[slot * E_STRIDE + E_AUX] = size;
        }
    }

    private static boolean isHostile(int type) {
        return type == T_KLINGON || type == T_TORPEDO || type == T_SAUCER || type == T_NOMAD || type == T_MINE;
    }

    /** A rough radius of each kind of object, in world units. */
    private static int radius(int type) {
        if (type == T_KLINGON) {
            return 14;
        }
        if (type == T_STARBASE) {
            return 26;
        }
        if (type == T_NOMAD) {
            return 12;
        }
        if (type == T_SAUCER) {
            return 9;
        }
        return 4;
    }

    // ---- Geometry ----

    /** The shortest signed difference across the wrapping sector, in world units x FIX. */
    private static int wrap(int delta) {
        int d = delta % SECTOR_FIX;
        if (d > SECTOR_FIX / 2) {
            d = d - SECTOR_FIX;
        } else if (d < -SECTOR_FIX / 2) {
            d = d + SECTOR_FIX;
        }
        return d;
    }

    private static int wrapPosition(int position) {
        return ((position % SECTOR_FIX) + SECTOR_FIX) % SECTOR_FIX;
    }

    private static int distance(int[] ents, int a, int b) {
        int dx = wrap(ents[a + E_X] - ents[b + E_X]) / FIX;
        int dy = wrap(ents[a + E_Y] - ents[b + E_Y]) / FIX;
        return (int) Math.sqrt((float) dx * dx + (float) dy * dy);
    }

    private static int distanceToShip(int[] ents, int b) {
        int dx = wrap(ents[b + E_X] - shipX) / FIX;
        int dy = wrap(ents[b + E_Y] - shipY) / FIX;
        return (int) Math.sqrt((float) dx * dx + (float) dy * dy);
    }

    /** Compass bearing of (dx, dy) in degrees, clockwise from up (screen y points down). */
    private static int bearing(int dx, int dy) {
        return normalize(Math.round((float) Math.toDegrees(Math.atan2(dx, -dy))));
    }

    /** The signed turn from one bearing to another, -180..180. */
    private static int angleBetween(int from, int to) {
        int d = normalize(to - from);
        return d > 180 ? d - 360 : d;
    }

    private static int normalize(int degrees) {
        return ((degrees % 360) + 360) % 360;
    }

    // ---- Rendering ----

    private static void render(short[] lines, int[] ents) {
        built = 0;
        setClip(0, HEADER, TACTICAL_RIGHT, HEIGHT - 1);
        drawTacticalStars(lines);
        for (int slot = 0; slot < ENTITIES; slot++) {
            drawTactical(lines, ents, slot);
        }
        drawEnterprise(lines);
        if (phaserFrames > 0) {
            float radians = (float) Math.toRadians(heading);
            int length = phaserLength / ZOOM;
            addLine(lines, TACTICAL_X, TACTICAL_Y, TACTICAL_X + Math.round((float) Math.sin(radians) * length),
                    TACTICAL_Y - Math.round((float) Math.cos(radians) * length), PHASER);
        }
        setClip(PANEL_X + 1, BRIDGE_TOP + 1, WIDTH - 2, BRIDGE_BOTTOM - 1);
        drawBridge(lines, ents);
        present(lines);
    }

    /** Stars fixed in the sector, so the tactical view shows the Enterprise's motion. */
    private static void drawTacticalStars(short[] lines) {
        for (int i = 0; i < 26; i++) {
            int sx = (i * 797 + 131) % SECTOR * FIX;
            int sy = (i * 523 + 37 * i * i) % SECTOR * FIX;
            int px = TACTICAL_X + wrap(sx - shipX) / FIX / ZOOM;
            int py = TACTICAL_Y + wrap(sy - shipY) / FIX / ZOOM;
            addLine(lines, px, py, px, py, STAR);
        }
    }

    private static void drawTactical(short[] lines, int[] ents, int slot) {
        int b = slot * E_STRIDE;
        int type = ents[b + E_TYPE];
        if (type == T_NONE) {
            return;
        }
        int px = TACTICAL_X + wrap(ents[b + E_X] - shipX) / FIX / ZOOM;
        int py = TACTICAL_Y + wrap(ents[b + E_Y] - shipY) / FIX / ZOOM;
        if (px < -40 || px > TACTICAL_RIGHT + 40 || py < -40 || py > HEIGHT + 40) {
            return;
        }
        if (type == T_KLINGON) {
            drawKlingonTop(lines, px, py, ents[b + E_HEADING]);
        } else if (type == T_STARBASE) {
            for (int k = 0; k < 8; k++) {
                addLine(lines, px + dirX(k) * 13 / 10, py + dirY(k) * 13 / 10, px + dirX(k + 1) * 13 / 10,
                        py + dirY(k + 1) * 13 / 10, STARBASE);
            }
            addLine(lines, px - 18, py, px + 18, py, STARBASE);
            addLine(lines, px, py - 18, px, py + 18, STARBASE);
        } else if (type == T_SAUCER) {
            int s = (frame + slot) % 2 == 0 ? 5 : 3;
            addLine(lines, px - 6, py, px, py - s, SAUCER);
            addLine(lines, px, py - s, px + 6, py, SAUCER);
            addLine(lines, px + 6, py, px, py + s, SAUCER);
            addLine(lines, px, py + s, px - 6, py, SAUCER);
        } else if (type == T_NOMAD) {
            addLine(lines, px - 6, py - 6, px + 6, py - 6, NOMAD);
            addLine(lines, px + 6, py - 6, px + 6, py + 6, NOMAD);
            addLine(lines, px + 6, py + 6, px - 6, py + 6, NOMAD);
            addLine(lines, px - 6, py + 6, px - 6, py - 6, NOMAD);
            addLine(lines, px - 3, py, px + 3, py, NOMAD);
        } else if (type == T_MINE) {
            addLine(lines, px - 2, py - 2, px + 2, py + 2, MINE);
            addLine(lines, px - 2, py + 2, px + 2, py - 2, MINE);
        } else if (type == T_TORPEDO || type == T_PHOTON) {
            int color = type == T_TORPEDO ? TORPEDO : PHOTON;
            int s = (frame & 1) == 0 ? 3 : 2;
            addLine(lines, px - s, py, px + s, py, color);
            addLine(lines, px, py - s, px, py + s, color);
        } else if (type == T_DEBRIS) {
            int size = ents[b + E_AUX] * (9 - ents[b + E_TIMER]) / 8 / ZOOM + 2;
            for (int k = 0; k < 8; k++) {
                addLine(lines, px + dirX(k) * size / 20, py + dirY(k) * size / 20, px + dirX(k) * size / 10,
                        py + dirY(k) * size / 10, (k & 1) == 0 ? TftTouchShield.YELLOW : TftTouchShield.ORANGE);
            }
        }
    }

    /** The Enterprise from above, in the middle of the tactical view: saucer, neck, hull, nacelles. */
    private static void drawEnterprise(short[] lines) {
        float radians = (float) Math.toRadians(heading);
        int c = Math.round((float) Math.cos(radians) * 64);
        int s = Math.round((float) Math.sin(radians) * 64);
        for (int k = 0; k < 8; k++) {
            shipLine(lines, c, s, dirX(k) * 7 / 10, dirY(k) * 7 / 10 - 5, dirX(k + 1) * 7 / 10, dirY(k + 1) * 7 / 10 - 5,
                    ENTERPRISE);
        }
        shipLine(lines, c, s, 0, 2, 0, 7, ENTERPRISE);
        shipLine(lines, c, s, -2, 7, 2, 7, ENTERPRISE);
        shipLine(lines, c, s, -2, 7, -2, 12, ENTERPRISE);
        shipLine(lines, c, s, 2, 7, 2, 12, ENTERPRISE);
        shipLine(lines, c, s, -7, 5, -7, 15, ENTERPRISE);
        shipLine(lines, c, s, 7, 5, 7, 15, ENTERPRISE);
        shipLine(lines, c, s, -7, 9, 7, 9, ENTERPRISE);
    }

    /** A line of a top-view model given with y pointing forward-down, rotated by (c, s)/64 about the center. */
    private static void shipLine(short[] lines, int c, int s, int x0, int y0, int x1, int y1, int color) {
        addLine(lines, TACTICAL_X + (x0 * c - y0 * s) / 64, TACTICAL_Y + (x0 * s + y0 * c) / 64,
                TACTICAL_X + (x1 * c - y1 * s) / 64, TACTICAL_Y + (x1 * s + y1 * c) / 64, color);
    }

    /** A Klingon battlecruiser from above: a head on a long neck, and swept wings. */
    private static void drawKlingonTop(short[] lines, int px, int py, int headingDegrees) {
        float radians = (float) Math.toRadians(headingDegrees);
        int c = Math.round((float) Math.cos(radians) * 64);
        int s = Math.round((float) Math.sin(radians) * 64);
        klingonLine(lines, px, py, c, s, -3, -12, 3, -12);
        klingonLine(lines, px, py, c, s, 0, -10, 0, 2);
        klingonLine(lines, px, py, c, s, -11, 7, 11, 7);
        klingonLine(lines, px, py, c, s, -11, 7, 0, 1);
        klingonLine(lines, px, py, c, s, 11, 7, 0, 1);
        klingonLine(lines, px, py, c, s, -11, 7, -11, 2);
        klingonLine(lines, px, py, c, s, 11, 7, 11, 2);
    }

    private static void klingonLine(short[] lines, int px, int py, int c, int s, int x0, int y0, int x1, int y1) {
        addLine(lines, px + (x0 * c - y0 * s) / 64, py + (x0 * s + y0 * c) / 64, px + (x1 * c - y1 * s) / 64,
                py + (x1 * s + y1 * c) / 64, KLINGON);
    }

    /**
     * The view ahead from the bridge: stars at their bearings, and whatever lies within 40 degrees
     * of the heading, drawn in perspective on the plane of the sector.
     */
    private static void drawBridge(short[] lines, int[] ents) {
        for (int i = 0; i < 24; i++) {
            int relative = angleBetween(heading, i * 15 + 7);
            if (Math.abs(relative) < 40) {
                int x = BRIDGE_X + Math.round(BRIDGE_FOCAL * (float) Math.tan(Math.toRadians(relative)));
                int y = BRIDGE_TOP + 8 + (i * 29) % (BRIDGE_BOTTOM - BRIDGE_TOP - 16);
                addLine(lines, x, y, x, y, STAR);
            }
        }
        float radians = (float) Math.toRadians(heading);
        float hx = (float) Math.sin(radians);
        float hy = -(float) Math.cos(radians);
        for (int slot = 0; slot < ENTITIES; slot++) {
            int b = slot * E_STRIDE;
            int type = ents[b + E_TYPE];
            if (type == T_NONE || type == T_DEBRIS) {
                continue;
            }
            float dx = wrap(ents[b + E_X] - shipX) / (float) FIX;
            float dy = wrap(ents[b + E_Y] - shipY) / (float) FIX;
            int ahead = Math.round(dx * hx + dy * hy);
            int side = Math.round(dx * -hy + dy * hx);
            if (ahead < 20 || Math.abs(side) > ahead) {
                continue;
            }
            int x = BRIDGE_X + side * BRIDGE_FOCAL / ahead;
            int y = BRIDGE_Y + (slot % 3 - 1) * 400 / ahead;
            int size = Math.max(1, radius(type) * BRIDGE_FOCAL / ahead);
            drawAhead(lines, type, x, y, size);
        }
        // The sight.
        addLine(lines, BRIDGE_X - 6, BRIDGE_Y, BRIDGE_X - 2, BRIDGE_Y, FRAME);
        addLine(lines, BRIDGE_X + 2, BRIDGE_Y, BRIDGE_X + 6, BRIDGE_Y, FRAME);
    }

    private static void drawAhead(short[] lines, int type, int x, int y, int size) {
        if (type == T_KLINGON) {
            // Head-on: a head above swept wings.
            addLine(lines, x - size, y + size / 2, x, y - size / 3, KLINGON);
            addLine(lines, x, y - size / 3, x + size, y + size / 2, KLINGON);
            addLine(lines, x - size, y + size / 2, x - size, y, KLINGON);
            addLine(lines, x + size, y + size / 2, x + size, y, KLINGON);
            addLine(lines, x, y - size / 3, x, y - size, KLINGON);
        } else if (type == T_STARBASE) {
            for (int k = 0; k < 8; k++) {
                addLine(lines, x + dirX(k) * size / 10, y + dirY(k) * size / 20, x + dirX(k + 1) * size / 10,
                        y + dirY(k + 1) * size / 20, STARBASE);
            }
            addLine(lines, x, y - size, x, y + size, STARBASE);
        } else if (type == T_SAUCER) {
            addLine(lines, x - size, y, x + size, y, SAUCER);
            addLine(lines, x - size / 2, y - size / 2, x + size / 2, y - size / 2, SAUCER);
        } else if (type == T_NOMAD) {
            addLine(lines, x - size, y - size, x + size, y - size, NOMAD);
            addLine(lines, x + size, y - size, x + size, y + size, NOMAD);
            addLine(lines, x + size, y + size, x - size, y + size, NOMAD);
            addLine(lines, x - size, y + size, x - size, y - size, NOMAD);
        } else {
            int color = type == T_PHOTON ? PHOTON : TORPEDO;
            addLine(lines, x - size, y, x + size, y, color);
            addLine(lines, x, y - size, x, y + size, color);
        }
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

    // ---- Display lists ----

    private static void setClip(int left, int top, int right, int bottom) {
        clipLeft = left;
        clipTop = top;
        clipRight = right;
        clipBottom = bottom;
    }

    /** Clips a screen line to the current view (Cohen-Sutherland) and appends it to the list being built. */
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
                x = x0 + (x1 - x0) * (clipTop - y0) / (y1 - y0);
                y = clipTop;
            } else if ((code & 4) != 0) {
                x = x0 + (x1 - x0) * (clipBottom - y0) / (y1 - y0);
                y = clipBottom;
            } else if ((code & 2) != 0) {
                y = y0 + (y1 - y0) * (clipRight - x0) / (x1 - x0);
                x = clipRight;
            } else {
                y = y0 + (y1 - y0) * (clipLeft - x0) / (x1 - x0);
                x = clipLeft;
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
        if (x < clipLeft) {
            code = code | 1;
        } else if (x > clipRight) {
            code = code | 2;
        }
        if (y < clipTop) {
            code = code | 8;
        } else if (y > clipBottom) {
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

    /** Blanks both views and forgets what the display list had drawn there. */
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

    // ---- Panel and text ----

    /** The bridge window's frame and the three buttons, with the torpedoes and warps left. */
    private static void drawPanel() {
        TftTouchShield.drawRect(PANEL_X, BRIDGE_TOP - 1, WIDTH - PANEL_X, BRIDGE_BOTTOM - BRIDGE_TOP + 2, FRAME);
        TftTouchShield.drawVerticalLine(TACTICAL_RIGHT + 1, HEADER, HEIGHT - HEADER, FRAME);
        drawButton(0, "PHASER", -1, true);
        drawButton(1, "PHOTON", photons, photons > 0);
        drawButton(2, "WARP", warps, warps > 0);
    }

    private static void drawButton(int index, String label, int count, boolean ready) {
        int y = BUTTON_Y + index * BUTTON_HEIGHT;
        int color = ready ? BUTTON : BUTTON_EMPTY;
        TftTouchShield.fillRect(PANEL_X + 2, y + 2, WIDTH - PANEL_X - 4, BUTTON_HEIGHT - 4, color);
        TftTouchShield.drawRect(PANEL_X + 2, y + 2, WIDTH - PANEL_X - 4, BUTTON_HEIGHT - 4, FRAME);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(ready ? TftTouchShield.YELLOW : TftTouchShield.GRAY, color);
        TftTouchShield.setCursor(PANEL_X + (WIDTH - PANEL_X - label.length() * 12) / 2, y + 8);
        TftTouchShield.print(label);
        if (count >= 0) {
            TftTouchShield.setTextSize(1);
            TftTouchShield.setTextColor(TftTouchShield.WHITE, color);
            TftTouchShield.setCursor(PANEL_X + 44, y + 28);
            TftTouchShield.print(count);
        }
    }

    private static void drawHeader(int[] ents) {
        headerFlashed = false;
        TftTouchShield.fillRect(0, 0, WIDTH, HEADER - 1, SPACE);
        TftTouchShield.drawHorizontalLine(0, HEADER - 1, WIDTH, FRAME);
        status = -1;
        drawStatus(ents);
    }

    /** Score, sector, what is left to destroy, and the shields. */
    private static void drawStatus(int[] ents) {
        int klingons = count(ents, T_KLINGON) + count(ents, T_NOMAD);
        int combined = klingons * 1000 + shields + (baseLost ? 100000 : 0) + (autopilot ? 200000 : 0);
        if (combined == status && score == shownScore) {
            return;
        }
        status = combined;
        shownScore = score;
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.YELLOW, SPACE);
        TftTouchShield.setCursor(4, 2);
        TftTouchShield.print("SCORE ");
        TftTouchShield.print(score);
        TftTouchShield.print("   ");
        TftTouchShield.setTextColor(TftTouchShield.CYAN, SPACE);
        TftTouchShield.setCursor(136, 2);
        TftTouchShield.print("HI ");
        TftTouchShield.print(best);
        TftTouchShield.setCursor(250, 2);
        TftTouchShield.print("SECTOR ");
        TftTouchShield.print(sector);
        int color = TftTouchShield.GREEN;
        if (shields <= 25) {
            color = TftTouchShield.RED;
        } else if (shields <= 50) {
            color = TftTouchShield.YELLOW;
        }
        TftTouchShield.setTextColor(color, SPACE);
        TftTouchShield.setCursor(4, 11);
        TftTouchShield.print("SHIELDS ");
        TftTouchShield.print(shields);
        TftTouchShield.print("%  ");
        TftTouchShield.setTextColor(TftTouchShield.WHITE, SPACE);
        TftTouchShield.setCursor(136, 11);
        TftTouchShield.print("ENEMY SHIPS ");
        TftTouchShield.print(klingons);
        TftTouchShield.print("  ");
        TftTouchShield.setTextColor(TftTouchShield.MAGENTA, SPACE);
        TftTouchShield.setCursor(228, 11);
        TftTouchShield.print(autopilot ? "CPU" : "   ");
        if (baseLost) {
            TftTouchShield.setTextColor(TftTouchShield.RED, SPACE);
            TftTouchShield.setCursor(250, 11);
            TftTouchShield.print("BASE LOST");
        }
    }

    /**
     * The opening: stars rush out from the center of the screen, faster and faster, stretching into
     * streaks as the Enterprise goes to warp, and a white flash as it jumps.
     */
    private static void warpIntro(int[] stars) {
        TftTouchShield.fillScreen(SPACE);
        shown = 0;
        for (int i = 0; i < INTRO_STARS; i++) {
            placeStar(stars, i * S_STRIDE, Random.nextInt(64, INTRO_DEPTH));
        }
        for (int f = 0; f < INTRO_FRAMES; f++) {
            int speed = 4 + f * f / 60;
            for (int i = 0; i < INTRO_STARS; i++) {
                int b = i * S_STRIDE;
                if (stars[b + S_HEAD_X] >= 0) {
                    drawLine(stars[b + S_TAIL_X], stars[b + S_TAIL_Y], stars[b + S_HEAD_X], stars[b + S_HEAD_Y], SPACE);
                }
                stars[b + S_Z] = stars[b + S_Z] - speed;
                int z = stars[b + S_Z];
                int headX = WIDTH / 2 + stars[b + S_X] * INTRO_FOCAL / Math.max(z, 1);
                int headY = HEIGHT / 2 + stars[b + S_Y] * INTRO_FOCAL / Math.max(z, 1);
                int tailZ = z + 1 + speed * 2;
                int tailX = WIDTH / 2 + stars[b + S_X] * INTRO_FOCAL / tailZ;
                int tailY = HEIGHT / 2 + stars[b + S_Y] * INTRO_FOCAL / tailZ;
                if (z < 8 || headX < 0 || headX >= WIDTH || headY < 0 || headY >= HEIGHT) {
                    placeStar(stars, b, INTRO_DEPTH);
                    continue;
                }
                int color = z < INTRO_DEPTH / 3 ? TftTouchShield.WHITE : z < INTRO_DEPTH * 2 / 3 ? STARBASE : STAR;
                drawLine(tailX, tailY, headX, headY, color);
                stars[b + S_TAIL_X] = tailX;
                stars[b + S_TAIL_Y] = tailY;
                stars[b + S_HEAD_X] = headX;
                stars[b + S_HEAD_Y] = headY;
            }
            Delay.millis(20);
        }
        TftTouchShield.fillScreen(TftTouchShield.WHITE);
        Delay.millis(60);
        TftTouchShield.fillScreen(SPACE);
        Delay.millis(250);
    }

    /** A star far away at a random spot, with no streak on screen yet. */
    private static void placeStar(int[] stars, int b, int depth) {
        stars[b + S_X] = Random.nextInt(-WIDTH * 2, WIDTH * 2);
        stars[b + S_Y] = Random.nextInt(-HEIGHT * 2, HEIGHT * 2);
        stars[b + S_Z] = depth;
        stars[b + S_HEAD_X] = -1;
    }

    /**
     * The title screen: stars, "STAR TREK" zooming in, the subtitle typing out beneath it, then the
     * Enterprise among Klingons.
     */
    private static void drawTitle(short[] lines, int[] ents, byte[] letter) {
        for (int i = 0; i < 40; i++) {
            TftTouchShield.drawPixel((i * 797 + 131) % WIDTH, (i * 523 + 37 * i * i) % HEIGHT, STAR);
        }
        for (int size = 1; size <= 4; size++) {
            TftTouchShield.fillRect(0, 36, WIDTH, 40, SPACE);
            showCentered("STAR TREK", 56 - 4 * size, size, size == 4 ? TftTouchShield.WHITE : STAR);
            Delay.millis(90);
        }
        Delay.millis(120);
        showCentered("STAR TREK", 40, 4, TftTouchShield.YELLOW);
        Delay.millis(200);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(STARBASE, SPACE);
        TftTouchShield.setCursor((WIDTH - SUBTITLE.length() * 6) / 2, 82);
        for (int i = 0; i < SUBTITLE.length(); i++) {
            letter[0] = (byte) SUBTITLE.charAt(i);
            TftTouchShield.print(letter, 1);
            Delay.millis(35);
        }
        Delay.millis(300);
        sector = 1;
        clear(ents, ENTITIES * E_STRIDE);
        shipX = SECTOR_FIX / 2;
        shipY = SECTOR_FIX / 2;
        heading = 30;
        built = 0;
        setClip(0, HEADER, WIDTH - 1, HEIGHT - 1);
        drawKlingonTop(lines, 70, 150, 120);
        drawKlingonTop(lines, 250, 160, 240);
        drawKlingonTop(lines, 200, 205, 330);
        drawEnterprise(lines);
        present(lines);
        showCentered("Hold the tactical view to steer", 206, 1, TftTouchShield.WHITE);
        showCentered("Tap to start", 222, 1, TftTouchShield.CYAN);
    }

    private static void showCentered(String text, int y, int size, int color) {
        TftTouchShield.setTextSize(size);
        TftTouchShield.setTextColor(color, SPACE);
        TftTouchShield.setCursor((WIDTH - text.length() * 6 * size) / 2, y);
        TftTouchShield.print(text);
    }

    // ---- Records ----

    private static int spawn(int[] ents, int type, int x, int y, int hp) {
        for (int slot = 0; slot < ENTITIES; slot++) {
            int b = slot * E_STRIDE;
            if (ents[b + E_TYPE] == T_NONE) {
                for (int k = 0; k < E_STRIDE; k++) {
                    ents[b + k] = 0;
                }
                ents[b + E_TYPE] = type;
                ents[b + E_X] = wrapPosition(x);
                ents[b + E_Y] = wrapPosition(y);
                ents[b + E_HP] = hp;
                return slot;
            }
        }
        return -1;
    }

    /** Spawns an enemy somewhere in the sector, at least 400 units from the Enterprise. */
    private static int spawnAway(int[] ents, int type, int hp) {
        int angle = Random.nextInt(360);
        int distance = Random.nextInt(400, 900);
        float radians = (float) Math.toRadians(angle);
        int x = shipX + Math.round((float) Math.sin(radians) * distance) * FIX;
        int y = shipY - Math.round((float) Math.cos(radians) * distance) * FIX;
        int slot = spawn(ents, type, x, y, hp);
        if (slot >= 0) {
            ents[slot * E_STRIDE + E_TIMER] = Random.nextInt(20, 80);
            ents[slot * E_STRIDE + E_COOL] = Random.nextInt(30, 80);
        }
        return slot;
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
}
