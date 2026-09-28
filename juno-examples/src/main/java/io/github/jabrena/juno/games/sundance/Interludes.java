package io.github.jabrena.juno.games.sundance;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/** Cover, round cards, completion messages, and game-over presentation. */
final class Interludes {
    private Interludes() {
    }

    /** Draws a self-contained initial cover before the pilot choice. */
    static void cover(short[] lines, int[] suns, int[] hatches) {
        TftTouchShield.fillScreen(Hud.SPACE);
        DisplayList.front = 0;
        DisplayList.shown = 0;
        Session.round = 1;
        Session.frame = 0;
        Session.clear(suns, Session.SUNS * Session.S_STRIDE);
        Session.clear(hatches, Grid.CELLS);
        hatches[4] = 12;
        Session.placeSun(suns, 0, 0, 1, 1, 30, 0);
        Session.placeSun(suns, 1, 2, 2, 1, 36, 1);
        Session.placeSun(suns, 2, 1, 4, 0, 34, 2);
        SceneRenderer.render(lines, suns, hatches);
        Hud.showCentered("SUNDANCE", 96, 4, TftTouchShield.YELLOW);
        TftTouchShield.fillRect(0, 143, Hud.WIDTH, 28, Hud.SPACE);
        Hud.showCentered("A VECTOR HATCH GAME", 144, 1, TftTouchShield.CYAN);
        Hud.showCentered("Tap to choose a pilot", 160, 1, TftTouchShield.WHITE);
        TftTouchShield.fillRect(0, 0, Hud.WIDTH, Hud.HEADER, Hud.SPACE);
        Hud.showCentered("Trap the suns before they collide", 4, 1, TftTouchShield.CYAN);
        Session.clear(suns, Session.SUNS * Session.S_STRIDE);
        Session.clear(hatches, Grid.CELLS);
    }

    static void round(int round, int quota) {
        Hud.clearView();
        Hud.showCentered("ROUND", 84, 3, TftTouchShield.YELLOW);
        TftTouchShield.setCursor(round < 10 ? 151 : 142, 114);
        TftTouchShield.print(round);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, Hud.SPACE);
        TftTouchShield.setCursor(106, 150);
        TftTouchShield.print("Trap ");
        TftTouchShield.print(quota);
        TftTouchShield.print(" suns");
        Delay.millis(1500);
        Hud.clearView();
    }

    static void roundCleared(int bonus) {
        Hud.clearView();
        Hud.showCentered("ROUND CLEARED", 90, 2, TftTouchShield.YELLOW);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, Hud.SPACE);
        TftTouchShield.setCursor(118, 122);
        TftTouchShield.print("TIME BONUS ");
        TftTouchShield.print(bonus);
        Delay.millis(1800);
    }

    static void outOfTime() {
        Hud.clearView();
        Hud.showCentered("OUT OF TIME", 100, 3, TftTouchShield.RED);
        Delay.millis(1500);
    }

    static void gameOver(int[] suns) {
        Hud.drawHeader(suns);
        Hud.clearView();
        Hud.showCentered("GAME OVER", 90, 3, TftTouchShield.RED);
        Hud.showCentered("Tap for the cover", 136, 1, TftTouchShield.WHITE);
        Delay.millis(1500);
        Controls.waitForTap();
    }
}
