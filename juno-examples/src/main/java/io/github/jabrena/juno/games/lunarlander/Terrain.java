package io.github.jabrena.juno.games.lunarlander;

import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/** Generates and draws the moonscape and its three landing pads. */
final class Terrain {
    static final int WIDTH = 240;
    static final int PLAY_TOP = 32;
    static final int PLAY_BOTTOM = 270;
    static final int SKY = TftTouchShield.BLACK;
    static final int GROUND = 0x6B4D;
    static final int PAD = TftTouchShield.YELLOW;
    private static final int SEGMENT = 16;
    private static final int PADS = 3;

    private Terrain() {
    }

    static void generate(short[] ground, byte[] pads) {
        generateSlopes(ground);
        clearPads(pads);
        for (int pad = PADS - 1; pad >= 0; pad--) {
            addPad(ground, pads, pad);
        }
    }

    private static void generateSlopes(short[] ground) {
        int points = WIDTH / SEGMENT + 1;
        int previous = 0;
        for (int point = 0; point < points; point++) {
            int height = Random.nextInt(PLAY_BOTTOM - 110, PLAY_BOTTOM - 20);
            if (point < 2) {
                height = Math.max(height, PLAY_BOTTOM - 50);
            }
            if (point > 0) {
                interpolate(ground, (point - 1) * SEGMENT, previous, height);
            }
            previous = height;
        }
    }

    private static void clearPads(byte[] pads) {
        for (int column = 0; column < WIDTH; column++) {
            pads[column] = 0;
        }
    }

    private static void addPad(short[] ground, byte[] pads, int pad) {
        int width = pad == 1 ? 32 : pad == 2 ? 48 : 22;
        int multiplier = pad == 1 ? 3 : pad == 2 ? 2 : 5;
        int start = findPadStart(pads, width);
        if (start < 0) {
            return;
        }
        int end = start + width;
        int level = ground[start];
        for (int column = start; column < end; column++) {
            level = Math.max(level, ground[column]);
        }
        for (int column = start; column < end; column++) {
            ground[column] = (short) level;
            pads[column] = (byte) multiplier;
        }
        smooth(ground, start - SEGMENT, start, level);
        smooth(ground, end + SEGMENT - 1, end - 1, level);
    }

    private static int findPadStart(byte[] pads, int width) {
        for (int tries = 0; tries < 50; tries++) {
            int start = Random.nextInt(2, (WIDTH - width) / SEGMENT - 1) * SEGMENT;
            if (fits(pads, start, width)) {
                return start;
            }
        }
        return -1;
    }

    private static void interpolate(short[] ground, int from, int fromHeight, int toHeight) {
        for (int offset = 0; offset < SEGMENT && from + offset < WIDTH; offset++) {
            ground[from + offset] = (short) (fromHeight + (toHeight - fromHeight) * offset / SEGMENT);
        }
    }

    private static boolean fits(byte[] pads, int start, int width) {
        for (int column = start - SEGMENT; column < start + width + SEGMENT; column++) {
            if (column >= 0 && column < WIDTH && pads[column] != 0) {
                return false;
            }
        }
        return true;
    }

    private static void smooth(short[] ground, int far, int near, int level) {
        if (far < 0 || far >= WIDTH) {
            return;
        }
        int farHeight = ground[far];
        int steps = Math.abs(near - far);
        int direction = near < far ? -1 : 1;
        for (int offset = 1; offset < steps; offset++) {
            int column = far + direction * offset;
            if (column >= 0 && column < WIDTH) {
                ground[column] = (short) (farHeight + (level - farHeight) * offset / steps);
            }
        }
    }

    static void draw(short[] ground, byte[] pads) {
        for (int column = 0; column < WIDTH; column++) {
            drawColumn(ground, pads, column, ground[column]);
        }
        drawMultipliers(ground, pads);
    }

    private static void drawMultipliers(short[] ground, byte[] pads) {
        TftTouchShield.setTextSize(1);
        int column = 0;
        while (column < WIDTH) {
            if (pads[column] == 0) {
                column = column + 1;
            } else {
                int start = column;
                while (column < WIDTH && pads[column] == pads[start]) {
                    column = column + 1;
                }
                TftTouchShield.setTextColor(PAD, GROUND);
                TftTouchShield.setCursor((start + column) / 2 - 6, ground[start] + 6);
                TftTouchShield.print("x");
                TftTouchShield.print(pads[start]);
            }
        }
    }

    static void drawColumn(short[] ground, byte[] pads, int column, int fromY) {
        int top = ground[column];
        int start = Math.max(fromY, top);
        if (start >= PLAY_BOTTOM) {
            return;
        }
        if (pads[column] != 0 && start < top + 2) {
            TftTouchShield.drawVerticalLine(column, start, top + 2 - start, PAD);
            start = top + 2;
        }
        TftTouchShield.drawVerticalLine(column, start, PLAY_BOTTOM - start, GROUND);
    }
}
