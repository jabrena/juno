package io.github.jabrena.juno.games.doom.engine;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.util.Delay;

/**
 * A full-screen list to tap, as DOOM's own menus: a red title, a question, then one row per choice with a name and
 * a line about it. A row can be offered greyed out, which no tap chooses (an episode the WAD does not hold).
 */
final class Menu {
    static final int LEFT = 20;
    static final int WIDTH = 280;
    private static final int GAP = 4;
    private static final int DISABLED = 0x18C3;

    private Menu() {
    }

    static void open(TftTouchShield tft, String title, String question) {
        tft.fillScreen(DisplayList.BACKGROUND);
        Hud.showCentered(tft, title, 14, 3, TftTouchShield.RED);
        Hud.showCentered(tft, question, 46, 1, TftTouchShield.WHITE);
    }

    static void row(TftTouchShield tft, int index, int top, int height, String name, String detail, boolean enabled, boolean chosen) {
        int y = top + index * (height + GAP);
        int color = !enabled ? DISABLED : chosen ? Controls.CHOICE_CHOSEN : Controls.CHOICE;
        tft.fillRect(LEFT, y, WIDTH, height, color);
        tft.drawRect(LEFT, y, WIDTH, height, chosen ? TftTouchShield.WHITE : Renderer.WALL_FAR);
        tft.setTextSize(2);
        tft.setTextColor(enabled ? TftTouchShield.WHITE : Renderer.WALL_FAR, color);
        tft.setCursor(LEFT + (WIDTH - name.length() * 12) / 2, y + 3);
        tft.print(name);
        tft.setTextSize(1);
        tft.setTextColor(enabled ? TftTouchShield.YELLOW : Renderer.WALL_FAR, color);
        tft.setCursor(LEFT + (WIDTH - detail.length() * 6) / 2, y + height - 10);
        tft.print(detail);
    }

    /** The row under ({@code x}, {@code y}), or -1. */
    static int rowAt(int x, int y, int top, int height, int count) {
        if (x < LEFT || x >= LEFT + WIDTH || y < top) {
            return -1;
        }
        int index = (y - top) / (height + GAP);
        int within = (y - top) % (height + GAP);
        return index < count && within < height ? index : -1;
    }

    /** Waits until an enabled row (a set bit of {@code enabled}) is tapped, and returns it. */
    static int choose(TftTouchShield tft, int top, int height, int count, int enabled) {
        int chosen = -1;
        while (chosen < 0) {
            if (tft.readTouch()) {
                int row = rowAt(tft.touchX(), tft.touchY(), top, height, count);
                chosen = row >= 0 && (enabled >> row & 1) != 0 ? row : -1;
            }
            Delay.millis(10);
        }
        return chosen;
    }
}
