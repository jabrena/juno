package io.github.jabrena.juno.games.doom;

import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * DOOM's status bar under the view: kills, health, the arms grid, the marine's face, armor and a map
 * table, in stone panels with big red numbers. The face grows bloodier as health drops.
 */
final class Hud {
    private static final int BAR_Y = DisplayList.VIEW_BOTTOM;
    private static final int BAR_HEIGHT = DisplayList.HEIGHT - BAR_Y;
    private static final int NUMBER_Y = BAR_Y + 5;
    private static final int LABEL_Y = BAR_Y + 28;
    private static final int[] DIVIDERS = {56, 124, 172, 212, 276};

    private static final int STONE = TftTouchShield.color(88, 84, 78);
    private static final int STONE_LIGHT = TftTouchShield.color(140, 135, 125);
    private static final int STONE_DARK = TftTouchShield.color(40, 36, 32);
    private static final int NUMBER = TftTouchShield.color(215, 20, 10);
    private static final int LABEL = TftTouchShield.color(190, 185, 170);
    private static final int OFF = TftTouchShield.color(55, 52, 48);
    private static final int SKIN = TftTouchShield.color(215, 160, 120);
    private static final int SKIN_DARK = TftTouchShield.color(160, 105, 75);
    private static final int HAIR = TftTouchShield.color(95, 52, 25);
    private static final int BLOOD = TftTouchShield.color(190, 15, 10);

    private static int shownHealth = Integer.MIN_VALUE;
    private static int shownKills = Integer.MIN_VALUE;
    private static int shownArmor = Integer.MIN_VALUE;
    private static int shownFace = Integer.MIN_VALUE;

    private Hud() {
    }

    /** Repaints the whole bar: the stone panels, their labels, the arms grid and the map table. */
    static void drawBar() {
        TftTouchShield.fillRect(0, BAR_Y, DisplayList.WIDTH, BAR_HEIGHT, STONE);
        TftTouchShield.drawHorizontalLine(0, BAR_Y, DisplayList.WIDTH, STONE_LIGHT);
        TftTouchShield.drawHorizontalLine(0, BAR_Y + 1, DisplayList.WIDTH, STONE_DARK);
        for (int divider = 0; divider < DIVIDERS.length; divider++) {
            TftTouchShield.drawVerticalLine(DIVIDERS[divider] - 1, BAR_Y + 2, BAR_HEIGHT - 2, STONE_DARK);
            TftTouchShield.drawVerticalLine(DIVIDERS[divider], BAR_Y + 2, BAR_HEIGHT - 2, STONE_LIGHT);
        }
        label("KILLS", 0, 56);
        label("HEALTH", 56, 124);
        label("ARMS", 124, 172);
        label("ARMOR", 212, 276);
        arms();
        table();
        shownHealth = Integer.MIN_VALUE;
        shownFace = Integer.MIN_VALUE;
        drawStatus();
    }

    /** Redraws only the numbers and the face that changed since the last call. */
    static void drawStatus() {
        int face = faceState();
        if (Player.health != shownHealth || Monsters.kills != shownKills || Player.armor != shownArmor
                || face != shownFace) {
            number(Monsters.kills, false, 0, 56);
            number(Player.health, true, 56, 124);
            number(Player.armor, true, 212, 276);
            if (face != shownFace) {
                face(face);
            }
            shownHealth = Player.health;
            shownKills = Monsters.kills;
            shownArmor = Player.armor;
            shownFace = face;
        }
    }

    static void showCentered(String text, int y, int size, int color) {
        TftTouchShield.setTextSize(size);
        TftTouchShield.setTextColor(color, DisplayList.BACKGROUND);
        TftTouchShield.setCursor((DisplayList.WIDTH - text.length() * 6 * size) / 2, y);
        TftTouchShield.print(text);
    }

    private static void label(String text, int from, int to) {
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(LABEL, STONE);
        TftTouchShield.setCursor((from + to - text.length() * 6) / 2 + 1, LABEL_Y);
        TftTouchShield.print(text);
    }

    /** A big red number centered in its panel, with a percent sign when {@code percent}. */
    private static void number(int value, boolean percent, int from, int to) {
        int digits = value >= 100 ? 3 : value >= 10 ? 2 : 1;
        int width = (digits + (percent ? 1 : 0)) * 12;
        int x = (from + to - width) / 2 + 1;
        TftTouchShield.fillRect(from + 2, NUMBER_Y, to - from - 3, 16, STONE);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(NUMBER, STONE);
        TftTouchShield.setCursor(x, NUMBER_Y);
        TftTouchShield.print(value);
        if (percent) {
            TftTouchShield.print("%");
        }
    }

