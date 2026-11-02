package io.github.jabrena.juno.games.missilecommand;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.util.Delay;

/** The cover, wave title and results cards, and the game-over screen. */
final class Interludes {
    private static final int COVER_STEPS = 10;
    private static final int COVER_STEP_MILLIS = 65;
    private static final int COVER_BLAST_MILLIS = 55;
    private static final int TITLE_MILLIS = 90;
    private static final int TITLE_X = 8;
    private static final int TITLE_Y = 30;
    private static final int TITLE_WIDTH = 224;
    private static final int TITLE_HEIGHT = 38;

    private Interludes() {
    }

    /**
     * A self-contained cover: two warheads and an interceptor streak across the sky, three blasts
     * bloom in sequence, and the title flies forward into a warm drop shadow.
     */
    static void cover(TftTouchShield tft, boolean[] alive, int[] ammo) {
        tft.fillScreen(SceneRenderer.SKY);
        for (int target = 0; target < Session.TARGETS; target++) {
            alive[target] = true;
        }
        for (int base = 0; base < 3; base++) {
            ammo[base] = Session.BASE_AMMO;
        }
        SceneRenderer.drawGround(tft, alive, ammo);
        animateStrike(tft);
        zoomTitle(tft);
        Hud.showCentered(tft, "TAP TO DEFEND", 74, 1, TftTouchShield.CYAN);
        Hud.showCentered(tft, "Tap the sky to fire", 88, 1, TftTouchShield.WHITE);
    }

    /** Extends only each trail's new segment per frame, keeping the bit-banged animation light. */
    private static void animateStrike(TftTouchShield tft) {
        int firstX = 70;
        int firstY = Session.HEADER + 10;
        int secondX = 178;
        int secondY = Session.HEADER + 34;
        int shotX = 18;
        int shotY = Session.GROUND_Y - 14;
        for (int step = 1; step <= COVER_STEPS; step++) {
            int nextFirstX = 70 + (58 - 70) * step / COVER_STEPS;
            int nextFirstY = Session.HEADER + 10 + (Session.GROUND_Y - 6 - Session.HEADER - 10) * step
                    / COVER_STEPS;
            int nextSecondX = 178 + (158 - 178) * step / COVER_STEPS;
            int nextSecondY = Session.HEADER + 34 + (Session.GROUND_Y - 13 - Session.HEADER - 34) * step
                    / COVER_STEPS;
            SceneRenderer.drawFullLine(tft, firstX, firstY, nextFirstX, nextFirstY, SceneRenderer.ENEMY_TRAIL);
            SceneRenderer.drawFullLine(tft, secondX, secondY, nextSecondX, nextSecondY, SceneRenderer.ENEMY_TRAIL);
            firstX = nextFirstX;
            firstY = nextFirstY;
            secondX = nextSecondX;
            secondY = nextSecondY;
            if (step >= 3) {
                int shotStep = step - 2;
                int nextShotX = 18 + (100 - 18) * shotStep / 8;
                int nextShotY = Session.GROUND_Y - 14 + (240 - Session.GROUND_Y + 14) * shotStep / 8;
                SceneRenderer.drawFullLine(tft, shotX, shotY, nextShotX, nextShotY, SceneRenderer.SHOT_TRAIL);
                shotX = nextShotX;
                shotY = nextShotY;
            }
            Delay.millis(COVER_STEP_MILLIS);
        }
        for (int radius = 3; radius <= Session.BLAST_RADIUS; radius = radius + 3) {
            tft.fillCircle(58, Session.GROUND_Y - 6, radius, coverBlastColor(radius));
            tft.fillCircle(158, Session.GROUND_Y - 13, radius, coverBlastColor(radius + 3));
            tft.fillCircle(100, 240, radius, coverBlastColor(radius + 6));
            Delay.millis(COVER_BLAST_MILLIS);
        }
    }

    private static int coverBlastColor(int phase) {
        int color = (phase / 3) % 4;
        if (color == 0) {
            return TftTouchShield.WHITE;
        }
        if (color == 1) {
            return TftTouchShield.YELLOW;
        }
        if (color == 2) {
            return TftTouchShield.ORANGE;
        }
        return TftTouchShield.MAGENTA;
    }

    /** The logo grows out of the strike, flashes white, then settles over an orange shadow. */
    private static void zoomTitle(TftTouchShield tft) {
        titleFrame(tft, 1, 51, TftTouchShield.CYAN);
        titleFrame(tft, 2, 42, TftTouchShield.YELLOW);
        titleFrame(tft, 2, 40, TftTouchShield.WHITE);
        tft.fillRect(TITLE_X, TITLE_Y, TITLE_WIDTH, TITLE_HEIGHT, SceneRenderer.SKY);
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.ORANGE, SceneRenderer.SKY);
        tft.setCursor(32, 42);
        tft.print("MISSILE COMMAND");
        Hud.showCentered(tft, "MISSILE COMMAND", 40, 2, TftTouchShield.RED);
        Delay.millis(TITLE_MILLIS);
    }

    private static void titleFrame(TftTouchShield tft, int size, int y, int color) {
        tft.fillRect(TITLE_X, TITLE_Y, TITLE_WIDTH, TITLE_HEIGHT, SceneRenderer.SKY);
        Hud.showCentered(tft, "MISSILE COMMAND", y, size, color);
        Delay.millis(TITLE_MILLIS);
    }

    /** The wave's title card: its number and the score multiplier it carries. */
    static void waveCard(TftTouchShield tft, int wave) {
        Hud.showCenteredValue(tft, "WAVE ", wave, 120, 2, TftTouchShield.YELLOW);
        Hud.showCenteredValue(tft, "MULTIPLIER x", Session.multiplier(), 148, 1, TftTouchShield.WHITE);
        Delay.millis(1200);
        tft.fillRect(0, 110, Session.WIDTH, 50, SceneRenderer.SKY);
    }

    /** The results card: unused ammo and surviving cities, each worth the wave's multiplier. */
    static void waveCleared(TftTouchShield tft, boolean[] alive, int[] ammo) {
        Delay.millis(300);
        int ammoBonus = Session.ammoBonus(ammo);
        int cityBonus = Session.cityBonus(alive);
        Hud.showCentered(tft, "WAVE CLEARED", 100, 2, TftTouchShield.YELLOW);
        Hud.showCenteredValue(tft, "AMMO BONUS ", ammoBonus, 130, 1, SceneRenderer.SHOT_TRAIL);
        Hud.showCenteredValue(tft, "CITY BONUS ", cityBonus, 150, 1, SceneRenderer.CITY_COLOR);
        Session.addScore(tft, ammoBonus + cityBonus, alive);
        Hud.drawHeader(tft);
        Delay.millis(1800);
        tft.fillRect(0, 90, Session.WIDTH, 80, SceneRenderer.SKY);
    }

    static void gameOver(TftTouchShield tft) {
        Hud.drawHeader(tft);
        Hud.showCentered(tft, "THE END", 120, 3, TftTouchShield.RED);
        Hud.showCenteredValue(tft, "FINAL SCORE ", Session.score, 160, 1, TftTouchShield.WHITE);
        Hud.showCentered(tft, "TAP TO CONTINUE", 184, 1, TftTouchShield.CYAN);
        Delay.millis(1500);
    }
}
