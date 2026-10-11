package io.github.jabrena.juno.games.gladiator;

/**
 * The AGGRESSIVE gladiator presses the attack: it runs at an opponent in sight and swings as soon as it is in reach;
 * once out of sight, it runs to where it saw it last. With nothing to chase it patrols the gap between the pillars
 * and the wall, glancing toward the middle at every corner of its round. Struck from where it is not looking, it
 * spins round to find the attacker.
 */
final class Aggressive {
    /** The patrol, in thousandths of the wall's radii: round the pillars, clear of the wall. */
    private static final short[] PATROL_X = {764, 537, 0, -537, -764, -537, 0, 537};
    private static final short[] PATROL_Y = {0, 745, 782, 745, 0, -745, -782, -745};
    private static final int CHASE_MILLIS = 8000;
    private static final int LOOK_MILLIS = 700;
    private static final int ARRIVED = 30;

    private Aggressive() {
    }

    static void think(Brain brain, Body body, int now) {
        if (brain.sees) {
            brain.mode = Brain.HUNTING;
            brain.fight(body);
            return;
        }
        if (brain.recentlyHurt(now)) {
            brain.mode = Brain.SEARCHING;
            brain.turn = 1.5f * Brain.TURN;
            return;
        }
        if (brain.everSeen && now - brain.seenAt < CHASE_MILLIS
                && body.distanceTo(brain.seenX, brain.seenY) > ARRIVED && !brain.isStuck()) {
            brain.mode = Brain.HUNTING;
            brain.goTo(body, brain.seenX, brain.seenY, Brain.RUN);
            return;
        }
        // The trail went cold: back to the patrol, from its nearest point.
        if (brain.everSeen) {
            brain.everSeen = false;
            brain.waypoint = -1;
            brain.stuck = 0;
        }
        patrol(brain, body, now);
    }

    private static void patrol(Brain brain, Body body, int now) {
        brain.mode = Brain.SEARCHING;
        if (brain.waypoint < 0) {
            brain.waypoint = nearestWaypoint(body);
        }
        if (now < brain.lookUntil) {
            brain.face(body, 0f, 0f);
            return;
        }
        float tx = PATROL_X[brain.waypoint] * Coliseum.radiusX / 1000f;
        float ty = PATROL_Y[brain.waypoint] * Coliseum.radiusY / 1000f;
        if (body.distanceTo(tx, ty) < ARRIVED || brain.isStuck()) {
            brain.waypoint = (brain.waypoint + 1) % PATROL_X.length;
            brain.lookUntil = now + LOOK_MILLIS;
            brain.stuck = 0;
            return;
        }
        brain.goTo(body, tx, ty, Brain.WALK);
    }

    private static int nearestWaypoint(Body body) {
        int nearest = 0;
        float best = Float.MAX_VALUE;
        for (int i = 0; i < PATROL_X.length; i++) {
            float distance = body.distanceTo(PATROL_X[i] * Coliseum.radiusX / 1000f,
                    PATROL_Y[i] * Coliseum.radiusY / 1000f);
            if (distance < best) {
                best = distance;
                nearest = i;
            }
        }
        return nearest;
    }
}
