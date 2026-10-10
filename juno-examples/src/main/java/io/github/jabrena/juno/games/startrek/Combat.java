package io.github.jabrena.juno.games.startrek;

import static io.github.jabrena.juno.games.startrek.Entities.ENTITIES;
import static io.github.jabrena.juno.games.startrek.Entities.E_HP;
import static io.github.jabrena.juno.games.startrek.Entities.E_STRIDE;
import static io.github.jabrena.juno.games.startrek.Entities.E_TIMER;
import static io.github.jabrena.juno.games.startrek.Entities.E_TYPE;
import static io.github.jabrena.juno.games.startrek.Entities.E_VX;
import static io.github.jabrena.juno.games.startrek.Entities.E_VY;
import static io.github.jabrena.juno.games.startrek.Entities.E_X;
import static io.github.jabrena.juno.games.startrek.Entities.E_Y;
import static io.github.jabrena.juno.games.startrek.Entities.HIT_RADIUS;
import static io.github.jabrena.juno.games.startrek.Entities.T_KLINGON;
import static io.github.jabrena.juno.games.startrek.Entities.T_MINE;
import static io.github.jabrena.juno.games.startrek.Entities.T_NOMAD;
import static io.github.jabrena.juno.games.startrek.Entities.T_NONE;
import static io.github.jabrena.juno.games.startrek.Entities.T_PHOTON;
import static io.github.jabrena.juno.games.startrek.Entities.T_SAUCER;
import static io.github.jabrena.juno.games.startrek.Entities.T_STARBASE;
import static io.github.jabrena.juno.games.startrek.Entities.T_TORPEDO;
import static io.github.jabrena.juno.games.startrek.Geometry.FIX;

/**
 * Phasers, photon torpedoes and Klingon torpedoes, and what happens to whatever they hit. The
 * phaser beam's recharge and length on screen live here too.
 */
final class Combat {
    static final int PHASER_RANGE = 320;
    private static final int PHASER_COOLDOWN = 9;
    private static final int PHASER_FRAMES = 3;
    private static final int PHOTON_SPEED = 11;
    private static final int PHOTON_FRAMES = 55;
    private static final int PHOTON_BLAST = 70;
    private static final int TORPEDO_SPEED = 5;
    private static final int TORPEDO_FRAMES = 110;
    private static final int TORPEDO_DAMAGE = 10;

    // Scoring.
    private static final int KLINGON_POINTS = 1000;
    private static final int SAUCER_POINTS = 500;
    private static final int MINE_POINTS = 100;
    private static final int TORPEDO_POINTS = 50;
    private static final int NOMAD_POINTS = 5000;

    static int phaserCooldown;
    static int phaserFrames;
    static int phaserLength;

    private Combat() {
    }

    static void reset() {
        phaserCooldown = 0;
        phaserFrames = 0;
    }

    /** Counts down the phaser recharge and how long the beam stays on screen. */
    static void recharge() {
        if (phaserCooldown > 0) {
            phaserCooldown = phaserCooldown - 1;
        }
        if (phaserFrames > 0) {
            phaserFrames = phaserFrames - 1;
        }
    }

    /**
     * Phasers strike the nearest enemy in a narrow beam straight ahead, within range, and need
     * {@value #PHASER_COOLDOWN} frames to recharge.
     */
    static void firePhasers(int[] ents) {
        if (phaserCooldown > 0) {
            return;
        }
        phaserCooldown = PHASER_COOLDOWN;
        phaserFrames = PHASER_FRAMES;
        phaserLength = PHASER_RANGE;
        float radians = (float) Math.toRadians(Enterprise.heading);
        float hx = (float) Math.sin(radians);
        float hy = -(float) Math.cos(radians);
        int target = -1;
        int nearest = PHASER_RANGE + 1;
        for (int slot = 0; slot < ENTITIES; slot++) {
            int b = slot * E_STRIDE;
            if (!Entities.isHostile(ents[b + E_TYPE])) {
                continue;
            }
            float dx = Geometry.wrap(ents[b + E_X] - Enterprise.x) / (float) FIX;
            float dy = Geometry.wrap(ents[b + E_Y] - Enterprise.y) / (float) FIX;
            int ahead = Math.round(dx * hx + dy * hy);
            int side = Math.round(dx * -hy + dy * hx);
            if (ahead > 0 && ahead < nearest && Math.abs(side) < HIT_RADIUS + Entities.radius(ents[b + E_TYPE])) {
                nearest = ahead;
                target = slot;
            }
        }
        if (target >= 0) {
            phaserLength = nearest;
            hit(ents, target, 1);
        }
    }

