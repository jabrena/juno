package io.github.jabrena.juno.games.doom;

import io.github.jabrena.juno.api.tft.TftTouchShield;

/** The DOOM logo of the cover: four bevelled letters, circuit-board blue over orange brick. */
final class Logo {
    private static final int CIRCUIT_BLUE = TftTouchShield.color(60, 80, 150);
    private static final int CIRCUIT_TRACE = TftTouchShield.color(25, 35, 80);
    private static final int CIRCUIT_GLINT = TftTouchShield.color(120, 150, 210);
    private static final int BRICK = TftTouchShield.color(235, 150, 60);
    private static final int BRICK_MORTAR = TftTouchShield.color(150, 80, 30);
    private static final int BRICK_LIGHT = TftTouchShield.color(250, 190, 100);
    static final int EDGE_LIGHT = TftTouchShield.color(240, 160, 130);
    static final int EDGE = TftTouchShield.color(215, 70, 30);
    static final int EDGE_DARK = TftTouchShield.color(120, 25, 12);
    private static final int SLOT = TftTouchShield.color(70, 14, 8);
    private static final int SLOT_LIGHT = TftTouchShield.color(250, 175, 130);
    static final int[] X = {25, 91, 155, 217};
    static final int[] W = {60, 58, 58, 70};
    static final int[] H = {122, 100, 98, 104};
    static final int[] H_RIGHT = {100, 96, 92, 126};
    static final int[] TOP = {46, 48, 50, 44};

    private Logo() {
    }

    /** Cuts a diagonal corner of {@code size} pixels off a letter, back to the lava behind it. */
    private static void chamfer(int x, int y, int size, boolean right, boolean bottom) {
        for (int i = 0; i < size; i++) {
            int row = bottom ? y - i : y + i;
            int length = size - i;
            Lava.run(right ? x - length : x, row, length);
        }
    }

    /**
     * One letter of the logo, after the box art: a blue circuit-board top over orange brick meeting along
     * a row of peaks, a bevelled front face whose bottom edge slopes, a thick extruded side and underside
     * in red, and a tapered slot where the letter is hollow.
     */
    static void draw(int letter) {
        int x = X[letter];
        int w = W[letter];
        int top = TOP[letter];
        int left = top + H[letter];
        int right = top + H_RIGHT[letter];
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
                Lava.run(x + column, row, w - column);
            } else if (column > 0) {
                Lava.run(x, row, column);
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
        Draw.quad(y, y + h, x, x + taper, x + topWidth - 1, x + topWidth - 1 - taper, SLOT);
        DisplayList.drawLine(x, y, x + taper, y + h, SLOT_LIGHT);
        DisplayList.drawLine(x + topWidth - 1, y, x + topWidth - 1 - taper, y + h, EDGE_DARK);
    }
}
