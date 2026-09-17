package io.github.jabrena.juno.api;

/**
 * Draws rectangles (a square is just a rectangle with {@code width == height}), triangles, and
 * circles into a {@link LedMatrix#loadFrame(int, int, int)} word, either filled solid or as a
 * 1-pixel border outline. Callers OR the result into the same {@code word0}/{@code word1}/
 * {@code word2} triple as {@link LedMatrixText} glyphs, then pass all three to {@code loadFrame}.
 * Combine with {@link LedMatrixTransform} to rotate a shape's points before drawing it.
 */
public final class LedMatrixShapes {
    private LedMatrixShapes() {
    }

    /**
     * Fills a solid {@code width x height} rectangle whose top-left corner is at
     * ({@code originX}, {@code originY}).
     */
    public static int fillRect(int word, int wordIndex, int originX, int originY, int width, int height) {
        int result = word;
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                result = LedCanvas.setPixel(result, wordIndex, originX + col, originY + row);
            }
        }
        return result;
    }

    /**
     * Draws a 1-pixel border around a {@code width x height} rectangle whose top-left corner is at
     * ({@code originX}, {@code originY}), leaving its interior unlit.
     */
    public static int drawRect(int word, int wordIndex, int originX, int originY, int width, int height) {
        int result = word;
        int lastCol = width - 1;
        int lastRow = height - 1;
        for (int col = 0; col < width; col++) {
            result = LedCanvas.setPixel(result, wordIndex, originX + col, originY);
            result = LedCanvas.setPixel(result, wordIndex, originX + col, originY + lastRow);
        }
        for (int row = 0; row < height; row++) {
            result = LedCanvas.setPixel(result, wordIndex, originX, originY + row);
            result = LedCanvas.setPixel(result, wordIndex, originX + lastCol, originY + row);
        }
        return result;
    }

    /** Fills the solid triangle with the given three vertices. */
    public static int fillTriangle(int word, int wordIndex, int x1, int y1, int x2, int y2, int x3, int y3) {
        int result = word;
        int minX = min3(x1, x2, x3);
        int maxX = max3(x1, x2, x3);
        int minY = min3(y1, y2, y3);
        int maxY = max3(y1, y2, y3);
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                int edge1 = edgeValue(x1, y1, x2, y2, x, y);
                int edge2 = edgeValue(x2, y2, x3, y3, x, y);
                int edge3 = edgeValue(x3, y3, x1, y1, x, y);
                boolean hasNegative = edge1 < 0 || edge2 < 0 || edge3 < 0;
                boolean hasPositive = edge1 > 0 || edge2 > 0 || edge3 > 0;
                if (!(hasNegative && hasPositive)) {
                    result = LedCanvas.setPixel(result, wordIndex, x, y);
                }
            }
        }
        return result;
    }

    /** Draws a 1-pixel outline of the triangle with the given three vertices. */
    public static int drawTriangle(int word, int wordIndex, int x1, int y1, int x2, int y2, int x3, int y3) {
        int result = drawLine(word, wordIndex, x1, y1, x2, y2);
        result = drawLine(result, wordIndex, x2, y2, x3, y3);
        result = drawLine(result, wordIndex, x3, y3, x1, y1);
        return result;
    }

    /** Draws a line between (x0, y0) and (x1, y1), both endpoints inclusive, using Bresenham's algorithm. */
    public static int drawLine(int word, int wordIndex, int x0, int y0, int x1, int y1) {
        int result = word;
        int dx = abs(x1 - x0);
        int dy = abs(y1 - y0);
        int stepX = 1;
        if (x0 > x1) {
            stepX = -1;
        }
        int stepY = 1;
        if (y0 > y1) {
            stepY = -1;
        }
        int error = dx - dy;
        int x = x0;
        int y = y0;
        while (true) {
            result = LedCanvas.setPixel(result, wordIndex, x, y);
            if (x == x1 && y == y1) {
                break;
            }
            int doubledError = 2 * error;
            if (doubledError > -dy) {
                error = error - dy;
                x = x + stepX;
            }
            if (doubledError < dx) {
                error = error + dx;
                y = y + stepY;
            }
        }
        return result;
    }

    /** Fills the solid circle of the given radius centered at (centerX, centerY). */
    public static int fillCircle(int word, int wordIndex, int centerX, int centerY, int radius) {
        int result = word;
        int radiusSquared = radius * radius;
        for (int y = -radius; y <= radius; y++) {
            for (int x = -radius; x <= radius; x++) {
                if (x * x + y * y <= radiusSquared) {
                    result = LedCanvas.setPixel(result, wordIndex, centerX + x, centerY + y);
                }
            }
        }
        return result;
    }

    /**
     * Draws a 1-pixel outline of the circle of the given radius centered at (centerX, centerY),
     * using the midpoint circle algorithm.
     */
    public static int drawCircle(int word, int wordIndex, int centerX, int centerY, int radius) {
        int result = word;
        int x = radius;
        int y = 0;
        int error = 1 - radius;
        while (x >= y) {
            result = LedCanvas.setPixel(result, wordIndex, centerX + x, centerY + y);
            result = LedCanvas.setPixel(result, wordIndex, centerX + y, centerY + x);
            result = LedCanvas.setPixel(result, wordIndex, centerX - y, centerY + x);
            result = LedCanvas.setPixel(result, wordIndex, centerX - x, centerY + y);
            result = LedCanvas.setPixel(result, wordIndex, centerX - x, centerY - y);
            result = LedCanvas.setPixel(result, wordIndex, centerX - y, centerY - x);
            result = LedCanvas.setPixel(result, wordIndex, centerX + y, centerY - x);
            result = LedCanvas.setPixel(result, wordIndex, centerX + x, centerY - y);
            y = y + 1;
            if (error < 0) {
                error = error + 2 * y + 1;
            } else {
                x = x - 1;
                error = error + 2 * (y - x) + 1;
            }
        }
        return result;
    }

    /** The doubled signed area of triangle (ax, ay)-(bx, by)-(px, py); its sign gives the winding side. */
    private static int edgeValue(int ax, int ay, int bx, int by, int px, int py) {
        return (bx - ax) * (py - ay) - (by - ay) * (px - ax);
    }

    private static int abs(int value) {
        if (value < 0) {
            return -value;
        }
        return value;
    }

    private static int min3(int a, int b, int c) {
        int result = a;
        if (b < result) {
            result = b;
        }
        if (c < result) {
            result = c;
        }
        return result;
    }

    private static int max3(int a, int b, int c) {
        int result = a;
        if (b > result) {
            result = b;
        }
        if (c > result) {
            result = c;
        }
        return result;
    }
}
