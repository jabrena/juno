package io.github.jabrena.juno.games.doom;

/**
 * The CPU marine: walks the map's demo route (turn toward the next waypoint, stride to it, loop) and
 * stops to fight any monster it can see that is awake, or asleep in front of it, and steps off the
 * route for health or armor it needs and can reach.
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

    static int target;

    private Autopilot() {
    }

    static void restart() {
        target = Math.min(1, Level.ROUTE_X.length - 1);
        detouring = false;
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
        float heading = (float) Math.atan2(dy, dx) - Player.angle;
        if (heading > (float) Math.PI) {
            heading = heading - 2 * (float) Math.PI;
        } else if (heading < (float) -Math.PI) {
            heading = heading + 2 * (float) Math.PI;
        }
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

    /** Turns on the nearest monster worth shooting and fires once lined up; returns whether it fought. */
    private static boolean fight(short[] ceilings, short[] monsters) {
        int enemy = -1;
        float nearest = ENGAGE;
        for (int i = 0; i < Level.MONSTERS; i++) {
            int at = i * Monsters.STRIDE;
            int state = monsters[at + Monsters.STATE];
            if (state == Monsters.DEAD) {
                continue;
            }
            float dx = monsters[at + Monsters.X] - Player.x;
            float dy = monsters[at + Monsters.Y] - Player.y;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            if (distance >= nearest) {
                continue;
            }
            boolean noticed = state != Monsters.IDLE || Math.abs(Weapon.angleTo(dx, dy)) < FIELD_OF_VIEW;
            if (noticed && Player.canSee(Player.x, Player.y, Player.eye, monsters[at + Monsters.X],
                    monsters[at + Monsters.Y], monsters[at + Monsters.FLOOR] + Monsters.CENTER, ceilings)) {
                enemy = at;
                nearest = distance;
            }
        }
        if (enemy < 0) {
            return false;
        }
        float heading = Weapon.angleTo(monsters[enemy + Monsters.X] - Player.x,
                monsters[enemy + Monsters.Y] - Player.y);
        Player.turn(Math.max(-TURN, Math.min(TURN, heading)));
        if (Math.abs(heading - Math.max(-TURN, Math.min(TURN, heading))) < (float) Math.atan(14f / nearest)) {
            Weapon.fire(monsters, ceilings);
        }
        return true;
    }
}
