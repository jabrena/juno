package io.github.jabrena.juno.games.doom;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * The episode menu, shown after the pilot is chosen when the map comes from {@code DOOM1.WAD}. DOOM has four
 * episodes, but the shareware WAD holds only the first, and only its first map fits the board's RAM so far: so the
 * menu offers Knee-Deep in the Dead, starting at E1M1.
 */
final class Episodes {
    private static final int BOX_X = 20;
    private static final int BOX_Y = 104;
    private static final int BOX_WIDTH = 280;
    private static final int BOX_HEIGHT = 72;

    private Episodes() {
    }

    /** Shows the menu and waits until an episode is tapped. */
    static void choose() {
        TftTouchShield.fillScreen(DisplayList.BACKGROUND);
        Hud.showCentered("CHOOSE EPISODE", 36, 3, TftTouchShield.RED);
        Hud.showCentered("Which episode do you play?", 74, 1, TftTouchShield.WHITE);
        drawEpisode(false);
        Hud.showCentered("Episodes 2-4 are not in DOOM1.WAD", 206, 1, Renderer.WALL_FAR);
        boolean chosen = false;
        while (!chosen) {
            if (TftTouchShield.readTouch()) {
                chosen = inside(TftTouchShield.touchX(), TftTouchShield.touchY());
            }
            Delay.millis(10);
        }
        drawEpisode(true);
        Controls.waitForRelease();
        Interludes.flood();
    }

    /** Whether ({@code x}, {@code y}) is on the episode's box. */
    static boolean inside(int x, int y) {
        return x >= BOX_X && x < BOX_X + BOX_WIDTH && y >= BOX_Y && y < BOX_Y + BOX_HEIGHT;
    }

    private static void drawEpisode(boolean chosen) {
        int color = chosen ? Controls.CHOICE_CHOSEN : Controls.CHOICE;
        TftTouchShield.fillRect(BOX_X, BOX_Y, BOX_WIDTH, BOX_HEIGHT, color);
        TftTouchShield.drawRect(BOX_X, BOX_Y, BOX_WIDTH, BOX_HEIGHT, chosen ? TftTouchShield.WHITE : Renderer.WALL_FAR);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, color);
        TftTouchShield.setCursor(BOX_X + (BOX_WIDTH - 21 * 12) / 2, BOX_Y + 18);
        TftTouchShield.print("KNEE-DEEP IN THE DEAD");
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.YELLOW, color);
        TftTouchShield.setCursor(BOX_X + (BOX_WIDTH - 22 * 6) / 2, BOX_Y + 50);
        TftTouchShield.print("Episode 1, starts E1M1");
    }
}
