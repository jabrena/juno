package io.github.jabrena.juno.games.sundance;

import io.github.jabrena.juno.api.tft.TftTouchShield;

/** Double-buffered vector display list with clipping and incremental line redraw. */
final class DisplayList {
    private static final int WIDTH = 320;
    private static final int HEIGHT = 240;
    private static final int MAX_LINES = 180;
    private static final int L_STRIDE = 5;
    static final int LIST_SIZE = MAX_LINES * L_STRIDE;

    static int front;
    static int shown;
    static int built;

    private DisplayList() {
    }

    static void begin() {
        built = 0;
    }

    /** Clips a line below the header and appends it to the list being built. */
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
            int x = clippedX(code, x0, y0, x1, y1);
            int y = clippedY(code, x0, y0, x1, y1);
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
        append(lines, x0, y0, x1, y1, color);
    }

    private static int clippedX(int code, int x0, int y0, int x1, int y1) {
        if ((code & 8) != 0) {
            return x0 + (x1 - x0) * (Hud.HEADER - y0) / (y1 - y0);
        }
        if ((code & 4) != 0) {
            return x0 + (x1 - x0) * (HEIGHT - 1 - y0) / (y1 - y0);
        }
        return (code & 2) != 0 ? WIDTH - 1 : 0;
    }

    private static int clippedY(int code, int x0, int y0, int x1, int y1) {
        if ((code & 8) != 0) {
            return Hud.HEADER;
        }
        if ((code & 4) != 0) {
            return HEIGHT - 1;
        }
        int edge = (code & 2) != 0 ? WIDTH - 1 : 0;
        return y0 + (y1 - y0) * (edge - x0) / (x1 - x0);
    }

    private static void append(short[] lines, int x0, int y0, int x1, int y1, int color) {
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
        if (x < 0) {
            code = code | 1;
        } else if (x > WIDTH - 1) {
            code = code | 2;
        }
        if (y < Hud.HEADER) {
            code = code | 8;
        } else if (y > HEIGHT - 1) {
            code = code | 4;
        }
        return code;
    }

    static void present(short[] lines) {
        int oldBase = front * LIST_SIZE;
        int newBase = (1 - front) * LIST_SIZE;
        eraseChanged(lines, oldBase);
        drawChanged(lines, oldBase, newBase);
        front = 1 - front;
        shown = built;
    }

    private static void eraseChanged(short[] lines, int oldBase) {
        for (int index = 0; index < shown; index++) {
            if (changed(lines, index)) {
                drawListed(lines, oldBase + index * L_STRIDE, Hud.SPACE);
            }
        }
    }

    private static void drawChanged(short[] lines, int oldBase, int newBase) {
        for (int index = 0; index < built; index++) {
            int at = newBase + index * L_STRIDE;
            boolean draw = changed(lines, index) || crossesErasedLine(lines, oldBase, at);
            if (draw) {
                drawListed(lines, at, lines[at + 4] & 0xFFFF);
            }
        }
    }

    private static boolean crossesErasedLine(short[] lines, int oldBase, int at) {
        for (int old = 0; old < shown; old++) {
            if (changed(lines, old) && overlaps(lines, oldBase + old * L_STRIDE, at)) {
                return true;
            }
        }
        return false;
    }

    private static boolean changed(short[] lines, int index) {
        if (index >= shown || index >= built) {
            return true;
        }
        int oldAt = front * LIST_SIZE + index * L_STRIDE;
        int newAt = (1 - front) * LIST_SIZE + index * L_STRIDE;
        for (int field = 0; field < L_STRIDE; field++) {
            if (lines[oldAt + field] != lines[newAt + field]) {
                return true;
            }
        }
        return false;
    }

    private static boolean overlaps(short[] lines, int first, int second) {
        return Math.min(lines[first], lines[first + 2]) <= Math.max(lines[second], lines[second + 2])
                && Math.min(lines[second], lines[second + 2]) <= Math.max(lines[first], lines[first + 2])
                && Math.min(lines[first + 1], lines[first + 3]) <= Math.max(lines[second + 1], lines[second + 3])
                && Math.min(lines[second + 1], lines[second + 3]) <= Math.max(lines[first + 1], lines[first + 3]);
    }

    private static void drawListed(short[] lines, int at, int color) {
        drawLine(lines[at], lines[at + 1], lines[at + 2], lines[at + 3], color);
    }

    /** Bresenham line; runs of pixels on the same row become one fill. */
    private static void drawLine(int x0, int y0, int x1, int y1, int color) {
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
