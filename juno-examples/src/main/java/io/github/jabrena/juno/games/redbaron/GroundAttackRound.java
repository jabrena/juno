package io.github.jabrena.juno.games.redbaron;

import static io.github.jabrena.juno.games.redbaron.Entities.E_STRIDE;
import static io.github.jabrena.juno.games.redbaron.Entities.E_TIMER;
import static io.github.jabrena.juno.games.redbaron.Entities.E_TYPE;
import static io.github.jabrena.juno.games.redbaron.Entities.E_X;
import static io.github.jabrena.juno.games.redbaron.Entities.E_Z;
import static io.github.jabrena.juno.games.redbaron.Entities.T_DEBRIS;
import static io.github.jabrena.juno.games.redbaron.Entities.T_FLAK;
import static io.github.jabrena.juno.games.redbaron.Entities.T_HANGAR;
import static io.github.jabrena.juno.games.redbaron.Entities.T_PYRAMID;
import static io.github.jabrena.juno.games.redbaron.Entities.T_TRACER;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/** Hangars, flak guns and pyramids flown in the second round of every wave. */
final class GroundAttackRound {
    static final int GROUND_RADIUS = 70;
    static final int HANGAR_HEIGHT = 70;
    static final int FLAK_HEIGHT = 26;
    static final int PYRAMID_HEIGHT = 110;

    private static int targetsToSpawn;
    private static int targetsTotal;
    static int targetsHit;

    private GroundAttackRound() {
    }

    static void start() {
        targetsTotal = Math.min(4 + Session.wave, 12);
        targetsToSpawn = targetsTotal;
        targetsHit = 0;
    }

    static void announce() {
        Hud.showCentered("GROUND ATTACK", 100, 2, TftTouchShield.YELLOW);
        Hud.showCentered("Fly low and strafe the targets", 134, 1, TftTouchShield.WHITE);
    }

    static boolean isOver(int[] ents) {
        return targetsToSpawn == 0 && Entities.count(ents, T_HANGAR) + Entities.count(ents, T_FLAK)
                + Entities.count(ents, T_TRACER) + Entities.count(ents, T_DEBRIS) == 0;
    }

    static int targetsAhead() {
        return targetsToSpawn;
    }

    static void awardAllTargetsBonus() {
        if (targetsHit != targetsTotal) {
            return;
        }
        Session.score = Session.score + Session.ALL_TARGETS_BONUS;
        Hud.drawHeader();
        DisplayList.clearView();
        Hud.showCentered("ALL TARGETS DESTROYED", 104, 1, SceneRenderer.TARGET);
        Hud.showCentered("BONUS 5000", 122, 1, TftTouchShield.WHITE);
        Delay.millis(1500);
    }

    static void spawn(int[] ents) {
        Session.spawnCountdown = Session.spawnCountdown - 1;
        if (Session.spawnCountdown > 0) {
            return;
        }
        if (targetsToSpawn > 0) {
            spawnGroundTarget(ents);
            targetsToSpawn = targetsToSpawn - 1;
            Session.spawnCountdown = Math.max(26, 44 - 2 * Session.wave);
        } else {
            Session.spawnCountdown = 20;
        }
        if (Random.nextInt(3) == 0) {
            int slot = Entities.freeSlot(ents);
            if (slot >= 0) {
                int b = slot * E_STRIDE;
                ents[b + E_TYPE] = T_PYRAMID;
                ents[b + E_X] = Random.nextInt(-900, 901);
                ents[b + E_Z] = Camera.FAR - 200;
            }
        }
    }

    private static void spawnGroundTarget(int[] ents) {
        int slot = Entities.freeSlot(ents);
        if (slot < 0) {
            return;
        }
        int b = slot * E_STRIDE;
        ents[b + E_TYPE] = Random.nextInt(3) == 0 ? T_FLAK : T_HANGAR;
        ents[b + E_X] = Random.nextInt(-500, 501);
        ents[b + E_Z] = Camera.FAR - 100;
        ents[b + E_TIMER] = 30 + Random.nextInt(40);
    }

    static void flakFires(int[] ents, int b) {
        ents[b + E_TIMER] = ents[b + E_TIMER] - 1;
        int z = ents[b + E_Z];
        if (ents[b + E_TIMER] <= 0 && z > 450 && z < 1600) {
            Entities.enemyFires(ents, ents[b + E_X], FLAK_HEIGHT, z, true);
            ents[b + E_TIMER] = Math.max(34, 70 - 4 * Session.wave);
        }
    }
}
