package io.github.jabrena.juno.games.redbaron;

import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;

import static io.github.jabrena.juno.games.redbaron.Entities.E_AUX;
import static io.github.jabrena.juno.games.redbaron.Entities.E_HEADING;
import static io.github.jabrena.juno.games.redbaron.Entities.E_HP;
import static io.github.jabrena.juno.games.redbaron.Entities.E_MODE;
import static io.github.jabrena.juno.games.redbaron.Entities.E_STRIDE;
import static io.github.jabrena.juno.games.redbaron.Entities.E_TIMER;
import static io.github.jabrena.juno.games.redbaron.Entities.E_TYPE;
import static io.github.jabrena.juno.games.redbaron.Entities.E_VX;
import static io.github.jabrena.juno.games.redbaron.Entities.E_VY;
import static io.github.jabrena.juno.games.redbaron.Entities.E_VZ;
import static io.github.jabrena.juno.games.redbaron.Entities.E_X;
import static io.github.jabrena.juno.games.redbaron.Entities.E_Y;
import static io.github.jabrena.juno.games.redbaron.Entities.E_Z;
import static io.github.jabrena.juno.games.redbaron.Entities.FLIGHT_SPEED;
import static io.github.jabrena.juno.games.redbaron.Entities.T_BLIMP;
import static io.github.jabrena.juno.games.redbaron.Entities.T_DEBRIS;
import static io.github.jabrena.juno.games.redbaron.Entities.T_FALLING;
import static io.github.jabrena.juno.games.redbaron.Entities.T_PLANE;
import static io.github.jabrena.juno.games.redbaron.Entities.T_TRACER;

/** Enemy biplanes and the blimp flown in the first round of every wave. */
final class DogfightRound {
    static final int PLANE_RADIUS = 60;
    static final int BLIMP_RADIUS = 130;
    static final int BLIMP_HITS = 4;
    private static final int PLANE_SPEED = 30;

    static int kills;
    static int killTarget;
    private static boolean blimpSpawned;

    private DogfightRound() {
    }

    static void start() {
        kills = 0;
        killTarget = Math.min(3 + Session.wave, 10);
        blimpSpawned = false;
    }

    static void announce() {
        Hud.showCentered("WAVE", 84, 3, TftTouchShield.YELLOW);
        TftTouchShield.setCursor(Session.wave < 10 ? 151 : 142, 116);
        TftTouchShield.print(Session.wave);
        Hud.showCentered("DOGFIGHT", 152, 2, TftTouchShield.WHITE);
    }

    static boolean isOver(int[] ents) {
        return kills >= killTarget && Entities.count(ents, T_PLANE) + Entities.count(ents, T_FALLING)
                + Entities.count(ents, T_TRACER) + Entities.count(ents, T_DEBRIS) == 0;
    }

    static int fightersLeft() {
        return Math.max(0, killTarget - kills);
    }

    static void spawn(int[] ents) {
        Session.spawnCountdown = Session.spawnCountdown - 1;
        if (Session.spawnCountdown > 0) {
            return;
        }
        int flying = Entities.count(ents, T_PLANE) + Entities.count(ents, T_FALLING);
        int atOnce = Math.min(1 + (Session.wave + 1) / 2, 3);
        if (kills + flying < killTarget && Entities.count(ents, T_PLANE) < atOnce) {
            spawnPlane(ents);
            Session.spawnCountdown = 50;
        } else if (!blimpSpawned && kills * 2 >= killTarget) {
            spawnBlimp(ents);
            Session.spawnCountdown = 60;
        } else {
            Session.spawnCountdown = 10;
        }
    }

    private static void spawnPlane(int[] ents) {
        int slot = Entities.freeSlot(ents);
        if (slot < 0) {
            return;
        }
        int b = slot * E_STRIDE;
        ents[b + E_TYPE] = T_PLANE;
        ents[b + E_X] = Random.nextInt(-1400, 1401);
        ents[b + E_Y] = Random.nextInt(200, 480);
        ents[b + E_Z] = Camera.FAR - 400;
        ents[b + E_HEADING] = 180;
        ents[b + E_VZ] = -PLANE_SPEED;
        ents[b + E_TIMER] = attackDelay();
        ents[b + E_AUX] = Random.nextInt(8);
    }

