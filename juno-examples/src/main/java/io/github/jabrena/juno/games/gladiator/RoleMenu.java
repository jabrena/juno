package io.github.jabrena.juno.games.gladiator;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/** The network role menu shared by the startup flow and every return from a running role. */
final class RoleMenu {
    static final int ARENA = 0;

    private static final int BUTTON_X = 20;
    private static final int BUTTON_WIDTH = 280;
    private static final int BUTTON_HEIGHT = 50;
    private static final int FIRST_BUTTON_Y = 64;
    private static final int BUTTON_GAP = 8;

    private RoleMenu() {
    }

    static int choose() {
        draw();
        while (true) {
            if (!TftTouchShield.readTouch()) {
                Delay.millis(10);
                continue;
            }
            int role = roleAt(TftTouchShield.touchX(), TftTouchShield.touchY());
            waitForRelease();
            if (role >= 0) {
                return role;
            }
        }
    }

    /** The role whose button is at ({@code x}, {@code y}), or -1. */
    static int roleAt(int x, int y) {
        if (x < BUTTON_X || x >= BUTTON_X + BUTTON_WIDTH) {
            return -1;
        }
        for (int role = ARENA; role <= Brain.AMBUSH; role++) {
            int top = FIRST_BUTTON_Y + role * (BUTTON_HEIGHT + BUTTON_GAP);
            if (y >= top && y < top + BUTTON_HEIGHT) {
                return role;
            }
        }
        return -1;
    }

    private static void draw() {
        TftTouchShield.fillScreen(ViewList.BACKGROUND);
        Hud.centered("GLADIATOR", 10, 3, View.WALL, ViewList.BACKGROUND);
        Hud.centered("One arena, two gladiators, every board its role", 44, 1, TftTouchShield.GRAY,
                ViewList.BACKGROUND);
        button(ARENA, "ARENA", "Keeps the battle, map from above (UNO Q)", TftTouchShield.YELLOW);
        button(Brain.AGGRESSIVE, "AGGRESSIVE", "Charges and presses the attack", Figure.AGGRESSIVE_COLOR);
        button(Brain.AMBUSH, "AMBUSH", "Lurks behind pillars, strikes unseen", Figure.AMBUSH_COLOR);
    }

    private static void button(int role, String label, String detail, int color) {
        int y = FIRST_BUTTON_Y + role * (BUTTON_HEIGHT + BUTTON_GAP);
        TftTouchShield.fillRect(BUTTON_X, y, BUTTON_WIDTH, BUTTON_HEIGHT, Hud.PANEL);
        TftTouchShield.fillRect(BUTTON_X, y, 6, BUTTON_HEIGHT, color);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(color, Hud.PANEL);
        TftTouchShield.setCursor(BUTTON_X + 16, y + 9);
        TftTouchShield.print(label);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, Hud.PANEL);
        TftTouchShield.setCursor(BUTTON_X + 16, y + 33);
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
