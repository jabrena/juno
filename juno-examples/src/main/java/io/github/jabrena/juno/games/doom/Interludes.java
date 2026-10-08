package io.github.jabrena.juno.games.doom;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * The screens between the game's moments. The cover is a pixel-art homage to DOOM's box art composed from rectangles and circles: a hellscape sky
 * and rock, the bevelled metal logo, the green marine firing into a pack of horned demons. The scene
 * is painted piece by piece, then its muzzle flash and plasma ball flicker until a tap.
 */
final class Interludes {
    private static final int ART_HEIGHT = DisplayList.HEIGHT;
    private static final int FRAME_MILLIS = 60;
    private static final int FLOOD_CELL = 8;
    private static final int PROMPT_X = 80;
    private static final int PROMPT_Y = 194;
    private static final int PROMPT_WIDTH = 160;
    private static final int PROMPT_HEIGHT = 28;
    private static final int FLOOD_RISE = 10;
    /** Switches the mountains on the cover back on (the sky is always painted first). */
    private static final boolean MOUNTAINS = false;
    /** Switches the horned demons on the cover back on. */
    private static final boolean DEMONS = false;
    /** Switches the marine, his plasma and muzzle flash on the cover back on. */
    private static final boolean MARINE = false;

    /** Boxes (left, top, right, bottom) around the helmet, body, arms, gun and plasma. */
    private static final int[] MARINE_BOXES = {
        74, 70, 128, 120, 56, 98, 176, 180, 150, 100, 284, 172, 60, 150, 182, 218, 68, 170, 170, 240, 150, 190, 204, 232};

    private static int tick;
    private static int lettersShown;
    private static boolean marineShown;
    private static boolean promptShown;

    private static final int METAL = TftTouchShield.color(70, 100, 125);
    private static final int METAL_LIGHT = TftTouchShield.color(170, 200, 220);
    private static final int METAL_DARK = TftTouchShield.color(20, 35, 55);
    private static final int CIRCUIT_BLUE = TftTouchShield.color(60, 80, 150);
    private static final int CIRCUIT_TRACE = TftTouchShield.color(25, 35, 80);
    private static final int CIRCUIT_GLINT = TftTouchShield.color(120, 150, 210);
    private static final int BRICK = TftTouchShield.color(235, 150, 60);
    private static final int BRICK_MORTAR = TftTouchShield.color(150, 80, 30);
    private static final int BRICK_LIGHT = TftTouchShield.color(250, 190, 100);
    private static final int EDGE_LIGHT = TftTouchShield.color(240, 160, 130);
    private static final int EDGE = TftTouchShield.color(215, 70, 30);
    private static final int EDGE_DARK = TftTouchShield.color(120, 25, 12);
    private static final int SLOT = TftTouchShield.color(70, 14, 8);
    private static final int SLOT_LIGHT = TftTouchShield.color(250, 175, 130);
    private static final int[] LOGO_X = {25, 91, 155, 217};
    private static final int[] LOGO_W = {60, 58, 58, 70};
    private static final int[] LOGO_H = {122, 100, 98, 104};
    private static final int[] LOGO_H_RIGHT = {100, 96, 92, 126};
    private static final int[] LOGO_TOP = {46, 48, 50, 44};
    private static final int SKIN_LIGHT = TftTouchShield.color(235, 140, 100);
    private static final int PANTS = TftTouchShield.color(80, 115, 45);
    private static final int PANTS_DARK = TftTouchShield.color(42, 70, 28);
    private static final int BELT = TftTouchShield.color(34, 34, 28);
    private static final int HELMET = TftTouchShield.color(112, 98, 88);
    private static final int HELMET_DARK = TftTouchShield.color(52, 46, 44);
    private static final int HELMET_LIGHT = TftTouchShield.color(170, 152, 135);
    private static final int VISOR = TftTouchShield.color(22, 24, 30);
    private static final int VISOR_GLINT = TftTouchShield.color(110, 125, 150);
    /** The lava's heat ramp as RGB565 constants (dark red up to yellow and back), so it is a flash table. */
    private static final int[] LAVA_FLOW = {0x6840, 0xB8C0, 0xF2C1, 0xFD02, 0xFEA8, 0xFD02, 0xF2C1, 0xB8C0};
    private static final int ROCK = TftTouchShield.color(130, 28, 12);
    private static final int ROCK_DARK = TftTouchShield.color(80, 14, 8);
    private static final int[] LEFT_PEAK = {64, 46, 36, 48, 72, 100, 128, 156, 184};
    private static final int[] RIGHT_PEAK = {8, 0, 14, 38, 70, 104, 140};
    private static final int LAVA = TftTouchShield.color(255, 120, 10);
    private static final int LAVA_CORE = TftTouchShield.color(255, 235, 110);
    private static final int CHARRED = TftTouchShield.color(55, 8, 6);
    private static final int MOUND = TftTouchShield.color(100, 40, 25);
    private static final int ARMOR = TftTouchShield.color(60, 150, 55);
    private static final int ARMOR_DARK = TftTouchShield.color(35, 95, 35);
    private static final int ARMOR_LIGHT = TftTouchShield.color(110, 200, 90);
    private static final int SKIN = TftTouchShield.color(190, 85, 55);
    private static final int SKIN_DARK = TftTouchShield.color(130, 45, 30);
    private static final int FACE = TftTouchShield.color(215, 150, 115);
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


