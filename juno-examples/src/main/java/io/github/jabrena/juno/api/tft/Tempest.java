package io.github.jabrena.juno.api.tft;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;

/**
 * Tempest on the ELEGOO 2.8" TFT touch screen shield: a vector-style tube of {@value #LANES} lanes
 * seen end-on. Your yellow claw rides the outer rim; touch near the rim and it moves around towards
 * your finger, firing down its lane while you hold. Red flippers climb out of the far end, flip
 * between lanes, fire back, and once they reach the rim crawl along it to grab you — shoot them
 * before they do. Tap the center of the tube to fire the Superzapper, which destroys every enemy on
 * screen, once per level. Each level changes the tube's shape (circle, square, star, clover) and
 * adds faster flippers. Three lives.
 *
 * <p>Depth is perspective-mapped: a point at depth {@code d} on a spoke is interpolated between
 * the spoke's far and near ends by {@code d(2d + D) / 3D²}, so enemies accelerate visually as they
 * approach. The web is drawn once per level; enemies are Bresenham line bow-ties that erase
 * themselves and then repair the short stretch of spoke they may have touched.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class Tempest {
    private static final int FRAME_MILLIS = 25;

    private static final int WIDTH = 240;
    private static final int HEADER = 20;
    private static final int CENTER_X = 120;
    private static final int CENTER_Y = 180;
    private static final int LANES = 16;
    private static final int DEPTH = 1024;
    private static final int INNER_SCALE = 20;
    private static final int ZAPPER_RADIUS = 22;

    // Enemies (flippers).
    private static final int ENEMIES = 8;
    private static final int E_ACTIVE = 0;
    private static final int E_LANE = 1;
    private static final int E_DEPTH = 2;
    private static final int E_SHOWN_LANE = 3;
    private static final int E_SHOWN_DEPTH = 4;
    private static final int E_ON_RIM = 5;
    private static final int E_TIMER = 6;
    private static final int E_STRIDE = 7;
    private static final int ENEMY_HALF_DEPTH = 40;
    /** Where a flipper rides the rim: close to it, without its outline touching the rim itself. */
    private static final int RIM_DEPTH = DEPTH - ENEMY_HALF_DEPTH - 16;
    private static final int FLIPPER_POINTS = 150;

    // Shots: yours travel inwards, the enemies' outwards.
    private static final int SHOTS = 6;
    private static final int BULLETS = 4;
    private static final int S_ACTIVE = 0;
    private static final int S_LANE = 1;
    private static final int S_DEPTH = 2;
    private static final int S_SHOWN_X = 3;
    private static final int S_SHOWN_Y = 4;
    private static final int S_STRIDE = 5;
    private static final int SHOT_SPEED = 64;
    private static final int HIT_DEPTH = 56;

    private static final int SPACE = TftTouchShield.BLACK;
    private static final int WEB = 0x3A7F;
    private static final int LANE_HIGHLIGHT = TftTouchShield.YELLOW;
    private static final int CLAW = TftTouchShield.YELLOW;
    private static final int FLIPPER = 0xF800;
    private static final int FLIPPER_TIPS = 0xF81F;
    private static final int SHOT = TftTouchShield.WHITE;
    private static final int BULLET = 0xFD20;

    private static int score;
    private static int best;
    private static int lives;
    private static int level;
    private static int playerLane;
    private static int moveCooldown;
    private static int fireCooldown;
    private static boolean zapperReady;
    private static int toSpawn;
    private static int spawnCountdown;
    private static int climbSpeed;
    private static boolean zapping;

    private Tempest() {
    }

    public static void main(String[] args) {
        int[] outerX = new int[LANES];
        int[] outerY = new int[LANES];
        int[] innerX = new int[LANES];
        int[] innerY = new int[LANES];
        int[] enemies = new int[ENEMIES * E_STRIDE];
        int[] shots = new int[SHOTS * S_STRIDE];
        int[] bullets = new int[BULLETS * S_STRIDE];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        TftTouchShield.fillScreen(SPACE);
        buildTube(0, outerX, outerY, innerX, innerY);
        drawWeb(outerX, outerY, innerX, innerY, -1);
        showCentered("TEMPEST", 40, 4, FLIPPER);
        showCentered("Touch the rim to move and fire", 292, 1, TftTouchShield.WHITE);
        showCentered("Tap the center to superzap", 306, 1, TftTouchShield.WHITE);
        waitForTap();
        Random.seed(Clock.micros());

        while (true) {
            score = 0;
            lives = 3;
            level = 0;
            boolean playing = true;
            while (playing) {
                level = level + 1;
                playing = playLevel(outerX, outerY, innerX, innerY, enemies, shots, bullets);
            }
            if (score > best) {
                best = score;
            }
            drawHeader();
            showCentered("GAME OVER", 168, 3, FLIPPER);
            Delay.millis(1200);
            waitForTap();
        }
    }

    // ---- Game flow ----

    /** Plays one level; returns false when the last life is lost. */
    private static boolean playLevel(int[] outerX, int[] outerY, int[] innerX, int[] innerY, int[] enemies,
            int[] shots, int[] bullets) {
        buildTube((level - 1) % 4, outerX, outerY, innerX, innerY);
        clear(enemies, ENEMIES * E_STRIDE);
        clear(shots, SHOTS * S_STRIDE);
        clear(bullets, BULLETS * S_STRIDE);
        toSpawn = Math.min(6 + 2 * level, 24);
        spawnCountdown = 30;
        climbSpeed = Math.min(5 + level, 14);
        zapperReady = true;
        playerLane = 0;
        TftTouchShield.fillScreen(SPACE);
        drawWeb(outerX, outerY, innerX, innerY, playerLane);
        drawClaw(outerX, outerY, playerLane, CLAW);
        drawHeader();
        showCentered("LEVEL", 150, 2, TftTouchShield.GREEN);
        TftTouchShield.setCursor(108, 172);
        TftTouchShield.print(level);
        Delay.millis(1200);
        TftTouchShield.fillRect(CENTER_X - 40, 146, 80, 44, SPACE);
        drawWeb(outerX, outerY, innerX, innerY, playerLane);

        int next = Clock.millis();
        int frame = 0;
        while (true) {
            while (Clock.millis() - next < 0) {
                Delay.millis(1);
            }
            next = next + FRAME_MILLIS;
            frame = frame + 1;

            handleTouch(outerX, outerY, innerX, innerY, shots);
            if (zapping) {
                zapping = false;
                superzap(outerX, outerY, innerX, innerY, enemies, bullets);
            }
            moveShots(outerX, outerY, innerX, innerY, shots, enemies, bullets);
            spawnEnemies(enemies);
            boolean caught = moveEnemies(outerX, outerY, innerX, innerY, enemies, bullets, frame);
            if (moveBullets(outerX, outerY, innerX, innerY, bullets)) {
                caught = true;
            }
            if (caught) {
                lives = lives - 1;
                loseLife(outerX, outerY, innerX, innerY, enemies, shots, bullets);
                if (lives == 0) {
                    return false;
                }
                next = Clock.millis();
            }
            if (toSpawn == 0 && count(enemies, ENEMIES, E_STRIDE) == 0) {
                score = score + 100 * level;
                drawHeader();
                clearShots(shots, SHOTS);
                clearShots(bullets, BULLETS);
                Delay.millis(800);
                return true;
            }
        }
    }

    private static void loseLife(int[] outerX, int[] outerY, int[] innerX, int[] innerY, int[] enemies,
            int[] shots, int[] bullets) {
        int mx = (outerX[playerLane] + outerX[(playerLane + 1) % LANES]) / 2;
        int my = (outerY[playerLane] + outerY[(playerLane + 1) % LANES]) / 2;
        for (int r = 2; r < 22; r = r + 2) {
            TftTouchShield.drawCircle(mx, my, r, (r & 2) == 0 ? CLAW : FLIPPER);
            Delay.millis(40);
        }
        Delay.millis(500);
        // The enemies still in the tube go back into the spawn queue; the level restarts.
        for (int slot = 0; slot < ENEMIES; slot++) {
            if (enemies[slot * E_STRIDE + E_ACTIVE] != 0) {
                enemies[slot * E_STRIDE + E_ACTIVE] = 0;
                toSpawn = toSpawn + 1;
            }
        }
        clear(shots, SHOTS * S_STRIDE);
        clear(bullets, BULLETS * S_STRIDE);
        spawnCountdown = 40;
        TftTouchShield.fillScreen(SPACE);
        drawWeb(outerX, outerY, innerX, innerY, playerLane);
        drawClaw(outerX, outerY, playerLane, CLAW);
        drawHeader();
    }

    // ---- Tube geometry ----

    /** Fills the rim and far-end points of lane spokes for one of four tube shapes. */
    private static void buildTube(int shape, int[] outerX, int[] outerY, int[] innerX, int[] innerY) {
        for (int i = 0; i < LANES; i++) {
            double angle = 2.0 * Math.PI * i / LANES - Math.PI / 2.0;
            double radius = 108.0;
            if (shape == 1) {
                // Square: the radius that puts the point on a square of half-side 98.
                double c = Math.abs(Math.cos(angle));
                double s = Math.abs(Math.sin(angle));
                radius = 98.0 / Math.max(c, s);
                radius = Math.min(radius, 118.0);
            } else if (shape == 2) {
                if ((i & 1) == 0) {
                    radius = 110.0;
                } else {
                    radius = 70.0;
                }
            } else if (shape == 3) {
                radius = 84.0 + 26.0 * Math.cos(4.0 * angle);
            }
            outerX[i] = CENTER_X + (int) Math.round(radius * Math.cos(angle));
            outerY[i] = CENTER_Y + (int) Math.round(radius * Math.sin(angle));
            innerX[i] = CENTER_X + (outerX[i] - CENTER_X) * INNER_SCALE / 100;
            innerY[i] = CENTER_Y + (outerY[i] - CENTER_Y) * INNER_SCALE / 100;
        }
    }

    /** Perspective interpolation along spoke {@code spoke} at {@code depth} (0 = far end, DEPTH = rim). */
    private static int spokeX(int[] outerX, int[] innerX, int spoke, int depth) {
        int s = spoke % LANES;
        return innerX[s] + (outerX[s] - innerX[s]) * perspective(depth) / (3 * DEPTH);
    }

    private static int spokeY(int[] outerY, int[] innerY, int spoke, int depth) {
        int s = spoke % LANES;
        return innerY[s] + (outerY[s] - innerY[s]) * perspective(depth) / (3 * DEPTH);
    }

    private static int perspective(int depth) {
        int d = Math.max(0, Math.min(depth, DEPTH));
        return d * (2 * d + DEPTH) / DEPTH;
    }

    // ---- Input ----

    private static void handleTouch(int[] outerX, int[] outerY, int[] innerX, int[] innerY, int[] shots) {
        if (moveCooldown > 0) {
            moveCooldown = moveCooldown - 1;
        }
        if (fireCooldown > 0) {
            fireCooldown = fireCooldown - 1;
        }
        if (!TftTouchShield.readTouch()) {
            return;
        }
        int x = TftTouchShield.touchX();
        int y = TftTouchShield.touchY();
        int dx = x - CENTER_X;
        int dy = y - CENTER_Y;
        if (dx * dx + dy * dy <= ZAPPER_RADIUS * ZAPPER_RADIUS) {
            if (zapperReady) {
                zapperReady = false;
                zapping = true;
            }
            return;
        }
        // Head for the lane whose rim midpoint is nearest the finger, one lane at a time.
        int target = 0;
        int bestDistance = 1 << 30;
        for (int lane = 0; lane < LANES; lane++) {
            int mx = (outerX[lane] + outerX[(lane + 1) % LANES]) / 2 - x;
            int my = (outerY[lane] + outerY[(lane + 1) % LANES]) / 2 - y;
            if (mx * mx + my * my < bestDistance) {
                bestDistance = mx * mx + my * my;
                target = lane;
            }
        }
        if (target != playerLane && moveCooldown == 0) {
            int forward = (target - playerLane + LANES) % LANES;
            int step = 1;
            if (forward > LANES / 2) {
                step = LANES - 1;
            }
            int previous = playerLane;
            playerLane = (playerLane + step) % LANES;
            moveClaw(outerX, outerY, innerX, innerY, previous);
            moveCooldown = 2;
        }
        if (fireCooldown == 0) {
            for (int slot = 0; slot < SHOTS; slot++) {
                int base = slot * S_STRIDE;
                if (shots[base + S_ACTIVE] == 0) {
                    shots[base + S_ACTIVE] = 1;
                    shots[base + S_LANE] = playerLane;
                    shots[base + S_DEPTH] = DEPTH;
                    shots[base + S_SHOWN_X] = -1;
                    fireCooldown = 4;
                    break;
                }
            }
        }
    }

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

    // ---- Shots ----

    private static void moveShots(int[] outerX, int[] outerY, int[] innerX, int[] innerY, int[] shots,
            int[] enemies, int[] bullets) {
        for (int slot = 0; slot < SHOTS; slot++) {
            int base = slot * S_STRIDE;
            if (shots[base + S_ACTIVE] == 0) {
                continue;
            }
            int lane = shots[base + S_LANE];
            int depth = shots[base + S_DEPTH] - SHOT_SPEED;
            shots[base + S_DEPTH] = depth;
            boolean spent = depth <= ENEMY_HALF_DEPTH / 2;
            for (int e = 0; e < ENEMIES && !spent; e++) {
                int eBase = e * E_STRIDE;
                if (enemies[eBase + E_ACTIVE] != 0 && enemies[eBase + E_LANE] == lane
                        && Math.abs(enemies[eBase + E_DEPTH] - depth) <= HIT_DEPTH) {
                    killEnemy(outerX, outerY, innerX, innerY, enemies, e);
                    score = score + FLIPPER_POINTS;
                    drawHeader();
                    spent = true;
                }
            }
            for (int b = 0; b < BULLETS && !spent; b++) {
                int bBase = b * S_STRIDE;
                if (bullets[bBase + S_ACTIVE] != 0 && bullets[bBase + S_LANE] == lane
                        && Math.abs(bullets[bBase + S_DEPTH] - depth) <= HIT_DEPTH) {
                    eraseShot(bullets, b);
                    bullets[bBase + S_ACTIVE] = 0;
                    spent = true;
                }
            }
            if (spent) {
                eraseShot(shots, slot);
                shots[base + S_ACTIVE] = 0;
            } else {
                drawShot(outerX, outerY, innerX, innerY, shots, slot, SHOT);
            }
        }
    }

    /** Moves the enemies' bullets outwards; returns true when one reaches the claw. */
    private static boolean moveBullets(int[] outerX, int[] outerY, int[] innerX, int[] innerY, int[] bullets) {
        boolean hit = false;
        for (int slot = 0; slot < BULLETS; slot++) {
            int base = slot * S_STRIDE;
            if (bullets[base + S_ACTIVE] == 0) {
                continue;
            }
            int depth = bullets[base + S_DEPTH] + climbSpeed * 3;
            bullets[base + S_DEPTH] = depth;
            if (depth >= DEPTH - ENEMY_HALF_DEPTH / 2) {
                eraseShot(bullets, slot);
                bullets[base + S_ACTIVE] = 0;
                if (bullets[base + S_LANE] == playerLane) {
                    hit = true;
                }
            } else {
                drawShot(outerX, outerY, innerX, innerY, bullets, slot, BULLET);
            }
        }
        return hit;
    }

    private static void drawShot(int[] outerX, int[] outerY, int[] innerX, int[] innerY, int[] records, int slot,
            int color) {
        int base = slot * S_STRIDE;
        int lane = records[base + S_LANE];
        int depth = records[base + S_DEPTH];
        int x = (spokeX(outerX, innerX, lane, depth) + spokeX(outerX, innerX, lane + 1, depth)) / 2;
        int y = (spokeY(outerY, innerY, lane, depth) + spokeY(outerY, innerY, lane + 1, depth)) / 2;
        if (x == records[base + S_SHOWN_X] && y == records[base + S_SHOWN_Y]) {
            return;
        }
        eraseShot(records, slot);
        TftTouchShield.fillRect(x - 1, y - 1, 2, 2, color);
        TftTouchShield.drawPixel(x, y + 1, color);
        TftTouchShield.drawPixel(x + 1, y, color);
        records[base + S_SHOWN_X] = x;
        records[base + S_SHOWN_Y] = y;
    }

    private static void eraseShot(int[] records, int slot) {
        int base = slot * S_STRIDE;
        int x = records[base + S_SHOWN_X];
        if (x >= 0) {
            int y = records[base + S_SHOWN_Y];
            TftTouchShield.fillRect(x - 1, y - 1, 2, 2, SPACE);
            TftTouchShield.drawPixel(x, y + 1, SPACE);
            TftTouchShield.drawPixel(x + 1, y, SPACE);
            records[base + S_SHOWN_X] = -1;
        }
    }

    private static void clearShots(int[] records, int slots) {
        for (int slot = 0; slot < slots; slot++) {
            if (records[slot * S_STRIDE + S_ACTIVE] != 0) {
                eraseShot(records, slot);
                records[slot * S_STRIDE + S_ACTIVE] = 0;
            }
        }
    }

    // ---- Enemies ----

    private static void spawnEnemies(int[] enemies) {
        if (toSpawn == 0) {
            return;
        }
        spawnCountdown = spawnCountdown - 1;
        if (spawnCountdown > 0) {
            return;
        }
        spawnCountdown = Math.max(20, Random.nextInt(40, 110) - level * 5);
        for (int slot = 0; slot < ENEMIES; slot++) {
            int base = slot * E_STRIDE;
            if (enemies[base + E_ACTIVE] == 0) {
                enemies[base + E_ACTIVE] = 1;
                enemies[base + E_LANE] = Random.nextInt(LANES);
                enemies[base + E_DEPTH] = ENEMY_HALF_DEPTH + 8;
                enemies[base + E_SHOWN_LANE] = -1;
                enemies[base + E_ON_RIM] = 0;
                enemies[base + E_TIMER] = Random.nextInt(20, 60);
                toSpawn = toSpawn - 1;
                return;
            }
        }
    }

    /**
     * Climbs, flips, fires and crawls; each enemy is redrawn on alternate frames. Returns true when
     * a flipper grabs the claw.
     */
    private static boolean moveEnemies(int[] outerX, int[] outerY, int[] innerX, int[] innerY, int[] enemies,
            int[] bullets, int frame) {
        boolean caught = false;
        for (int slot = 0; slot < ENEMIES; slot++) {
            int base = slot * E_STRIDE;
            if (enemies[base + E_ACTIVE] == 0 || ((slot + frame) & 1) != 0) {
                continue;
            }
            int lane = enemies[base + E_LANE];
            int timer = enemies[base + E_TIMER] - 1;
            if (enemies[base + E_ON_RIM] == 0) {
                int depth = enemies[base + E_DEPTH] + climbSpeed * 2;
                if (depth >= RIM_DEPTH) {
                    depth = RIM_DEPTH;
                    enemies[base + E_ON_RIM] = 1;
                    timer = 8;
                } else if (timer <= 0) {
                    timer = Random.nextInt(15, 50);
                    if (Random.nextInt(3) == 0) {
                        fireBullet(bullets, lane, depth);
                    } else {
                        lane = (lane + LANES + Random.nextInt(2) * 2 - 1) % LANES;
                    }
                }
                enemies[base + E_DEPTH] = depth;
            } else if (lane == playerLane) {
                // On the rim in your lane: a short grace period to shoot it, then it grabs you.
                if (timer <= 0) {
                    caught = true;
                }
            } else if (timer <= 0) {
                int forward = (playerLane - lane + LANES) % LANES;
                if (forward <= LANES / 2) {
                    lane = (lane + 1) % LANES;
                } else {
                    lane = (lane + LANES - 1) % LANES;
                }
                timer = Math.max(4, 14 - level);
                if (lane == playerLane) {
                    timer = 10;
                }
            }
            enemies[base + E_LANE] = lane;
            enemies[base + E_TIMER] = timer;
            drawEnemy(outerX, outerY, innerX, innerY, enemies, slot);
        }
        return caught;
    }

    private static void fireBullet(int[] bullets, int lane, int depth) {
        for (int slot = 0; slot < BULLETS; slot++) {
            int base = slot * S_STRIDE;
            if (bullets[base + S_ACTIVE] == 0) {
                bullets[base + S_ACTIVE] = 1;
                bullets[base + S_LANE] = lane;
                bullets[base + S_DEPTH] = depth + ENEMY_HALF_DEPTH;
                bullets[base + S_SHOWN_X] = -1;
                return;
            }
        }
    }

    private static void killEnemy(int[] outerX, int[] outerY, int[] innerX, int[] innerY, int[] enemies, int slot) {
        int base = slot * E_STRIDE;
        enemies[base + E_ACTIVE] = 0;
        eraseEnemy(outerX, outerY, innerX, innerY, enemies, slot);
    }

    private static void superzap(int[] outerX, int[] outerY, int[] innerX, int[] innerY, int[] enemies,
            int[] bullets) {
        for (int flash = 0; flash < 3; flash++) {
            TftTouchShield.drawCircle(CENTER_X, CENTER_Y, ZAPPER_RADIUS - 6, TftTouchShield.WHITE);
            Delay.millis(40);
            TftTouchShield.drawCircle(CENTER_X, CENTER_Y, ZAPPER_RADIUS - 6, SPACE);
            Delay.millis(40);
        }
        for (int slot = 0; slot < ENEMIES; slot++) {
            if (enemies[slot * E_STRIDE + E_ACTIVE] != 0) {
                killEnemy(outerX, outerY, innerX, innerY, enemies, slot);
                score = score + FLIPPER_POINTS;
            }
        }
        clearShots(bullets, BULLETS);
        drawWeb(outerX, outerY, innerX, innerY, playerLane);
        drawHeader();
    }

    private static void drawEnemy(int[] outerX, int[] outerY, int[] innerX, int[] innerY, int[] enemies,
            int slot) {
        int base = slot * E_STRIDE;
        int lane = enemies[base + E_LANE];
        int depth = enemies[base + E_DEPTH];
        if (lane == enemies[base + E_SHOWN_LANE] && Math.abs(depth - enemies[base + E_SHOWN_DEPTH]) < 12) {
            return;
        }
        eraseEnemy(outerX, outerY, innerX, innerY, enemies, slot);
        bowTie(outerX, outerY, innerX, innerY, lane, depth, FLIPPER, FLIPPER_TIPS);
        enemies[base + E_SHOWN_LANE] = lane;
        enemies[base + E_SHOWN_DEPTH] = depth;
    }

    private static void eraseEnemy(int[] outerX, int[] outerY, int[] innerX, int[] innerY, int[] enemies,
            int slot) {
        int base = slot * E_STRIDE;
        int lane = enemies[base + E_SHOWN_LANE];
        if (lane < 0) {
            return;
        }
        int depth = enemies[base + E_SHOWN_DEPTH];
        bowTie(outerX, outerY, innerX, innerY, lane, depth, SPACE, SPACE);
        enemies[base + E_SHOWN_LANE] = -1;
        // Repair the stretches of the lane's two spokes the bow-tie may have overlapped.
        int from = Math.max(0, depth - ENEMY_HALF_DEPTH - 24);
        int to = Math.min(DEPTH, depth + ENEMY_HALF_DEPTH + 24);
        for (int side = 0; side <= 1; side++) {
            int spoke = (lane + side) % LANES;
            int color = WEB;
            if (spoke == playerLane || spoke == (playerLane + 1) % LANES) {
                color = LANE_HIGHLIGHT;
            }
            drawLine(spokeX(outerX, innerX, spoke, from), spokeY(outerY, innerY, spoke, from),
                    spokeX(outerX, innerX, spoke, to), spokeY(outerY, innerY, spoke, to), color);
        }
        if (depth - ENEMY_HALF_DEPTH <= 8) {
            drawLine(innerX[lane], innerY[lane], innerX[(lane + 1) % LANES], innerY[(lane + 1) % LANES], WEB);
        }
    }

    /** A flipper: a bow-tie spanning the middle of the lane, pinched at its crossing. */
    private static void bowTie(int[] outerX, int[] outerY, int[] innerX, int[] innerY, int lane, int depth,
            int color, int tips) {
        int near = Math.min(DEPTH, depth + ENEMY_HALF_DEPTH);
        int far = Math.max(0, depth - ENEMY_HALF_DEPTH);
        int ax0 = spokeX(outerX, innerX, lane, far);
        int ay0 = spokeY(outerY, innerY, lane, far);
        int bx0 = spokeX(outerX, innerX, lane + 1, far);
        int by0 = spokeY(outerY, innerY, lane + 1, far);
        int ax1 = spokeX(outerX, innerX, lane, near);
        int ay1 = spokeY(outerY, innerY, lane, near);
        int bx1 = spokeX(outerX, innerX, lane + 1, near);
        int by1 = spokeY(outerY, innerY, lane + 1, near);
        // Inset a fifth of the lane width from each spoke.
        int lx0 = ax0 + (bx0 - ax0) / 5;
        int ly0 = ay0 + (by0 - ay0) / 5;
        int rx0 = bx0 - (bx0 - ax0) / 5;
        int ry0 = by0 - (by0 - ay0) / 5;
        int lx1 = ax1 + (bx1 - ax1) / 5;
        int ly1 = ay1 + (by1 - ay1) / 5;
        int rx1 = bx1 - (bx1 - ax1) / 5;
        int ry1 = by1 - (by1 - ay1) / 5;
        drawLine(lx0, ly0, rx1, ry1, color);
        drawLine(rx0, ry0, lx1, ly1, color);
        drawLine(lx0, ly0, lx1, ly1, tips);
        drawLine(rx0, ry0, rx1, ry1, tips);
    }

    // ---- Claw ----

    private static void moveClaw(int[] outerX, int[] outerY, int[] innerX, int[] innerY, int previous) {
        drawClaw(outerX, outerY, previous, SPACE);
        drawSpoke(outerX, outerY, innerX, innerY, previous, WEB);
        drawSpoke(outerX, outerY, innerX, innerY, previous + 1, WEB);
        drawSpoke(outerX, outerY, innerX, innerY, playerLane, LANE_HIGHLIGHT);
        drawSpoke(outerX, outerY, innerX, innerY, playerLane + 1, LANE_HIGHLIGHT);
        drawClaw(outerX, outerY, playerLane, CLAW);
    }

    /** The claw sits just outside the rim, so drawing and erasing it never touches the web. */
    private static void drawClaw(int[] outerX, int[] outerY, int lane, int color) {
        int ax = outerX[lane];
        int ay = outerY[lane];
        int bx = outerX[(lane + 1) % LANES];
        int by = outerY[(lane + 1) % LANES];
        int mx = (ax + bx) / 2;
        int my = (ay + by) / 2;
        int length = Math.max(1, (int) Math.sqrt((double) ((mx - CENTER_X) * (mx - CENTER_X)
                + (my - CENTER_Y) * (my - CENTER_Y))));
        int ox = (mx - CENTER_X) * 12 / length;
        int oy = (my - CENTER_Y) * 12 / length;
        int leftX = ax + ox / 4;
        int leftY = ay + oy / 4;
        int rightX = bx + ox / 4;
        int rightY = by + oy / 4;
        drawLine(leftX, leftY, mx + ox, my + oy, color);
        drawLine(mx + ox, my + oy, rightX, rightY, color);
        drawLine(leftX, leftY, mx + ox / 2, my + oy / 2, color);
        drawLine(mx + ox / 2, my + oy / 2, rightX, rightY, color);
    }

    // ---- Web ----

    private static void drawWeb(int[] outerX, int[] outerY, int[] innerX, int[] innerY, int highlighted) {
        for (int i = 0; i < LANES; i++) {
            int j = (i + 1) % LANES;
            drawLine(outerX[i], outerY[i], outerX[j], outerY[j], WEB);
            drawLine(innerX[i], innerY[i], innerX[j], innerY[j], WEB);
        }
        for (int i = 0; i < LANES; i++) {
            int color = WEB;
            if (highlighted >= 0 && (i == highlighted || i == (highlighted + 1) % LANES)) {
                color = LANE_HIGHLIGHT;
            }
            drawSpoke(outerX, outerY, innerX, innerY, i, color);
        }
    }

    private static void drawSpoke(int[] outerX, int[] outerY, int[] innerX, int[] innerY, int spoke, int color) {
        int s = spoke % LANES;
        drawLine(innerX[s], innerY[s], outerX[s], outerY[s], color);
    }

    /** Bresenham line; runs of pixels on the same row become one fill. */
    private static void drawLine(int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int stepX = 1;
        if (x0 > x1) {
            stepX = -1;
        }
        int stepY = 1;
        if (y0 > y1) {
            stepY = -1;
        }
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

    // ---- Records ----

    private static void clear(int[] records, int length) {
        for (int i = 0; i < length; i++) {
            records[i] = 0;
        }
    }

    private static int count(int[] records, int slots, int stride) {
        int active = 0;
        for (int slot = 0; slot < slots; slot++) {
            if (records[slot * stride] != 0) {
                active = active + 1;
            }
        }
        return active;
    }

    // ---- Text ----

    private static void drawHeader() {
        TftTouchShield.fillRect(0, 0, WIDTH, HEADER, SPACE);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.GREEN, SPACE);
        TftTouchShield.setCursor(4, 3);
        TftTouchShield.print(score);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(CLAW, SPACE);
        TftTouchShield.setCursor(110, 3);
        TftTouchShield.print("HI ");
        TftTouchShield.print(best);
        TftTouchShield.setCursor(110, 12);
        TftTouchShield.print("LEVEL ");
        TftTouchShield.print(level);
        TftTouchShield.setTextColor(FLIPPER, SPACE);
        TftTouchShield.setCursor(186, 3);
        TftTouchShield.print("LIVES ");
        TftTouchShield.print(lives);
        if (zapperReady) {
            TftTouchShield.setTextColor(TftTouchShield.CYAN, SPACE);
            TftTouchShield.setCursor(186, 12);
            TftTouchShield.print("ZAP");
        }
    }

    private static void showCentered(String text, int y, int size, int color) {
        TftTouchShield.setTextSize(size);
        TftTouchShield.setTextColor(color, SPACE);
        TftTouchShield.setCursor((WIDTH - text.length() * 6 * size) / 2, y);
        TftTouchShield.print(text);
    }
}
