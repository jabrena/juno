package io.github.jabrena.juno.games.doom;

import io.github.jabrena.juno.api.Random;

/**
 * The CPU marine: walks the map's route (turn toward the next waypoint, stride to it, loop): on a WAD map the walk
 * to the exit switch that {@link RoutePlanner} plans, on the built-in map its own patrol. It
 * fights the monsters it notices ({@link Combat}) and steps off the route for health or armor it needs and can
 * reach.
 */
final class Autopilot {
    private static final float TURN = 0.11f;
    private static final float STRIDE = 9f;
    private static final float ARRIVED = 28f;
    private static final float GRAB = 450f;
    private static final float EXIT_COMMIT = 512f;

    private static boolean detouring;
    private static float detourX;
    private static float detourY;
    private static int stuck;
    private static float goalX;
    private static float goalY;
    private static float bestDistance;
    private static final int GIVE_UP = 40;


    private static float sway;

    static int target;

    private Autopilot() {
    }

    static void restart() {
        target = Math.min(1, World.routeLength - 1);
        detouring = false;
        stuck = 0;
        goalX = Float.MAX_VALUE;
        sway = 0;
        Combat.restart();
    }

    /**
     * Picks up the route again after manual play, from the nearest waypoint the marine can walk
     * straight to (or simply the nearest, when none is in reach).
     */
    static void resume(short[] ceilings) {
        resume(ceilings, -1);
    }

    private static void resume(short[] ceilings, int avoided) {
        detouring = false;
        sway = 0;
        float best = Float.MAX_VALUE;
        float bestReachable = Float.MAX_VALUE;
        int nearest = target == avoided ? 0 : target;
        int reachable = -1;
        for (int i = 0; i < World.routeLength; i++) {
            if (i == avoided) {
                continue;
            }
            float dx = World.routeX[i] - Player.x;
            float dy = World.routeY[i] - Player.y;
            float distance = dx * dx + dy * dy;
            if (distance < best) {
                best = distance;
                nearest = i;
            }
            if (distance < bestReachable && !Player.blocked(Player.x, Player.y, World.routeX[i], World.routeY[i], ceilings)) {
                bestReachable = distance;
                reachable = i;
            }
        }
        target = reachable >= 0 ? reachable : nearest;
        goalX = Float.MAX_VALUE;
    }

    static void step(short[] ceilings, short[] monsters, byte[] taken) {
        if (committedToExit()) {
            followRoute(ceilings);
            return;
        }
        if (Combat.fight(ceilings, monsters, sway)) {
            return;
        }
        int item = wanted(taken, ceilings);
        if (item >= 0) {
            if (!detouring) {
                detouring = true;
                detourX = Player.x;
                detourY = Player.y;
            }
            walkTo(World.itemX[item], World.itemY[item], ceilings);
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
        followRoute(ceilings);
    }

    /** Once the exit is close on the final approach, finishing the map takes priority over combat and pickups. */
    private static boolean committedToExit() {
        if (!World.fromWad || World.exitLine < 0 || World.routeLength < 2
                || target < World.routeLength - 2) {
            return false;
        }
        float dx = World.exitX - Player.x;
        float dy = World.exitY - Player.y;
        return dx * dx + dy * dy < EXIT_COMMIT * EXIT_COMMIT;
    }

    private static void followRoute(short[] ceilings) {
        float dx = World.routeX[target] - Player.x;
        float dy = World.routeY[target] - Player.y;
        if (dx * dx + dy * dy < ARRIVED * ARRIVED) {
            target = target + 1;
            if (target == World.routeLength) {
                target = World.loopStart;
            }
            return;
        }
        walkTo(World.routeX[target], World.routeY[target], ceilings);
    }

    private static void walkTo(float x, float y, short[] ceilings) {
        float dx = x - Player.x;
        float dy = y - Player.y;
        if (goalX != x || goalY != y) {
            goalX = x;
            goalY = y;
            bestDistance = (float) Math.sqrt(dx * dx + dy * dy);
            stuck = 0;
        }
        sway = Math.max(-0.08f, Math.min(0.08f, sway * 0.97f + random(-0.012f, 0.012f)));
        float heading = Weapon.angleTo(dx, dy) + sway;
        Combat.steady();
        Player.turn(Math.max(-TURN, Math.min(TURN, heading)));
        float alignment = Math.abs(heading);
        if (alignment < 0.6f) {
            Player.walk(STRIDE * (1f - alignment), ceilings);
            float remainingX = x - Player.x;
            float remainingY = y - Player.y;
            float after = (float) Math.sqrt(remainingX * remainingX + remainingY * remainingY);
            if (after < bestDistance - 1f) {
                bestDistance = after;
                stuck = 0;
            } else {
                stuck = stuck + 1;
            }
        }
        if (stuck > GIVE_UP) {
            stuck = 0;
            if (detouring) {
                detouring = false;
            } else if (World.fromWad && RoutePlanner.planFrom(Player.x, Player.y)) {
                target = Math.min(1, World.routeLength - 1);
                goalX = Float.MAX_VALUE;
            } else {
                resume(ceilings, target);
            }
        }
    }

    /** The nearest untaken item the marine needs, can see, and can walk straight to; or -1. */
    private static int wanted(byte[] taken, short[] ceilings) {
        int best = -1;
        float nearest = GRAB;
        for (int i = 0; i < World.items; i++) {
            if (taken[i] != 0 || !Items.useful(World.itemKind[i])) {
                continue;
            }
            float dx = World.itemX[i] - Player.x;
            float dy = World.itemY[i] - Player.y;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            if (distance < nearest && !Player.blocked(Player.x, Player.y, World.itemX[i], World.itemY[i], ceilings)) {
                best = i;
                nearest = distance;
            }
        }
        return best;
    }

    static float random(float low, float high) {
        return low + (high - low) * Random.nextInt(1001) / 1000f;
    }
}
