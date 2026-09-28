package io.github.jabrena.juno.games.empirestrikesback;

import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_AUX;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_FLAG;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_STRIDE;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_TIMER;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_TYPE;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_VX;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_VY;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_VZ;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_X;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_Y;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_Z;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.T_ASTEROID;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.T_DEBRIS;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.T_FIREBALL;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.T_NONE;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.T_TIE;

import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * The asteroid field: at the controls of the Millennium Falcon, weave through tumbling asteroids
 * while TIE fighters close in, until you are out of the field.
 */
final class AsteroidsRound {
    private static int fieldLength;

    private AsteroidsRound() {
    }

    static void start() {
        fieldLength = 30000 + 4000 * Math.min(Session.wave, 6);
    }

    static void announce() {
        Hud.showCentered("THE ASTEROID FIELD", 104, 2, TftTouchShield.YELLOW);
        Hud.showCentered("Never tell me the odds!", 136, 1, TftTouchShield.WHITE);
    }

    static boolean isOver(int[] ents) {
        int threats = Entities.count(ents, T_FIREBALL) + Entities.count(ents, T_DEBRIS);
        return Session.travel >= fieldLength && Entities.count(ents, T_ASTEROID) + Entities.count(ents, T_TIE)
                + threats == 0;
    }

    static int distanceOut() {
        return Math.max(0, (fieldLength - Session.travel) / 100);
    }

    /** Called when the spawn countdown runs out. */
    static void spawn(int[] ents) {
        if (Session.travel >= fieldLength) {
            Session.spawnCountdown = 10;
            return;
        }
        Session.spawnCountdown = Math.max(8, Random.nextInt(14, 30) - Session.wave);
        if (Random.nextInt(5) == 0 && Entities.count(ents, T_TIE) < Math.min(1 + Session.wave / 2, 3)) {
            spawnTie(ents);
        } else {
            spawnAsteroid(ents);
        }
    }

    private static void spawnAsteroid(int[] ents) {
        int slot = Entities.freeSlot(ents);
        if (slot < 0) {
            return;
        }
        int b = slot * E_STRIDE;
        ents[b + E_TYPE] = T_ASTEROID;
        ents[b + E_X] = Camera.camX + Random.nextInt(-700, 701);
        ents[b + E_Y] = Camera.camY + Random.nextInt(-450, 451);
        ents[b + E_Z] = Camera.FAR;
        ents[b + E_VX] = Random.nextInt(-3, 4);
        ents[b + E_VY] = Random.nextInt(-3, 4);
        ents[b + E_VZ] = -Random.nextInt(0, 6 + Session.wave);
        ents[b + E_AUX] = Random.nextInt(45, 120);
        ents[b + E_FLAG] = Random.nextInt(1000);
    }

    private static void spawnTie(int[] ents) {
        int slot = Entities.freeSlot(ents);
        if (slot < 0) {
            return;
        }
        int b = slot * E_STRIDE;
        ents[b + E_TYPE] = T_TIE;
        ents[b + E_X] = Camera.camX + Random.nextInt(-500, 501);
        ents[b + E_Y] = Camera.camY + Random.nextInt(-250, 251);
        ents[b + E_Z] = Camera.FAR - Random.nextInt(400);
        ents[b + E_VZ] = Session.flightSpeed() - Math.min(10 + 2 * Session.wave, 26);
        ents[b + E_TIMER] = 1;
    }

    /** Weaves towards the camera firing, then breaks off and flies out of view. */
    static void flyTie(int[] ents, int b) {
        int x = ents[b + E_X] - Camera.camX;
        int y = ents[b + E_Y] - Camera.camY;
        int z = ents[b + E_Z];
        if (ents[b + E_AUX] == 0) {
            int timer = ents[b + E_TIMER] - 1;
            if (timer <= 0) {
                int weave = 6 + Math.min(Session.wave, 6);
                ents[b + E_VX] = Random.nextInt(-weave, weave + 1);
                ents[b + E_VY] = Random.nextInt(-5, 6);
                timer = Random.nextInt(15, 40);
            }
            ents[b + E_TIMER] = timer;
            if (x > z * 3 / 5) {
                ents[b + E_VX] = -Math.abs(ents[b + E_VX]) - 1;
            } else if (x < -z * 3 / 5) {
                ents[b + E_VX] = Math.abs(ents[b + E_VX]) + 1;
            }
            if (z < 350) {
                ents[b + E_AUX] = 1;
                ents[b + E_VZ] = Session.flightSpeed() + 20;
                ents[b + E_VX] = x >= 0 ? 16 : -16;
                ents[b + E_VY] = y >= 0 ? 8 : -8;
            } else if (z > 500 && z < 1700 && Random.nextInt(1000) < 12 + 3 * Math.min(Session.wave, 10)) {
                Entities.fireball(ents, ents[b + E_X], ents[b + E_Y], z);
            }
        } else if (z > Camera.FAR + 200 || Math.abs(x) > z + 300 || Math.abs(y) > z + 300) {
            ents[b + E_TYPE] = T_NONE;
        }
    }
}
