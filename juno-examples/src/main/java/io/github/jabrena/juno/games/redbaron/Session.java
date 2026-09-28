package io.github.jabrena.juno.games.redbaron;

import static io.github.jabrena.juno.games.redbaron.Entities.ENTITIES;
import static io.github.jabrena.juno.games.redbaron.Entities.E_STRIDE;
import static io.github.jabrena.juno.games.redbaron.Entities.E_TYPE;
import static io.github.jabrena.juno.games.redbaron.Entities.E_Z;
import static io.github.jabrena.juno.games.redbaron.Entities.T_BULLET;
import static io.github.jabrena.juno.games.redbaron.Entities.T_FLAK;
import static io.github.jabrena.juno.games.redbaron.Entities.T_HANGAR;
import static io.github.jabrena.juno.games.redbaron.Entities.T_NONE;
import static io.github.jabrena.juno.games.redbaron.Entities.T_PYRAMID;
import static io.github.jabrena.juno.games.redbaron.Entities.T_TRACER;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/** One game's score, planes, wave and current round. */
final class Session {
    static final int START_PLANES = 3;
    private static final int EXTRA_PLANE_SCORE = 20000;

    static final int PLANE_POINTS = 1000;
    static final int BLIMP_POINTS = 5000;
    static final int HANGAR_POINTS = 500;
    static final int FLAK_POINTS = 800;
    static final int ALL_TARGETS_BONUS = 5000;

    static int score;
    static int best;
    static int planes;
    static int nextExtraPlane;
    static int wave;
    static Round round = Round.DOGFIGHT;
    static int frame;
    static boolean dead;
    static boolean shotDown;
    static int spawnCountdown;

    private Session() {
    }

    static void newGame() {
        score = 0;
        planes = START_PLANES;
        nextExtraPlane = EXTRA_PLANE_SCORE;
        wave = 1;
    }

    static void endGame() {
        if (score > best) {
            best = score;
        }
    }

    /** After a hit or crash, removes one plane and resets the immediate danger. */
    static boolean loseAPlane(int[] ents) {
        shotDown = false;
        planes = planes - 1;
        TftTouchShield.fillRect(0, DisplayList.HEADER, DisplayList.WIDTH,
                DisplayList.HEIGHT - DisplayList.HEADER, TftTouchShield.RED);
        Delay.millis(80);
        DisplayList.clearView();
        Hud.drawHeader();
        if (planes == 0) {
            dead = true;
            return false;
        }
        Hud.showCentered("SHOT DOWN", 104, 2, TftTouchShield.RED);
        Hud.showCentered("Planes left", 130, 1, TftTouchShield.WHITE);
        TftTouchShield.setCursor(158, 146);
        TftTouchShield.print(planes);
        Delay.millis(1300);
        DisplayList.clearView();
        for (int slot = 0; slot < ENTITIES; slot++) {
            int b = slot * E_STRIDE;
            int type = ents[b + E_TYPE];
            boolean close = ents[b + E_Z] < 400;
            if (type == T_TRACER || type == T_BULLET
                    || (close && (type == T_PYRAMID || type == T_HANGAR || type == T_FLAK))) {
                ents[b + E_TYPE] = T_NONE;
            }
        }
        Camera.level();
        Camera.altitude = Math.max(Camera.altitude, round == Round.DOGFIGHT ? 300 : 200);
        return true;
    }

    static void addScore(int points) {
        score = score + points;
        if (score >= nextExtraPlane) {
            planes = planes + 1;
            nextExtraPlane = nextExtraPlane + EXTRA_PLANE_SCORE;
        }
        Hud.drawHeader();
    }
}
