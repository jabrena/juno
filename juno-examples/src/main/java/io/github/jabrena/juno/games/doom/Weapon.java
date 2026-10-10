package io.github.jabrena.juno.games.doom;

import io.github.jabrena.juno.api.Random;

/**
 * The marine's pistol: a hitscan shot along the view with DOOM's vertical auto-aim, so it hits the
 * nearest visible monster within a few degrees of the crosshair. A shot wakes everything in earshot.
 */
final class Weapon {
    private static final int COOLDOWN = 10;
    private static final float SLACK = 0.03f;

    private static int ready;
    /** Frames left of the muzzle flash. */
    static int flash;

    private Weapon() {
    }

    static void reset() {
        ready = 0;
        flash = 0;
    }

    /** Counts down the reload and the muzzle flash; call once per frame. */
    static void tick() {
        if (ready > 0) {
            ready = ready - 1;
        }
        if (flash > 0) {
            flash = flash - 1;
        }
    }

    static boolean loaded() {
        return ready == 0;
    }

    /** Fires if the pistol is ready; returns whether a monster was hit. */
    static boolean fire(short[] monsters, short[] ceilings) {
        if (ready > 0) {
            return false;
        }
        ready = COOLDOWN;
        flash = 3;
        Monsters.hearShot(monsters);
        int target = aimed(monsters, ceilings);
        if (target < 0) {
            return false;
        }
        Monsters.hit(monsters, target, Random.nextInt(5, 16));
        return true;
    }

    /** The nearest live monster in line with the view and in sight, or -1. */
    static int aimed(short[] monsters, short[] ceilings) {
        int best = -1;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0; i < World.monsters; i++) {
            int at = i * Monsters.STRIDE;
            if (monsters[at + Monsters.STATE] == Monsters.DEAD) {
                continue;
            }
            float dx = monsters[at + Monsters.X] - Player.x;
            float dy = monsters[at + Monsters.Y] - Player.y;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            if (distance >= bestDistance || distance < 1) {
                continue;
            }
            float off = Math.abs(angleTo(dx, dy));
            if (off < (float) Math.atan(18f / distance) + SLACK && Player.canSee(Player.x, Player.y, Player.eye,
                    monsters[at + Monsters.X], monsters[at + Monsters.Y],
                    monsters[at + Monsters.FLOOR] + Monsters.CENTER, ceilings)) {
                best = at;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Signed angle from the view direction to the direction ({@code dx}, {@code dy}), in -&#960;..&#960;. */
    static float angleTo(float dx, float dy) {
        float heading = (float) Math.atan2(dy, dx) - Player.angle;
        if (heading > (float) Math.PI) {
            heading = heading - 2 * (float) Math.PI;
        } else if (heading < (float) -Math.PI) {
            heading = heading + 2 * (float) Math.PI;
        }
        return heading;
    }
}
