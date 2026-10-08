package io.github.jabrena.juno.games.doom;

/** Walks the map's demo route: turn toward the next waypoint, then stride to it, and loop. */
final class Autopilot {
    private static final float TURN = 0.11f;
    private static final float STRIDE = 9f;
    private static final float ARRIVED = 28f;

    static int target;

    private Autopilot() {
    }

    static void restart() {
        target = Math.min(1, Level.ROUTE_X.length - 1);
    }

    /** Picks up the route again from the waypoint nearest the marine, after manual play. */
    static void resume() {
        float best = Float.MAX_VALUE;
        for (int i = 0; i < Level.ROUTE_X.length; i++) {
            float dx = Level.ROUTE_X[i] - Player.x;
            float dy = Level.ROUTE_Y[i] - Player.y;
            float distance = dx * dx + dy * dy;
            if (distance < best) {
                best = distance;
                target = i;
            }
        }
    }

    static void step(short[] ceilings) {
        float dx = Level.ROUTE_X[target] - Player.x;
        float dy = Level.ROUTE_Y[target] - Player.y;
        if (dx * dx + dy * dy < ARRIVED * ARRIVED) {
            target = target + 1;
            if (target == Level.ROUTE_X.length) {
                target = Level.LOOP_START;
            }
            return;
        }
        float heading = (float) Math.atan2(dy, dx) - Player.angle;
        if (heading > (float) Math.PI) {
            heading = heading - 2 * (float) Math.PI;
        } else if (heading < (float) -Math.PI) {
            heading = heading + 2 * (float) Math.PI;
        }
        Player.turn(Math.max(-TURN, Math.min(TURN, heading)));
        float alignment = Math.abs(heading);
        if (alignment < 0.6f) {
            Player.walk(STRIDE * (1f - alignment), ceilings);
        }
    }
}
