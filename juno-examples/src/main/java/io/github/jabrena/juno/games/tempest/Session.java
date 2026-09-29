package io.github.jabrena.juno.games.tempest;

import io.github.jabrena.juno.api.Random;

/** Score, lives, level progress, cooldowns, and the rules governing enemies, shots and bullets. */
final class Session {
    static final int ENEMIES = 8;
    static final int E_ACTIVE = 0;
    static final int E_LANE = 1;
    static final int E_DEPTH = 2;
    static final int E_SHOWN_LANE = 3;
    static final int E_SHOWN_DEPTH = 4;
    static final int E_ON_RIM = 5;
    static final int E_TIMER = 6;
    static final int E_STRIDE = 7;
    static final int ENEMY_HALF_DEPTH = 40;
    /** Where a flipper rides the rim: close to it, without its outline touching the rim itself. */
    static final int RIM_DEPTH = Tube.DEPTH - ENEMY_HALF_DEPTH - 16;
    static final int FLIPPER_POINTS = 150;

    static final int SHOTS = 6;
    static final int BULLETS = 4;
    static final int S_ACTIVE = 0;
    static final int S_LANE = 1;
    static final int S_DEPTH = 2;
    static final int S_SHOWN_X = 3;
    static final int S_SHOWN_Y = 4;
    static final int S_STRIDE = 5;
    static final int SHOT_SPEED = 64;
    static final int HIT_DEPTH = 56;

    private static final int MOVE_COOLDOWN = 2;
    private static final int FIRE_COOLDOWN = 4;

    static int score;
    static int best;
    static int lives;
    static int level;
    static int playerLane;
    static int moveCooldown;
    static int fireCooldown;
    static boolean zapperReady;
    static boolean zapRequested;
    static int toSpawn;
    static int spawnCountdown;
    static int climbSpeed;

    private Session() {
    }

    static void newGame() {
        score = 0;
        lives = 3;
        level = 0;
    }

    static void finishGame() {
        if (score > best) {
            best = score;
        }
    }

    /** Resets the rules for the next level; the tube shape and screen are drawn by the caller. */
    static void startLevel(int[] enemies, int[] shots, int[] bullets) {
        level = level + 1;
        clear(enemies, ENEMIES * E_STRIDE);
        clear(shots, SHOTS * S_STRIDE);
        clear(bullets, BULLETS * S_STRIDE);
        toSpawn = Math.min(6 + 2 * level, 24);
        spawnCountdown = 30;
        climbSpeed = Math.min(5 + level, 14);
        zapperReady = true;
        zapRequested = false;
        playerLane = 0;
        moveCooldown = 0;
        fireCooldown = 0;
    }

    static boolean levelCleared(int[] enemies) {
        return toSpawn == 0 && count(enemies, ENEMIES, E_STRIDE) == 0;
    }

    /** Awards the level-clear bonus and returns it. */
    static int finishLevel() {
        int bonus = 100 * level;
        score = score + bonus;
        return bonus;
    }

    // ---- Cooldowns and the claw ----

    static void tickCooldowns() {
        if (moveCooldown > 0) {
            moveCooldown = moveCooldown - 1;
        }
        if (fireCooldown > 0) {
            fireCooldown = fireCooldown - 1;
        }
    }

    /** Steps the claw one lane towards {@code target}, if the cooldown allows it. */
    static void moveToward(int[] tube, int target) {
        if (target == playerLane || moveCooldown > 0) {
            return;
        }
        int previous = playerLane;
        playerLane = Tube.stepToward(playerLane, target);
        SceneRenderer.moveClaw(tube, previous, playerLane);
        moveCooldown = MOVE_COOLDOWN;
    }

    static void fire(int[] shots) {
        if (fireCooldown > 0) {
            return;
        }
        int slot = firstInactive(shots, SHOTS, S_STRIDE);
        if (slot < 0) {
            return;
        }
        int base = slot * S_STRIDE;
        shots[base + S_ACTIVE] = 1;
        shots[base + S_LANE] = playerLane;
        shots[base + S_DEPTH] = Tube.DEPTH;
        shots[base + S_SHOWN_X] = -1;
        fireCooldown = FIRE_COOLDOWN;
    }

    static void requestZap() {
        if (zapperReady) {
            zapperReady = false;
            zapRequested = true;
        }
    }

    static boolean consumeZapRequest() {
        boolean requested = zapRequested;
        zapRequested = false;
        return requested;
    }

