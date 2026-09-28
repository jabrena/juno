package io.github.jabrena.juno.games.empirestrikesback;

import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_STRIDE;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_TIMER;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_TYPE;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_VX;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_VY;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_VZ;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_X;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_Y;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_Z;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.GROUND;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.T_DEBRIS;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.T_FIREBALL;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.T_PROBE;

import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * Probe droids: fly your snowspeeder low over the snowfields of Hoth and destroy the Imperial probe
 * droids hovering ahead before their shots wear your shields down.
 */
final class ProbesRound {
    static int kills;
    private static int killTarget;

    private ProbesRound() {
    }

    static void start() {
        kills = 0;
        killTarget = Math.min(8 + 2 * Session.wave, 18);
    }

    static void announce() {
        Hud.showCentered("WAVE", 72, 3, TftTouchShield.YELLOW);
        TftTouchShield.setCursor(Session.wave < 10 ? 151 : 142, 102);
        TftTouchShield.print(Session.wave);
        Hud.showCentered("HOTH", 138, 2, SceneRenderer.SNOW);
        Hud.showCentered("Destroy the probe droids", 164, 1, TftTouchShield.WHITE);
    }

    static boolean isOver(int[] ents) {
        int threats = Entities.count(ents, T_FIREBALL) + Entities.count(ents, T_DEBRIS);
        return kills >= killTarget && Entities.count(ents, T_PROBE) + threats == 0;
    }

    static int probesLeft() {
        return Math.max(0, killTarget - kills);
    }

    /** Called when the spawn countdown runs out. */
    static void spawn(int[] ents) {
        Session.spawnCountdown = 10;
        if (kills + Entities.count(ents, T_PROBE) < killTarget
                && Entities.count(ents, T_PROBE) < Math.min(2 + Session.wave / 2, 4)) {
            spawnProbe(ents);
            Session.spawnCountdown = Random.nextInt(20, 50);
        }
    }

    private static void spawnProbe(int[] ents) {
        int slot = Entities.freeSlot(ents);
        if (slot < 0) {
            return;
        }
        int b = slot * E_STRIDE;
        ents[b + E_TYPE] = T_PROBE;
        ents[b + E_X] = Camera.camX + Random.nextInt(-500, 501);
        ents[b + E_Y] = Random.nextInt(-40, 160);
        ents[b + E_Z] = Camera.FAR - Random.nextInt(300);
        ents[b + E_TIMER] = Random.nextInt(8, 30);
    }

    /**
     * A probe droid hovers ahead, keeping its distance while it drifts from side to side, and fires
     * now and then; it bobs as it floats.
     */
    static void flyProbe(int[] ents, int b) {
        int z = ents[b + E_Z];
        // Hold station at 700-1400 ahead: match the flight speed there.
        if (z < 900 + (b % 5) * 100) {
            ents[b + E_VZ] = Session.flightSpeed() - 2;
        } else {
            ents[b + E_VZ] = 0;
        }
        ents[b + E_TIMER] = ents[b + E_TIMER] - 1;
        if (ents[b + E_TIMER] <= 0) {
            ents[b + E_VX] = Random.nextInt(-8, 9);
            ents[b + E_VY] = Random.nextInt(-3, 4);
            ents[b + E_TIMER] = Random.nextInt(20, 50);
            if (z < 2500 && Random.nextInt(100) < 16 + 4 * Math.min(Session.wave, 8)) {
                Entities.fireball(ents, ents[b + E_X], ents[b + E_Y], z);
            }
        }
        ents[b + E_Y] = Camera.clamp(ents[b + E_Y], GROUND + 90, 220);
        int dx = ents[b + E_X] - Camera.camX;
        if (dx > z * 3 / 5) {
            ents[b + E_VX] = -Math.abs(ents[b + E_VX]) - 1;
        } else if (dx < -z * 3 / 5) {
            ents[b + E_VX] = Math.abs(ents[b + E_VX]) + 1;
        }
    }
}
