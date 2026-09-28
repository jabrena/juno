package io.github.jabrena.juno.games.startrek;

import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * Screen layout, the double display list clipped to whichever view is being drawn, and its
 * Bresenham lines.
 */
final class DisplayList {
    // Screen: landscape, header on top, tactical view on the left, bridge view and buttons on the right.
    static final int WIDTH = 320;
    static final int HEIGHT = 240;
    static final int HEADER = 20;
    static final int TACTICAL_RIGHT = 219;
    static final int TACTICAL_X = 110;
    static final int TACTICAL_Y = 130;
    static final int PANEL_X = 222;
    static final int BRIDGE_TOP = 21;
    static final int BRIDGE_BOTTOM = 98;
    static final int BUTTON_Y = 102;
    static final int BUTTON_HEIGHT = 44;
    static final int SPACE = TftTouchShield.BLACK;

    // Display lists: two of MAX_LINES lines (x0, y0, x1, y1, color), the one on screen and the next.
    private static final int MAX_LINES = 200;
    private static final int L_STRIDE = 5;
    static final int LIST_SIZE = MAX_LINES * L_STRIDE;

    static int front;
    static int shown;
    static int built;
    private static int clipLeft;
    private static int clipTop;
    private static int clipRight;
    private static int clipBottom;

    private DisplayList() {
    }

    static void begin() {
        built = 0;
    }

    static void setClip(int left, int top, int right, int bottom) {
        clipLeft = left;
        clipTop = top;
        clipRight = right;
        clipBottom = bottom;
    }

    /** Clips a screen line to the current view (Cohen-Sutherland) and appends it to the list being built. */
    static void addLine(short[] lines, int x0, int y0, int x1, int y1, int color) {
        int code0 = outCode(x0, y0);
        int code1 = outCode(x1, y1);
        int rounds = 0;
        while ((code0 | code1) != 0) {
            if ((code0 & code1) != 0 || rounds == 4) {
                return;
            }
            rounds = rounds + 1;
            int code = code0 != 0 ? code0 : code1;
            int x;
            int y;
            if ((code & 8) != 0) {
                x = x0 + (x1 - x0) * (clipTop - y0) / (y1 - y0);
                y = clipTop;
            } else if ((code & 4) != 0) {
                x = x0 + (x1 - x0) * (clipBottom - y0) / (y1 - y0);
                y = clipBottom;
            } else if ((code & 2) != 0) {
                y = y0 + (y1 - y0) * (clipRight - x0) / (x1 - x0);
                x = clipRight;
            } else {
                y = y0 + (y1 - y0) * (clipLeft - x0) / (x1 - x0);
                x = clipLeft;
            }
            if (code == code0) {
                x0 = x;
                y0 = y;
                code0 = outCode(x0, y0);
            } else {
                x1 = x;
                y1 = y;
                code1 = outCode(x1, y1);
            }
        }
        if (built == MAX_LINES) {
            return;
        }
        int at = (1 - front) * LIST_SIZE + built * L_STRIDE;
        lines[at] = (short) x0;
        lines[at + 1] = (short) y0;
        lines[at + 2] = (short) x1;
        lines[at + 3] = (short) y1;
        lines[at + 4] = (short) color;
        built = built + 1;
    }

    private static int outCode(int x, int y) {
        int code = 0;
        if (x < clipLeft) {
            code = code | 1;
        } else if (x > clipRight) {
            code = code | 2;
        }
        if (y < clipTop) {
            code = code | 8;
        } else if (y > clipBottom) {
            code = code | 4;
        }
        return code;
    }

    /**
     * Shows the list just built: erases the previous frame's lines that changed, then draws the new
     * ones plus any unchanged line an erased one may have crossed.
     */
    static void present(short[] lines) {
        int oldBase = front * LIST_SIZE;
        int newBase = (1 - front) * LIST_SIZE;
        for (int i = 0; i < shown; i++) {
            if (changed(lines, i)) {
                drawListed(lines, oldBase + i * L_STRIDE, SPACE);
            }
        }
        for (int j = 0; j < built; j++) {
            int at = newBase + j * L_STRIDE;
            boolean draw = changed(lines, j);
            for (int i = 0; i < shown && !draw; i++) {
                if (changed(lines, i) && overlaps(lines, oldBase + i * L_STRIDE, at)) {
                    draw = true;
                }
            }
            if (draw) {
                drawListed(lines, at, lines[at + 4] & 0xFFFF);
            }
        }
        front = 1 - front;
        shown = built;
    }

    /** Whether entry {@code i} differs between the list on screen and the one just built. */
    private static boolean changed(short[] lines, int i) {
        if (i >= shown || i >= built) {
            return true;
        }
        int a = front * LIST_SIZE + i * L_STRIDE;
        int b = (1 - front) * LIST_SIZE + i * L_STRIDE;
        for (int k = 0; k < L_STRIDE; k++) {
            if (lines[a + k] != lines[b + k]) {
                return true;
            }
        }
        return false;
    }

    private static boolean overlaps(short[] lines, int a, int b) {
        return Math.min(lines[a], lines[a + 2]) <= Math.max(lines[b], lines[b + 2])
                && Math.min(lines[b], lines[b + 2]) <= Math.max(lines[a], lines[a + 2])
                && Math.min(lines[a + 1], lines[a + 3]) <= Math.max(lines[b + 1], lines[b + 3])
                && Math.min(lines[b + 1], lines[b + 3]) <= Math.max(lines[a + 1], lines[a + 3]);
    }

    private static void drawListed(short[] lines, int at, int color) {
        drawLine(lines[at], lines[at + 1], lines[at + 2], lines[at + 3], color);
    }

    /** Blanks both views and forgets what the display list had drawn there. */
    static void clearView() {
        TftTouchShield.fillRect(0, HEADER, WIDTH, HEIGHT - HEADER, SPACE);
        shown = 0;
    }

    /** Bresenham line; runs of pixels on the same row become one fill. */
    static void drawLine(int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int stepX = x0 > x1 ? -1 : 1;
        int stepY = y0 > y1 ? -1 : 1;
        int error = dx + dy;
        int x = x0;
        int y = y0;
        int runStart = x0;
        while (x != x1 || y != y1) {
            int twice = 2 * error;
            int nextX = x;
            int nextY = y;
            if (twice >= dy) {
                error = error + dy;
                nextX = x + stepX;
            }
            if (twice <= dx) {
                error = error + dx;
                nextY = y + stepY;
            }
            if (nextY != y) {
                fillRun(runStart, x, y, color);
                runStart = nextX;
            }
            x = nextX;
            y = nextY;
        }
        fillRun(runStart, x, y, color);
    }

    private static void fillRun(int from, int to, int y, int color) {
        int left = Math.min(from, to);
        int right = Math.max(from, to);
        TftTouchShield.fillRect(left, y, right - left + 1, 1, color);
    }
}
