package io.github.jabrena.juno.games.gladiator.gladiator;

import io.github.jabrena.juno.api.util.Random;
import io.github.jabrena.juno.games.gladiator.arena.Body;
import io.github.jabrena.juno.games.gladiator.arena.Braziers;
import io.github.jabrena.juno.games.gladiator.arena.Coliseum;

/**
 * The AMBUSH gladiator lets the opponent come to it. It creeps behind a pillar, on the side away from where it
 * expects the opponent, and lurks there watching past both edges ({@link Lair}). An opponent seen far off is only
 * tracked; one that comes close is charged, so the first blow often lands before the victim sees it, for double
 * damage. Losing sight after a charge, it chases briefly, then slips behind another pillar. Waiting too long, or
 * struck by an unseen blade, it moves on. A burning brazier's smoke hides it better than any pillar, and a javelin
 * picked up on the way is thrown from cover.
 */
public final class Ambush {
    /** How near the opponent has to come before it is charged. */
    public static final int LUNGE = 220;
    private static final int PURSUE_MILLIS = 2000;
    private static final int LURK_MILLIS = 12_000;
    private static final int ARRIVED = 10;
    /** How far it creeps out of its way for a javelin, to throw from cover. */
    private static final float FETCH = 700f;
    /** Hiding places in smoke are numbered after the pillars, from here. */
    public static final int SMOKE_SPOT = 100;
    /** How much nearer than a pillar a cloud of smoke may be, to be preferred. */
    private static final float SMOKE_BONUS = 200f;
    /** Patrol points the opponent may be heading for, in thousandths of the wall's radii. */
    private static final short[] GUESS_X = {764, 0, -764, 0, 400, -400};
    private static final short[] GUESS_Y = {0, 780, 0, -780, 0, 0};
    /** Hiding places an ambusher gives up on before it goes looking for the opponent instead. */
    private static final int PATIENCE = 1;

    private Ambush() {
    }

    public static void think(Brain brain, Body body, int now) {
        Lair lair = brain.lair();
        if (!brain.sees() && lair.restless() >= PATIENCE && !brain.recentlyHurt(now)) {
            // Nobody came to the ambush, so it hunts like the AGGRESSIVE gladiator until it sees the opponent.
            Aggressive.think(brain, body, now);
            return;
        }
        if (brain.sees()) {
            watch(brain, body, now);
            return;
        }
        if (brain.recentlyHurt(now)) {
            // Struck unseen: turn to face the blow, then find a new hiding place.
            brain.setMode(Brain.SEARCHING);
            brain.spin();
            lair.choose(Lair.NONE);
            return;
        }
        if (brain.mode() == Brain.CHARGING && now - brain.seenAt() < PURSUE_MILLIS) {
            brain.goTo(body, brain.seenX(), brain.seenY(), Brain.RUN);
            return;
        }
        if (brain.senses().fetch(brain, body, FETCH, Brain.WALK)) {
            brain.setMode(Brain.SEARCHING);
            return;
        }
        if (brain.mode() == Brain.CHARGING || brain.mode() == Brain.SEARCHING || lair.pillar() < 0) {
            hide(brain, body, lair.pillar(), now);
        }
        if (brain.mode() == Brain.HIDING) {
            creep(brain, body, now);
            return;
        }
        lurk(brain, body, now);
    }

    /** The opponent is in sight: charge it when it is near, keep still and watch it while it is far. */
    private static void watch(Brain brain, Body body, int now) {
        Lair lair = brain.lair();
        lair.calm();
        lair.threat(brain.seenX(), brain.seenY());
        boolean near = body.distanceTo(brain.seenX(), brain.seenY()) <= LUNGE;
        if (near || brain.mode() == Brain.CHARGING || brain.recentlyHurt(now)) {
            brain.setMode(Brain.CHARGING);
            brain.fight(body);
            return;
        }
        // Far off: keep still and keep it in view, but not for ever: a long stand-off ends in a charge.
        if (brain.mode() != Brain.LURKING) {
            lair.lurk(now);
        }
        brain.setMode(Brain.LURKING);
        if (now - lair.lurkSince() > LURK_MILLIS) {
            brain.setMode(Brain.CHARGING);
            brain.fight(body);
        } else {
            brain.face(body, brain.seenX(), brain.seenY());
        }
    }

