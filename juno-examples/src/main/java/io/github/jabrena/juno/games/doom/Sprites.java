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
    static final int RIFLE = 12;
    static final int SAW = 13;
    static final int AMMO = 14;
    static final int AMMO_BOX = 15;
    static final int PACK = 16;

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
        // A gun lying on the floor: stock, then body and barrel.
        -16, 1, -4, 1, -16, 1, -16, 6, -16, 6, -4, 5, -4, 1, -4, 7, -4, 7, 18, 7, 18, 7, 18, 5, 18, 5, -4, 5,
        // Chainsaw: the engine block and the toothed bar.
        -14, 1, -2, 1, -2, 1, -2, 12, -2, 12, -14, 12, -14, 12, -14, 1, -2, 4, 22, 4, -2, 9, 22, 9, 22, 4, 24, 6,
        24, 6, 22, 9,
        // Ammo: a clip, shells, a rocket or a cell.
        -4, 0, 4, 0, 4, 0, 4, 6, 4, 6, -4, 6, -4, 6, -4, 0,
        // A box of ammo with its lid.
        -10, 0, 10, 0, 10, 0, 10, 12, 10, 12, -10, 12, -10, 12, -10, 0, -10, 9, 10, 9,
        // Backpack with its flap.
        -8, 0, 8, 0, 8, 0, 9, 14, 9, 14, -9, 14, -9, 14, -8, 0, -9, 14, -4, 8, -4, 8, 4, 8, 4, 8, 9, 14,
    };
    /** First value of each shape in {@link #SEGMENTS}, then its end. */
    static final short[] START = {0, 64, 140, 236, 280, 304, 328, 344, 368, 392, 416, 436, 464, 492, 524, 540, 560,
        588};

    // One repeated byte each, like the walls' colors (see Renderer): cheap to send to the screen.
    static final int ZOMBIEMAN_COLOR = 0xD6D6;
    static final int SERGEANT_COLOR = 0x8D8D;
    static final int IMP_COLOR = 0xE3E3;
    static final int DEMON_COLOR = 0xF3F3;
    static final int CORPSE_COLOR = 0xA0A0;
    static final int FIREBALL_COLOR = 0xE1E1;
    static final int FLASH_COLOR = 0xE7E7;
    static final int PAIN_COLOR = TftTouchShield.WHITE;
    static final int HEALTH_COLOR = 0xDFDF;
    static final int BONUS_COLOR = 0x5C5C;
    static final int GREEN_ARMOR_COLOR = 0x4747;
    static final int BLUE_ARMOR_COLOR = 0x3B3B;
    static final int HELMET_COLOR = 0x9696;
    static final int GUN_COLOR = 0xB5B5;
    static final int BULLET_COLOR = 0xE6E6;
    static final int SHELL_COLOR = 0xC9C9;
    static final int ROCKET_COLOR = 0x9C9C;
    static final int CELL_COLOR = 0x5F5F;
    static final int BFG_COLOR = 0x4747;
    static final int PACK_COLOR = 0x8A8A;

    private Sprites() {
    }

    /** The shape of a pickup kind ({@link Items}): health, armor, a weapon, ammo, a box of it or a backpack. */
    static int itemShape(int item) {
        return switch (item) {
            case Items.HEALTH_BONUS -> BOTTLE;
            case Items.STIMPACK -> STIMPACK;
            case Items.MEDIKIT -> MEDIKIT;
            case Items.ARMOR_BONUS -> HELMET;
            case Items.GREEN_ARMOR, Items.BLUE_ARMOR -> VEST;
            case Items.CHAINSAW_PICKUP -> SAW;
            case Items.BULLET_BOX, Items.SHELL_BOX, Items.ROCKET_BOX, Items.CELL_PACK -> AMMO_BOX;
            case Items.BACKPACK -> PACK;
            case Items.CLIP, Items.SHELLS, Items.ROCKET, Items.CELL, Items.DROPPED_CLIP -> AMMO;
            default -> RIFLE;
        };
    }

    static int itemColor(int item) {
        return switch (item) {
            case Items.HEALTH_BONUS -> BONUS_COLOR;
            case Items.STIMPACK, Items.MEDIKIT -> HEALTH_COLOR;
            case Items.ARMOR_BONUS -> HELMET_COLOR;
            case Items.GREEN_ARMOR -> GREEN_ARMOR_COLOR;
            case Items.BLUE_ARMOR -> BLUE_ARMOR_COLOR;
            case Items.CHAINSAW_PICKUP -> FIREBALL_COLOR;
            case Items.CLIP, Items.BULLET_BOX, Items.DROPPED_CLIP -> BULLET_COLOR;
            case Items.SHELLS, Items.SHELL_BOX -> SHELL_COLOR;
            case Items.ROCKET, Items.ROCKET_BOX -> ROCKET_COLOR;
            case Items.CELL, Items.CELL_PACK, Items.BFG_PICKUP - 1 -> CELL_COLOR;
            case Items.BFG_PICKUP -> BFG_COLOR;
            case Items.BACKPACK -> PACK_COLOR;
            default -> GUN_COLOR;
        };
    }

    /** The color of what flies: an imp's fireball, a rocket's flame, plasma or the BFG's ball. */
    static int shotColor(int kind) {
        return switch (kind) {
            case Monsters.ROCKET -> BULLET_COLOR;
            case Monsters.PLASMA -> CELL_COLOR;
            case Monsters.BFG_BALL -> BFG_COLOR;
            default -> FIREBALL_COLOR;
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
