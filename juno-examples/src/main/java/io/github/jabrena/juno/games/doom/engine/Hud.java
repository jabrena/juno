package io.github.jabrena.juno.games.doom.engine;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;

/**
 * DOOM's status bar under the view: ammo for the weapon in hand, health, the arms grid, the marine's face, armor and a
 * compact map/pilot/FPS panel, in stone panels with big red numbers. The face grows bloodier as health drops. Tapping
 * a number of the arms grid raises that weapon; tapping the ARMS label swaps between the fist and the chainsaw.
 */
final class Hud {
    private static final int BAR_Y = DisplayList.VIEW_BOTTOM;
    private static final int BAR_HEIGHT = DisplayList.HEIGHT - BAR_Y;
    private static final int NUMBER_Y = BAR_Y + 5;
    private static final int LABEL_Y = BAR_Y + 28;
    private static final int[] DIVIDERS = {56, 124, 172, 212, 276};
    /** The arms panel, whose numbers and label {@link Controls} takes taps on. */
    static final int ARMS_LEFT = 124;
    static final int ARMS_RIGHT = 172;
    static final int ARMS_LABEL_Y = BAR_Y + 24;
    /** The face's panel, whose tap {@link Controls} takes to pause the game. */
    static final int FACE_LEFT = 172;
    static final int FACE_RIGHT = 212;

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
    private static int shownAmmo = Integer.MIN_VALUE;
    private static int shownArms = Integer.MIN_VALUE;
    private static int shownArmor = Integer.MIN_VALUE;
    private static int shownFace = Integer.MIN_VALUE;
    private static int shownFps = Integer.MIN_VALUE;

    private Hud() {
    }

    /** Repaints the whole bar: the stone panels, their labels, the arms grid and the map table. */
    static void drawBar(TftTouchShield tft) {
        tft.fillRect(0, BAR_Y, DisplayList.WIDTH, BAR_HEIGHT, STONE);
        tft.drawHorizontalLine(0, BAR_Y, DisplayList.WIDTH, STONE_LIGHT);
        tft.drawHorizontalLine(0, BAR_Y + 1, DisplayList.WIDTH, STONE_DARK);
        for (int divider = 0; divider < DIVIDERS.length; divider++) {
            tft.drawVerticalLine(DIVIDERS[divider] - 1, BAR_Y + 2, BAR_HEIGHT - 2, STONE_DARK);
            tft.drawVerticalLine(DIVIDERS[divider], BAR_Y + 2, BAR_HEIGHT - 2, STONE_LIGHT);
        }
        label(tft, "AMMO", 0, 56);
        label(tft, "HEALTH", 56, 124);
        label(tft, "ARMOR", 212, 276);
        table(tft);
        shownHealth = Integer.MIN_VALUE;
        shownArms = Integer.MIN_VALUE;
        shownFace = Integer.MIN_VALUE;
        drawStatus(tft);
    }

    /** Redraws only the numbers and the face that changed since the last call. */
    static void drawStatus(TftTouchShield tft) {
        int face = faceState();
        int ammo = Weapon.ammoInHand();
        if (Player.health != shownHealth || ammo != shownAmmo || Player.armor != shownArmor || face != shownFace) {
            if (ammo < 0) {
                tft.fillRect(2, NUMBER_Y, 53, 16, STONE);
            } else {
                number(tft, ammo, false, 0, 56);
            }
            number(tft, Player.health, true, 56, 124);
            number(tft, Player.armor, true, 212, 276);
            if (face != shownFace) {
                face(tft, face);
            }
            shownHealth = Player.health;
            shownAmmo = ammo;
            shownArmor = Player.armor;
            shownFace = face;
        }
        int arms = Weapon.owned << 3 | Weapon.wanted();
        if (arms != shownArms) {
            arms(tft);
            shownArms = arms;
        }
        performance(tft);
    }

    static void showCentered(TftTouchShield tft, String text, int y, int size, int color) {
        tft.setTextSize(size);
        tft.setTextColor(color, DisplayList.BACKGROUND);
        tft.setCursor((DisplayList.WIDTH - text.length() * 6 * size) / 2, y);
        tft.print(text);
    }

    private static void label(TftTouchShield tft, String text, int from, int to) {
        tft.setTextSize(1);
        tft.setTextColor(LABEL, STONE);
        tft.setCursor((from + to - text.length() * 6) / 2 + 1, LABEL_Y);
        tft.print(text);
    }

    /** A big red number centered in its panel, with a percent sign when {@code percent}. */
    private static void number(TftTouchShield tft, int value, boolean percent, int from, int to) {
        int digits = value >= 100 ? 3 : value >= 10 ? 2 : 1;
        int width = (digits + (percent ? 1 : 0)) * 12;
        int x = (from + to - width) / 2 + 1;
        tft.fillRect(from + 2, NUMBER_Y, to - from - 3, 16, STONE);
        tft.setTextSize(2);
        tft.setTextColor(NUMBER, STONE);
        tft.setCursor(x, NUMBER_Y);
        tft.print(value);
        if (percent) {
            tft.print("%");
        }
    }