    static void firePhoton(int[] ents) {
        if (Enterprise.photons == 0) {
            return;
        }
        float radians = (float) Math.toRadians(Enterprise.heading);
        int slot = Entities.spawn(ents, T_PHOTON, Enterprise.x, Enterprise.y, 1);
        if (slot < 0) {
            return;
        }
        Enterprise.photons = Enterprise.photons - 1;
        int b = slot * E_STRIDE;
        ents[b + E_VX] = Enterprise.vx + Math.round((float) Math.sin(radians) * PHOTON_SPEED * FIX);
        ents[b + E_VY] = Enterprise.vy - Math.round((float) Math.cos(radians) * PHOTON_SPEED * FIX);
        ents[b + E_TIMER] = PHOTON_FRAMES;
        Hud.drawPanel();
    }

    /** A photon torpedo flies straight until it meets an enemy or burns out, then its blast hits all around. */
    static void flyPhoton(int[] ents, int slot) {
        int b = slot * E_STRIDE;
        ents[b + E_TIMER] = ents[b + E_TIMER] - 1;
        boolean contact = ents[b + E_TIMER] <= 0;
        for (int other = 0; other < ENTITIES && !contact; other++) {
            int o = other * E_STRIDE;
            if (Entities.isHostile(ents[o + E_TYPE])
                    && Entities.distance(ents, b, o) < HIT_RADIUS + Entities.radius(ents[o + E_TYPE])) {
                contact = true;
            }
        }
        if (!contact) {
            return;
        }
        int x = ents[b + E_X];
        int y = ents[b + E_Y];
        ents[b + E_TYPE] = T_NONE;
        for (int other = 0; other < ENTITIES; other++) {
            int o = other * E_STRIDE;
            if (Entities.isHostile(ents[o + E_TYPE])
                    && Entities.distance(ents, b, o) < PHOTON_BLAST + Entities.radius(ents[o + E_TYPE])) {
                hit(ents, other, 3);
            }
        }
        Entities.debris(ents, x, y, PHOTON_BLAST);
    }

    static void fireTorpedo(int[] ents, int x, int y, int direction) {
        int slot = Entities.spawn(ents, T_TORPEDO, x, y, 1);
        if (slot < 0) {
            return;
        }
        int b = slot * E_STRIDE;
        float radians = (float) Math.toRadians(direction);
        ents[b + E_VX] = Math.round((float) Math.sin(radians) * TORPEDO_SPEED * FIX);
        ents[b + E_VY] = -Math.round((float) Math.cos(radians) * TORPEDO_SPEED * FIX);
        ents[b + E_TIMER] = TORPEDO_FRAMES;
    }

    /** Klingon torpedoes hit the Enterprise or the starbase, or burn out. */
    static void flyTorpedo(int[] ents, int b) {
        ents[b + E_TIMER] = ents[b + E_TIMER] - 1;
        if (ents[b + E_TIMER] <= 0) {
            ents[b + E_TYPE] = T_NONE;
            return;
        }
        if (Entities.distanceToShip(ents, b) < HIT_RADIUS) {
            ents[b + E_TYPE] = T_NONE;
            Enterprise.damage(TORPEDO_DAMAGE);
            return;
        }
        int base = Entities.find(ents, T_STARBASE);
        if (base >= 0 && Entities.distance(ents, b, base * E_STRIDE) < Entities.radius(T_STARBASE)) {
            ents[b + E_TYPE] = T_NONE;
            int s = base * E_STRIDE;
            ents[s + E_HP] = ents[s + E_HP] - 1;
            if (ents[s + E_HP] <= 0) {
                Session.baseLost = true;
                Entities.debris(ents, ents[s + E_X], ents[s + E_Y], 40);
                ents[s + E_TYPE] = T_NONE;
                Hud.invalidate();
            }
        }
    }

    /** Damages a hostile; destroyed ones score and leave debris. */
    static void hit(int[] ents, int slot, int damage) {
        int b = slot * E_STRIDE;
        int type = ents[b + E_TYPE];
        ents[b + E_HP] = ents[b + E_HP] - damage;
        if (ents[b + E_HP] > 0) {
            return;
        }
        ents[b + E_TYPE] = T_NONE;
        int points = TORPEDO_POINTS;
        int size = 12;
        if (type == T_KLINGON) {
            points = KLINGON_POINTS;
            size = 30;
        } else if (type == T_SAUCER) {
            points = SAUCER_POINTS;
            size = 16;
        } else if (type == T_NOMAD) {
            points = NOMAD_POINTS;
            size = 40;
        } else if (type == T_MINE) {
            points = MINE_POINTS;
        }
        Session.score = Session.score + points;
        Entities.debris(ents, ents[b + E_X], ents[b + E_Y], size);
        Hud.invalidate();
    }
}
