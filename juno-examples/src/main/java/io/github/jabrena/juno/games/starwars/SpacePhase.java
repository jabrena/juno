package io.github.jabrena.juno.games.starwars;

import static io.github.jabrena.juno.games.starwars.Entities.E_AUX;
import static io.github.jabrena.juno.games.starwars.Entities.E_STRIDE;
import static io.github.jabrena.juno.games.starwars.Entities.E_TIMER;
import static io.github.jabrena.juno.games.starwars.Entities.E_TYPE;
import static io.github.jabrena.juno.games.starwars.Entities.E_VX;
import static io.github.jabrena.juno.games.starwars.Entities.E_VY;
import static io.github.jabrena.juno.games.starwars.Entities.E_VZ;
import static io.github.jabrena.juno.games.starwars.Entities.E_X;
import static io.github.jabrena.juno.games.starwars.Entities.E_Y;
import static io.github.jabrena.juno.games.starwars.Entities.E_Z;
import static io.github.jabrena.juno.games.starwars.Entities.T_DEBRIS;
import static io.github.jabrena.juno.games.starwars.Entities.T_FIREBALL;
import static io.github.jabrena.juno.games.starwars.Entities.T_NONE;
import static io.github.jabrena.juno.games.starwars.Entities.T_TIE;
import static io.github.jabrena.juno.games.starwars.Entities.T_VADER;

import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * Space: TIE fighters swoop in and fire spinning fireballs until the wave's quota is destroyed.
 * Darth Vader's TIE fighter joins halfway.
 */
final class SpacePhase {
    static int kills;
    static int killTarget;
    private static boolean vaderSpawned;

    private SpacePhase() {
    }

    static void start() {
        kills = 0;
        killTarget = Math.min(4 + 2 * Session.wave, 12);
        vaderSpawned = false;
    }

    static void announce() {
        Hud.showCentered("WAVE", 88, 3, TftTouchShield.YELLOW);
        TftTouchShield.setCursor(Session.wave < 10 ? 151 : 142, 120);
        TftTouchShield.print(Session.wave);
        Hud.showCentered("Destroy the TIE fighters", 156, 1, TftTouchShield.WHITE);
    }

    static boolean isOver(int[] ents) {
        return kills >= killTarget && Entities.count(ents, T_TIE) + Entities.count(ents, T_VADER)
                + Entities.count(ents, T_FIREBALL) + Entities.count(ents, T_DEBRIS) == 0;
    }

    static int fightersLeft() {
        return Math.max(0, killTarget - kills);
    }

    static void spawn(int[] ents) {
        if (kills >= killTarget) {
            return;
        }
        Session.spawnCountdown = Session.spawnCountdown - 1;
        int attacking = Entities.count(ents, T_TIE) + Entities.count(ents, T_VADER);
        if (Session.spawnCountdown > 0 || attacking >= Math.min(2 + Session.wave / 2, 4)) {
            return;
        }
        int slot = Entities.freeSlot(ents);
        if (slot < 0) {
            return;
        }
        Session.spawnCountdown = Random.nextInt(15, 45);
        int b = slot * E_STRIDE;
        int type = T_TIE;
        if (!vaderSpawned && kills * 2 >= killTarget) {
            type = T_VADER;
            vaderSpawned = true;
        }
        ents[b + E_TYPE] = type;
        ents[b + E_X] = Random.nextInt(-600, 601);
        ents[b + E_Y] = Random.nextInt(-280, 281);
        ents[b + E_Z] = Camera.FAR - Random.nextInt(400);
        ents[b + E_VZ] = -Math.min(12 + 2 * Session.wave, 30);
        ents[b + E_TIMER] = 1;
    }

    /** Attacks, weaving towards the camera and firing, then breaks off and flies out of view. */
    static void flyTie(int[] ents, int b) {
        int x = ents[b + E_X];
        int y = ents[b + E_Y];
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
            // Stay inside the view while attacking.
            if (x > z * 3 / 5) {
                ents[b + E_VX] = -Math.abs(ents[b + E_VX]) - 1;
            } else if (x < -z * 3 / 5) {
                ents[b + E_VX] = Math.abs(ents[b + E_VX]) + 1;
            }
            if (y > z * 2 / 5) {
                ents[b + E_VY] = -Math.abs(ents[b + E_VY]) - 1;
            } else if (y < -z * 2 / 5) {
                ents[b + E_VY] = Math.abs(ents[b + E_VY]) + 1;
            }
            if (z < 320 || kills >= killTarget) {
                breakOff(ents, b);
            } else if (z > 450 && z < 1700 && Random.nextInt(1000) < 10 + 3 * Math.min(Session.wave, 10)) {
                Entities.fireball(ents, x, y, z);
            }
        } else if (z > Camera.FAR + 200 || x > z + 200 || x < -z - 200 || y > z + 200 || y < -z - 200) {
            ents[b + E_TYPE] = T_NONE;
        }
    }

    static void breakOff(int[] ents, int b) {
        ents[b + E_AUX] = 1;
        ents[b + E_VZ] = 18 + Session.wave;
        ents[b + E_VX] = ents[b + E_X] >= 0 ? 16 : -16;
        ents[b + E_VY] = ents[b + E_Y] >= 0 ? 8 : -8;
    }
}
