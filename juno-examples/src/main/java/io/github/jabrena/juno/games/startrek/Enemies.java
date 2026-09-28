package io.github.jabrena.juno.games.startrek;

import static io.github.jabrena.juno.games.startrek.Entities.E_COOL;
import static io.github.jabrena.juno.games.startrek.Entities.E_HEADING;
import static io.github.jabrena.juno.games.startrek.Entities.E_STRIDE;
import static io.github.jabrena.juno.games.startrek.Entities.E_TIMER;
import static io.github.jabrena.juno.games.startrek.Entities.E_TYPE;
import static io.github.jabrena.juno.games.startrek.Entities.E_VX;
import static io.github.jabrena.juno.games.startrek.Entities.E_VY;
import static io.github.jabrena.juno.games.startrek.Entities.E_X;
import static io.github.jabrena.juno.games.startrek.Entities.E_Y;
import static io.github.jabrena.juno.games.startrek.Entities.E_AUX;
import static io.github.jabrena.juno.games.startrek.Entities.HIT_RADIUS;
import static io.github.jabrena.juno.games.startrek.Entities.T_MINE;
import static io.github.jabrena.juno.games.startrek.Entities.T_NONE;
import static io.github.jabrena.juno.games.startrek.Entities.T_STARBASE;
import static io.github.jabrena.juno.games.startrek.Geometry.FIX;

import io.github.jabrena.juno.api.Random;

/** How Klingons, anti-matter saucers, Nomad and its mines behave. */
final class Enemies {
    private static final int KLINGON_SPEED = 38;
    private static final int KLINGON_TURN = 4;
    private static final int BREAK_DISTANCE = 110;
    private static final int BREAK_FRAMES = 45;

    // Shield damage, in percent.
    private static final int SAUCER_DAMAGE = 8;
    private static final int MINE_DAMAGE = 12;

    private Enemies() {
    }

    /**
     * A Klingon makes attack passes at its target (the Enterprise, or sometimes the starbase): it
     * closes in, breaks away once close, and comes round again, firing a torpedo whenever it is
     * facing its target and its tubes are loaded.
     */
    static void flyKlingon(int[] ents, int b) {
        int target = ents[b + E_AUX] == 1 ? Entities.find(ents, T_STARBASE) : -1;
        int tx = Enterprise.x;
        int ty = Enterprise.y;
        if (target >= 0) {
            tx = ents[target * E_STRIDE + E_X];
            ty = ents[target * E_STRIDE + E_Y];
        }
        int dx = Geometry.wrap(tx - ents[b + E_X]) / FIX;
        int dy = Geometry.wrap(ty - ents[b + E_Y]) / FIX;
        int distance = (int) Math.sqrt((float) dx * dx + (float) dy * dy);
        int toTarget = Geometry.bearing(dx, dy);
        int want = toTarget;
        if (ents[b + E_TIMER] > 0) {
            // Breaking away after a pass, before coming round for the next one.
            ents[b + E_TIMER] = ents[b + E_TIMER] - 1;
            want = Geometry.normalize(toTarget + 150);
        } else if (distance < BREAK_DISTANCE) {
            ents[b + E_TIMER] = BREAK_FRAMES;
        } else if (distance < 260) {
            want = Geometry.normalize(toTarget + 20);
        }
        int h = Geometry.normalize(ents[b + E_HEADING]
                + Geometry.clamp(Geometry.angleBetween(ents[b + E_HEADING], want), -KLINGON_TURN, KLINGON_TURN));
        ents[b + E_HEADING] = h;
        float radians = (float) Math.toRadians(h);
        int speed = KLINGON_SPEED + 2 * Math.min(Session.sector, 8);
        ents[b + E_VX] = Math.round((float) Math.sin(radians) * speed);
        ents[b + E_VY] = -Math.round((float) Math.cos(radians) * speed);
        ents[b + E_COOL] = ents[b + E_COOL] - 1;
        boolean facing = Math.abs(Geometry.angleBetween(h, toTarget)) < 35;
        if (ents[b + E_COOL] <= 0 && facing && distance < 420) {
            Combat.fireTorpedo(ents, ents[b + E_X], ents[b + E_Y], toTarget);
            ents[b + E_COOL] = Math.max(35, Random.nextInt(60, 110) - 4 * Session.sector);
        }
    }

    /** Anti-matter saucers home in on the Enterprise; touching it drains its shields. */
    static void flySaucer(int[] ents, int b) {
        int dx = Geometry.wrap(Enterprise.x - ents[b + E_X]);
        int dy = Geometry.wrap(Enterprise.y - ents[b + E_Y]);
        float length = (float) Math.sqrt((float) dx * dx + (float) dy * dy);
        if (length > 1) {
            float speed = 22 + 3 * Math.min(Session.sector, 8);
            ents[b + E_VX] = Math.round(dx * speed / length);
            ents[b + E_VY] = Math.round(dy * speed / length);
        }
        if (length / FIX < HIT_RADIUS + 6) {
            ents[b + E_TYPE] = T_NONE;
            Enterprise.damage(SAUCER_DAMAGE);
        }
    }

    /** Nomad wanders the sector, turning now and then, and lays a mine every so often. */
    static void flyNomad(int[] ents, int b) {
        ents[b + E_TIMER] = ents[b + E_TIMER] - 1;
        if (ents[b + E_TIMER] <= 0) {
            ents[b + E_HEADING] = Random.nextInt(360);
            ents[b + E_TIMER] = Random.nextInt(40, 100);
        }
        float radians = (float) Math.toRadians(ents[b + E_HEADING]);
        ents[b + E_VX] = Math.round((float) Math.sin(radians) * 24);
        ents[b + E_VY] = -Math.round((float) Math.cos(radians) * 24);
        ents[b + E_COOL] = ents[b + E_COOL] - 1;
        if (ents[b + E_COOL] <= 0 && Entities.count(ents, T_MINE) < 8) {
            Entities.spawn(ents, T_MINE, ents[b + E_X], ents[b + E_Y], 1);
            ents[b + E_COOL] = Math.max(30, 70 - 3 * Session.sector);
        }
    }

    /** A mine goes off when the Enterprise flies into it. */
    static void touchMine(int[] ents, int b) {
        if (Entities.distanceToShip(ents, b) < HIT_RADIUS + 6) {
            ents[b + E_TYPE] = T_NONE;
            Enterprise.damage(MINE_DAMAGE);
        }
    }
}
