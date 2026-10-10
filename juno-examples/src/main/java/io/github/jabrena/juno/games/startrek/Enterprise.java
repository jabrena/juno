package io.github.jabrena.juno.games.startrek;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;

import static io.github.jabrena.juno.games.startrek.Geometry.FIX;
import static io.github.jabrena.juno.games.startrek.Geometry.SECTOR_FIX;

/**
 * The Enterprise: where it is and where it is heading, in world units x FIX, and what it has left
 * (shields, photon torpedoes, warp jumps). It turns and moves on impulse power, warps, docks and
 * takes hits.
 */
final class Enterprise {
    static final int MAX_PHOTONS = 5;
    static final int MAX_WARPS = 3;
    private static final int MAX_SPEED = 4 * FIX;
    private static final int THRUST = 5;
    private static final int TURN = 6;
    private static final int WARP_DISTANCE = 700;
    static final int DOCK_RANGE = 40;

    static int x;
    static int y;
    static int vx;
    static int vy;
    static int heading;
    static int shields;
    static int photons;
    static int warps;
    static boolean docked;

    private Enterprise() {
    }

    static void newGame() {
        shields = 100;
        photons = MAX_PHOTONS;
        warps = MAX_WARPS;
    }

    /** Arrives in the middle of a new sector, at rest and heading up. */
    static void arrive() {
        docked = false;
        x = SECTOR_FIX / 2;
        y = SECTOR_FIX / 2;
        vx = 0;
        vy = 0;
        heading = 0;
    }

    static void steer() {
        if (!Controls.steering) {
            // Without impulse power the Enterprise coasts to a stop.
            vx = vx * 15 / 16;
            vy = vy * 15 / 16;
            return;
        }
        int want = Geometry.bearing(Controls.steerX - DisplayList.TACTICAL_X, Controls.steerY - DisplayList.TACTICAL_Y);
        heading = Geometry.normalize(heading + Geometry.clamp(Geometry.angleBetween(heading, want), -TURN, TURN));
        float radians = (float) Math.toRadians(heading);
        vx = vx + Math.round((float) Math.sin(radians) * THRUST);
        vy = vy - Math.round((float) Math.cos(radians) * THRUST);
        int speed = (int) Math.sqrt((float) vx * vx + (float) vy * vy);
        if (speed > MAX_SPEED) {
            vx = vx * MAX_SPEED / speed;
            vy = vy * MAX_SPEED / speed;
        }
    }

    static void move() {
        x = Geometry.wrapPosition(x + vx);
        y = Geometry.wrapPosition(y + vy);
    }

    /** Jumps the Enterprise far ahead, out of trouble. */
    static void warp() {
        if (warps == 0) {
            return;
        }
        warps = warps - 1;
        float radians = (float) Math.toRadians(heading);
        x = Geometry.wrapPosition(x + Math.round((float) Math.sin(radians) * WARP_DISTANCE * FIX));
        y = Geometry.wrapPosition(y - Math.round((float) Math.cos(radians) * WARP_DISTANCE * FIX));
        TftTouchShield.fillRect(0, DisplayList.HEADER, DisplayList.TACTICAL_RIGHT + 1,
                DisplayList.HEIGHT - DisplayList.HEADER, TftTouchShield.WHITE);
        Delay.millis(40);
        DisplayList.clearView();
        Hud.drawPanel();
    }

    /** Docking restores shields, photon torpedoes and warp jumps, once per sector. */
    static void dockIfClose(int[] ents, int b) {
        if (!docked && Entities.distanceToShip(ents, b) < DOCK_RANGE) {
            docked = true;
            shields = 100;
            photons = MAX_PHOTONS;
            warps = MAX_WARPS;
            Hud.invalidate();
            Hud.drawPanel();
        }
    }

    /** One hit on the Enterprise: shields drop, and at zero the Enterprise is lost. */
    static void damage(int percent) {
        shields = Math.max(0, shields - percent);
        if (shields == 0) {
            Session.dead = true;
        }
        Hud.flashHit();
    }
}