    /** Creeps to its hiding place, and starts lurking there. */
    private static void creep(Brain brain, Body body, int now) {
        Lair lair = brain.lair();
        if (body.distanceTo(lair.hideX(), lair.hideY()) <= ARRIVED) {
            brain.setMode(Brain.LURKING);
            lair.lurk(now);
        } else if (brain.isStuck()) {
            hide(brain, body, lair.pillar(), now);
        } else {
            brain.goTo(body, lair.hideX(), lair.hideY(), Brain.CREEP);
        }
    }

    /** Watches past one edge of the pillar, then the other; after a while, moves to another pillar. */
    private static void lurk(Brain brain, Body body, int now) {
        Lair lair = brain.lair();
        brain.setMode(Brain.LURKING);
        if (now - lair.lurkSince() > LURK_MILLIS) {
            lair.giveUp();
            int guess = Random.nextInt(GUESS_X.length);
            Coliseum coliseum = body.coliseum();
            lair.threat(GUESS_X[guess] * coliseum.radiusX() / 1000f, GUESS_Y[guess] * coliseum.radiusY() / 1000f);
            hide(brain, body, lair.pillar(), now);
            return;
        }
        float toward = (float) Math.atan2(lair.threatY() - body.y(), lair.threatX() - body.x());
        float glance = ((now - lair.lurkSince()) / 2500) % 2 == 0 ? 0.55f : -0.55f;
        brain.face(body, body.x() + (float) Math.cos(toward + glance) * 100f,
                body.y() + (float) Math.sin(toward + glance) * 100f);
    }

    /**
     * Picks a pillar other than {@code avoid} to hide behind from the threat: near this gladiator and not far from
     * the threat, with the hiding place on the pillar's far side from it. A cloud of smoke hides better still.
     */
    public static void hide(Brain brain, Body body, int avoid, int now) {
        Lair lair = brain.lair();
        Coliseum coliseum = body.coliseum();
        int best = Lair.NONE;
        float bestCost = Float.MAX_VALUE;
        float around = coliseum.pillarRadius() + Coliseum.BODY + 8;
        for (int p = 0; p < coliseum.pillars(); p++) {
            float dx = coliseum.pillarX(p) - lair.threatX();
            float dy = coliseum.pillarY(p) - lair.threatY();
            float length = Math.max(1f, (float) Math.hypot(dx, dy));
            float spotX = coliseum.pillarX(p) + dx / length * around;
            float spotY = coliseum.pillarY(p) + dy / length * around;
            float cost = body.distanceTo(spotX, spotY) + 0.5f * length;
            if (p != avoid && coliseum.walkable(spotX, spotY) && cost < bestCost) {
                bestCost = cost;
                best = p;
                lair.spot(spotX, spotY);
            }
        }
        Braziers braziers = coliseum.braziers();
        for (int b = 0; b < Braziers.COUNT; b++) {
            float length = (float) Math.hypot(Braziers.X[b] - lair.threatX(), Braziers.Y[b] - lair.threatY());
            float cost = body.distanceTo(Braziers.X[b], Braziers.Y[b]) + 0.5f * length - SMOKE_BONUS;
            if (braziers.smoky(b) && avoid != SMOKE_SPOT + b && cost < bestCost) {
                bestCost = cost;
                best = SMOKE_SPOT + b;
                lair.spot(Braziers.X[b], Braziers.Y[b]);
            }
        }
        lair.choose(best);
        brain.setMode(best < 0 ? Brain.LURKING : Brain.HIDING);
        // With no hiding place found, the lurk clock keeps running so the ambusher eventually gives up and hunts.
        if (best >= 0) {
            lair.lurk(now);
        }
        brain.unstick();
    }
}
