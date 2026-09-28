package io.github.jabrena.juno.games.startrek;

import static io.github.jabrena.juno.games.startrek.Entities.ENTITIES;
import static io.github.jabrena.juno.games.startrek.Entities.E_STRIDE;
import static io.github.jabrena.juno.games.startrek.Entities.E_TYPE;
import static io.github.jabrena.juno.games.startrek.Entities.E_X;
import static io.github.jabrena.juno.games.startrek.Entities.E_Y;
import static io.github.jabrena.juno.games.startrek.Entities.T_KLINGON;
import static io.github.jabrena.juno.games.startrek.Entities.T_NOMAD;
import static io.github.jabrena.juno.games.startrek.Entities.T_SAUCER;
import static io.github.jabrena.juno.games.startrek.Entities.T_STARBASE;
import static io.github.jabrena.juno.games.startrek.Entities.T_TORPEDO;
import static io.github.jabrena.juno.games.startrek.Geometry.FIX;
import static io.github.jabrena.juno.games.startrek.Geometry.SECTOR;

/** The CPU at the helm of the Enterprise. */
final class AutopilotST {
    private static final int DOCK_SHIELDS = 50;
    private static final int HOLD_OFF = 200;
    private static final int AIM = 12;
    private static final int PHOTON_EVERY = 90;
    private static final int WARP_SHIELDS = 30;
    private static final int WARP_THREAT = 80;

    private AutopilotST() {
    }

    /**
     * Steers at the nearest enemy ship, or at the starbase to dock when the shields run low, but
     * holds off at phaser range instead of ramming it. It fires phasers when the target is dead
     * ahead, adds a photon torpedo now and then, and warps away when a Klingon torpedo is about to
     * finish off the shields.
     */
    static void fly(int[] ents) {
        if (Enterprise.warps > 0 && Enterprise.shields <= WARP_SHIELDS
                && nearestDistance(ents, T_TORPEDO) < WARP_THREAT) {
            Enterprise.warp();
            return;
        }
        int base = Entities.find(ents, T_STARBASE);
        boolean needDock = Enterprise.shields < DOCK_SHIELDS && base >= 0 && !Enterprise.docked;
        int target = -1;
        int nearest = SECTOR;
        for (int slot = 0; slot < ENTITIES; slot++) {
            int type = ents[slot * E_STRIDE + E_TYPE];
            boolean wanted = needDock ? slot == base : type == T_KLINGON || type == T_NOMAD || type == T_SAUCER;
            if (wanted) {
                int d = Entities.distanceToShip(ents, slot * E_STRIDE);
                if (d < nearest) {
                    target = slot;
                    nearest = d;
                }
            }
        }
        if (target < 0) {
            Controls.steering = false;
            return;
        }
        int b = target * E_STRIDE;
        int dx = Geometry.wrap(ents[b + E_X] - Enterprise.x) / FIX;
        int dy = Geometry.wrap(ents[b + E_Y] - Enterprise.y) / FIX;
        int off = Math.abs(Geometry.angleBetween(Enterprise.heading, Geometry.bearing(dx, dy)));
        Controls.steering = needDock || nearest > HOLD_OFF || off > 20;
        Controls.steerX = DisplayList.TACTICAL_X + dx;
        Controls.steerY = DisplayList.TACTICAL_Y + dy;
        if (!needDock && off < AIM && nearest < Combat.PHASER_RANGE) {
            Combat.firePhasers(ents);
            if (nearest > 120 && Session.frame % PHOTON_EVERY == 0) {
                Combat.firePhoton(ents);
            }
        }
    }

    /** The distance to the nearest object of a type, or the size of the sector when there is none. */
    private static int nearestDistance(int[] ents, int type) {
        int nearest = SECTOR;
        for (int slot = 0; slot < ENTITIES; slot++) {
            if (ents[slot * E_STRIDE + E_TYPE] == type) {
                nearest = Math.min(nearest, Entities.distanceToShip(ents, slot * E_STRIDE));
            }
        }
        return nearest;
    }
}
