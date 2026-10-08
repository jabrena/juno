package io.github.jabrena.juno.games.doom;

import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * The cast of the cover, switched off for now: the marine of the box art, his muzzle flash and plasma
 * ball, and the horned demons rising out of the lava.
 */
final class Figures {
    /** Boxes (left, top, right, bottom) around the helmet, body, arms, gun and plasma. */
    static final int[] BOXES = {
        74, 70, 128, 120, 56, 98, 176, 180, 150, 100, 284, 172, 60, 150, 182, 218, 68, 170, 170, 240, 150, 190, 204, 232};

    private static final int SKIN_LIGHT = TftTouchShield.color(235, 140, 100);
    private static final int PANTS = TftTouchShield.color(80, 115, 45);
    private static final int PANTS_DARK = TftTouchShield.color(42, 70, 28);
    private static final int BELT = TftTouchShield.color(34, 34, 28);
    private static final int HELMET = TftTouchShield.color(112, 98, 88);
    private static final int HELMET_DARK = TftTouchShield.color(52, 46, 44);
    private static final int HELMET_LIGHT = TftTouchShield.color(170, 152, 135);
    private static final int VISOR = TftTouchShield.color(22, 24, 30);
    private static final int VISOR_GLINT = TftTouchShield.color(110, 125, 150);
    private static final int ARMOR = TftTouchShield.color(60, 150, 55);
    private static final int ARMOR_DARK = TftTouchShield.color(35, 95, 35);
    private static final int ARMOR_LIGHT = TftTouchShield.color(110, 200, 90);
    private static final int SKIN = TftTouchShield.color(190, 85, 55);
    private static final int SKIN_DARK = TftTouchShield.color(130, 45, 30);
    private static final int STEEL = TftTouchShield.color(95, 105, 125);
    private static final int STEEL_DARK = TftTouchShield.color(45, 50, 65);
    private static final int GLOVE = TftTouchShield.color(35, 35, 50);
    private static final int DEMON = TftTouchShield.color(205, 60, 35);
    private static final int DEMON_DARK = TftTouchShield.color(140, 30, 20);
    private static final int HORN = TftTouchShield.color(235, 215, 170);
    private static final int FLASH_A = TftTouchShield.color(255, 250, 220);
    private static final int FLASH_B = TftTouchShield.color(255, 220, 90);
    private static final int FLASH_C = TftTouchShield.color(255, 140, 40);
    private static final int PLASMA_A = TftTouchShield.color(255, 255, 255);
    private static final int PLASMA_B = TftTouchShield.color(255, 190, 150);

    private Figures() {
    }

    /**
     * The marine, after the box art: a brown-grey helmet with a dark visor and vented face guard, a green
     * shirt over bare muscular arms and belly, a belt with pouches, olive pants, and the minigun in one
     * hand while the other throws a plasma ball.
     */
    static void marine() {
        // legs: olive, torn, braced wide
        Draw.stroke(98, 174, 80, 238, 12, PANTS);
        Draw.stroke(128, 172, 158, 238, 12, PANTS);
        Draw.stroke(100, 176, 86, 236, 4, PANTS_DARK);
        Draw.stroke(132, 176, 156, 236, 4, PANTS_DARK);
        TftTouchShield.fillRect(88, 192, 7, 5, SKIN_DARK);
        // belt and pouches
        Draw.quad(162, 175, 84, 82, 152, 156, BELT);
        TftTouchShield.fillRect(124, 168, 26, 14, PANTS_DARK);
        TftTouchShield.fillRect(126, 170, 22, 3, PANTS);
        TftTouchShield.drawVerticalLine(137, 170, 12, BELT);
        // bare belly under the shirt, abs picked out
        Draw.quad(142, 164, 88, 90, 148, 148, SKIN);
        Draw.quad(142, 164, 124, 125, 148, 148, SKIN_DARK);
        TftTouchShield.drawVerticalLine(116, 144, 20, SKIN_DARK);
        for (int row = 148; row < 164; row += 5) {
            TftTouchShield.drawHorizontalLine(92, row, 24, SKIN_DARK);
            TftTouchShield.drawHorizontalLine(94, row + 1, 20, SKIN_LIGHT);
        }
        // the shirt: broad shoulders, short sleeves, shaded on the right
        Draw.quad(100, 146, 68, 88, 164, 150, ARMOR);
        Draw.quad(100, 146, 128, 128, 164, 150, ARMOR_DARK);
        Draw.quad(100, 112, 70, 74, 160, 162, ARMOR_LIGHT);
        TftTouchShield.fillCircle(64, 120, 14, ARMOR);
        TftTouchShield.fillCircle(60, 115, 6, ARMOR_LIGHT);
        TftTouchShield.fillCircle(162, 106, 14, ARMOR);
        TftTouchShield.fillCircle(166, 110, 7, ARMOR_DARK);
        TftTouchShield.drawVerticalLine(116, 104, 40, ARMOR_DARK);
        // his left arm: bare, bent down to the gun
        Draw.stroke(62, 128, 76, 160, 10, SKIN);
        Draw.stroke(76, 162, 112, 186, 8, SKIN);
        Draw.stroke(66, 134, 80, 160, 3, SKIN_LIGHT);
        Draw.stroke(82, 164, 108, 182, 2, SKIN_DARK);
        // the gun: a minigun, dark housing and a cluster of barrels
        Draw.stroke(112, 180, 172, 206, 8, STEEL_DARK);
        Draw.stroke(116, 176, 172, 200, 3, STEEL);
        Draw.stroke(120, 182, 172, 207, 2, STEEL);
        Draw.stroke(124, 186, 168, 205, 2, STEEL);
        TftTouchShield.fillCircle(120, 188, 10, GLOVE);
        TftTouchShield.fillCircle(116, 185, 4, VISOR_GLINT);
        // his right arm: short sleeve, then bare muscle and torn flesh reaching out
        Draw.stroke(160, 106, 172, 124, 11, ARMOR);
        Draw.stroke(172, 124, 196, 148, 9, SKIN);
        Draw.stroke(176, 126, 196, 145, 3, SKIN_LIGHT);
        Draw.stroke(190, 144, 204, 153, 7, SKIN);
        TftTouchShield.fillRect(186, 142, 5, 4, SKIN_DARK);
        TftTouchShield.fillCircle(209, 154, 9, GLOVE);
        TftTouchShield.fillRect(213, 146, 9, 4, GLOVE);
        TftTouchShield.fillRect(215, 152, 10, 4, GLOVE);
        TftTouchShield.fillRect(212, 158, 8, 4, GLOVE);
        TftTouchShield.fillCircle(206, 150, 3, VISOR_GLINT);
        // neck and the helmet: heavy dome, glossy visor, vented guard
        TftTouchShield.fillRect(92, 112, 18, 10, SKIN_DARK);
        TftTouchShield.fillCircle(104, 93, 20, HELMET);
        TftTouchShield.fillCircle(94, 94, 21, HELMET);
        TftTouchShield.drawCircle(94, 94, 21, HELMET_DARK);
        TftTouchShield.fillCircle(90, 81, 8, HELMET_LIGHT);
        TftTouchShield.fillCircle(112, 86, 4, HELMET_LIGHT);
        Draw.quad(91, 101, 78, 80, 118, 116, VISOR);
        TftTouchShield.drawHorizontalLine(82, 93, 28, VISOR_GLINT);
        TftTouchShield.drawHorizontalLine(86, 95, 12, VISOR_GLINT);
        Draw.quad(101, 115, 82, 88, 116, 108, HELMET_DARK);
        for (int vent = 0; vent < 4; vent++) {
            TftTouchShield.drawHorizontalLine(88 + vent / 2, 104 + vent * 3, 20 - vent * 2, HELMET);
        }
        TftTouchShield.fillRect(94, 114, 12, 3, VISOR);
    }