    /** The 2-7 weapon slots: only the pistol (2) is carried. */
    private static void arms() {
        TftTouchShield.setTextSize(1);
        for (int slot = 0; slot < 6; slot++) {
            TftTouchShield.setTextColor(slot == 0 ? TftTouchShield.YELLOW : OFF, STONE);
            TftTouchShield.setCursor(131 + slot % 3 * 14, BAR_Y + 7 + slot / 3 * 11);
            TftTouchShield.print(slot + 2);
        }
    }

    /** The map and pilot table where DOOM lists its ammunition. */
    private static void table() {
        TftTouchShield.fillRect(278, BAR_Y + 2, 42, BAR_HEIGHT - 2, STONE);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(LABEL, STONE);
        TftTouchShield.setCursor(281, BAR_Y + 3);
        TftTouchShield.print("MAP");
        TftTouchShield.setTextColor(TftTouchShield.YELLOW, STONE);
        TftTouchShield.setCursor(281, BAR_Y + 12);
        TftTouchShield.print(Level.NAME);
        TftTouchShield.setTextColor(LABEL, STONE);
        TftTouchShield.setCursor(281, BAR_Y + 21);
        TftTouchShield.print("PILOT");
        TftTouchShield.setTextColor(Controls.autopilot ? TftTouchShield.MAGENTA : TftTouchShield.CYAN, STONE);
        TftTouchShield.setCursor(281, BAR_Y + 29);
        TftTouchShield.print(Controls.autopilot ? "CPU  " : "HUMAN");
    }

    /** 0 healthy, 1 scratched, 2 wounded, 3 near death, +4 while a wound is fresh, 8 dead. */
    private static int faceState() {
        if (Player.health == 0) {
            return 8;
        }
        int tier = Player.health > 75 ? 0 : Player.health > 50 ? 1 : Player.health > 25 ? 2 : 3;
        return Player.hurt > 0 ? tier + 4 : tier;
    }

    /** The marine's face, 28x32 pixels of skin, hair, eyes and a mouth that grimaces as he is hurt. */
    private static void face(int state) {
        int x = 178;
        int y = BAR_Y + 4;
        int tier = state & 3;
        boolean grimace = state >= 4;
        TftTouchShield.fillRect(x - 2, y - 1, 32, 36, STONE_DARK);
        TftTouchShield.fillRect(x, y, 28, 33, SKIN);
        TftTouchShield.fillRect(x, y, 28, 7, HAIR);
        TftTouchShield.fillRect(x, y + 7, 3, 8, HAIR);
        TftTouchShield.fillRect(x + 25, y + 7, 3, 8, HAIR);
        TftTouchShield.fillRect(x + 4, y + 11, 8, 2, HAIR);
        TftTouchShield.fillRect(x + 16, y + 11, 8, 2, HAIR);
        if (state == 8) {
            for (int k = 0; k < 4; k++) {
                TftTouchShield.fillRect(x + 5 + k * 2, y + 14 + k * 2, 2, 2, BLOOD);
                TftTouchShield.fillRect(x + 11 - k * 2, y + 14 + k * 2, 2, 2, BLOOD);
                TftTouchShield.fillRect(x + 17 + k * 2, y + 14 + k * 2, 2, 2, BLOOD);
                TftTouchShield.fillRect(x + 23 - k * 2, y + 14 + k * 2, 2, 2, BLOOD);
            }
        } else {
            TftTouchShield.fillRect(x + 5, y + 14, 7, 4, TftTouchShield.WHITE);
            TftTouchShield.fillRect(x + 17, y + 14, 7, 4, TftTouchShield.WHITE);
            TftTouchShield.fillRect(x + (grimace ? 6 : 8), y + 14, 3, 4, STONE_DARK);
            TftTouchShield.fillRect(x + 20, y + 14, 3, 4, STONE_DARK);
        }
        TftTouchShield.fillRect(x + 13, y + 17, 3, 8, SKIN_DARK);
        if (grimace || state == 8) {
            TftTouchShield.fillRect(x + 7, y + 25, 14, 5, STONE_DARK);
            TftTouchShield.fillRect(x + 8, y + 25, 12, 2, TftTouchShield.WHITE);
        } else {
            TftTouchShield.fillRect(x + 8, y + 26, 12, 2, STONE_DARK);
        }
        if (tier >= 1 && state != 0) {
            TftTouchShield.fillRect(x + 3, y + 8, 3, 9, BLOOD);
        }
        if (tier >= 2) {
            TftTouchShield.fillRect(x + 22, y + 10, 3, 12, BLOOD);
            TftTouchShield.fillRect(x + 4, y + 22, 6, 3, BLOOD);
        }
        if (tier >= 3 || state == 8) {
            TftTouchShield.fillRect(x + 11, y + 2, 4, 16, BLOOD);
            TftTouchShield.fillRect(x + 18, y + 20, 6, 4, BLOOD);
            TftTouchShield.fillRect(x + 2, y + 18, 5, 8, BLOOD);
        }
    }
}
