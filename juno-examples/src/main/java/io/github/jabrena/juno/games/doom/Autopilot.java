package io.github.jabrena.juno.games.doom;

import io.github.jabrena.juno.api.Random;

/**
 * The CPU marine: walks the map's demo route (turn toward the next waypoint, stride to it, loop),
 * fights the monsters it notices and steps off the route for health or armor it needs and can reach.
 *
 * <p>It fights like a person, not an aimbot: it needs a moment to react to a new monster, swings its
 * view with momentum (so it overshoots and corrects), aims with an error that only slowly settles and
 * is thrown off again by each shot's recoil, sometimes pulls the trigger before it is lined up, keeps
 * strafing, backs off from monsters that come close, retreats when badly hurt, and catches its breath
 * after a kill. So it misses, takes hits, and now and then dies.
 */
final class Autopilot {
    private static final float TURN = 0.11f;
    private static final float STRIDE = 9f;
    private static final float ARRIVED = 28f;
    private static final float ENGAGE = 1000f;
    private static final float FIELD_OF_VIEW = 1.2f;
    private static final float GRAB = 450f;

    private static boolean detouring;
    private static float detourX;
    private static float detourY;
    private static int stuck;
    private static final int GIVE_UP = 40;

    private static final float MAX_SPIN = 0.15f;
    private static final float SPIN_ACCELERATION = 0.03f;
    private static final float TOO_CLOSE = 160f;
    private static final int LOW_HEALTH = 30;

    private static int enemy = -1;
    private static int reaction;
    private static int calm;
    private static float aimError;
    private static float spin;
    private static float sway;
    private static int strafeDirection;
    private static int strafeTime;

    static int target;

    private Autopilot() {
    }

    static void restart() {
        target = Math.min(1, Level.ROUTE_X.length - 1);
        detouring = false;
        enemy = -1;
        reaction = 0;
        calm = 0;
        spin = 0;
        sway = 0;
        aimError = 0;
        strafeDirection = 0;
        strafeTime = 0;
    }

    /**
     * Picks up the route again after manual play, from the nearest waypoint the marine can walk
     * straight to (or simply the nearest, when none is in reach).
     */
    static void resume(short[] ceilings) {
        detouring = false;
        float best = Float.MAX_VALUE;
        float bestReachable = Float.MAX_VALUE;
        int nearest = target;
        int reachable = -1;
        for (int i = 0; i < Level.ROUTE_X.length; i++) {
            float dx = Level.ROUTE_X[i] - Player.x;
            float dy = Level.ROUTE_Y[i] - Player.y;
            float distance = dx * dx + dy * dy;
            if (distance < best) {
                best = distance;
                nearest = i;
            }
            if (distance < bestReachable && !Player.blocked(Player.x, Player.y, Level.ROUTE_X[i], Level.ROUTE_Y[i], ceilings)) {
                bestReachable = distance;
                reachable = i;
            }
        }
        target = reachable >= 0 ? reachable : nearest;
    }

    static void step(short[] ceilings, short[] monsters, byte[] taken) {
        if (fight(ceilings, monsters)) {
            return;
        }
        int item = wanted(taken, ceilings);
        if (item >= 0) {
            if (!detouring) {
                detouring = true;
                detourX = Player.x;
                detourY = Player.y;
            }
            walkTo(Level.ITEM_X[item], Level.ITEM_Y[item], ceilings);
            return;
        }
        if (detouring) {
            float backX = detourX - Player.x;
            float backY = detourY - Player.y;
            if (backX * backX + backY * backY > ARRIVED * ARRIVED) {
                walkTo(detourX, detourY, ceilings);
                return;
            }
            detouring = false;
        }
        float dx = Level.ROUTE_X[target] - Player.x;
        float dy = Level.ROUTE_Y[target] - Player.y;
        if (dx * dx + dy * dy < ARRIVED * ARRIVED) {
            target = target + 1;
            if (target == Level.ROUTE_X.length) {
                target = Level.LOOP_START;
            }
            return;
        }
        walkTo(Level.ROUTE_X[target], Level.ROUTE_Y[target], ceilings);
    }

    private static void walkTo(float x, float y, short[] ceilings) {
        float dx = x - Player.x;
        float dy = y - Player.y;
        sway = Math.max(-0.08f, Math.min(0.08f, sway * 0.97f + random(-0.012f, 0.012f)));
        float heading = Weapon.angleTo(dx, dy) + sway;
        spin = 0;
        Player.turn(Math.max(-TURN, Math.min(TURN, heading)));
        float alignment = Math.abs(heading);
        if (alignment < 0.6f) {
            stuck = Player.walk(STRIDE * (1f - alignment), ceilings) ? 0 : stuck + 1;
        }
        if (stuck > GIVE_UP) {
            stuck = 0;
            if (detouring) {
                detouring = false;
            } else {
                target = target + 1 == Level.ROUTE_X.length ? Level.LOOP_START : target + 1;
            }
        }
    }

