package io.github.jabrena.juno.games.doom;

import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * Vector figures for the things in the map, drawn as billboards that always face the marine. Each
 * shape is a run of line segments {@code x0, z0, x1, z1} in DOOM's world units: x across the figure,
 * z up from its feet (or from its center, for the fireball). Original line art, not id's sprites.
 */
final class Sprites {
    static final int ZOMBIEMAN = 0;
    static final int SERGEANT = 1;
    static final int IMP = 2;
    static final int DEMON = 3;
    static final int CORPSE = 4;
    static final int FIREBALL = 5;
    static final int FLASH = 6;
    static final int MEDIKIT = 7;
    static final int STIMPACK = 8;
    static final int BOTTLE = 9;
    static final int HELMET = 10;
    static final int VEST = 11;

    static final short[] SEGMENTS = {
        // Zombieman: head, torso, legs, arms holding a rifle.
        -5, 46, 5, 46, 5, 46, 5, 56, 5, 56, -5, 56, -5, 56, -5, 46,
        -9, 44, 9, 44, 9, 44, 7, 24, 7, 24, -7, 24, -7, 24, -9, 44,
        -5, 24, -7, 0, 5, 24, 7, 0, -7, 0, -11, 0, 7, 0, 11, 0,
        -9, 42, -3, 30, 9, 42, 12, 32, -3, 30, 18, 34, 12, 32, 18, 34,
        // Shotgun sergeant: helmeted head, broader torso, legs, a long shotgun.
        -6, 46, 6, 46, 6, 46, 6, 54, -6, 46, -6, 54, -8, 54, 8, 54, -6, 54, 0, 58, 0, 58, 6, 54,
        -10, 44, 10, 44, 10, 44, 8, 24, 8, 24, -8, 24, -8, 24, -10, 44,
        -5, 24, -7, 0, 5, 24, 7, 0, -7, 0, -11, 0, 7, 0, 11, 0,
        -10, 42, -4, 30, 10, 42, 12, 32, -4, 30, 26, 33, 26, 33, 26, 30, 12, 32, 26, 30,
        // Imp: horned head, spiked shoulders, clawed arms, digitigrade legs.
        -6, 44, 6, 44, 6, 44, 5, 54, 5, 54, -5, 54, -5, 54, -6, 44, -5, 54, -9, 60, 5, 54, 9, 60,
        -12, 44, 12, 44, 12, 44, 7, 22, 7, 22, -7, 22, -7, 22, -12, 44, -12, 44, -15, 48, 12, 44, 15, 48,
        -12, 42, -17, 26, 12, 42, 17, 26, -17, 26, -20, 30, -17, 26, -14, 22, 17, 26, 20, 30, 17, 26, 14, 22,
        -5, 22, -9, 10, -9, 10, -6, 0, 5, 22, 9, 10, 9, 10, 6, 0, -6, 0, -11, 0, 6, 0, 11, 0,
        // Demon: hunched body, gaping jaw, stubby legs.
        -22, 10, -24, 30, -24, 30, -10, 44, -10, 44, 16, 40, 16, 40, 24, 26, 24, 26, 20, 10, 20, 10, -22, 10,
        16, 40, 30, 34, 30, 34, 22, 28, 24, 26, 30, 22, -14, 10, -16, 0, 12, 10, 14, 0,
        // Corpse: a body lying on the floor in a pool.
        -26, 3, 20, 3, 20, 3, 24, 8, 24, 8, 30, 6, 30, 6, 26, 2, -26, 3, -30, 7, -20, 0, 20, 0,
        // Fireball, around its center.
        0, -6, 6, 0, 6, 0, 0, 6, 0, 6, -6, 0, -6, 0, 0, -6, -3, 0, 3, 0, 0, -3, 0, 3,
        // Muzzle flash, at the gun of a firing zombieman or sergeant.
        14, 34, 24, 34, 19, 29, 19, 39, 15, 30, 23, 38, 15, 38, 23, 30,
        // Medikit: a box with a cross.
        -12, 0, 12, 0, 12, 0, 12, 14, 12, 14, -12, 14, -12, 14, -12, 0, -4, 7, 4, 7, 0, 3, 0, 11,
        // Stimpack: a smaller box with a cross.
        -8, 0, 8, 0, 8, 0, 8, 10, 8, 10, -8, 10, -8, 10, -8, 0, -3, 5, 3, 5, 0, 2, 0, 8,
        // Health bonus: a potion bottle.
        -3, 0, 3, 0, 3, 0, 3, 8, 3, 8, 1, 11, 1, 11, -1, 11, -1, 11, -3, 8, -3, 8, -3, 0,
        // Armor bonus: a helmet.
        -6, 0, 6, 0, 6, 0, 5, 6, 5, 6, 0, 9, 0, 9, -5, 6, -5, 6, -6, 0,
        // Armor: a vest.
        -12, 0, 12, 0, 12, 0, 14, 20, 14, 20, 6, 24, 6, 24, 0, 18, 0, 18, -6, 24, -6, 24, -14, 20, -14, 20, -12, 0,
    };
    /** First value of each shape in {@link #SEGMENTS}, then its end. */
    static final short[] START = {0, 64, 140, 236, 280, 304, 328, 344, 368, 392, 416, 436, 464};

    static final int ZOMBIEMAN_COLOR = TftTouchShield.color(210, 210, 200);
    static final int SERGEANT_COLOR = TftTouchShield.color(150, 190, 110);
    static final int IMP_COLOR = TftTouchShield.color(235, 125, 45);
    static final int DEMON_COLOR = TftTouchShield.color(255, 120, 170);
    static final int CORPSE_COLOR = TftTouchShield.color(150, 25, 25);
    static final int FIREBALL_COLOR = TftTouchShield.color(255, 70, 0);
    static final int FLASH_COLOR = TftTouchShield.color(255, 240, 80);
    static final int PAIN_COLOR = TftTouchShield.WHITE;
    static final int HEALTH_COLOR = TftTouchShield.color(240, 240, 255);
    static final int BONUS_COLOR = TftTouchShield.color(80, 140, 255);
    static final int GREEN_ARMOR_COLOR = TftTouchShield.color(60, 220, 60);
    static final int BLUE_ARMOR_COLOR = TftTouchShield.color(70, 110, 255);
    static final int HELMET_COLOR = TftTouchShield.color(160, 200, 160);

    private Sprites() {
    }

    /** The shape of a pickup kind: health bonus, stimpack, medikit, armor bonus, green or blue armor. */
    static int itemShape(int item) {
        return switch (item) {
            case 0 -> BOTTLE;
            case 1 -> STIMPACK;
            case 2 -> MEDIKIT;
            case 3 -> HELMET;
            default -> VEST;
        };
    }

    static int itemColor(int item) {
        return switch (item) {
            case 0 -> BONUS_COLOR;
            case 1, 2 -> HEALTH_COLOR;
            case 3 -> HELMET_COLOR;
            case 4 -> GREEN_ARMOR_COLOR;
            default -> BLUE_ARMOR_COLOR;
        };
    }

    static int color(int kind) {
        return switch (kind) {
            case ZOMBIEMAN -> ZOMBIEMAN_COLOR;
            case SERGEANT -> SERGEANT_COLOR;
            case IMP -> IMP_COLOR;
            default -> DEMON_COLOR;
        };
    }
}
