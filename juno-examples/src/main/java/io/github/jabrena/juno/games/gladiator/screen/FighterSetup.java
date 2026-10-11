package io.github.jabrena.juno.games.gladiator.screen;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;
import io.github.jabrena.juno.games.gladiator.arena.GladiatorClass;
import io.github.jabrena.juno.games.gladiator.view.Hud;
import io.github.jabrena.juno.games.gladiator.view.View;
import io.github.jabrena.juno.games.gladiator.view.ViewList;

/** Two touch pages that choose a historical name and a gladiator class, which decides the fighting style. */
public final class FighterSetup {
    public static final int NAME = 0;
    public static final int CLASS = 1;

    private static final int BUTTON_WIDTH = 142;
    private static final int BUTTON_HEIGHT = 62;
    private static final int LEFT_X = 12;
    private static final int RIGHT_X = 166;
    private static final int TOP_Y = 70;
    private static final int BOTTOM_Y = 142;

    private FighterSetup() {
    }

    public static FighterProfile choose() {
        int name = choose(NAME);
        return new FighterProfile(name, choose(CLASS));
    }

    public static int choiceAt(int page, int x, int y) {
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
        Hud.centered(page == NAME ? "CHOOSE YOUR NAME" : "CHOOSE YOUR CLASS", 15, 2, View.WALL, ViewList.BACKGROUND);
        if (page == NAME) {
            for (int choice = 0; choice < FighterProfile.NAMES; choice++) {
                button(choice, FighterProfile.name(choice), FighterProfile.history(choice), TftTouchShield.YELLOW);
            }
        } else {
            classButton(GladiatorClass.MURMILLO, "GLADIUS + SCUTUM");
            classButton(GladiatorClass.THRAEX, "SICA + PARMULA");
            classButton(GladiatorClass.RETIARIUS, "TRIDENT + NET");
            classButton(GladiatorClass.SECUTOR, "GLADIUS + SCUTUM");
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
