package io.github.jabrena.juno.games.gladiator;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/** Three touch pages that choose a historical name, gladiator class and fighting style. */
final class FighterSetup {
    static final int NAME = 0;
    static final int CLASS = 1;
    static final int STYLE = 2;

    private static final int BUTTON_WIDTH = 142;
    private static final int BUTTON_HEIGHT = 62;
    private static final int LEFT_X = 12;
    private static final int RIGHT_X = 166;
    private static final int TOP_Y = 70;
    private static final int BOTTOM_Y = 142;

    private FighterSetup() {
    }

    static FighterProfile choose() {
        int name = choose(NAME);
        int kind = choose(CLASS);
        int behaviour = choose(STYLE) == 0 ? Brain.AGGRESSIVE : Brain.AMBUSH;
        return new FighterProfile(name, kind, behaviour);
    }

    static int choiceAt(int page, int x, int y) {
        if (page == STYLE) {
            if (x < LEFT_X || x >= ViewList.WIDTH - LEFT_X) {
                return -1;
            }
            if (y >= TOP_Y && y < TOP_Y + BUTTON_HEIGHT) {
                return 0;
            }
            return y >= BOTTOM_Y && y < BOTTOM_Y + BUTTON_HEIGHT ? 1 : -1;
        }
        int column = x >= LEFT_X && x < LEFT_X + BUTTON_WIDTH ? 0
                : x >= RIGHT_X && x < RIGHT_X + BUTTON_WIDTH ? 1 : -1;
        int row = y >= TOP_Y && y < TOP_Y + BUTTON_HEIGHT ? 0
                : y >= BOTTOM_Y && y < BOTTOM_Y + BUTTON_HEIGHT ? 1 : -1;
        return column < 0 || row < 0 ? -1 : row * 2 + column;
    }

    private static int choose(int page) {
        draw(page);
        while (true) {
            if (!TftTouchShield.readTouch()) {
                Delay.millis(10);
                continue;
            }
            int choice = choiceAt(page, TftTouchShield.touchX(), TftTouchShield.touchY());
            waitForRelease();
            if (choice >= 0) {
                return choice;
            }
        }
    }

    private static void draw(int page) {
        TftTouchShield.fillScreen(ViewList.BACKGROUND);
        Hud.centered(page == NAME ? "CHOOSE YOUR NAME" : page == CLASS ? "CHOOSE YOUR CLASS" : "FIGHTING STYLE",
                15, 2, View.WALL, ViewList.BACKGROUND);
        if (page == NAME) {
            for (int choice = 0; choice < FighterProfile.NAMES; choice++) {
                button(choice, FighterProfile.name(choice), FighterProfile.history(choice), TftTouchShield.YELLOW);
            }
        } else if (page == CLASS) {
            classButton(Opening.MURMILLO, "GLADIUS + SCUTUM");
            classButton(Opening.THRAEX, "SICA + PARMULA");
            classButton(Opening.RETIARIUS, "TRIDENT + NET");
            classButton(Opening.SECUTOR, "GLADIUS + SCUTUM");
        } else {
            wideButton(0, "AGGRESSIVE", "CHARGE AND PRESS THE ATTACK", Figure.AGGRESSIVE_COLOR);
            wideButton(1, "AMBUSH", "HIDE, LURE AND STRIKE UNSEEN", Figure.AMBUSH_COLOR);
        }
    }

    private static void classButton(int kind, String arms) {
        button(kind, Opening.className(kind), arms, Opening.classColor(kind));
    }

    private static void button(int choice, String label, String detail, int color) {
        int x = (choice & 1) == 0 ? LEFT_X : RIGHT_X;
        int y = choice < 2 ? TOP_Y : BOTTOM_Y;
        drawButton(x, y, BUTTON_WIDTH, label, detail, color);
    }

    private static void wideButton(int choice, String label, String detail, int color) {
        drawButton(LEFT_X, choice == 0 ? TOP_Y : BOTTOM_Y, ViewList.WIDTH - 2 * LEFT_X, label, detail, color);
    }

    private static void drawButton(int x, int y, int width, String label, String detail, int color) {
        TftTouchShield.fillRect(x, y, width, BUTTON_HEIGHT, Hud.PANEL);
        TftTouchShield.fillRect(x, y, 5, BUTTON_HEIGHT, color);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(color, Hud.PANEL);
        TftTouchShield.setCursor(x + 12, y + 10);
        TftTouchShield.print(label);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, Hud.PANEL);
        TftTouchShield.setCursor(x + 12, y + 39);
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