    static void zapAllEnemies(int[] tube, int[] enemies) {
        for (int slot = 0; slot < ENEMIES; slot++) {
            if (enemies[slot * E_STRIDE + E_ACTIVE] != 0) {
                Enemies.killEnemy(tube, enemies, slot);
                score = score + FLIPPER_POINTS;
            }
        }
    }

    // ---- Shots and bullets ----

    /** Moves your shots inward; scores and destroys any enemy or bullet they meet. */
    static void moveShots(int[] tube, int[] shots, int[] enemies, int[] bullets) {
        for (int slot = 0; slot < SHOTS; slot++) {
            if (shots[slot * S_STRIDE + S_ACTIVE] != 0) {
                advanceShot(tube, shots, enemies, bullets, slot);
            }
        }
    }

    private static void advanceShot(int[] tube, int[] shots, int[] enemies, int[] bullets, int slot) {
        int base = slot * S_STRIDE;
        int lane = shots[base + S_LANE];
        int depth = shots[base + S_DEPTH] - SHOT_SPEED;
        shots[base + S_DEPTH] = depth;
        boolean spent = depth <= ENEMY_HALF_DEPTH / 2
                || hitsEnemy(tube, enemies, lane, depth)
                || hitsBullet(bullets, lane, depth);
        if (spent) {
            shots[base + S_ACTIVE] = 0;
            SceneRenderer.eraseShot(shots, slot);
        } else {
            SceneRenderer.showShot(tube, shots, slot);
        }
    }

    private static boolean hitsEnemy(int[] tube, int[] enemies, int lane, int depth) {
        for (int e = 0; e < ENEMIES; e++) {
            int base = e * E_STRIDE;
            if (enemies[base + E_ACTIVE] != 0 && enemies[base + E_LANE] == lane
                    && Math.abs(enemies[base + E_DEPTH] - depth) <= HIT_DEPTH) {
                Enemies.killEnemy(tube, enemies, e);
                score = score + FLIPPER_POINTS;
                return true;
            }
        }
        return false;
    }

    private static boolean hitsBullet(int[] bullets, int lane, int depth) {
        for (int b = 0; b < BULLETS; b++) {
            int base = b * S_STRIDE;
            if (bullets[base + S_ACTIVE] != 0 && bullets[base + S_LANE] == lane
                    && Math.abs(bullets[base + S_DEPTH] - depth) <= HIT_DEPTH) {
                bullets[base + S_ACTIVE] = 0;
                SceneRenderer.eraseShot(bullets, b);
                return true;
            }
        }
        return false;
    }

    /** Moves the enemies' bullets outward; returns true when one reaches the claw. */
    static boolean moveBullets(int[] tube, int[] bullets) {
        boolean hit = false;
        for (int slot = 0; slot < BULLETS; slot++) {
            int base = slot * S_STRIDE;
            if (bullets[base + S_ACTIVE] == 0) {
                continue;
            }
            int depth = bullets[base + S_DEPTH] + climbSpeed * 3;
            bullets[base + S_DEPTH] = depth;
            if (depth >= Tube.DEPTH - ENEMY_HALF_DEPTH / 2) {
                bullets[base + S_ACTIVE] = 0;
                SceneRenderer.eraseShot(bullets, slot);
                hit = hit || bullets[base + S_LANE] == playerLane;
            } else {
                SceneRenderer.showBullet(tube, bullets, slot);
            }
        }
        return hit;
    }

    static void fireBullet(int[] bullets, int lane, int depth) {
        int slot = firstInactive(bullets, BULLETS, S_STRIDE);
        if (slot < 0) {
            return;
        }
        int base = slot * S_STRIDE;
        bullets[base + S_ACTIVE] = 1;
        bullets[base + S_LANE] = lane;
        bullets[base + S_DEPTH] = depth + ENEMY_HALF_DEPTH;
        bullets[base + S_SHOWN_X] = -1;
    }

    // ---- Life loss ----

    /** Recycles the enemies still in the tube back into the spawn queue and clears live shots. */
    static void recycleAfterDeath(int[] enemies, int[] shots, int[] bullets) {
        for (int slot = 0; slot < ENEMIES; slot++) {
            if (enemies[slot * E_STRIDE + E_ACTIVE] != 0) {
                enemies[slot * E_STRIDE + E_ACTIVE] = 0;
                toSpawn = toSpawn + 1;
            }
        }
        clear(shots, SHOTS * S_STRIDE);
        clear(bullets, BULLETS * S_STRIDE);
        spawnCountdown = 40;
    }

    // ---- Records ----

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

    static void clear(int[] records, int length) {
        for (int i = 0; i < length; i++) {
            records[i] = 0;
        }
    }
}
