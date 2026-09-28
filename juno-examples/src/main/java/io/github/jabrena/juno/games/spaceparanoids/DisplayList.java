package io.github.jabrena.juno.games.spaceparanoids;

import io.github.jabrena.juno.api.tft.TftTouchShield;

/** Double-buffered vector display list clipped to the 3D view above the control bar. */
final class DisplayList {
    static final int WIDTH = 320;
    static final int HEIGHT = 240;
    static final int HEADER = 20;
    static final int VIEW_BOTTOM = 199;
    static final int BAR_TOP = 200;
    static final int SPACE = TftTouchShield.BLACK;

    private static final int MAX_LINES = 150;
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
                x = x0 + (x1 - x0) * (HEADER - y0) / (y1 - y0);
                y = HEADER;
            } else if ((code & 4) != 0) {
                x = x0 + (x1 - x0) * (VIEW_BOTTOM - y0) / (y1 - y0);
                y = VIEW_BOTTOM;
            } else if ((code & 2) != 0) {
                y = y0 + (y1 - y0) * (WIDTH - 1 - x0) / (x1 - x0);
                x = WIDTH - 1;
            } else {
                y = y0 + (y1 - y0) * (0 - x0) / (x1 - x0);
                x = 0;
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
        if (x < 0) {
            code = code | 1;
        } else if (x > WIDTH - 1) {
            code = code | 2;
        }
        if (y < HEADER) {
            code = code | 8;
        } else if (y > VIEW_BOTTOM) {
            code = code | 4;
        }
        return code;
    }

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

    static void clearView() {
        TftTouchShield.fillRect(0, HEADER, WIDTH, BAR_TOP - HEADER, SPACE);
        shown = 0;
    }

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
