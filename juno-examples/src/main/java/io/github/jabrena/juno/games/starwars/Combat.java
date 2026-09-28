package io.github.jabrena.juno.games.starwars;

import static io.github.jabrena.juno.games.starwars.Entities.CAP_HEIGHT;
import static io.github.jabrena.juno.games.starwars.Entities.ENTITIES;
import static io.github.jabrena.juno.games.starwars.Entities.E_AUX;
import static io.github.jabrena.juno.games.starwars.Entities.E_FLAG;
import static io.github.jabrena.juno.games.starwars.Entities.E_SR;
import static io.github.jabrena.juno.games.starwars.Entities.E_STRIDE;
import static io.github.jabrena.juno.games.starwars.Entities.E_SX;
import static io.github.jabrena.juno.games.starwars.Entities.E_SY;
import static io.github.jabrena.juno.games.starwars.Entities.E_TIMER;
import static io.github.jabrena.juno.games.starwars.Entities.E_TYPE;
import static io.github.jabrena.juno.games.starwars.Entities.E_VZ;
import static io.github.jabrena.juno.games.starwars.Entities.E_X;
import static io.github.jabrena.juno.games.starwars.Entities.E_Y;
import static io.github.jabrena.juno.games.starwars.Entities.E_Z;
import static io.github.jabrena.juno.games.starwars.Entities.GROUND;
import static io.github.jabrena.juno.games.starwars.Entities.T_FIREBALL;
import static io.github.jabrena.juno.games.starwars.Entities.T_NONE;
import static io.github.jabrena.juno.games.starwars.Entities.T_PORT;
import static io.github.jabrena.juno.games.starwars.Entities.T_SHOT;
import static io.github.jabrena.juno.games.starwars.Entities.T_TIE;
import static io.github.jabrena.juno.games.starwars.Entities.T_TOWER;
import static io.github.jabrena.juno.games.starwars.Entities.T_TURRET;
import static io.github.jabrena.juno.games.starwars.Entities.T_VADER;

/**
 * The four wing cannons: at most {@value #SHOTS} volleys in flight, each converging on the point
 * tapped and hitting what was under the crosshair when it was fired, for points.
 */
final class Combat {
    private static final int SHOTS = 3;
    static final int SHOT_FRAMES = 4;

    private Combat() {
    }

    static void fire(int[] ents) {
        if (Entities.count(ents, T_SHOT) >= SHOTS) {
            return;
        }
        int slot = Entities.freeSlot(ents);
        if (slot < 0) {
            return;
        }
        int b = slot * E_STRIDE;
        ents[b + E_TYPE] = T_SHOT;
        ents[b + E_X] = Controls.crossX;
        ents[b + E_Y] = Controls.crossY;
        ents[b + E_TIMER] = SHOT_FRAMES;
        // The target is what is under the crosshair now; it is hit when the beams converge.
        int target = targetAt(ents, Controls.crossX, Controls.crossY);
        ents[b + E_AUX] = target;
        if (target >= 0) {
            ents[b + E_FLAG] = ents[target * E_STRIDE + E_TYPE];
        }
        if (Session.phase == Phase.TRENCH) {
            TrenchPhase.trenchShots = TrenchPhase.trenchShots + 1;
        }
    }

    /**
     * Lasers take {@value #SHOT_FRAMES} frames to converge, then hit the target that was under the
     * crosshair when they were fired, if it is still there (a tower top still standing).
     */
    static void resolveShots(int[] ents) {
        for (int slot = 0; slot < ENTITIES; slot++) {
            int b = slot * E_STRIDE;
            if (ents[b + E_TYPE] != T_SHOT) {
                continue;
            }
            ents[b + E_TIMER] = ents[b + E_TIMER] - 1;
            if (ents[b + E_TIMER] > 0) {
                continue;
            }
            ents[b + E_TYPE] = T_NONE;
            int target = ents[b + E_AUX];
            if (target >= 0) {
                int t = target * E_STRIDE;
                boolean standing = ents[t + E_TYPE] != T_TOWER || ents[t + E_FLAG] != 0;
                if (ents[t + E_TYPE] == ents[b + E_FLAG] && standing) {
                    hit(ents, target);
                }
            }
        }
    }

    /** The nearest entity whose target circle contains the screen point, or -1. */
    static int targetAt(int[] ents, int x, int y) {
        int found = -1;
        int nearest = 1 << 30;
        for (int slot = 0; slot < ENTITIES; slot++) {
            int b = slot * E_STRIDE;
            int type = ents[b + E_TYPE];
            int r = ents[b + E_SR];
            if (type == T_NONE || type == T_SHOT || r <= 0) {
                continue;
            }
            int dx = ents[b + E_SX] - x;
            int dy = ents[b + E_SY] - y;
            if (dx * dx + dy * dy <= r * r && ents[b + E_Z] < nearest) {
                nearest = ents[b + E_Z];
                found = slot;
            }
        }
        return found;
    }

    private static void hit(int[] ents, int slot) {
        int b = slot * E_STRIDE;
        int type = ents[b + E_TYPE];
        int x = ents[b + E_X];
        int y = ents[b + E_Y];
        int z = ents[b + E_Z];
        if (type == T_VADER) {
            // Vader cannot be destroyed: the hit sends him spinning out of the fight.
            SpacePhase.breakOff(ents, b);
            ents[b + E_VZ] = 48;
            return;
        }
        if (type == T_TOWER) {
            ents[b + E_FLAG] = 0;
            ents[b + E_SR] = 0;
            SurfacePhase.topsHit = SurfacePhase.topsHit + 1;
            Session.score = Session.score + Session.TOWER_POINTS;
            Entities.debris(ents, x, GROUND + ents[b + E_AUX] + CAP_HEIGHT / 2, z, 40);
            Hud.drawHeader();
            return;
        }
        ents[b + E_TYPE] = T_NONE;
        if (type == T_TIE) {
            SpacePhase.kills = SpacePhase.kills + 1;
            Session.score = Session.score + Session.TIE_POINTS;
            Entities.debris(ents, x, y, z, 90);
        } else if (type == T_FIREBALL) {
            Session.score = Session.score + Session.FIREBALL_POINTS;
            Entities.debris(ents, x, y, z, 30);
        } else if (type == T_TURRET) {
            Session.score = Session.score + Session.TURRET_POINTS;
            Entities.debris(ents, x, y, z, 40);
        } else if (type == T_PORT) {
            TrenchPhase.portDestroyed = true;
            Session.score = Session.score + Session.PORT_POINTS;
            Entities.debris(ents, x, y, z, 120);
        }
        Hud.drawHeader();
    }
}
