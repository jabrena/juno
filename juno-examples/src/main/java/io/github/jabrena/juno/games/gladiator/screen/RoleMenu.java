package io.github.jabrena.juno.games.gladiator.screen;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;
import io.github.jabrena.juno.games.gladiator.gladiator.Brain;
import io.github.jabrena.juno.games.gladiator.view.Figure;
import io.github.jabrena.juno.games.gladiator.view.Hud;
import io.github.jabrena.juno.games.gladiator.view.View;
import io.github.jabrena.juno.games.gladiator.view.ViewList;

/**
 * The network role menu, shown on MULTIPLAYER and on every return from a running role: the ARENA, PLAYER 1, an
 * AGGRESSIVE Secutor, PLAYER 2, an AMBUSH Retiarius, or RETURN to the game menu.
 */
public final class RoleMenu {
    public static final int ARENA = 0;
    public static final int PLAYER_1 = Brain.AGGRESSIVE;
    public static final int PLAYER_2 = Brain.AMBUSH;
    public static final int RETURN = 3;

    private static final int BUTTON_X = 20;
    private static final int BUTTON_WIDTH = 280;
    private static final int BUTTON_HEIGHT = 40;
    private static final int FIRST_BUTTON_Y = 58;
    private static final int BUTTON_GAP = 6;
    private static final int RETURN_X = 100;
    private static final int RETURN_Y = 200;
    private static final int RETURN_WIDTH = 120;
    private static final int RETURN_HEIGHT = 32;

    private RoleMenu() {
    }

    public static int choose() {
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

    /** The role whose button is at ({@code x}, {@code y}), {@link #RETURN}, or -1. */
    public static int roleAt(int x, int y) {
        if (x >= RETURN_X && x < RETURN_X + RETURN_WIDTH && y >= RETURN_Y && y < RETURN_Y + RETURN_HEIGHT) {
            return RETURN;
        }
        if (x < BUTTON_X || x >= BUTTON_X + BUTTON_WIDTH) {
            return -1;
        }
        for (int role = ARENA; role <= PLAYER_2; role++) {
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
        Hud.centered("One arena, two players, every board its role", 42, 1, TftTouchShield.GRAY,
                ViewList.BACKGROUND);
        button(ARENA, "ARENA", "Keeps the battle, map from above (UNO Q)", TftTouchShield.YELLOW);
        button(PLAYER_1, "PLAYER 1", "Secutor: charges and presses the attack", Figure.AGGRESSIVE_COLOR);
        button(PLAYER_2, "PLAYER 2", "Retiarius: nets and strikes from cover", Figure.AMBUSH_COLOR);
        TftTouchShield.fillRect(RETURN_X, RETURN_Y, RETURN_WIDTH, RETURN_HEIGHT, Hud.PANEL);
        TftTouchShield.drawRect(RETURN_X, RETURN_Y, RETURN_WIDTH, RETURN_HEIGHT, TftTouchShield.WHITE);
        Hud.centered("RETURN", RETURN_Y + 9, 2, TftTouchShield.WHITE, Hud.PANEL);
    }

    private static void button(int role, String label, String detail, int color) {
        int y = FIRST_BUTTON_Y + role * (BUTTON_HEIGHT + BUTTON_GAP);
        TftTouchShield.fillRect(BUTTON_X, y, BUTTON_WIDTH, BUTTON_HEIGHT, Hud.PANEL);
        TftTouchShield.fillRect(BUTTON_X, y, 6, BUTTON_HEIGHT, color);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(color, Hud.PANEL);
        TftTouchShield.setCursor(BUTTON_X + 16, y + 5);
        TftTouchShield.print(label);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, Hud.PANEL);
        TftTouchShield.setCursor(BUTTON_X + 16, y + 27);
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