    private Interludes() {
    }

    /** Plays the cover and returns once the screen has been tapped (and released). */
    static void title() {
        TftTouchShield.fillScreen(DisplayList.BACKGROUND);
        tick = 0;
        lettersShown = 0;
        marineShown = false;
        promptShown = false;
        sky();
        if (MOUNTAINS) {
            mountains();
        }
        Delay.millis(500);
        for (int top = DisplayList.HEIGHT - FLOOD_CELL; top > -FLOOD_CELL; top -= FLOOD_RISE) {
            lavaFlood(Math.max(top, 0));
            tick = tick + 1;
            Delay.millis(FRAME_MILLIS);
        }
        pause(150);
        for (int letter = 0; letter < 4; letter++) {
            logo(letter);
            lettersShown = letter + 1;
            pause(260);
        }
        if (DEMONS) {
            scenery();
        }
        if (MARINE) {
            marine();
        }
        prompt();
        promptShown = true;
        boolean tapped = false;
        int frame = 0;
        while (!tapped) {
            flicker(tick);
            tick = tick + 1;
            blink(frame % 14 < 9);
            frame = frame + 1;
            tapped = TftTouchShield.readTouch();
            Delay.millis(FRAME_MILLIS);
        }
        Controls.waitForRelease();
    }

    /** The plaque under the logo that holds the blinking prompt. */
    private static void prompt() {
        TftTouchShield.fillRect(PROMPT_X, PROMPT_Y, PROMPT_WIDTH, PROMPT_HEIGHT, DisplayList.BACKGROUND);
        TftTouchShield.drawRect(PROMPT_X, PROMPT_Y, PROMPT_WIDTH, PROMPT_HEIGHT, EDGE);
        TftTouchShield.drawRect(PROMPT_X + 2, PROMPT_Y + 2, PROMPT_WIDTH - 4, PROMPT_HEIGHT - 4, EDGE_DARK);
    }

    private static void blink(boolean on) {
        Hud.showCentered(on ? "TAP TO PLAY" : "           ", PROMPT_Y + 8, 2, TftTouchShield.WHITE);
    }

    /**
     * The screen below {@code top} flooded with lava, in coarse cells so a full-screen frame stays cheap.
     * Cells touching the logo or the marine are left alone once those are painted.
     */
    private static void lavaFlood(int top) {
        for (int y = (top + FLOOD_CELL - 1) / FLOOD_CELL * FLOOD_CELL; y < DisplayList.HEIGHT; y += FLOOD_CELL) {
            for (int x = 0; x < DisplayList.WIDTH; x += FLOOD_CELL) {
                if (!covered(x, y)) {
                    TftTouchShield.fillRect(x, y, FLOOD_CELL, FLOOD_CELL, lavaTone(x, y));
                }
            }
        }
    }

