package io.github.jabrena.juno.games.doom;

import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * The live lava behind the cover: a mosaic of coarse cells whose heat ripples diagonally frame by frame,
 * with bubbles popping on it. Cells under the logo or the prompt are left alone once those are painted.
 */
final class Lava {
    static final int CELL = 8;
    /** The lava's heat ramp as RGB565 constants (dark red up to yellow and back), so it is a flash table. */
    private static final int[] FLOW = {0x6840, 0xB8C0, 0xF2C1, 0xFD02, 0xFEA8, 0xFD02, 0xF2C1, 0xB8C0};

    static int tick;
    static int lettersShown;
    static boolean marineShown;
    static boolean promptShown;

    private Lava() {
    }

    static void reset() {
        tick = 0;
        lettersShown = 0;
        marineShown = false;
        promptShown = false;
    }

    /** One frame of the flow and its bubbles. */
    static void fx() {
        flood(0);
        for (int bubble = 0; bubble < 4; bubble++) {
            int x = Random.nextInt(8, DisplayList.WIDTH - 8);
            int y = Random.nextInt(8, DisplayList.HEIGHT - 8);
            int r = 2 + Random.nextInt(3);
            if (!covered(x - r - 1, y - r - 1) && !covered(x + r, y + r)) {
                TftTouchShield.fillCircle(x, y, r + 1, FLOW[2]);
                TftTouchShield.fillCircle(x, y, r, FLOW[4]);
            }
        }
    }

    /**
     * The screen below {@code top} flooded with lava, in coarse cells so a full-screen frame stays cheap.
     * Cells touching the logo or the marine are left alone once those are painted.
     */
    static void flood(int top) {
        for (int y = (top + CELL - 1) / CELL * CELL; y < DisplayList.HEIGHT; y += CELL) {
            for (int x = 0; x < DisplayList.WIDTH; x += CELL) {
                if (!covered(x, y)) {
                    TftTouchShield.fillRect(x, y, CELL, CELL, tone(x, y));
                }
            }
        }
    }

    /** Whether the cell at ({@code x}, {@code y}) overlaps a letter or the marine already painted over the lava. */
    private static boolean covered(int x, int y) {
        for (int letter = 0; letter < lettersShown; letter++) {
            int bottom = Logo.TOP[letter] + Math.max(Logo.H[letter], Logo.H_RIGHT[letter]) + 8;
            if (overlaps(x, y, Logo.X[letter], Logo.TOP[letter], Logo.X[letter] + Logo.W[letter] + 8, bottom)) {
                return true;
            }
        }
        if (promptShown && overlaps(x, y, Interludes.PROMPT_X, Interludes.PROMPT_Y, Interludes.PROMPT_X + Interludes.PROMPT_WIDTH, Interludes.PROMPT_Y + Interludes.PROMPT_HEIGHT)) {
            return true;
        }
        if (marineShown) {
            for (int box = 0; box < Figures.BOXES.length; box += 4) {
                if (overlaps(x, y, Figures.BOXES[box], Figures.BOXES[box + 1], Figures.BOXES[box + 2],
                        Figures.BOXES[box + 3])) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean overlaps(int x, int y, int left, int top, int right, int bottom) {
        return x < right && x + CELL > left && y < bottom && y + CELL > top;
    }

    /** The lava's color at a pixel, so cut-out corners match the flow around them. */
    static int tone(int x, int y) {
        int index = (tick + (x / CELL + y / CELL) / 2 + (x + y) / 64) % FLOW.length;
        return FLOW[index];
    }

    /** A one-pixel-high run of lava, cell color by cell color. */
    static void run(int x, int y, int length) {
        int at = x;
        while (at < x + length) {
            int end = Math.min(x + length, (at / CELL + 1) * CELL);
            TftTouchShield.fillRect(at, y, end - at, 1, tone(at, y));
            at = end;
        }
    }
}