    /** The nearest untaken item the marine needs, can see, and can walk straight to; or -1. */
    private static int wanted(byte[] taken, short[] ceilings) {
        int best = -1;
        float nearest = GRAB;
        for (int i = 0; i < Level.ITEMS; i++) {
            if (taken[i] != 0 || !Items.useful(Level.ITEM_KIND[i])) {
                continue;
            }
            float dx = Level.ITEM_X[i] - Player.x;
            float dy = Level.ITEM_Y[i] - Player.y;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            if (distance < nearest && !Player.blocked(Player.x, Player.y, Level.ITEM_X[i], Level.ITEM_Y[i], ceilings)) {
                best = i;
                nearest = distance;
            }
        }
        return best;
    }

    /**
     * Fights the monster it has noticed, the way a player would; returns whether the marine was busy
     * fighting (or catching its breath after a kill) instead of walking on.
     */
    private static boolean fight(short[] ceilings, short[] monsters) {
        int seen = noticed(ceilings, monsters);
        if (seen < 0) {
            if (enemy >= 0) {
                enemy = -1;
                calm = Random.nextInt(8, 22);
            }
            if (calm > 0) {
                calm = calm - 1;
                Player.turn(sway * 0.5f);
                return true;
            }
            return false;
        }
        if (seen != enemy) {
            enemy = seen;
            reaction = Random.nextInt(6, 18);
            aimError = (Random.nextInt(2) == 0 ? -1 : 1) * random(0.08f, 0.25f);
        }
        if (reaction > 0) {
            reaction = reaction - 1;
            return false;
        }
        float dx = monsters[enemy + Monsters.X] - Player.x;
        float dy = monsters[enemy + Monsters.Y] - Player.y;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        aim(Weapon.angleTo(dx, dy) + aimError);
        aimError = aimError * 0.96f + random(-0.02f, 0.02f);
        float off = Math.abs(Weapon.angleTo(dx, dy) + aimError);
        float tolerance = (float) Math.atan(14f / distance);
        boolean lined = off < tolerance;
        boolean impatient = off < 2.5f * tolerance && Random.nextInt(100) < 10;
        if ((lined || impatient) && Weapon.loaded()) {
            Weapon.fire(monsters, ceilings);
            aimError = aimError + random(-0.14f, 0.14f);
        }
        dodge(distance, ceilings);
        return true;
    }

    /** The monster to fight: the current one while it stays in sight, unless another is much closer. */
    private static int noticed(short[] ceilings, short[] monsters) {
        int nearest = -1;
        float nearestDistance = ENGAGE;
        float currentDistance = Float.MAX_VALUE;
        for (int i = 0; i < Level.MONSTERS; i++) {
            int at = i * Monsters.STRIDE;
            int state = monsters[at + Monsters.STATE];
            if (state == Monsters.DEAD) {
                continue;
            }
            float dx = monsters[at + Monsters.X] - Player.x;
            float dy = monsters[at + Monsters.Y] - Player.y;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            if (distance >= ENGAGE) {
                continue;
            }
            boolean aware = state != Monsters.IDLE || Math.abs(Weapon.angleTo(dx, dy)) < FIELD_OF_VIEW;
            if (aware && Player.canSee(Player.x, Player.y, Player.eye, monsters[at + Monsters.X],
                    monsters[at + Monsters.Y], monsters[at + Monsters.FLOOR] + Monsters.CENTER, ceilings)) {
                if (at == enemy) {
                    currentDistance = distance;
                }
                if (distance < nearestDistance) {
                    nearest = at;
                    nearestDistance = distance;
                }
            }
        }
        return currentDistance < Float.MAX_VALUE && nearestDistance > 0.6f * currentDistance ? enemy : nearest;
    }

    /** Swings the view toward {@code heading} with a hand's momentum: it speeds up, overshoots, corrects. */
    private static void aim(float heading) {
        float wanted = Math.max(-MAX_SPIN, Math.min(MAX_SPIN, heading * 0.4f));
        spin = spin + Math.max(-SPIN_ACCELERATION, Math.min(SPIN_ACCELERATION, wanted - spin));
        Player.turn(spin);
    }

    /** Keeps moving under fire: retreats when hurt, backs off from close monsters, otherwise strafes. */
    private static void dodge(float distance, short[] ceilings) {
        if (Player.health < LOW_HEALTH && distance < 500) {
            Player.walk(-6f, ceilings);
            return;
        }
        if (distance < TOO_CLOSE) {
            Player.walk(-4f, ceilings);
            return;
        }
        strafeTime = strafeTime - 1;
        if (strafeTime <= 0) {
            strafeDirection = Random.nextInt(3) - 1;
            strafeTime = Random.nextInt(15, 40);
        }
        if (strafeDirection != 0 && !Player.strafe(4f * strafeDirection, ceilings)) {
            strafeDirection = -strafeDirection;
        }
    }

    private static float random(float low, float high) {
        return low + (high - low) * Random.nextInt(1001) / 1000f;
    }
}
