package io.github.jabrena.juno.games;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * Missile Command on the ELEGOO 2.8" TFT touch screen shield. Enemy warheads rain down on six
 * cities and three missile bases; tap the sky to launch an interceptor from the nearest base that
 * still has ammunition. It detonates where you tapped, and anything its expanding fireball touches
 * is destroyed — including other warheads, whose own explosions can set off a chain reaction.
 *
 * <p>Every base holds {@value #BASE_AMMO} interceptors per wave, and a warhead that hits a base
 * empties it. From wave 2, some warheads split into several (MIRVs) halfway down. At the end of a
 * wave, unused interceptors score {@value #AMMO_BONUS} points and surviving cities
 * {@value #CITY_BONUS} each; every {@value #BONUS_CITY_SCORE} points rebuilds a lost city. The game
 * ends when a wave finishes with no city standing.
 *
 * <p>Each trail is one Bresenham line traced a few steps per frame and retraced in the sky's color
 * when the warhead dies, so no stray pixels are left behind; fireballs grow, hold and shrink with filled circles. Missiles, interceptors and
 * explosions live in fixed-stride {@code int} arrays allocated once in {@code main}.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class MissileCommand {
    private static final int FRAME_MILLIS = 20;
    private static final int FIXED = 64;

    // Layout (portrait, 240x320).
    private static final int WIDTH = 240;
    private static final int HEADER = 20;
    private static final int GROUND_Y = 300;
    private static final int LOWEST_TARGET_Y = GROUND_Y - 22;

    // Targets: six cities (0-5) and three bases (6-8).
    private static final int TARGETS = 9;
    private static final int CITIES = 6;
    private static final int FIRST_BASE = 6;
    private static final int BASE_AMMO = 10;

    private static final int CITY_BONUS = 100;
    private static final int AMMO_BONUS = 5;
    private static final int MISSILE_POINTS = 25;
    private static final int BONUS_CITY_SCORE = 10000;

    // Trails — enemy missiles and interceptors alike: fixed-stride records of a line being traced
    // from its start to its end, one Bresenham step at a time.
    private static final int T_ACTIVE = 0;
    private static final int T_START_X = 1;
    private static final int T_START_Y = 2;
    private static final int T_END_X = 3;
    private static final int T_END_Y = 4;
    private static final int T_STEPS = 5;
    private static final int T_PROGRESS = 6;
    private static final int T_RATE = 7;
    private static final int T_DRAWN = 8;
    private static final int T_HEAD_X = 9;
    private static final int T_HEAD_Y = 10;
    private static final int T_TARGET = 11;
    private static final int T_SPLIT = 12;
    private static final int T_STRIDE = 13;

    private static final int MISSILES = 12;
    private static final int SHOTS = 8;
    private static final int SHOT_SPEED = 5 * FIXED;

    // Explosions.
    private static final int BLASTS = 16;
    private static final int B_ACTIVE = 0;
    private static final int B_X = 1;
    private static final int B_Y = 2;
    private static final int B_AGE = 3;
    private static final int B_RADIUS = 4;
    private static final int B_STRIDE = 5;
    private static final int BLAST_RADIUS = 15;
    private static final int BLAST_HOLD = 10;
    private static final int BLAST_LIFE = BLAST_RADIUS * 2 + BLAST_HOLD;

    private static final int SKY = TftTouchShield.BLACK;
    private static final int GROUND = 0xC4A0;
    private static final int ENEMY_TRAIL = 0xF800;
    private static final int SHOT_TRAIL = 0x07FF;
    private static final int CITY_COLOR = 0x04DF;
    private static final int CITY_WINDOWS = 0xFFE0;
    private static final int RUBBLE = 0x6B4D;
    private static final int HEADER_BACKGROUND = 0x2945;

    private static int score;
    private static int best;
    private static int wave;
    private static int nextBonusCity;
    private static int toSpawn;
    private static int spawnCountdown;
    private static int enemySpeed;
    private static boolean touching;
    private static int releaseMisses;
    private static int traceX;
    private static int traceY;

    private MissileCommand() {
    }

    public static void main(String[] args) {
        int[] missiles = new int[MISSILES * T_STRIDE];
        int[] shots = new int[SHOTS * T_STRIDE];
        int[] blasts = new int[BLASTS * B_STRIDE];
        boolean[] alive = new boolean[TARGETS];
        int[] ammo = new int[3];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        TftTouchShield.fillScreen(SKY);
        showCentered("MISSILE COMMAND", 110, 2, TftTouchShield.RED);
        showCentered("Tap the sky to fire", 150, 1, TftTouchShield.WHITE);
        showCentered("Tap to start", 170, 1, TftTouchShield.YELLOW);
        waitForTap();
        Random.seed(Clock.micros());

        while (true) {
            playGame(missiles, shots, blasts, alive, ammo);
            if (score > best) {
                best = score;
            }
            drawHeader();
            showCentered("THE END", 120, 3, TftTouchShield.RED);
            showCentered("Tap to play again", 170, 1, TftTouchShield.WHITE);
            Delay.millis(1000);
            waitForTap();
        }
    }

    // ---- Game flow ----

    private static void playGame(int[] missiles, int[] shots, int[] blasts, boolean[] alive, int[] ammo) {
        score = 0;
        wave = 0;
        nextBonusCity = BONUS_CITY_SCORE;
        for (int target = 0; target < TARGETS; target++) {
            alive[target] = true;
        }
        while (true) {
            wave = wave + 1;
            startWave(missiles, shots, blasts, alive, ammo);
            playWave(missiles, shots, blasts, alive, ammo);
            tallyBonus(alive, ammo);
            if (countCities(alive) == 0) {
                return;
            }
        }
    }

    private static void startWave(int[] missiles, int[] shots, int[] blasts, boolean[] alive, int[] ammo) {
        clearSlots(missiles, MISSILES, T_STRIDE);
        clearSlots(shots, SHOTS, T_STRIDE);
        clearSlots(blasts, BLASTS, B_STRIDE);
        for (int base = 0; base < 3; base++) {
            ammo[base] = BASE_AMMO;
            alive[FIRST_BASE + base] = true;
        }
        toSpawn = Math.min(10 + 2 * wave, 30);
        spawnCountdown = 40;
        enemySpeed = Math.min(26 + 6 * wave, 110);
        TftTouchShield.fillScreen(SKY);
        drawHeader();
        drawGround(alive, ammo);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.YELLOW, SKY);
        TftTouchShield.setCursor(84, 130);
        TftTouchShield.print("WAVE ");
        TftTouchShield.print(wave);
        Delay.millis(1200);
        TftTouchShield.fillRect(0, 125, WIDTH, 30, SKY);
    }

    private static void playWave(int[] missiles, int[] shots, int[] blasts, boolean[] alive, int[] ammo) {
        int next = Clock.millis();
        while (true) {
            while (Clock.millis() - next < 0) {
                Delay.millis(1);
            }
            next = next + FRAME_MILLIS;

            pollTouch(shots, alive, ammo);
            spawnEnemies(missiles, alive);
            moveShots(shots, blasts);
            moveMissiles(missiles, blasts, alive, ammo);
            updateBlasts(missiles, blasts, alive, ammo);

            if (toSpawn == 0 && countActive(missiles, MISSILES, T_STRIDE) == 0
                    && countActive(blasts, BLASTS, B_STRIDE) == 0 && countActive(shots, SHOTS, T_STRIDE) == 0) {
                return;
            }
        }
    }

    private static void tallyBonus(boolean[] alive, int[] ammo) {
        Delay.millis(500);
        int left = ammo[0] + ammo[1] + ammo[2];
        int cities = countCities(alive);
        int bonus = left * AMMO_BONUS * multiplier() + cities * CITY_BONUS * multiplier();
        showCentered("BONUS POINTS", 110, 2, TftTouchShield.YELLOW);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(SHOT_TRAIL, SKY);
        TftTouchShield.setCursor(60, 140);
        TftTouchShield.print(left);
        TftTouchShield.print(" x ");
        TftTouchShield.print(AMMO_BONUS * multiplier());
        TftTouchShield.setTextColor(CITY_COLOR, SKY);
        TftTouchShield.setCursor(60, 164);
        TftTouchShield.print(cities);
        TftTouchShield.print(" x ");
        TftTouchShield.print(CITY_BONUS * multiplier());
        addScore(bonus, alive);
        drawHeader();
        Delay.millis(2200);
    }

    /** Points double every two waves, up to six times, as in the arcade game. */
    private static int multiplier() {
        return Math.min(1 + (wave - 1) / 2, 6);
    }

    private static void addScore(int points, boolean[] alive) {
        score = score + points;
        while (score >= nextBonusCity) {
            nextBonusCity = nextBonusCity + BONUS_CITY_SCORE;
            for (int city = 0; city < CITIES; city++) {
                if (!alive[city]) {
                    alive[city] = true;
                    drawCity(city, true);
                    break;
                }
            }
        }
    }

    // ---- Enemy missiles ----

    private static void spawnEnemies(int[] missiles, boolean[] alive) {
        if (toSpawn == 0) {
            return;
        }
        spawnCountdown = spawnCountdown - 1;
        if (spawnCountdown > 0) {
            return;
        }
        int burst = Math.min(toSpawn, Random.nextInt(1, 3 + wave / 3));
        for (int i = 0; i < burst; i++) {
            int startX = Random.nextInt(8, WIDTH - 8);
            if (launchEnemy(missiles, alive, startX, HEADER + 1, false)) {
                toSpawn = toSpawn - 1;
            }
        }
        spawnCountdown = Random.nextInt(60, 150) - Math.min(wave * 6, 50);
    }

    /** Starts a warhead from ({@code x}, {@code y}) towards a random target; false when no slot is free. */
    private static boolean launchEnemy(int[] missiles, boolean[] alive, int x, int y, boolean split) {
        int slot = freeSlot(missiles, MISSILES, T_STRIDE);
        if (slot < 0) {
            return false;
        }
        // Mostly aim at what is still standing, like the arcade game.
        int target = Random.nextInt(TARGETS);
        for (int tries = 0; tries < 6 && !alive[target]; tries++) {
            target = Random.nextInt(TARGETS);
        }
        startTrail(missiles, slot, x, y, targetX(target), targetY(target), enemySpeed);
        int base = slot * T_STRIDE;
        missiles[base + T_TARGET] = target;
        missiles[base + T_SPLIT] = 0;
        if (split) {
            missiles[base + T_SPLIT] = 1;
        }
        return true;
    }

    private static void moveMissiles(int[] missiles, int[] blasts, boolean[] alive, int[] ammo) {
        for (int slot = 0; slot < MISSILES; slot++) {
            int base = slot * T_STRIDE;
            if (missiles[base + T_ACTIVE] == 0) {
                continue;
            }
            if (advanceTrail(missiles, slot, ENEMY_TRAIL)) {
                int target = missiles[base + T_TARGET];
                eraseTrail(missiles, slot);
                hitTarget(target, alive, ammo);
                startBlast(blasts, targetX(target), targetY(target));
                continue;
            }
            int x = missiles[base + T_HEAD_X];
            int y = missiles[base + T_HEAD_Y];
            // MIRV: from wave 2, an unsplit warhead may split while crossing the middle band.
            if (wave >= 2 && missiles[base + T_SPLIT] == 0 && y > 110 && y < 170
                    && Random.nextInt(400) < 2 + wave) {
                missiles[base + T_SPLIT] = 1;
                int children = Random.nextInt(1, 3);
                for (int i = 0; i < children; i++) {
                    launchEnemy(missiles, alive, x, y, true);
                }
            }
        }
    }

    private static void hitTarget(int target, boolean[] alive, int[] ammo) {
        if (target >= FIRST_BASE) {
            ammo[target - FIRST_BASE] = 0;
            alive[target] = false;
            drawBase(target - FIRST_BASE, ammo);
        } else if (alive[target]) {
            alive[target] = false;
            drawCity(target, false);
        }
    }

    // ---- Interceptors ----

    private static void fire(int[] shots, boolean[] alive, int[] ammo, int targetX, int targetY) {
        int chosen = -1;
        int bestDistance = 1000;
        for (int base = 0; base < 3; base++) {
            int distance = Math.abs(baseX(base) - targetX);
            if (ammo[base] > 0 && distance < bestDistance) {
                bestDistance = distance;
                chosen = base;
            }
        }
        int slot = freeSlot(shots, SHOTS, T_STRIDE);
        if (chosen < 0 || slot < 0) {
            return;
        }
        ammo[chosen] = ammo[chosen] - 1;
        drawAmmo(chosen, ammo);
        startTrail(shots, slot, baseX(chosen), GROUND_Y - 14, targetX, targetY, SHOT_SPEED);
        drawMarker(targetX, targetY, TftTouchShield.WHITE);
    }

    private static void moveShots(int[] shots, int[] blasts) {
        for (int slot = 0; slot < SHOTS; slot++) {
            int base = slot * T_STRIDE;
            if (shots[base + T_ACTIVE] != 0 && advanceTrail(shots, slot, SHOT_TRAIL)) {
                int x = shots[base + T_END_X];
                int y = shots[base + T_END_Y];
                eraseTrail(shots, slot);
                drawMarker(x, y, SKY);
                startBlast(blasts, x, y);
            }
        }
    }

    // ---- Trails ----

    /** Starts tracing a line from ({@code x0}, {@code y0}) to ({@code x1}, {@code y1}) at {@code speed} pixels per frame (fixed point). */
    private static void startTrail(int[] trails, int slot, int x0, int y0, int x1, int y1, int speed) {
        int base = slot * T_STRIDE;
        int dx = x1 - x0;
        int dy = y1 - y0;
        int length = Math.max(1, (int) Math.sqrt((double) (dx * dx + dy * dy)));
        // A Bresenham line takes one step per pixel along its major axis.
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        trails[base + T_ACTIVE] = 1;
        trails[base + T_START_X] = x0;
        trails[base + T_START_Y] = y0;
        trails[base + T_END_X] = x1;
        trails[base + T_END_Y] = y1;
        trails[base + T_STEPS] = steps;
        trails[base + T_PROGRESS] = 0;
        trails[base + T_RATE] = Math.max(1, speed * steps / length);
        trails[base + T_DRAWN] = 0;
        trails[base + T_HEAD_X] = x0;
        trails[base + T_HEAD_Y] = y0;
    }

    /** Draws the steps the trail has advanced this frame; returns true once it reaches its end. */
    private static boolean advanceTrail(int[] trails, int slot, int color) {
        int base = slot * T_STRIDE;
        int steps = trails[base + T_STEPS];
        int progress = trails[base + T_PROGRESS] + trails[base + T_RATE];
        trails[base + T_PROGRESS] = progress;
        int reached = Math.min(progress / FIXED, steps) + 1;
        int drawn = trails[base + T_DRAWN];
        if (reached > drawn) {
            traceLine(trails[base + T_START_X], trails[base + T_START_Y], trails[base + T_END_X],
                    trails[base + T_END_Y], drawn, reached, color);
            trails[base + T_DRAWN] = reached;
            trails[base + T_HEAD_X] = traceX;
            trails[base + T_HEAD_Y] = traceY;
        }
        return reached > steps;
    }

    /** Retraces exactly the pixels the trail has drawn, in the sky's color, and frees its slot. */
    private static void eraseTrail(int[] trails, int slot) {
        int base = slot * T_STRIDE;
        trails[base + T_ACTIVE] = 0;
        traceLine(trails[base + T_START_X], trails[base + T_START_Y], trails[base + T_END_X],
                trails[base + T_END_Y], 0, trails[base + T_DRAWN], SKY);
    }

    // ---- Explosions ----

    private static void startBlast(int[] blasts, int x, int y) {
        int slot = freeSlot(blasts, BLASTS, B_STRIDE);
        if (slot < 0) {
            return;
        }
        int base = slot * B_STRIDE;
        blasts[base + B_ACTIVE] = 1;
        blasts[base + B_X] = x;
        blasts[base + B_Y] = y;
        blasts[base + B_AGE] = 0;
        blasts[base + B_RADIUS] = 0;
    }

    private static void updateBlasts(int[] missiles, int[] blasts, boolean[] alive, int[] ammo) {
        boolean groundTouched = false;
        boolean headerTouched = false;
        for (int slot = 0; slot < BLASTS; slot++) {
            int base = slot * B_STRIDE;
            if (blasts[base + B_ACTIVE] == 0) {
                continue;
            }
            int x = blasts[base + B_X];
            int y = blasts[base + B_Y];
            int age = blasts[base + B_AGE] + 1;
            blasts[base + B_AGE] = age;
            int radius = blastRadius(age);
            int shown = blasts[base + B_RADIUS];
            if (age >= BLAST_LIFE) {
                TftTouchShield.fillCircle(x, y, shown, SKY);
                blasts[base + B_ACTIVE] = 0;
                if (y + BLAST_RADIUS >= GROUND_Y - 16) {
                    groundTouched = true;
                }
                if (y - BLAST_RADIUS <= HEADER) {
                    headerTouched = true;
                }
                continue;
            }
            if (radius < shown) {
                TftTouchShield.fillCircle(x, y, shown, SKY);
            }
            if (radius != shown || (age & 3) == 0) {
                TftTouchShield.fillCircle(x, y, radius, blastColor(age));
            }
            blasts[base + B_RADIUS] = radius;

            // Any warhead inside the fireball explodes too.
            for (int m = 0; m < MISSILES; m++) {
                int mBase = m * T_STRIDE;
                if (missiles[mBase + T_ACTIVE] == 0) {
                    continue;
                }
                int dx = missiles[mBase + T_HEAD_X] - x;
                int dy = missiles[mBase + T_HEAD_Y] - y;
                if (dx * dx + dy * dy <= radius * radius) {
                    int headX = missiles[mBase + T_HEAD_X];
                    int headY = missiles[mBase + T_HEAD_Y];
                    eraseTrail(missiles, m);
                    addScore(MISSILE_POINTS * multiplier(), alive);
                    drawHeader();
                    startBlast(blasts, headX, headY);
                }
            }
        }
        if (groundTouched) {
            drawGround(alive, ammo);
        }
        if (headerTouched) {
            drawHeader();
        }
    }

    private static int blastRadius(int age) {
        if (age <= BLAST_RADIUS) {
            return age;
        }
        if (age <= BLAST_RADIUS + BLAST_HOLD) {
            return BLAST_RADIUS;
        }
        return Math.max(0, BLAST_LIFE - age);
    }

    private static int blastColor(int age) {
        int phase = (age / 2) % 4;
        if (phase == 0) {
            return TftTouchShield.WHITE;
        }
        if (phase == 1) {
            return TftTouchShield.YELLOW;
        }
        if (phase == 2) {
            return TftTouchShield.ORANGE;
        }
        return TftTouchShield.MAGENTA;
    }

    // ---- Slots ----

    private static void clearSlots(int[] records, int count, int stride) {
        for (int i = 0; i < count * stride; i++) {
            records[i] = 0;
        }
    }

    private static int freeSlot(int[] records, int count, int stride) {
        for (int slot = 0; slot < count; slot++) {
            if (records[slot * stride] == 0) {
                return slot;
            }
        }
        return -1;
    }

    private static int countActive(int[] records, int count, int stride) {
        int active = 0;
        for (int slot = 0; slot < count; slot++) {
            if (records[slot * stride] != 0) {
                active = active + 1;
            }
        }
        return active;
    }

    private static int countCities(boolean[] alive) {
        int count = 0;
        for (int city = 0; city < CITIES; city++) {
            if (alive[city]) {
                count = count + 1;
            }
        }
        return count;
    }

    // ---- Geometry ----

    private static int baseX(int base) {
        return 18 + base * 102;
    }

    private static int cityX(int city) {
        if (city < 3) {
            return 46 + city * 24;
        }
        return 146 + (city - 3) * 24;
    }

    private static int targetX(int target) {
        if (target >= FIRST_BASE) {
            return baseX(target - FIRST_BASE);
        }
        return cityX(target);
    }

    private static int targetY(int target) {
        if (target >= FIRST_BASE) {
            return GROUND_Y - 13;
        }
        return GROUND_Y - 6;
    }

    // ---- Input ----

    /** Fires on each new press in the sky; holding a touch does not repeat. */
    private static void pollTouch(int[] shots, boolean[] alive, int[] ammo) {
        if (!TftTouchShield.readTouch()) {
            releaseMisses = releaseMisses + 1;
            if (releaseMisses >= 3) {
                touching = false;
            }
            return;
        }
        releaseMisses = 0;
        if (touching) {
            return;
        }
        touching = true;
        int x = Math.max(3, Math.min(TftTouchShield.touchX(), WIDTH - 4));
        int y = Math.max(HEADER + 4, Math.min(TftTouchShield.touchY(), LOWEST_TARGET_Y));
        fire(shots, alive, ammo, x, y);
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
        touching = false;
    }

    // ---- Drawing ----

    /**
     * Walks the Bresenham line from ({@code x0}, {@code y0}) to ({@code x1}, {@code y1}) and plots
     * only its steps {@code from} (inclusive) to {@code to} (exclusive), so a trail drawn a few steps
     * per frame and erased in one pass covers exactly the same pixels. Runs of plotted pixels on the
     * same row become one fill; the last plotted pixel is left in {@link #traceX}/{@link #traceY}.
     */
    private static void traceLine(int x0, int y0, int x1, int y1, int from, int to, int color) {
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
        for (int step = 0; step < to; step++) {
            if (step == from) {
                runStart = x;
            }
            if (step >= from) {
                traceX = x;
                traceY = y;
            }
            if (step == to - 1 || (x == x1 && y == y1)) {
                if (step >= from) {
                    fillRun(runStart, x, y, color);
                }
                return;
            }
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
            if (nextY != y && step >= from) {
                fillRun(runStart, x, y, color);
            }
            if (nextY != y) {
                runStart = nextX;
            }
            x = nextX;
            y = nextY;
        }
    }

    private static void fillRun(int from, int to, int y, int color) {
        int left = Math.min(from, to);
        int right = Math.max(from, to);
        TftTouchShield.fillRect(left, y, right - left + 1, 1, color);
    }

    private static void drawMarker(int x, int y, int color) {
        TftTouchShield.drawPixel(x - 2, y - 2, color);
        TftTouchShield.drawPixel(x + 2, y - 2, color);
        TftTouchShield.drawPixel(x - 2, y + 2, color);
        TftTouchShield.drawPixel(x + 2, y + 2, color);
    }

    private static void drawGround(boolean[] alive, int[] ammo) {
        TftTouchShield.fillRect(0, GROUND_Y, WIDTH, 320 - GROUND_Y, GROUND);
        TftTouchShield.fillRect(0, GROUND_Y - 30, WIDTH, 30, SKY);
        for (int base = 0; base < 3; base++) {
            drawBase(base, ammo);
        }
        for (int city = 0; city < CITIES; city++) {
            drawCity(city, alive[city]);
        }
    }

    private static void drawBase(int base, int[] ammo) {
        int x = baseX(base);
        for (int row = 0; row < 12; row++) {
            int half = 9 + row;
            TftTouchShield.fillRect(x - half, GROUND_Y - 12 + row, 2 * half + 1, 1, GROUND);
        }
        drawAmmo(base, ammo);
    }

    private static void drawAmmo(int base, int[] ammo) {
        int x = baseX(base);
        TftTouchShield.fillRect(x - 9, GROUND_Y - 11, 19, 9, GROUND);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.NAVY, GROUND);
        if (ammo[base] == 0) {
            TftTouchShield.setTextColor(TftTouchShield.RED, GROUND);
            TftTouchShield.setCursor(x - 8, GROUND_Y - 10);
            TftTouchShield.print("OUT");
            return;
        }
        if (ammo[base] < 10) {
            TftTouchShield.setCursor(x - 2, GROUND_Y - 10);
        } else {
            TftTouchShield.setCursor(x - 5, GROUND_Y - 10);
        }
        TftTouchShield.print(ammo[base]);
    }

    private static void drawCity(int city, boolean standing) {
        int x = cityX(city) - 9;
        TftTouchShield.fillRect(x, GROUND_Y - 14, 19, 14, SKY);
        if (!standing) {
            TftTouchShield.fillRect(x + 1, GROUND_Y - 3, 17, 3, RUBBLE);
            TftTouchShield.fillRect(x + 5, GROUND_Y - 5, 5, 2, RUBBLE);
            return;
        }
        TftTouchShield.fillRect(x, GROUND_Y - 6, 19, 6, CITY_COLOR);
        TftTouchShield.fillRect(x + 2, GROUND_Y - 11, 4, 5, CITY_COLOR);
        TftTouchShield.fillRect(x + 8, GROUND_Y - 14, 4, 8, CITY_COLOR);
        TftTouchShield.fillRect(x + 14, GROUND_Y - 9, 3, 3, CITY_COLOR);
        TftTouchShield.drawPixel(x + 9, GROUND_Y - 12, CITY_WINDOWS);
        TftTouchShield.drawPixel(x + 9, GROUND_Y - 9, CITY_WINDOWS);
        TftTouchShield.drawPixel(x + 3, GROUND_Y - 9, CITY_WINDOWS);
        TftTouchShield.drawPixel(x + 15, GROUND_Y - 4, CITY_WINDOWS);
    }

    private static void drawHeader() {
        TftTouchShield.fillRect(0, 0, WIDTH, HEADER, HEADER_BACKGROUND);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, HEADER_BACKGROUND);
        TftTouchShield.setCursor(4, 3);
        TftTouchShield.print(score);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.YELLOW, HEADER_BACKGROUND);
        TftTouchShield.setCursor(110, 7);
        TftTouchShield.print("HI ");
        TftTouchShield.print(best);
        TftTouchShield.setTextColor(TftTouchShield.CYAN, HEADER_BACKGROUND);
        TftTouchShield.setCursor(190, 7);
        TftTouchShield.print("WAVE ");
        TftTouchShield.print(wave);
    }

    private static void showCentered(String text, int y, int size, int color) {
        TftTouchShield.setTextSize(size);
        TftTouchShield.setTextColor(color, SKY);
        TftTouchShield.setCursor((WIDTH - text.length() * 6 * size) / 2, y);
        TftTouchShield.print(text);
    }
}
