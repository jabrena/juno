package io.github.jabrena.juno.games.missilecommand;

import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/** Score, wave progress, targets and ammo, and the rules governing missiles, shots and blasts. */
final class Session {
    static final int WIDTH = 240;
    static final int HEADER = 20;
    static final int GROUND_Y = 300;
    static final int LOWEST_TARGET_Y = GROUND_Y - 22;

    // Targets: six cities (0-5) and three bases (6-8).
    static final int TARGETS = 9;
    static final int CITIES = 6;
    static final int FIRST_BASE = 6;
    static final int BASE_AMMO = 10;

    static final int CITY_BONUS = 100;
    static final int AMMO_BONUS = 5;
    static final int MISSILE_POINTS = 25;
    static final int BONUS_CITY_SCORE = 10000;

    // Trails — enemy missiles and interceptors alike: fixed-stride records of a line being traced
    // from its start to its end, one Bresenham step at a time.
    static final int T_ACTIVE = 0;
    static final int T_START_X = 1;
    static final int T_START_Y = 2;
    static final int T_END_X = 3;
    static final int T_END_Y = 4;
    static final int T_STEPS = 5;
    static final int T_PROGRESS = 6;
    static final int T_RATE = 7;
    static final int T_DRAWN = 8;
    static final int T_HEAD_X = 9;
    static final int T_HEAD_Y = 10;
    static final int T_TARGET = 11;
    static final int T_SPLIT = 12;
    static final int T_STRIDE = 13;

    static final int MISSILES = 12;
    static final int SHOTS = 8;
    private static final int FIXED = 64;
    static final int SHOT_SPEED = 5 * FIXED;

    // Explosions.
    static final int BLASTS = 16;
    static final int B_ACTIVE = 0;
    static final int B_X = 1;
    static final int B_Y = 2;
    static final int B_AGE = 3;
    static final int B_RADIUS = 4;
    static final int B_STRIDE = 5;
    static final int BLAST_RADIUS = 15;
    static final int BLAST_HOLD = 10;
    static final int BLAST_LIFE = BLAST_RADIUS * 2 + BLAST_HOLD;

    static int score;
    static int best;
    static int wave;
    static int nextBonusCity;
    static int toSpawn;
    static int spawnCountdown;
    static int enemySpeed;

    private Session() {
    }

    static void newGame(boolean[] alive) {
        score = 0;
        wave = 0;
        nextBonusCity = BONUS_CITY_SCORE;
        for (int target = 0; target < TARGETS; target++) {
            alive[target] = true;
        }
    }

    static void finishGame() {
        if (score > best) {
            best = score;
        }
    }

    static void startWave(int[] missiles, int[] shots, int[] blasts, boolean[] alive, int[] ammo) {
        wave = wave + 1;
        clear(missiles, MISSILES * T_STRIDE);
        clear(shots, SHOTS * T_STRIDE);
        clear(blasts, BLASTS * B_STRIDE);
        for (int base = 0; base < 3; base++) {
            ammo[base] = BASE_AMMO;
            alive[FIRST_BASE + base] = true;
        }
        toSpawn = Math.min(10 + 2 * wave, 30);
        spawnCountdown = 40;
        enemySpeed = Math.min(26 + 6 * wave, 110);
    }

    static boolean waveOver(int[] missiles, int[] shots, int[] blasts) {
        return toSpawn == 0 && count(missiles, MISSILES, T_STRIDE) == 0
                && count(blasts, BLASTS, B_STRIDE) == 0 && count(shots, SHOTS, T_STRIDE) == 0;
    }

    /** Points double every two waves, up to six times, as in the arcade game. */
    static int multiplier() {
        return Math.min(1 + (wave - 1) / 2, 6);
    }

    static int ammoBonus(int[] ammo) {
        return (ammo[0] + ammo[1] + ammo[2]) * AMMO_BONUS * multiplier();
    }

    static int cityBonus(boolean[] alive) {
        return countCities(alive) * CITY_BONUS * multiplier();
    }

    static void addScore(int points, boolean[] alive) {
        score = score + points;
        while (score >= nextBonusCity) {
            nextBonusCity = nextBonusCity + BONUS_CITY_SCORE;
            for (int city = 0; city < CITIES; city++) {
                if (!alive[city]) {
                    alive[city] = true;
                    SceneRenderer.drawCity(city, true);
                    break;
                }
            }
        }
    }

    // ---- Enemy missiles ----

