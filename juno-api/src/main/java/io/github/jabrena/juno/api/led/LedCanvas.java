package io.github.jabrena.juno.api.led;

/**
 * A 12x8 pixel buffer for the UNO R4 WiFi's built-in LED matrix, plus the shape and text drawing
 * operations that paint into it. Callers create one {@code boolean[HEIGHT][WIDTH]} frame with
 * {@code new boolean[LedCanvas.HEIGHT][LedCanvas.WIDTH]}, draw into it with the methods below, and
 * send it to the hardware with {@link #show(boolean[][])}.
 */
public final class LedCanvas {
    public static final int WIDTH = 12;
    public static final int HEIGHT = 8;

    private LedCanvas() {
    }

    public static boolean inBounds(int x, int y) {
        return x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT;
    }

    /** Turns every pixel in {@code frame} off. */
    public static void clear(boolean[][] frame) {
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                frame[y][x] = false;
            }
        }
    }

    /** Lights pixel (x, y), silently ignoring coordinates outside the 12x8 frame. */
    public static void setPixel(boolean[][] frame, int x, int y) {
        if (inBounds(x, y)) {
            frame[y][x] = true;
        }
    }

    /**
     * Packs frame word {@code wordIndex} (0-2) out of {@code frame}'s 96 pixels, MSB-first
     * left-to-right/top-to-bottom, exactly as {@link LedMatrix#loadFrame(int, int, int)} expects.
     */
    public static int packWord(boolean[][] frame, int wordIndex) {
        int result = 0;
        int base = wordIndex * 32;
        for (int bit = 0; bit < 32; bit++) {
            int index = base + bit;
            int y = index / WIDTH;
            int x = index - y * WIDTH;
            if (frame[y][x]) {
                result = result | (1 << (31 - bit));
            }
        }
        return result;
    }

    /** Packs {@code frame} and loads it onto the hardware via {@link LedMatrix#loadFrame(int, int, int)}. */
    public static void show(boolean[][] frame) {
        LedMatrix.loadFrame(packWord(frame, 0), packWord(frame, 1), packWord(frame, 2));
    }

    /**
     * Fills a solid {@code width x height} rectangle whose top-left corner is at
     * ({@code originX}, {@code originY}).
     */
    public static void fillRect(boolean[][] frame, int originX, int originY, int width, int height) {
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                setPixel(frame, originX + col, originY + row);
            }
        }
    }

    /**
     * Draws a 1-pixel border around a {@code width x height} rectangle whose top-left corner is at
     * ({@code originX}, {@code originY}), leaving its interior unlit.
     */
    public static void drawRect(boolean[][] frame, int originX, int originY, int width, int height) {
        int lastCol = width - 1;
        int lastRow = height - 1;
        for (int col = 0; col < width; col++) {
            setPixel(frame, originX + col, originY);
            setPixel(frame, originX + col, originY + lastRow);
        }
        for (int row = 0; row < height; row++) {
            setPixel(frame, originX, originY + row);
            setPixel(frame, originX + lastCol, originY + row);
        }
    }

    /** Fills the solid triangle with the given three vertices. */
    public static void fillTriangle(boolean[][] frame, int x1, int y1, int x2, int y2, int x3, int y3) {
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
                    setPixel(frame, x, y);
                }
            }
        }
    }

    /** Draws a 1-pixel outline of the triangle with the given three vertices. */
    public static void drawTriangle(boolean[][] frame, int x1, int y1, int x2, int y2, int x3, int y3) {
        drawLine(frame, x1, y1, x2, y2);
        drawLine(frame, x2, y2, x3, y3);
        drawLine(frame, x3, y3, x1, y1);
    }

    /** Draws a line between (x0, y0) and (x1, y1), both endpoints inclusive, using Bresenham's algorithm. */
    public static void drawLine(boolean[][] frame, int x0, int y0, int x1, int y1) {
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
            setPixel(frame, x, y);
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
    }

    /** Fills the solid circle of the given radius centered at (centerX, centerY). */
    public static void fillCircle(boolean[][] frame, int centerX, int centerY, int radius) {
        int radiusSquared = radius * radius;
        for (int y = -radius; y <= radius; y++) {
            for (int x = -radius; x <= radius; x++) {
                if (x * x + y * y <= radiusSquared) {
                    setPixel(frame, centerX + x, centerY + y);
                }
            }
        }
    }

    /**
     * Draws a 1-pixel outline of the circle of the given radius centered at (centerX, centerY),
     * using the midpoint circle algorithm.
     */
    public static void drawCircle(boolean[][] frame, int centerX, int centerY, int radius) {
        int x = radius;
        int y = 0;
        int error = 1 - radius;
        while (x >= y) {
            setPixel(frame, centerX + x, centerY + y);
            setPixel(frame, centerX + y, centerY + x);
            setPixel(frame, centerX - y, centerY + x);
            setPixel(frame, centerX - x, centerY + y);
            setPixel(frame, centerX - x, centerY - y);
            setPixel(frame, centerX - y, centerY - x);
            setPixel(frame, centerX + y, centerY - x);
            setPixel(frame, centerX + x, centerY - y);
            y = y + 1;
            if (error < 0) {
                error = error + 2 * y + 1;
            } else {
                x = x - 1;
                error = error + 2 * (y - x) + 1;
            }
        }
    }

    /** Draws digit (0-9), using {@link LedMatrixFont}. */
    public static void drawDigit(boolean[][] frame, int digit, int originX, int originY) {
        for (int row = 0; row < LedMatrixFont.GLYPH_HEIGHT; row++) {
            for (int col = 0; col < LedMatrixFont.GLYPH_WIDTH; col++) {
                if (LedMatrixFont.digitPixel(digit, col, row)) {
                    setPixel(frame, originX + col, originY + row);
                }
            }
        }
    }

    /** Draws letter (0 = 'A' .. 25 = 'Z'), using {@link LedMatrixFont}. */
    public static void drawLetter(boolean[][] frame, int letter, int originX, int originY) {
        for (int row = 0; row < LedMatrixFont.GLYPH_HEIGHT; row++) {
            for (int col = 0; col < LedMatrixFont.GLYPH_WIDTH; col++) {
                if (LedMatrixFont.letterPixel(letter, col, row)) {
                    setPixel(frame, originX + col, originY + row);
                }
            }
        }
    }

    /**
     * Draws the printable ASCII character with the given code (32 space .. 126 '~'), using
     * {@link LedMatrixFontAscii}. Reach for {@link #drawDigit}/{@link #drawLetter} instead when a
     * program only ever prints digits and/or uppercase letters, to avoid linking in the full ASCII
     * table.
     */
    public static void drawChar(boolean[][] frame, int asciiCode, int originX, int originY) {
        for (int row = 0; row < LedMatrixFontAscii.GLYPH_HEIGHT; row++) {
            for (int col = 0; col < LedMatrixFontAscii.GLYPH_WIDTH; col++) {
                if (LedMatrixFontAscii.charPixel(asciiCode, col, row)) {
                    setPixel(frame, originX + col, originY + row);
                }
            }
        }
    }

    /** Draws digit (0-9) with the small font, using {@link LedMatrixFontSmall}. */
    public static void drawSmallDigit(boolean[][] frame, int digit, int originX, int originY) {
        for (int row = 0; row < LedMatrixFontSmall.GLYPH_HEIGHT; row++) {
            for (int col = 0; col < LedMatrixFontSmall.GLYPH_WIDTH; col++) {
                if (LedMatrixFontSmall.digitPixel(digit, col, row)) {
                    setPixel(frame, originX + col, originY + row);
                }
            }
        }
    }

    /**
     * Draws a one-decimal-digit reading such as "2.5" (wholeDigit = 2, fractionDigit = 5): a small
     * digit, a decimal point, and another small digit, laid out left to right from originX. The
     * group is 9 columns wide (3 + 1 gap + 1 point + 1 gap + 3), leaving 3 of the matrix's 12
     * columns free to center it, e.g. originX = 1.
     */
    public static void drawDecimal(boolean[][] frame, int wholeDigit, int fractionDigit,
            int originX, int originY) {
        drawSmallDigit(frame, wholeDigit, originX, originY);
        int pointX = originX + LedMatrixFontSmall.GLYPH_WIDTH + 1;
        for (int row = 0; row < LedMatrixFontSmall.GLYPH_HEIGHT; row++) {
            if (LedMatrixFontSmall.pointPixel(row)) {
                setPixel(frame, pointX, originY + row);
            }
        }
        int fractionX = pointX + 2;
        drawSmallDigit(frame, fractionDigit, fractionX, originY);
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
