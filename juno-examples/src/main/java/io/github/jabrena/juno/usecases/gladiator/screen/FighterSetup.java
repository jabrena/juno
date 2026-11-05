package io.github.jabrena.juno.usecases.gladiator.screen;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.usecases.gladiator.arena.GladiatorClass;
import io.github.jabrena.juno.usecases.gladiator.view.Hud;
import io.github.jabrena.juno.usecases.gladiator.view.View;
import io.github.jabrena.juno.usecases.gladiator.view.ViewList;
import io.github.jabrena.juno.util.Delay;

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

    public static FighterProfile choose(TftTouchShield tft) {
        int name = choose(tft, NAME);
        return new FighterProfile(name, choose(tft, CLASS));
    }

    public static int choiceAt(int page, int x, int y) {
        int column = x >= LEFT_X && x < LEFT_X + BUTTON_WIDTH ? 0
                : x >= RIGHT_X && x < RIGHT_X + BUTTON_WIDTH ? 1 : -1;
        int row = y >= TOP_Y && y < TOP_Y + BUTTON_HEIGHT ? 0
                : y >= BOTTOM_Y && y < BOTTOM_Y + BUTTON_HEIGHT ? 1 : -1;
        return column < 0 || row < 0 ? -1 : row * 2 + column;
    }

    private static int choose(TftTouchShield tft, int page) {
        draw(tft, page);
        while (true) {
            if (!tft.readTouch()) {
                Delay.millis(10);
                continue;
            }
            int choice = choiceAt(page, tft.touchX(), tft.touchY());
            waitForRelease(tft);
            if (choice >= 0) {
                return choice;
            }
        }
    }

    private static void draw(TftTouchShield tft, int page) {
        tft.fillScreen(ViewList.BACKGROUND);
        Hud.centered(tft, page == NAME ? "CHOOSE YOUR NAME" : "CHOOSE YOUR CLASS", 15, 2, View.WALL, ViewList.BACKGROUND);
        if (page == NAME) {
            for (int choice = 0; choice < FighterProfile.NAMES; choice++) {
                button(tft, choice, FighterProfile.name(choice), FighterProfile.history(choice), TftTouchShield.YELLOW);
            }
        } else {
            classButton(tft, GladiatorClass.MURMILLO, "GLADIUS + SCUTUM");
            classButton(tft, GladiatorClass.THRAEX, "SICA + PARMULA");
            classButton(tft, GladiatorClass.RETIARIUS, "TRIDENT + NET");
            classButton(tft, GladiatorClass.SECUTOR, "GLADIUS + SCUTUM");
        }
    }

    private static void classButton(TftTouchShield tft, int kind, String arms) {
        button(tft, kind, Opening.className(kind), arms, Opening.classColor(kind));
    }

    private static void button(TftTouchShield tft, int choice, String label, String detail, int color) {
        int x = (choice & 1) == 0 ? LEFT_X : RIGHT_X;
        int y = choice < 2 ? TOP_Y : BOTTOM_Y;
        drawButton(tft, x, y, BUTTON_WIDTH, label, detail, color);
    }

    private static void drawButton(TftTouchShield tft, int x, int y, int width, String label, String detail, int color) {
        tft.fillRect(x, y, width, BUTTON_HEIGHT, Hud.PANEL);
        tft.fillRect(x, y, 5, BUTTON_HEIGHT, color);
        tft.setTextSize(2);
        tft.setTextColor(color, Hud.PANEL);
        tft.setCursor(x + 12, y + 10);
        tft.print(label);
        tft.setTextSize(1);
        tft.setTextColor(TftTouchShield.WHITE, Hud.PANEL);
        tft.setCursor(x + 12, y + 39);
        tft.print(detail);
    }

    private static void waitForRelease(TftTouchShield tft) {
        int misses = 0;
        while (misses < 3) {
            if (tft.readTouch()) {
                misses = 0;
            } else {
                misses = misses + 1;
            }
            Delay.millis(10);
        }
    }
}
