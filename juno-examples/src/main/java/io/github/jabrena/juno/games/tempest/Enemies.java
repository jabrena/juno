package io.github.jabrena.juno.games.tempest;

import io.github.jabrena.juno.api.Random;

/**
 * The flippers: spawning, climbing out of the tube, and crawling or grabbing along the rim. Split
 * out of {@link Session} to keep that class's own cyclomatic complexity in check; the record layout
 * ({@code E_*}/{@code ENEMIES}/{@code ENEMY_HALF_DEPTH}/{@code RIM_DEPTH}) stays there since
 * {@link SceneRenderer} and {@link AutopilotTempest} also reference it.
 */
final class Enemies {
    private Enemies() {
    }

    static void spawnEnemies(int[] enemies) {
        if (Session.toSpawn == 0) {
            return;
        }
        Session.spawnCountdown = Session.spawnCountdown - 1;
        if (Session.spawnCountdown > 0) {
            return;
        }
        Session.spawnCountdown = Math.max(20, Random.nextInt(40, 110) - Session.level * 5);
        int slot = Session.firstInactive(enemies, Session.ENEMIES, Session.E_STRIDE);
        if (slot < 0) {
            return;
        }
        int base = slot * Session.E_STRIDE;
        enemies[base + Session.E_ACTIVE] = 1;
        enemies[base + Session.E_LANE] = Random.nextInt(Tube.LANES);
        enemies[base + Session.E_DEPTH] = Session.ENEMY_HALF_DEPTH + 8;
        enemies[base + Session.E_SHOWN_LANE] = -1;
        enemies[base + Session.E_ON_RIM] = 0;
        enemies[base + Session.E_TIMER] = Random.nextInt(20, 60);
        Session.toSpawn = Session.toSpawn - 1;
    }

    /**
     * Climbs, flips, fires and crawls; each enemy is redrawn on alternate frames. Returns true when
     * a flipper grabs the claw.
     */
    static boolean moveEnemies(int[] tube, int[] enemies, int[] bullets, int frame) {
        boolean caught = false;
        for (int slot = 0; slot < Session.ENEMIES; slot++) {
            int base = slot * Session.E_STRIDE;
            if (enemies[base + Session.E_ACTIVE] == 0 || ((slot + frame) & 1) != 0) {
                continue;
            }
            if (enemies[base + Session.E_ON_RIM] == 0) {
                climb(enemies, bullets, base);
            } else if (enemies[base + Session.E_LANE] == Session.playerLane) {
                caught = onRimInPlayerLane(enemies, base) || caught;
            } else {
                crawlAlongRim(enemies, base);
            }
            SceneRenderer.drawEnemy(tube, enemies, slot);
        }
        return caught;
    }

    /** Climbs towards the rim; along the way it may veer a lane or fire back at you. */
    private static void climb(int[] enemies, int[] bullets, int base) {
        int timer = enemies[base + Session.E_TIMER] - 1;
        int depth = enemies[base + Session.E_DEPTH] + Session.climbSpeed * 2;
        int lane = enemies[base + Session.E_LANE];
        if (depth >= Session.RIM_DEPTH) {
            depth = Session.RIM_DEPTH;
            enemies[base + Session.E_ON_RIM] = 1;
            timer = 8;
        } else if (timer <= 0) {
            timer = Random.nextInt(15, 50);
            if (Random.nextInt(3) == 0) {
                Session.fireBullet(bullets, lane, depth);
            } else {
                lane = (lane + Tube.LANES + Random.nextInt(2) * 2 - 1) % Tube.LANES;
            }
        }
        enemies[base + Session.E_DEPTH] = depth;
        enemies[base + Session.E_LANE] = lane;
        enemies[base + Session.E_TIMER] = timer;
    }

    /** On the rim in your lane: a short grace period to shoot it, then it grabs you. */
    private static boolean onRimInPlayerLane(int[] enemies, int base) {
        int timer = enemies[base + Session.E_TIMER] - 1;
        enemies[base + Session.E_TIMER] = timer;
        return timer <= 0;
    }

    /** On the rim elsewhere: crawls a lane closer to you once its pause ends. */
    private static void crawlAlongRim(int[] enemies, int base) {
        int timer = enemies[base + Session.E_TIMER] - 1;
        if (timer > 0) {
            enemies[base + Session.E_TIMER] = timer;
            return;
        }
        int lane = Tube.stepToward(enemies[base + Session.E_LANE], Session.playerLane);
        enemies[base + Session.E_LANE] = lane;
        enemies[base + Session.E_TIMER] = lane == Session.playerLane ? 10 : Math.max(4, 14 - Session.level);
    }

    static void killEnemy(int[] tube, int[] enemies, int slot) {
        enemies[slot * Session.E_STRIDE + Session.E_ACTIVE] = 0;
        SceneRenderer.eraseEnemy(tube, enemies, slot);
    }
}
