package io.github.jabrena.juno.games.doom;

import io.github.jabrena.juno.api.tft.TftTouchShield;

/** Pixel-art strokes and polygons the cover is composed from. */
final class Draw {
    private Draw() {
    }

    /** A thick stroke from one point to another: a run of discs. */
    static void stroke(int x0, int y0, int x1, int y1, int radius, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) / 2 + 1;
        for (int step = 0; step <= steps; step++) {
            TftTouchShield.fillCircle(x0 + (x1 - x0) * step / steps, y0 + (y1 - y0) * step / steps, radius, color);
        }
    }

    /** A quadrilateral between two horizontal edges, filled row by row. */
    static void quad(int yTop, int yBottom, int leftTop, int leftBottom, int rightTop, int rightBottom,
            int color) {
        int rows = yBottom - yTop;
        for (int row = 0; row <= rows; row++) {
            int left = leftTop + (leftBottom - leftTop) * row / rows;
            int right = rightTop + (rightBottom - rightTop) * row / rows;
            TftTouchShield.fillRect(left, yTop + row, right - left + 1, 1, color);
        }
    }
}