    /** Whether the cell at ({@code x}, {@code y}) overlaps a letter or the marine already painted over the lava. */
    private static boolean covered(int x, int y) {
        for (int letter = 0; letter < lettersShown; letter++) {
            int bottom = LOGO_TOP[letter] + Math.max(LOGO_H[letter], LOGO_H_RIGHT[letter]) + 8;
            if (overlaps(x, y, LOGO_X[letter], LOGO_TOP[letter], LOGO_X[letter] + LOGO_W[letter] + 8, bottom)) {
                return true;
            }
        }
        if (promptShown && overlaps(x, y, PROMPT_X, PROMPT_Y, PROMPT_X + PROMPT_WIDTH, PROMPT_Y + PROMPT_HEIGHT)) {
            return true;
        }
        if (marineShown) {
            for (int box = 0; box < MARINE_BOXES.length; box += 4) {
                if (overlaps(x, y, MARINE_BOXES[box], MARINE_BOXES[box + 1], MARINE_BOXES[box + 2],
                        MARINE_BOXES[box + 3])) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean overlaps(int x, int y, int left, int top, int right, int bottom) {
        return x < right && x + FLOOD_CELL > left && y < bottom && y + FLOOD_CELL > top;
    }

    /** Waits while the lava keeps flowing behind whatever is being painted. */
    private static void pause(int millis) {
        for (int waited = 0; waited < millis; waited += FRAME_MILLIS) {
            lavaFx(tick);
            tick = tick + 1;
            Delay.millis(FRAME_MILLIS);
        }
    }

    /** The end-of-level card shown when the marine reaches the exit switch. */
    static void complete() {
        Hud.drawStatus();
        Hud.showCentered(Level.NAME + " COMPLETE", 100, 3, TftTouchShield.GREEN);
        Delay.millis(4000);
    }

    /** The card shown when the marine dies. */
    static void died() {
        Hud.drawStatus();
        Hud.showCentered("YOU DIED", 110, 4, TftTouchShield.RED);
        Delay.millis(2500);
    }

    /** The sky, painted at once: dark red at the top, burning red mid-way, lava orange at the horizon. */
    private static void sky() {
        for (int y = 0; y < ART_HEIGHT; y += 4) {
            TftTouchShield.fillRect(0, y, DisplayList.WIDTH, 4, skyColor(y));
        }
    }

    private static int skyColor(int y) {
        int t = y / 4 * 4 * 100 / ART_HEIGHT;
        if (t < 50) {
            return TftTouchShield.color(85 + (190 - 85) * t / 50, 8 + (35 - 8) * t / 50, 5 + 5 * t / 50);
        }
        return TftTouchShield.color(190 + (245 - 190) * (t - 50) / 50, 35 + (115 - 35) * (t - 50) / 50,
                10 + 10 * (t - 50) / 50);
    }

    private static void mountains() {
        for (int i = 0; i < LEFT_PEAK.length; i++) {
            int top = LEFT_PEAK[i];
            TftTouchShield.fillRect(i * 6, top, 6, ART_HEIGHT - top, ROCK);
            TftTouchShield.fillRect(i * 6, top, 6, 3, ROCK_DARK);
        }
        for (int i = 0; i < RIGHT_PEAK.length; i++) {
            int top = RIGHT_PEAK[i];
            int x = DisplayList.WIDTH - (RIGHT_PEAK.length - i) * 7;
            TftTouchShield.fillRect(x, top, 7, ART_HEIGHT - top, ROCK);
            TftTouchShield.fillRect(x, top, 7, 3, ROCK_DARK);
        }
    }

    /** The lava's color at a pixel, so cut-out corners match the flow around them. */
    private static int lavaTone(int x, int y) {
        int index = (tick + (x / FLOOD_CELL + y / FLOOD_CELL) / 2 + (x + y) / 64) % LAVA_FLOW.length;
        return LAVA_FLOW[index];
    }

    /** A one-pixel-high run of lava, cell color by cell color. */
    private static void lavaRun(int x, int y, int length) {
        int at = x;
        while (at < x + length) {
            int end = Math.min(x + length, (at / FLOOD_CELL + 1) * FLOOD_CELL);
            TftTouchShield.fillRect(at, y, end - at, 1, lavaTone(at, y));
            at = end;
        }
    }

    /** Cuts a diagonal corner of {@code size} pixels off a letter, back to the lava behind it. */
    private static void chamfer(int x, int y, int size, boolean right, boolean bottom) {
        for (int i = 0; i < size; i++) {
            int row = bottom ? y - i : y + i;
            int length = size - i;
            lavaRun(right ? x - length : x, row, length);
        }
    }

    /**
     * One letter of the logo, after the box art: a blue circuit-board top over orange brick meeting along
     * a row of peaks, a bevelled front face whose bottom edge slopes, a thick extruded side and underside
     * in red, and a tapered slot where the letter is hollow.
     */
    private static void logo(int letter) {
        int x = LOGO_X[letter];
        int w = LOGO_W[letter];
        int top = LOGO_TOP[letter];
        int left = top + LOGO_H[letter];
        int right = top + LOGO_H_RIGHT[letter];
        int deepest = Math.max(left, right);
        int split = top + (Math.min(left, right) - top) * 5 / 9;
        TftTouchShield.fillRect(x, top, w, deepest - top, CIRCUIT_BLUE);
        for (int row = top + 4; row < split + 6; row += 3) {
            for (int run = 0; run < 3; run++) {
                int at = x + 5 + (row * 7 + run * 23) % (w - 14);
                TftTouchShield.drawHorizontalLine(at, row, 4 + (row + run * 5) % 9, CIRCUIT_TRACE);
            }
            TftTouchShield.drawHorizontalLine(x + 4 + row % 7, row + 1, 3, CIRCUIT_GLINT);
        }
        for (int column = x + 6; column < x + w - 4; column += 7) {
            int from = top + 5 + column * 3 % 11;
            TftTouchShield.drawVerticalLine(column, from, 8 + column % 9, CIRCUIT_TRACE);
            TftTouchShield.drawVerticalLine(column + 1, from + 2, 4, CIRCUIT_GLINT);
        }
        for (int column = 0; column < w; column += 2) {
            int phase = (column + letter * 9) % 40;
            int peak = phase < 20 ? phase : 40 - phase;
            int border = split + 8 - peak * 20 / 20;
            TftTouchShield.fillRect(x + column, border, 2, deepest - border, BRICK);
        }
        for (int row = split - 6; row < deepest; row += 6) {
            TftTouchShield.drawHorizontalLine(x, row, w, BRICK_MORTAR);
            int offset = (row / 6 % 2) * 7;
            for (int column = x + offset; column < x + w; column += 14) {
                int cap = Math.max(row - 1, split - 6);
                TftTouchShield.drawVerticalLine(column, cap, Math.min(6, deepest - cap), BRICK_MORTAR);
                TftTouchShield.drawHorizontalLine(column + 1, cap + 1, 5, BRICK_LIGHT);
            }
        }
        slots(letter, x, w, top, Math.min(left, right));
        slope(x, w, left, right, deepest);
        TftTouchShield.fillRect(x, top, w, 3, EDGE_LIGHT);
        TftTouchShield.fillRect(x, top, 4, left - top, EDGE);
        TftTouchShield.drawVerticalLine(x, top, left - top, EDGE_LIGHT);
        TftTouchShield.fillRect(x + w - 4, top, 4, right - top, EDGE_DARK);
        TftTouchShield.fillRect(x + w, top + 3, 8, right - top, EDGE);
        TftTouchShield.fillRect(x + w + 5, top + 3, 3, right - top, EDGE_DARK);
        TftTouchShield.drawVerticalLine(x + w, top + 3, right - top, EDGE_LIGHT);
        int cut = letter == 0 ? 26 : letter == 3 ? 10 : 16;
        chamfer(x + w + 8, top, cut, true, false);
        chamfer(x + w + 8, right + 6, cut, true, true);
        if (letter != 0) {
            chamfer(x, top, cut, false, false);
            chamfer(x, left + 6, cut, false, true);
        }
    }

    /** Trims the body to its sloping bottom edge and adds the red underside beneath it. */
    private static void slope(int x, int w, int left, int right, int deepest) {
        for (int row = Math.min(left, right); row < deepest && left != right; row++) {
            int column = Math.max(0, Math.min(w, (row - left) * w / (right - left)));
            if (right < left) {
                lavaRun(x + column, row, w - column);
            } else if (column > 0) {
                lavaRun(x, row, column);
            }
        }
        for (int depth = 0; depth < 6; depth++) {
            DisplayList.drawLine(x, left + depth, x + w + 8, right + depth, depth < 2 ? EDGE : EDGE_DARK);
        }
    }

    /** The hollow inside the letter: one tapered slot for D and O, two slots and a V for M. */
    private static void slots(int letter, int x, int w, int top, int bottom) {
        int from = top + 14;
        int to = bottom - 20;
        if (letter < 3) {
            slot(x + w / 2 - 6, from, 12, 8, to - from);
        } else {
            slot(x + 13, from, 8, 5, to - from);
            slot(x + w - 21, from, 8, 5, to - from);
            for (int step = 0; step < 10; step++) {
                TftTouchShield.fillRect(x + 20 + step * 3 / 2, from + step * 6, 6, 8, SLOT);
                TftTouchShield.fillRect(x + w - 26 - step * 3 / 2, from + step * 6, 6, 8, SLOT);
                TftTouchShield.drawVerticalLine(x + 20 + step * 3 / 2, from + step * 6, 8, SLOT_LIGHT);
            }
        }
    }

    /** A slot whose sides converge from {@code topWidth} to {@code bottomWidth} as it recedes. */
    private static void slot(int x, int y, int topWidth, int bottomWidth, int h) {
        int taper = (topWidth - bottomWidth) / 2;
        quad(y, y + h, x, x + taper, x + topWidth - 1, x + topWidth - 1 - taper, SLOT);
        DisplayList.drawLine(x, y, x + taper, y + h, SLOT_LIGHT);
        DisplayList.drawLine(x + topWidth - 1, y, x + topWidth - 1 - taper, y + h, EDGE_DARK);
    }

    /** The static part of the scene: the claws and horned head reaching in from the right edge. */
    private static void scenery() {
        TftTouchShield.fillRect(300, 150, 20, 48, DEMON_DARK);
        demon(312, 158, 12, false);
    }

    /** One frame of the live lava: the flow, bubbles popping on it, and the demons rising out of it. */
    private static void lavaFx(int frame) {
        lavaFlood(0);
        for (int bubble = 0; bubble < 4; bubble++) {
            int x = Random.nextInt(8, DisplayList.WIDTH - 8);
            int y = Random.nextInt(8, DisplayList.HEIGHT - 8);
            int r = 2 + Random.nextInt(3);
            if (!covered(x - r - 1, y - r - 1) && !covered(x + r, y + r)) {
                TftTouchShield.fillCircle(x, y, r + 1, LAVA_FLOW[2]);
                TftTouchShield.fillCircle(x, y, r, LAVA_FLOW[4]);
            }
        }
        if (DEMONS) {
            demon(26, 218, 14, true);
            demon(290, 220, 17, true);
            demon(244, 224, 12, false);
        }
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

    /** A thick stroke from one point to another: a run of discs. */
    private static void stroke(int x0, int y0, int x1, int y1, int radius, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) / 2 + 1;
        for (int step = 0; step <= steps; step++) {
            TftTouchShield.fillCircle(x0 + (x1 - x0) * step / steps, y0 + (y1 - y0) * step / steps, radius, color);
        }
    }

    /** A quadrilateral between two horizontal edges, filled row by row. */
    private static void quad(int yTop, int yBottom, int leftTop, int leftBottom, int rightTop, int rightBottom,
            int color) {
        int rows = yBottom - yTop;
        for (int row = 0; row <= rows; row++) {
            int left = leftTop + (leftBottom - leftTop) * row / rows;
            int right = rightTop + (rightBottom - rightTop) * row / rows;
            TftTouchShield.fillRect(left, yTop + row, right - left + 1, 1, color);
        }
    }

    /**
     * The marine, after the box art: a brown-grey helmet with a dark visor and vented face guard, a green
     * shirt over bare muscular arms and belly, a belt with pouches, olive pants, and the minigun in one
     * hand while the other throws a plasma ball.
     */
    private static void marine() {
        // legs: olive, torn, braced wide
        stroke(98, 174, 80, 238, 12, PANTS);
        stroke(128, 172, 158, 238, 12, PANTS);
        stroke(100, 176, 86, 236, 4, PANTS_DARK);
        stroke(132, 176, 156, 236, 4, PANTS_DARK);
        TftTouchShield.fillRect(88, 192, 7, 5, SKIN_DARK);
        // belt and pouches
        quad(162, 175, 84, 82, 152, 156, BELT);
        TftTouchShield.fillRect(124, 168, 26, 14, PANTS_DARK);
        TftTouchShield.fillRect(126, 170, 22, 3, PANTS);
        TftTouchShield.drawVerticalLine(137, 170, 12, BELT);
        // bare belly under the shirt, abs picked out
        quad(142, 164, 88, 90, 148, 148, SKIN);
        quad(142, 164, 124, 125, 148, 148, SKIN_DARK);
        TftTouchShield.drawVerticalLine(116, 144, 20, SKIN_DARK);
        for (int row = 148; row < 164; row += 5) {
            TftTouchShield.drawHorizontalLine(92, row, 24, SKIN_DARK);
            TftTouchShield.drawHorizontalLine(94, row + 1, 20, SKIN_LIGHT);
        }
        // the shirt: broad shoulders, short sleeves, shaded on the right
        quad(100, 146, 68, 88, 164, 150, ARMOR);
        quad(100, 146, 128, 128, 164, 150, ARMOR_DARK);
        quad(100, 112, 70, 74, 160, 162, ARMOR_LIGHT);
        TftTouchShield.fillCircle(64, 120, 14, ARMOR);
        TftTouchShield.fillCircle(60, 115, 6, ARMOR_LIGHT);
        TftTouchShield.fillCircle(162, 106, 14, ARMOR);
        TftTouchShield.fillCircle(166, 110, 7, ARMOR_DARK);
        TftTouchShield.drawVerticalLine(116, 104, 40, ARMOR_DARK);
        // his left arm: bare, bent down to the gun
        stroke(62, 128, 76, 160, 10, SKIN);
        stroke(76, 162, 112, 186, 8, SKIN);
        stroke(66, 134, 80, 160, 3, SKIN_LIGHT);
        stroke(82, 164, 108, 182, 2, SKIN_DARK);
        // the gun: a minigun, dark housing and a cluster of barrels
        stroke(112, 180, 172, 206, 8, STEEL_DARK);
        stroke(116, 176, 172, 200, 3, STEEL);
        stroke(120, 182, 172, 207, 2, STEEL);
        stroke(124, 186, 168, 205, 2, STEEL);
        TftTouchShield.fillCircle(120, 188, 10, GLOVE);
        TftTouchShield.fillCircle(116, 185, 4, VISOR_GLINT);
        // his right arm: short sleeve, then bare muscle and torn flesh reaching out
        stroke(160, 106, 172, 124, 11, ARMOR);
        stroke(172, 124, 196, 148, 9, SKIN);
        stroke(176, 126, 196, 145, 3, SKIN_LIGHT);
        stroke(190, 144, 204, 153, 7, SKIN);
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
        quad(91, 101, 78, 80, 118, 116, VISOR);
        TftTouchShield.drawHorizontalLine(82, 93, 28, VISOR_GLINT);
        TftTouchShield.drawHorizontalLine(86, 95, 12, VISOR_GLINT);
        quad(101, 115, 82, 88, 116, 108, HELMET_DARK);
        for (int vent = 0; vent < 4; vent++) {
            TftTouchShield.drawHorizontalLine(88 + vent / 2, 104 + vent * 3, 20 - vent * 2, HELMET);
        }
        TftTouchShield.fillRect(94, 114, 12, 3, VISOR);
        marineShown = true;
        flicker(tick);
    }

    /** The muzzle flash, the plasma and the lava, repainted over the same footprint so nothing is left behind. */
    private static void flicker(int frame) {
        lavaFx(frame);
        if (!MARINE) {
            return;
        }
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
        stroke(224, 150, 276, 166, 8, bright ? PLASMA_B : FLASH_C);
        stroke(224, 150, 272, 164, 5, PLASMA_B);
        stroke(224, 150, 262, 161, 2, PLASMA_A);
        TftTouchShield.fillCircle(222, 148, 11, bright ? PLASMA_B : FLASH_C);
        TftTouchShield.fillCircle(222, 148, 8, PLASMA_A);
    }
}
