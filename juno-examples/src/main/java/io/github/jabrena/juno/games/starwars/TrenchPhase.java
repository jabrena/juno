package io.github.jabrena.juno.games.starwars;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;

import static io.github.jabrena.juno.games.starwars.Entities.E_STRIDE;
import static io.github.jabrena.juno.games.starwars.Entities.E_TIMER;
import static io.github.jabrena.juno.games.starwars.Entities.E_TYPE;
import static io.github.jabrena.juno.games.starwars.Entities.E_X;
import static io.github.jabrena.juno.games.starwars.Entities.E_Y;
import static io.github.jabrena.juno.games.starwars.Entities.E_Z;
import static io.github.jabrena.juno.games.starwars.Entities.TRENCH_HALF_WIDTH;
import static io.github.jabrena.juno.games.starwars.Entities.T_CATWALK;
import static io.github.jabrena.juno.games.starwars.Entities.T_DEBRIS;
import static io.github.jabrena.juno.games.starwars.Entities.T_PORT;
import static io.github.jabrena.juno.games.starwars.Entities.T_TURRET;

/**
 * The trench: the X-wing follows the crosshair past wall turrets and, from wave 2, catwalks, to the
 * exhaust port at its end. Missing the port means flying the trench again.
 */
final class TrenchPhase {
    private static final int TRENCH_FLOOR = -150;
    private static final int TRENCH_TOP = 150;
    private static final int SEGMENT = 320;

    private static int trenchLength;
    private static boolean portSpawned;
    static boolean portDestroyed;
    static boolean portMissed;
    static int trenchShots;

    private TrenchPhase() {
    }

    static void start() {
        trenchLength = 8000 + 1500 * Math.min(Session.wave, 6);
        portSpawned = false;
        portDestroyed = false;
        portMissed = false;
        trenchShots = 0;
    }

    static void announce() {
        Hud.showCentered("THE TRENCH", 104, 2, TftTouchShield.YELLOW);
        Hud.showCentered("Steer with the crosshair and hit the port", 136, 1, TftTouchShield.WHITE);
    }

    static void announceMiss() {
        DisplayList.clearView();
        Hud.showCentered("YOU MISSED", 100, 2, TftTouchShield.RED);
        Hud.showCentered("Fly the trench again", 130, 1, TftTouchShield.WHITE);
        Delay.millis(1500);
    }

    static boolean isOver(int[] ents) {
        return portMissed || (portDestroyed && Entities.count(ents, T_DEBRIS) == 0);
    }

    static int distanceToPort() {
        return Math.max(0, (trenchLength - Session.travel) / 10);
    }

    /** The Force is with the pilot who hit the port with the only shot fired in the trench. */
    static boolean withTheForce() {
        return trenchShots == 1;
    }

    static void spawn(int[] ents) {
        if (portSpawned) {
            return;
        }
        int slot = Entities.freeSlot(ents);
        if (slot < 0) {
            return;
        }
        int b = slot * E_STRIDE;
        if (Session.travel >= trenchLength) {
            portSpawned = true;
            ents[b + E_TYPE] = T_PORT;
            ents[b + E_Y] = TRENCH_FLOOR;
            ents[b + E_Z] = Camera.FAR;
            return;
        }
        Session.spawnCountdown = Session.spawnCountdown - 1;
        if (Session.spawnCountdown > 0) {
            return;
        }
        Session.spawnCountdown = Math.max(14, Random.nextInt(24, 50) - 2 * Session.wave);
        ents[b + E_Z] = Camera.FAR;
        if (Session.wave >= 2 && Random.nextInt(3) == 0) {
            ents[b + E_TYPE] = T_CATWALK;
            ents[b + E_Y] = Random.nextInt(-90, 91);
        } else {
            ents[b + E_TYPE] = T_TURRET;
            ents[b + E_X] = Random.nextInt(2) == 0 ? -TRENCH_HALF_WIDTH : TRENCH_HALF_WIDTH;
            ents[b + E_Y] = Random.nextInt(-110, 111);
            ents[b + E_TIMER] = Random.nextInt(10, 60);
        }
    }

    /** The trench's walls and floor, their segments streaming towards the camera. */
    static void drawTrench(short[] lines) {
        int w = TRENCH_HALF_WIDTH;
        int near = Camera.NEAR;
        int far = Camera.FAR;
        Camera.line3(lines, -w, TRENCH_TOP, near, -w, TRENCH_TOP, far, SceneRenderer.STRUCTURE);
        Camera.line3(lines, -w, TRENCH_FLOOR, near, -w, TRENCH_FLOOR, far, SceneRenderer.STRUCTURE);
        Camera.line3(lines, w, TRENCH_FLOOR, near, w, TRENCH_FLOOR, far, SceneRenderer.STRUCTURE);
        Camera.line3(lines, w, TRENCH_TOP, near, w, TRENCH_TOP, far, SceneRenderer.STRUCTURE);
        int offset = Session.travel % SEGMENT;
        for (int k = 0; k < 8; k++) {
            int z = SEGMENT * (k + 1) - offset;
            if (z < near || z > far) {
                continue;
            }
            Camera.line3(lines, -w, TRENCH_FLOOR, z, -w, TRENCH_TOP, z, SceneRenderer.GRID_LINE);
            Camera.line3(lines, -w, TRENCH_FLOOR, z, w, TRENCH_FLOOR, z, SceneRenderer.GRID_LINE);
            Camera.line3(lines, w, TRENCH_FLOOR, z, w, TRENCH_TOP, z, SceneRenderer.GRID_LINE);
        }
    }
}
