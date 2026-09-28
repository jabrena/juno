package io.github.jabrena.juno.games.startrek;

import static io.github.jabrena.juno.games.startrek.Entities.ENTITIES;
import static io.github.jabrena.juno.games.startrek.Entities.E_AUX;
import static io.github.jabrena.juno.games.startrek.Entities.E_COOL;
import static io.github.jabrena.juno.games.startrek.Entities.E_HEADING;
import static io.github.jabrena.juno.games.startrek.Entities.E_STRIDE;
import static io.github.jabrena.juno.games.startrek.Entities.E_TIMER;
import static io.github.jabrena.juno.games.startrek.Entities.KLINGON_HP;
import static io.github.jabrena.juno.games.startrek.Entities.NOMAD_HP;
import static io.github.jabrena.juno.games.startrek.Entities.STARBASE_HP;
import static io.github.jabrena.juno.games.startrek.Entities.T_KLINGON;
import static io.github.jabrena.juno.games.startrek.Entities.T_NOMAD;
import static io.github.jabrena.juno.games.startrek.Entities.T_SAUCER;
import static io.github.jabrena.juno.games.startrek.Entities.T_STARBASE;
import static io.github.jabrena.juno.games.startrek.Geometry.FIX;

import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * One sector: who is in it (Klingons, the starbase, saucers from sector 2, and Nomad every fourth
 * sector), its announcement, when it is cleared, and the bonus for clearing it.
 */
final class Sector {
    private static final int SECTOR_BONUS = 5000;

    private Sector() {
    }

    static void start(int[] ents) {
        Session.dead = false;
        Session.baseLost = false;
        Entities.clear(ents, ENTITIES * E_STRIDE);
        Enterprise.arrive();
        Combat.reset();
        Controls.steering = false;
        int sector = Session.sector;
        // The starbase, some way from where the Enterprise arrives.
        Entities.spawn(ents, T_STARBASE, Enterprise.x + Random.nextInt(-300, 301) * FIX, Enterprise.y + 350 * FIX,
                STARBASE_HP);
        int klingons = Math.min(2 + sector, 8);
        if (sector % 4 == 0) {
            klingons = Math.min(1 + sector / 4, 4);
            Entities.spawnAway(ents, T_NOMAD, NOMAD_HP);
        }
        for (int i = 0; i < klingons; i++) {
            int slot = Entities.spawnAway(ents, T_KLINGON, KLINGON_HP);
            if (slot >= 0) {
                int b = slot * E_STRIDE;
                ents[b + E_AUX] = Random.nextInt(100) < 25 ? 1 : 0;
                ents[b + E_COOL] = Random.nextInt(40, 100);
                ents[b + E_HEADING] = Random.nextInt(360);
                ents[b + E_TIMER] = 0;
            }
        }
        if (sector >= 2) {
            for (int i = 0; i < Math.min(sector / 2, 4); i++) {
                Entities.spawnAway(ents, T_SAUCER, 1);
            }
        }
    }

    static void announce() {
        Hud.showCentered("SECTOR", 84, 3, TftTouchShield.YELLOW);
        TftTouchShield.setCursor(Session.sector < 10 ? 151 : 142, 114);
        TftTouchShield.print(Session.sector);
        if (Session.sector % 4 == 0) {
            Hud.showCentered("Nomad is laying mines", 150, 1, SceneRenderer.NOMAD);
        } else {
            Hud.showCentered("Destroy the Klingons", 150, 1, TftTouchShield.WHITE);
        }
    }

    static boolean isCleared(int[] ents) {
        return Entities.count(ents, T_KLINGON) + Entities.count(ents, T_NOMAD) == 0;
    }

    /** Awards the bonus for the sector and the shields left, then warps to the next sector. */
    static void finish() {
        int bonus = SECTOR_BONUS + Enterprise.shields * 50;
        Session.score = Session.score + bonus;
        Interludes.sectorCleared(bonus);
    }
}
