package io.github.jabrena.juno.games.gladiator.screen;

import io.github.jabrena.juno.games.gladiator.view.Hud;
import io.github.jabrena.juno.games.gladiator.view.View;
import io.github.jabrena.juno.games.gladiator.view.ViewList;
import io.github.jabrena.juno.thirdparty.tft.TftTouchShield;
import io.github.jabrena.juno.util.Delay;

/** Selects a local duel or the original multi-board network game. */
public final class ModeMenu {
    public static final int SOLO = 0;
    public static final int MULTIPLAYER = 1;

    private static final int BUTTON_X = 20;
    private static final int BUTTON_WIDTH = 280;
    private static final int BUTTON_HEIGHT = 55;
    private static final int FIRST_BUTTON_Y = 76;
    private static final int BUTTON_GAP = 14;

    private ModeMenu() {
    }

    public static int choose(boolean soloAvailable) {
        draw(soloAvailable);
        while (true) {
            if (!TftTouchShield.readTouch()) {
                Delay.millis(10);
                continue;
            }
            int mode = modeAt(TftTouchShield.touchX(), TftTouchShield.touchY(), soloAvailable);
            waitForRelease();
            if (mode >= 0) {
                return mode;
            }
        }
    }

    public static int modeAt(int x, int y, boolean soloAvailable) {
        if (x < BUTTON_X || x >= BUTTON_X + BUTTON_WIDTH) {
            return -1;
        }
        if (y >= FIRST_BUTTON_Y && y < FIRST_BUTTON_Y + BUTTON_HEIGHT) {
            return soloAvailable ? SOLO : -1;
        }
        int multiplayerY = FIRST_BUTTON_Y + BUTTON_HEIGHT + BUTTON_GAP;
        return y >= multiplayerY && y < multiplayerY + BUTTON_HEIGHT ? MULTIPLAYER : -1;
    }

    private static void draw(boolean soloAvailable) {
        TftTouchShield.fillScreen(ViewList.BACKGROUND);
        Hud.centered("CHOOSE GAME", 16, 3, View.WALL, ViewList.BACKGROUND);
        button(FIRST_BUTTON_Y, "SOLO", soloAvailable ? "ONE BOARD - YOU VS CPU" : "UNO Q ONLY",
                soloAvailable ? TftTouchShield.YELLOW : TftTouchShield.GRAY);
        button(FIRST_BUTTON_Y + BUTTON_HEIGHT + BUTTON_GAP, "MULTIPLAYER", "ARENA + TWO GLADIATOR BOARDS",
                TftTouchShield.CYAN);
    }

    private static void button(int y, String label, String detail, int color) {
        TftTouchShield.fillRect(BUTTON_X, y, BUTTON_WIDTH, BUTTON_HEIGHT, Hud.PANEL);
        TftTouchShield.fillRect(BUTTON_X, y, 6, BUTTON_HEIGHT, color);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(color, Hud.PANEL);
        TftTouchShield.setCursor(BUTTON_X + 16, y + 10);
        TftTouchShield.print(label);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, Hud.PANEL);
        TftTouchShield.setCursor(BUTTON_X + 16, y + 36);
        TftTouchShield.print(detail);
    }

    private static void waitForRelease() {
        int misses = 0;
        while (misses < 3) {
            if (TftTouchShield.readTouch()) {
                misses = 0;
            } else {
                misses = misses + 1;
            }
            Delay.millis(10);
        }
    }
}
