package io.github.jabrena.juno.games.starwars;

import static io.github.jabrena.juno.games.starwars.Entities.E_AUX;
import static io.github.jabrena.juno.games.starwars.Entities.E_FLAG;
import static io.github.jabrena.juno.games.starwars.Entities.E_STRIDE;
import static io.github.jabrena.juno.games.starwars.Entities.E_TIMER;
import static io.github.jabrena.juno.games.starwars.Entities.E_TYPE;
import static io.github.jabrena.juno.games.starwars.Entities.E_X;
import static io.github.jabrena.juno.games.starwars.Entities.E_Y;
import static io.github.jabrena.juno.games.starwars.Entities.E_Z;
import static io.github.jabrena.juno.games.starwars.Entities.GROUND;
import static io.github.jabrena.juno.games.starwars.Entities.T_DEBRIS;
import static io.github.jabrena.juno.games.starwars.Entities.T_FIREBALL;
import static io.github.jabrena.juno.games.starwars.Entities.T_TOWER;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * The surface: laser towers stream past above the Death Star's surface. Shoot their yellow tops
 * before they fire; destroying every top in the wave pays a bonus.
 */
final class SurfacePhase {
    private static final int GRID = 300;

    private static int towersToSpawn;
    private static int towersTotal;
    static int topsHit;

    private SurfacePhase() {
    }

    static void start() {
        towersTotal = Math.min(4 + 2 * Session.wave, 14);
        towersToSpawn = towersTotal;
        topsHit = 0;
    }

    static void announce() {
        Hud.showCentered("DEATH STAR SURFACE", 104, 2, TftTouchShield.YELLOW);
        Hud.showCentered("Shoot the tower tops", 136, 1, TftTouchShield.WHITE);
    }

    static boolean isOver(int[] ents) {
        return towersToSpawn == 0 && Entities.count(ents, T_TOWER) + Entities.count(ents, T_FIREBALL)
                + Entities.count(ents, T_DEBRIS) == 0;
    }

    static int towersAhead() {
        return towersToSpawn;
    }

    /** Pays the bonus when every tower top of the wave was destroyed. */
    static void awardAllTopsBonus() {
        if (topsHit != towersTotal) {
            return;
        }
        Session.score = Session.score + Session.ALL_TOWERS_BONUS;
        Hud.drawHeader();
        DisplayList.clearView();
        Hud.showCentered("ALL TOWER TOPS DESTROYED", 110, 1, SceneRenderer.TOWER_TOP);
        Hud.showCentered("BONUS 50000", 126, 1, TftTouchShield.WHITE);
        Delay.millis(1500);
    }

    static void spawn(int[] ents) {
        if (towersToSpawn == 0) {
            return;
        }
        Session.spawnCountdown = Session.spawnCountdown - 1;
        if (Session.spawnCountdown > 0) {
            return;
        }
        int slot = Entities.freeSlot(ents);
        if (slot < 0) {
            return;
        }
        Session.spawnCountdown = Math.max(18, Random.nextInt(30, 60) - 2 * Session.wave);
        int b = slot * E_STRIDE;
        int x = Random.nextInt(140, 620);
        if (Random.nextInt(2) == 0) {
            x = -x;
        }
        ents[b + E_TYPE] = T_TOWER;
        ents[b + E_X] = x;
        ents[b + E_Y] = GROUND;
        ents[b + E_Z] = Camera.FAR;
        ents[b + E_AUX] = Random.nextInt(170, 330);
        ents[b + E_FLAG] = 1;
        ents[b + E_TIMER] = Random.nextInt(10, 50);
        towersToSpawn = towersToSpawn - 1;
        Hud.drawStatus();
    }

    /** The horizon and the surface's grid, streaming towards the camera. */
    static void drawSurface(short[] lines) {
        DisplayList.addLine(lines, 0, Camera.CENTER_Y, DisplayList.WIDTH - 1, Camera.CENTER_Y, SceneRenderer.STRUCTURE);
        int offset = Session.travel % GRID;
        for (int k = 0; k < 8; k++) {
            int z = GRID * (k + 1) - offset;
            if (z < Camera.NEAR || z > Camera.FAR) {
                continue;
            }
            for (int c = 0; c < 4; c++) {
                int x = -540 + c * 360 + ((k & 1) == 0 ? 0 : 180);
                Camera.line3(lines, x - 50, GROUND, z, x + 50, GROUND, z, SceneRenderer.GRID_LINE);
            }
        }
    }
}
