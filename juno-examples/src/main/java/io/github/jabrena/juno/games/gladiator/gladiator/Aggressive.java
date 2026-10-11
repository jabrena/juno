package io.github.jabrena.juno.games.gladiator.gladiator;

import io.github.jabrena.juno.games.gladiator.arena.Body;
import io.github.jabrena.juno.games.gladiator.arena.Coliseum;

/**
 * The AGGRESSIVE gladiator presses the attack: it runs at an opponent in sight and swings as soon as it is in reach;
 * once out of sight, it runs to where it saw it last. With nothing to chase it patrols the gap between the pillars
 * and the wall, glancing toward the middle at every corner of its round, going out of its way for any javelin
 * lying on the sand, which it throws as soon as the opponent is in range. Struck from where it is not looking, it
 * spins round to find the attacker.
 */
public final class Aggressive {
    /** The patrol, in thousandths of the wall's radii: round the pillars, clear of the wall. */
    private static final short[] PATROL_X = {764, 537, 0, -537, -764, -537, 0, 537};
    private static final short[] PATROL_Y = {0, 745, 782, 745, 0, -745, -782, -745};
    private static final int CHASE_MILLIS = 8000;
    private static final int LOOK_MILLIS = 700;
    private static final int ARRIVED = 30;
    /** How far it goes out of its way for a javelin lying on the sand. */
    private static final float FETCH = 900f;

    private Aggressive() {
    }

    public static void think(Brain brain, Body body, int now) {
        if (brain.sees()) {
            brain.setMode(Brain.HUNTING);
            brain.fight(body);
            return;
        }
        if (brain.recentlyHurt(now)) {
            brain.setMode(Brain.SEARCHING);
            brain.spin();
            return;
        }
        if (brain.everSeen() && now - brain.seenAt() < CHASE_MILLIS
                && body.distanceTo(brain.seenX(), brain.seenY()) > ARRIVED && !brain.isStuck()) {
            brain.setMode(Brain.HUNTING);
            brain.goTo(body, brain.seenX(), brain.seenY(), Brain.RUN);
            return;
        }
        // The trail went cold: back to the patrol, from its nearest point.
        if (brain.everSeen()) {
            brain.forget();
            brain.patrol().headFor(Patrol.NONE);
            brain.unstick();
        }
        if (brain.senses().fetch(brain, body, FETCH, Brain.WALK)) {
            brain.setMode(Brain.SEARCHING);
            return;
        }
        patrol(brain, body, now);
    }

    private static void patrol(Brain brain, Body body, int now) {
        brain.setMode(Brain.SEARCHING);
        Patrol patrol = brain.patrol();
        if (patrol.waypoint() < 0) {
            patrol.headFor(nearestWaypoint(body));
        }
        if (now < patrol.lookUntil()) {
            brain.face(body, 0f, 0f);
            return;
        }
        Coliseum coliseum = body.coliseum();
        float tx = PATROL_X[patrol.waypoint()] * coliseum.radiusX() / 1000f;
        float ty = PATROL_Y[patrol.waypoint()] * coliseum.radiusY() / 1000f;
        if (body.distanceTo(tx, ty) < ARRIVED || brain.isStuck()) {
            // The right gate's gladiator patrols the other way round, so the two always meet instead of keeping half
            // a lap apart.
            int step = brain.slot() == 1 ? PATROL_X.length - 1 : 1;
            patrol.headFor((patrol.waypoint() + step) % PATROL_X.length);
            patrol.lookUntil(now + LOOK_MILLIS);
            brain.unstick();
            return;
        }
        brain.goTo(body, tx, ty, Brain.WALK);
    }

    private static int nearestWaypoint(Body body) {
        Coliseum coliseum = body.coliseum();
        int nearest = 0;
        float best = Float.MAX_VALUE;
        for (int i = 0; i < PATROL_X.length; i++) {
            float distance = body.distanceTo(PATROL_X[i] * coliseum.radiusX() / 1000f,
                    PATROL_Y[i] * coliseum.radiusY() / 1000f);
            if (distance < best) {
                best = distance;
                nearest = i;
            }
        }
        return nearest;
    }
}
