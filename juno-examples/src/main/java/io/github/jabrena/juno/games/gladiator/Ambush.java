package io.github.jabrena.juno.games.gladiator;

import io.github.jabrena.juno.api.Random;

/**
 * The AMBUSH gladiator lets the opponent come to it. It creeps behind a pillar, on the side away from where it
 * expects the opponent, and lurks there watching past both edges. An opponent seen far off is only tracked; one
 * that comes close is charged, so the first blow often lands before the victim sees it, for double damage. Losing
 * sight after a charge, it chases briefly, then slips behind another pillar. Waiting too long, or struck by an
 * unseen blade, it moves on.
 */
final class Ambush {
    /** How near the opponent has to come before it is charged. */
    static final int LUNGE = 220;
    private static final int PURSUE_MILLIS = 2000;
    private static final int LURK_MILLIS = 12_000;
    private static final int ARRIVED = 10;
    /** Patrol points the opponent may be heading for, in thousandths of the wall's radii. */
    private static final short[] GUESS_X = {764, 0, -764, 0, 400, -400};
    private static final short[] GUESS_Y = {0, 780, 0, -780, 0, 0};

    private Ambush() {
    }

    /** Hiding places an ambusher gives up on before it goes looking for the opponent instead. */
    private static final int PATIENCE = 1;

    static void think(Brain brain, Body body, int now) {
        if (!brain.sees && brain.restless >= PATIENCE && !brain.recentlyHurt(now)) {
            // Nobody came to the ambush, so it hunts like the AGGRESSIVE gladiator until it sees the opponent.
            Aggressive.think(brain, body, now);
            return;
        }
        if (brain.sees) {
            brain.restless = 0;
            brain.threatX = brain.seenX;
            brain.threatY = brain.seenY;
            boolean near = body.distanceTo(brain.seenX, brain.seenY) <= LUNGE;
            if (near || brain.mode == Brain.CHARGING || brain.recentlyHurt(now)) {
                brain.mode = Brain.CHARGING;
                brain.fight(body);
            } else {
                // Far off: keep still and keep it in view.
                brain.mode = Brain.LURKING;
                brain.face(body, brain.seenX, brain.seenY);
                brain.lurkSince = now;
            }
            return;
        }
        if (brain.recentlyHurt(now)) {
            // Struck unseen: turn to face the blow, then find a new hiding place.
            brain.mode = Brain.SEARCHING;
            brain.turn = 1.5f * Brain.TURN;
            brain.pillar = -1;
            return;
        }
        if (brain.mode == Brain.CHARGING && now - brain.seenAt < PURSUE_MILLIS) {
            brain.goTo(body, brain.seenX, brain.seenY, Brain.RUN);
            return;
        }
        if (brain.mode == Brain.CHARGING || brain.mode == Brain.SEARCHING || brain.pillar < 0) {
            hide(brain, body, brain.pillar, now);
        }
        if (brain.mode == Brain.HIDING) {
            if (body.distanceTo(brain.hideX, brain.hideY) <= ARRIVED) {
                brain.mode = Brain.LURKING;
                brain.lurkSince = now;
            } else if (brain.isStuck()) {
                hide(brain, body, brain.pillar, now);
            } else {
                brain.goTo(body, brain.hideX, brain.hideY, Brain.CREEP);
            }
            return;
        }
        lurk(brain, body, now);
    }

    /** Watches past one edge of the pillar, then the other; after a while, moves to another pillar. */
    private static void lurk(Brain brain, Body body, int now) {
        brain.mode = Brain.LURKING;
        if (now - brain.lurkSince > LURK_MILLIS) {
            brain.restless = brain.restless + 1;
            int guess = Random.nextInt(GUESS_X.length);
            brain.threatX = GUESS_X[guess] * Coliseum.radiusX / 1000f;
            brain.threatY = GUESS_Y[guess] * Coliseum.radiusY / 1000f;
            hide(brain, body, brain.pillar, now);
            return;
        }
        float dx = brain.threatX - body.x;
        float dy = brain.threatY - body.y;
        float toward = (float) Math.atan2(dy, dx);
        float glance = ((now - brain.lurkSince) / 2500) % 2 == 0 ? 0.55f : -0.55f;
        brain.face(body, body.x + (float) Math.cos(toward + glance) * 100f,
                body.y + (float) Math.sin(toward + glance) * 100f);
    }

    /**
     * Picks a pillar other than {@code avoid} to hide behind from the threat: near this gladiator and not far from
     * the threat, with the hiding place on the pillar's far side from it.
     */
    static void hide(Brain brain, Body body, int avoid, int now) {
        int best = -1;
        float bestCost = Float.MAX_VALUE;
        float around = Coliseum.pillarRadius + Coliseum.BODY + 8;
        for (int p = 0; p < Coliseum.pillars; p++) {
            if (p == avoid) {
                continue;
            }
            float dx = Coliseum.pillarX[p] - brain.threatX;
            float dy = Coliseum.pillarY[p] - brain.threatY;
            float length = Math.max(1f, (float) Math.hypot(dx, dy));
            float spotX = Coliseum.pillarX[p] + dx / length * around;
            float spotY = Coliseum.pillarY[p] + dy / length * around;
            if (!Coliseum.walkable(spotX, spotY)) {
                continue;
            }
            float cost = body.distanceTo(spotX, spotY) + 0.5f * length;
            if (cost < bestCost) {
                bestCost = cost;
                best = p;
                brain.hideX = spotX;
                brain.hideY = spotY;
            }
        }
        brain.pillar = best;
        brain.mode = best < 0 ? Brain.LURKING : Brain.HIDING;
        brain.lurkSince = now;
        brain.stuck = 0;
    }
}
