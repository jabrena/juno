package io.github.jabrena.juno.games.doom;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;

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

    static void open(String title, String question) {
        TftTouchShield.fillScreen(DisplayList.BACKGROUND);
        Hud.showCentered(title, 14, 3, TftTouchShield.RED);
        Hud.showCentered(question, 46, 1, TftTouchShield.WHITE);
    }

    static void row(int index, int top, int height, String name, String detail, boolean enabled, boolean chosen) {
        int y = top + index * (height + GAP);
        int color = !enabled ? DISABLED : chosen ? Controls.CHOICE_CHOSEN : Controls.CHOICE;
        TftTouchShield.fillRect(LEFT, y, WIDTH, height, color);
        TftTouchShield.drawRect(LEFT, y, WIDTH, height, chosen ? TftTouchShield.WHITE : Renderer.WALL_FAR);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(enabled ? TftTouchShield.WHITE : Renderer.WALL_FAR, color);
        TftTouchShield.setCursor(LEFT + (WIDTH - name.length() * 12) / 2, y + 3);
        TftTouchShield.print(name);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(enabled ? TftTouchShield.YELLOW : Renderer.WALL_FAR, color);
        TftTouchShield.setCursor(LEFT + (WIDTH - detail.length() * 6) / 2, y + height - 10);
        TftTouchShield.print(detail);
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
    static int choose(int top, int height, int count, int enabled) {
        int chosen = -1;
        while (chosen < 0) {
            if (TftTouchShield.readTouch()) {
                int row = rowAt(TftTouchShield.touchX(), TftTouchShield.touchY(), top, height, count);
                chosen = row >= 0 && (enabled >> row & 1) != 0 ? row : -1;
            }
            Delay.millis(10);
        }
        return chosen;
    }
}