    /** The 2-7 weapon slots: yellow when carried, white for the one in hand, dark otherwise. */
    private static void arms(TftTouchShield tft) {
        tft.setTextSize(1);
        for (int slot = 2; slot <= 7; slot++) {
            int color = slot == Weapon.wanted() ? TftTouchShield.WHITE
                    : Weapon.carries(slot) ? TftTouchShield.YELLOW : OFF;
            tft.setTextColor(color, STONE);
            tft.setCursor(131 + (slot - 2) % 3 * 14, BAR_Y + 7 + (slot - 2) / 3 * 11);
            tft.print(slot);
        }
        tft.setTextColor(Weapon.wanted() <= Weapon.CHAINSAW ? TftTouchShield.WHITE : LABEL, STONE);
        tft.setCursor((ARMS_LEFT + ARMS_RIGHT - 4 * 6) / 2 + 1, LABEL_Y);
        tft.print("ARMS");
    }

    /** The weapon slot under a tap on the arms panel: 2-7 on its numbers, 1 on its label, -1 elsewhere. */
    static int slotAt(int x, int y) {
        if (x < ARMS_LEFT || x >= ARMS_RIGHT || y < BAR_Y) {
            return -1;
        }
        if (y >= ARMS_LABEL_Y) {
            return 1;
        }
        int column = Math.max(0, Math.min(2, (x - 128) / 14));
        return 2 + column + (y < BAR_Y + 16 ? 0 : 3);
    }

    /** The current map, pilot and measured frame rate, without labels in the narrow panel. */
    private static void table(TftTouchShield tft) {
        tft.fillRect(278, BAR_Y + 2, 42, BAR_HEIGHT - 2, STONE);
        tft.setTextSize(1);
        tft.setTextColor(TftTouchShield.YELLOW, STONE);
        tft.setCursor(281, BAR_Y + 4);
        if (World.fromWad) {
            tft.print("E");
            tft.print(World.episode);
            tft.print("M");
            tft.print(World.map);
        } else {
            tft.print(Level.NAME);
        }
        tft.setTextColor(Controls.autopilot ? TftTouchShield.MAGENTA : TftTouchShield.CYAN, STONE);
        tft.setCursor(281, BAR_Y + 15);
        tft.print(Controls.autopilot ? "CPU  " : "HUMAN");
        shownFps = Integer.MIN_VALUE;
        performance(tft);
    }

    private static void performance(TftTouchShield tft) {
        int fps = FrameStats.fps();
        if (fps != shownFps) {
            tft.fillRect(280, BAR_Y + 26, 39, 10, STONE);
            tft.setTextSize(1);
            tft.setTextColor(LABEL, STONE);
            tft.setCursor(281, BAR_Y + 27);
            tft.print(Math.min(999, fps));
            tft.print("FPS");
            shownFps = fps;
        }
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
    private static void face(TftTouchShield tft, int state) {
        int x = 178;
        int y = BAR_Y + 4;
        int tier = state & 3;
        boolean grimace = state >= 4;
        tft.fillRect(x - 2, y - 1, 32, 36, STONE_DARK);
        tft.fillRect(x, y, 28, 33, SKIN);
        tft.fillRect(x, y, 28, 7, HAIR);
        tft.fillRect(x, y + 7, 3, 8, HAIR);
        tft.fillRect(x + 25, y + 7, 3, 8, HAIR);
        tft.fillRect(x + 4, y + 11, 8, 2, HAIR);
        tft.fillRect(x + 16, y + 11, 8, 2, HAIR);
        if (state == 8) {
            for (int k = 0; k < 4; k++) {
                tft.fillRect(x + 5 + k * 2, y + 14 + k * 2, 2, 2, BLOOD);
                tft.fillRect(x + 11 - k * 2, y + 14 + k * 2, 2, 2, BLOOD);
                tft.fillRect(x + 17 + k * 2, y + 14 + k * 2, 2, 2, BLOOD);
                tft.fillRect(x + 23 - k * 2, y + 14 + k * 2, 2, 2, BLOOD);
            }
        } else {
            tft.fillRect(x + 5, y + 14, 7, 4, TftTouchShield.WHITE);
            tft.fillRect(x + 17, y + 14, 7, 4, TftTouchShield.WHITE);
            tft.fillRect(x + (grimace ? 6 : 8), y + 14, 3, 4, STONE_DARK);
            tft.fillRect(x + 20, y + 14, 3, 4, STONE_DARK);
        }
        tft.fillRect(x + 13, y + 17, 3, 8, SKIN_DARK);
        if (grimace || state == 8) {
            tft.fillRect(x + 7, y + 25, 14, 5, STONE_DARK);
            tft.fillRect(x + 8, y + 25, 12, 2, TftTouchShield.WHITE);
        } else {
            tft.fillRect(x + 8, y + 26, 12, 2, STONE_DARK);
        }
        if (tier >= 1 && state != 0) {
            tft.fillRect(x + 3, y + 8, 3, 9, BLOOD);
        }
        if (tier >= 2) {
            tft.fillRect(x + 22, y + 10, 3, 12, BLOOD);
            tft.fillRect(x + 4, y + 22, 6, 3, BLOOD);
        }
        if (tier >= 3 || state == 8) {
            tft.fillRect(x + 11, y + 2, 4, 16, BLOOD);
            tft.fillRect(x + 18, y + 20, 6, 4, BLOOD);
            tft.fillRect(x + 2, y + 18, 5, 8, BLOOD);
        }
    }
}