    static void spawnEnemies(int[] missiles, boolean[] alive) {
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
    static boolean launchEnemy(int[] missiles, boolean[] alive, int x, int y, boolean split) {
        int slot = firstInactive(missiles, MISSILES, T_STRIDE);
        if (slot < 0) {
            return false;
        }
        // Mostly aim at what is still standing, like the arcade game.
        int target = Random.nextInt(TARGETS);
        for (int tries = 0; tries < 6 && !alive[target]; tries++) {
            target = Random.nextInt(TARGETS);
        }
        SceneRenderer.startTrail(missiles, slot, x, y, targetX(target), targetY(target), enemySpeed);
        int base = slot * T_STRIDE;
        missiles[base + T_TARGET] = target;
        missiles[base + T_SPLIT] = split ? 1 : 0;
        return true;
    }

    static void moveMissiles(int[] missiles, int[] blasts, boolean[] alive, int[] ammo) {
        for (int slot = 0; slot < MISSILES; slot++) {
            int base = slot * T_STRIDE;
            if (missiles[base + T_ACTIVE] == 0) {
                continue;
            }
            if (SceneRenderer.advanceTrail(missiles, slot, SceneRenderer.ENEMY_TRAIL)) {
                int target = missiles[base + T_TARGET];
                SceneRenderer.eraseTrail(missiles, slot);
                hitTarget(target, alive, ammo);
                Blasts.startBlast(blasts, targetX(target), targetY(target));
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
            SceneRenderer.drawBase(target - FIRST_BASE, ammo);
        } else if (alive[target]) {
            alive[target] = false;
            SceneRenderer.drawCity(target, false);
        }
    }

    // ---- Interceptors ----

    /** The base nearest {@code x} that still has ammunition, or -1 if none do. */
    static int nearestBase(int[] ammo, int x) {
        int chosen = -1;
        int bestDistance = 1000;
        for (int base = 0; base < 3; base++) {
            int distance = Math.abs(SceneRenderer.baseX(base) - x);
            if (ammo[base] > 0 && distance < bestDistance) {
                bestDistance = distance;
                chosen = base;
            }
        }
        return chosen;
    }

    /** Fires from the nearest base with ammunition; returns the shot's slot, or -1 if it couldn't fire. */
    static int fire(int[] shots, int[] ammo, int targetX, int targetY) {
        int chosen = nearestBase(ammo, targetX);
        int slot = firstInactive(shots, SHOTS, T_STRIDE);
        if (chosen < 0 || slot < 0) {
            return -1;
        }
        ammo[chosen] = ammo[chosen] - 1;
        SceneRenderer.drawAmmo(chosen, ammo);
        SceneRenderer.startTrail(shots, slot, SceneRenderer.baseX(chosen), GROUND_Y - 14, targetX, targetY,
                SHOT_SPEED);
        SceneRenderer.drawMarker(targetX, targetY, TftTouchShield.WHITE);
        // No target claimed yet; the caller can note one afterwards keyed on this slot.
        shots[slot * T_STRIDE + T_TARGET] = -1;
        return slot;
    }

    static void moveShots(int[] shots, int[] blasts) {
        for (int slot = 0; slot < SHOTS; slot++) {
            int base = slot * T_STRIDE;
            if (shots[base + T_ACTIVE] != 0 && SceneRenderer.advanceTrail(shots, slot, SceneRenderer.SHOT_TRAIL)) {
                int x = shots[base + T_END_X];
                int y = shots[base + T_END_Y];
                SceneRenderer.eraseTrail(shots, slot);
                SceneRenderer.drawMarker(x, y, SceneRenderer.SKY);
                Blasts.startBlast(blasts, x, y);
            }
        }
    }

    // ---- Targets ----

    static int targetX(int target) {
        return target >= FIRST_BASE ? SceneRenderer.baseX(target - FIRST_BASE) : SceneRenderer.cityX(target);
    }

    static int targetY(int target) {
        return target >= FIRST_BASE ? GROUND_Y - 13 : GROUND_Y - 6;
    }

    static int countCities(boolean[] alive) {
        int count = 0;
        for (int city = 0; city < CITIES; city++) {
            if (alive[city]) {
                count = count + 1;
            }
        }
        return count;
    }

    // ---- Slots ----

    static void clear(int[] records, int length) {
        for (int i = 0; i < length; i++) {
            records[i] = 0;
        }
    }

    static int firstInactive(int[] records, int slots, int stride) {
        for (int slot = 0; slot < slots; slot++) {
            if (records[slot * stride] == 0) {
                return slot;
            }
        }
        return -1;
    }

    static int count(int[] records, int slots, int stride) {
        int active = 0;
        for (int slot = 0; slot < slots; slot++) {
            if (records[slot * stride] != 0) {
                active = active + 1;
            }
        }
        return active;
    }
}
