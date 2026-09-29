package io.github.jabrena.juno.games.missilecommand;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/** The cover, wave title and results cards, and the game-over screen. */
final class Interludes {
    private Interludes() {
    }

    /**
     * A self-contained cover: the ground and cities stand behind a still tableau of a strike already
     * under way — two warheads down, one interceptor going up — then the title.
     */
    static void cover(boolean[] alive, int[] ammo) {
        TftTouchShield.fillScreen(SceneRenderer.SKY);
        for (int target = 0; target < Session.TARGETS; target++) {
            alive[target] = true;
        }
        for (int base = 0; base < 3; base++) {
            ammo[base] = Session.BASE_AMMO;
        }
        SceneRenderer.drawGround(alive, ammo);
        SceneRenderer.drawFullLine(70, Session.HEADER + 10, 58, Session.GROUND_Y - 6, SceneRenderer.ENEMY_TRAIL);
        TftTouchShield.fillCircle(58, Session.GROUND_Y - 6, Session.BLAST_RADIUS, TftTouchShield.YELLOW);
        SceneRenderer.drawFullLine(178, Session.HEADER + 34, 158, Session.GROUND_Y - 13, SceneRenderer.ENEMY_TRAIL);
        TftTouchShield.fillCircle(158, Session.GROUND_Y - 13, Session.BLAST_RADIUS, TftTouchShield.ORANGE);
        SceneRenderer.drawFullLine(18, Session.GROUND_Y - 14, 100, Session.GROUND_Y - 60, SceneRenderer.SHOT_TRAIL);
        Hud.showCentered("MISSILE COMMAND", 40, 2, TftTouchShield.RED);
        Hud.showCentered("Tap the sky to fire", 62, 1, TftTouchShield.WHITE);
    }

    /** The wave's title card: its number and the score multiplier it carries. */
    static void waveCard(int wave) {
        Hud.showCenteredValue("WAVE ", wave, 120, 2, TftTouchShield.YELLOW);
        Hud.showCenteredValue("MULTIPLIER x", Session.multiplier(), 148, 1, TftTouchShield.WHITE);
        Delay.millis(1200);
        TftTouchShield.fillRect(0, 110, Session.WIDTH, 50, SceneRenderer.SKY);
    }

    /** The results card: unused ammo and surviving cities, each worth the wave's multiplier. */
    static void waveCleared(boolean[] alive, int[] ammo) {
        Delay.millis(300);
        int ammoBonus = Session.ammoBonus(ammo);
        int cityBonus = Session.cityBonus(alive);
        Hud.showCentered("WAVE CLEARED", 100, 2, TftTouchShield.YELLOW);
        Hud.showCenteredValue("AMMO BONUS ", ammoBonus, 130, 1, SceneRenderer.SHOT_TRAIL);
        Hud.showCenteredValue("CITY BONUS ", cityBonus, 150, 1, SceneRenderer.CITY_COLOR);
        Session.addScore(ammoBonus + cityBonus, alive);
        Hud.drawHeader();
        Delay.millis(1800);
        TftTouchShield.fillRect(0, 90, Session.WIDTH, 80, SceneRenderer.SKY);
    }

    static void gameOver() {
        Hud.drawHeader();
        Hud.showCentered("THE END", 120, 3, TftTouchShield.RED);
        Hud.showCenteredValue("FINAL SCORE ", Session.score, 160, 1, TftTouchShield.WHITE);
        Delay.millis(1500);
    }
}
