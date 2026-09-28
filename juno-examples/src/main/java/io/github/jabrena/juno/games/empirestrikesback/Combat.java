package io.github.jabrena.juno.games.empirestrikesback;

import static io.github.jabrena.juno.games.empirestrikesback.Entities.ATAT_HEAD_Y;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.ATST_HEAD_Y;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.ENTITIES;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_AUX;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_FLAG;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_HP;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_SR;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_STRIDE;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_SX;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_SY;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_TIMER;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_TYPE;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_X;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_Y;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_Z;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.GROUND;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.T_ASTEROID;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.T_ATAT;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.T_ATST;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.T_FIREBALL;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.T_NONE;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.T_PROBE;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.T_SHOT;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.T_TIE;

/**
 * The twin lasers: at most {@value #SHOTS} shots in flight, each converging on the point tapped and
 * hitting what was under the crosshair when it was fired, for points. An AT-AT's head takes
 * {@value Entities#ATAT_HITS} hits.
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
    }

    /**
     * Lasers take {@value #SHOT_FRAMES} frames to converge, then hit the target that was under the
     * crosshair when they were fired, if it is still there.
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
            if (target >= 0 && ents[target * E_STRIDE + E_TYPE] == ents[b + E_FLAG]) {
                hit(ents, target);
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
        if (type == T_ATAT) {
            ents[b + E_HP] = ents[b + E_HP] - 1;
            int headX = x + (ents[b + E_FLAG] >= 0 ? 175 : -175);
            Entities.debris(ents, headX, ATAT_HEAD_Y, z, 30);
            if (ents[b + E_HP] > 0) {
                return;
            }
            WalkersRound.atatsDown = WalkersRound.atatsDown + 1;
            Session.score = Session.score + Session.ATAT_POINTS;
            ents[b + E_TYPE] = T_NONE;
            Entities.debris(ents, x, GROUND + 130, z, 160);
            Hud.drawHeader();
            return;
        }
        ents[b + E_TYPE] = T_NONE;
        if (type == T_PROBE) {
            ProbesRound.kills = ProbesRound.kills + 1;
            Session.score = Session.score + Session.PROBE_POINTS;
            Entities.debris(ents, x, y, z, 70);
        } else if (type == T_ATST) {
            Session.score = Session.score + Session.ATST_POINTS;
            Entities.debris(ents, x, ATST_HEAD_Y, z, 70);
        } else if (type == T_ASTEROID) {
            Session.score = Session.score + Session.ASTEROID_POINTS;
            Entities.debris(ents, x, y, z, ents[b + E_AUX]);
        } else if (type == T_TIE) {
            Session.score = Session.score + Session.TIE_POINTS;
            Entities.debris(ents, x, y, z, 90);
        } else if (type == T_FIREBALL) {
            Session.score = Session.score + Session.FIREBALL_POINTS;
            Entities.debris(ents, x, y, z, 30);
        }
        Hud.drawHeader();
    }
}