    private static void spawnBlimp(int[] ents) {
        int slot = Entities.freeSlot(ents);
        if (slot < 0) {
            return;
        }
        blimpSpawned = true;
        int b = slot * E_STRIDE;
        int side = Random.nextInt(2) == 0 ? -1 : 1;
        ents[b + E_TYPE] = T_BLIMP;
        ents[b + E_X] = side * 900;
        ents[b + E_Y] = 420;
        ents[b + E_Z] = 2000;
        ents[b + E_VX] = -side * 5;
        ents[b + E_HEADING] = -side * 90;
        ents[b + E_HP] = BLIMP_HITS;
    }

    /** Holds formation, makes a firing pass, then comes round again from ahead. */
    static void flyEnemy(int[] ents, int b) {
        int x = ents[b + E_X];
        int y = ents[b + E_Y];
        int z = ents[b + E_Z];
        if (z < -300 || z > Camera.FAR + 600 || Math.abs(x) > 3000) {
            ents[b + E_X] = Random.nextInt(-1400, 1401);
            ents[b + E_Y] = Random.nextInt(200, 480);
            ents[b + E_Z] = Camera.FAR - 400;
            ents[b + E_VX] = 0;
            ents[b + E_VY] = 0;
            ents[b + E_VZ] = -PLANE_SPEED;
            ents[b + E_MODE] = 0;
            ents[b + E_TIMER] = attackDelay();
            return;
        }
        int targetX;
        int targetY;
        int targetZ;
        if (ents[b + E_MODE] == 1) {
            targetX = 0;
            targetY = Camera.altitude;
            targetZ = -400;
            if (ents[b + E_TIMER] > 0) {
                ents[b + E_TIMER] = ents[b + E_TIMER] - 1;
            } else if (z > 200 && z < 1100 && Math.abs(x) < z / 4
                    && Math.abs(y - Camera.altitude) < z / 4) {
                Entities.enemyFires(ents, x, y, z, false);
                ents[b + E_TIMER] = 30;
            }
        } else {
            int place = ents[b + E_AUX];
            float t = (Session.frame + place * 40) * 0.035f;
            targetX = (place - 4) * 180 + Math.round((float) Math.sin(t) * 260);
            targetY = 260 + Math.round((float) Math.cos(t * 0.7f) * 110);
            targetZ = 900 + place * 60;
            ents[b + E_TIMER] = ents[b + E_TIMER] - 1;
            if (ents[b + E_TIMER] <= 0) {
                ents[b + E_MODE] = 1;
                ents[b + E_TIMER] = 10;
            }
        }
        int dx = targetX - x;
        int dy = targetY - y;
        int dz = targetZ - z;
        float length = (float) Math.sqrt((float) dx * dx + (float) dy * dy + (float) dz * dz);
        if (length > 1) {
            float speed = PLANE_SPEED + FLIGHT_SPEED;
            int wantX = Math.round(dx * speed / length);
            int wantY = Math.round(dy * speed / length);
            int wantZ = Math.round(dz * speed / length) + FLIGHT_SPEED;
            ents[b + E_VX] = ents[b + E_VX] + (wantX - ents[b + E_VX]) / 6;
            ents[b + E_VY] = ents[b + E_VY] + (wantY - ents[b + E_VY]) / 6;
            ents[b + E_VZ] = ents[b + E_VZ] + (wantZ - ents[b + E_VZ]) / 6;
        }
        int airX = ents[b + E_VX];
        int airZ = ents[b + E_VZ] - FLIGHT_SPEED;
        if (airX != 0 || airZ != 0) {
            ents[b + E_HEADING] = Math.round((float) Math.toDegrees(Math.atan2(airX, airZ)));
        }
    }

    private static int attackDelay() {
        return Math.max(60, 150 - 10 * Session.wave) + Random.nextInt(100);
    }
}