    /** The muzzle flash and the plasma, repainted over the same footprint so nothing is left behind. */
    static void flash(int frame) {
        boolean bright = frame % 2 == 0;
        int core = bright ? FLASH_A : FLASH_B;
        int ring = bright ? FLASH_B : FLASH_C;
        TftTouchShield.fillCircle(172, 207, 14, FLASH_C);
        TftTouchShield.fillCircle(172, 207, 10, ring);
        TftTouchShield.fillCircle(172, 207, 5, core);
        DisplayList.drawLine(172, 207, 152, 193, ring);
        DisplayList.drawLine(172, 207, 198, 197, ring);
        DisplayList.drawLine(172, 207, 162, 227, core);
        DisplayList.drawLine(172, 207, 192, 225, core);
        Draw.stroke(224, 150, 276, 166, 8, bright ? PLASMA_B : FLASH_C);
        Draw.stroke(224, 150, 272, 164, 5, PLASMA_B);
        Draw.stroke(224, 150, 262, 161, 2, PLASMA_A);
        TftTouchShield.fillCircle(222, 148, 11, bright ? PLASMA_B : FLASH_C);
        TftTouchShield.fillCircle(222, 148, 8, PLASMA_A);
    }

    /** The claws and horned head reaching in from the right edge. */
    static void scenery() {
        TftTouchShield.fillRect(300, 150, 20, 48, DEMON_DARK);
        demon(312, 158, 12, false);
    }

    /** The demons rising out of the lava, redrawn each frame so they stay on top. */
    static void demonsInLava() {
        demon(26, 218, 14, true);
        demon(290, 220, 17, true);
        demon(244, 224, 12, false);
    }

    /** A demon head: round, horned, eyes burning, a mouth of teeth. */
    private static void demon(int x, int y, int r, boolean snarl) {
        TftTouchShield.fillCircle(x, y, r, DEMON);
        TftTouchShield.fillCircle(x + r / 4, y + r / 3, r * 2 / 3, DEMON_DARK);
        TftTouchShield.fillCircle(x, y - r / 6, r * 3 / 4, DEMON);
        TftTouchShield.fillRect(x - r - 2, y - r + 2, 5, 9, HORN);
        TftTouchShield.fillRect(x - r - 5, y - r - 4, 5, 7, HORN);
        TftTouchShield.fillRect(x + r - 3, y - r + 2, 5, 9, HORN);
        TftTouchShield.fillRect(x + r, y - r - 4, 5, 7, HORN);
        TftTouchShield.fillRect(x - r / 2, y - r / 4, 4, 3, TftTouchShield.YELLOW);
        TftTouchShield.fillRect(x + r / 3, y - r / 4, 4, 3, TftTouchShield.YELLOW);
        int mouth = snarl ? r / 2 : r / 3;
        TftTouchShield.fillRect(x - r / 2, y + r / 4, r, mouth, GLOVE);
        for (int tooth = 0; tooth < 4; tooth++) {
            TftTouchShield.fillRect(x - r / 2 + 2 + tooth * (r / 4 + 1), y + r / 4, 3, 4, HORN);
        }
    }
}
