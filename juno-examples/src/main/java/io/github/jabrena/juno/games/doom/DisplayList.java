package io.github.jabrena.juno.games.doom;

import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * Double-buffered list of the frame's visible line fragments. Presenting a frame erases only the
 * lines that changed since the last one and draws only the new ones, so the view never flickers
 * through a full clear.
 *
 * <p>Lines are compared slot by slot, so the fixed overlay (the gun and the crosshair) is added first and
 * {@linkplain #pin() pinned}: it keeps the same slots every frame however many walls follow, and is drawn
 * last so it stays on top.
 */
final class DisplayList {
    static final int WIDTH = 320;
    static final int HEIGHT = 240;
    /** The view runs from this row (row 0 stays free as the renderer's clip sentinel) down to {@link #VIEW_BOTTOM}. */
    static final int VIEW_TOP = 1;
    /** The first row of the status bar under the view. */
    static final int VIEW_BOTTOM = 200;
    static final int BACKGROUND = TftTouchShield.BLACK;

    static final int MAX_LINES = 240;
    private static final int L_STRIDE = 5;
    static final int LIST_SIZE = MAX_LINES * L_STRIDE;

    private static int front;
    private static int shown;
    private static int built;
    private static int pinned;

    /** TFT fills sent (each one address window), pixels written and lines dropped for want of room. */
    static int windows;
    static int pixels;
    static int dropped;

    private DisplayList() {
    }

    static void begin() {
        built = 0;
        pinned = 0;
    }

    /** Marks the lines added so far as the overlay: drawn after the rest, on top of it. */
    static void pin() {
        pinned = built;
    }

    /** Queues a line already inside the view (the renderer clips every fragment to the screen). */
    static void add(short[] lines, int x0, int y0, int x1, int y1, int color) {
        if (built == MAX_LINES) {
            dropped = dropped + 1;
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

    /**
     * Erases the lines that changed since the last frame and draws the new ones. {@code changes} is scratch space of
     * at least {@link #MAX_LINES} bytes, overwritten with which slots changed.
     */
    static void present(short[] lines, byte[] changes) {
        int slots = Math.max(shown, built);
        for (int i = 0; i < slots; i++) {
            changes[i] = (byte) (changed(lines, i) ? 1 : 0);
        }
        int oldBase = front * LIST_SIZE;
        for (int i = 0; i < shown; i++) {
            if (changes[i] != 0) {
                drawListed(lines, oldBase + i * L_STRIDE, BACKGROUND);
            }
        }
        for (int j = pinned; j < built; j++) {
            show(lines, changes, j, false);
        }
        for (int j = 0; j < pinned; j++) {
            show(lines, changes, j, true);
        }
        front = 1 - front;
        shown = built;
    }

    static void clearView() {
        TftTouchShield.fillRect(0, VIEW_TOP, WIDTH, VIEW_BOTTOM - VIEW_TOP, BACKGROUND);
        shown = 0;
    }

    /**
     * Draws new line {@code j} when it changed or an erased line crossed it; an overlay line also when a changed line
     * was just drawn across it.
     */
    private static void show(short[] lines, byte[] changes, int j, boolean overlay) {
        int oldBase = front * LIST_SIZE;
        int newBase = (1 - front) * LIST_SIZE;
        int at = newBase + j * L_STRIDE;
        boolean draw = changes[j] != 0;
        for (int i = 0; i < shown && !draw; i++) {
            draw = changes[i] != 0 && overlaps(lines, oldBase + i * L_STRIDE, at);
        }
        for (int i = pinned; i < built && overlay && !draw; i++) {
            draw = changes[i] != 0 && overlaps(lines, newBase + i * L_STRIDE, at);
        }
        if (draw) {
            drawListed(lines, at, lines[at + 4] & 0xFFFF);
        }
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

    /**
     * Bresenham, drawn as runs along the line's major axis: a shallow line costs one address window per row, a
     * steep one one per column, and a vertical or horizontal line a single window.
     */
    static void drawLine(int x0, int y0, int x1, int y1, int color) {
        boolean steep = Math.abs(y1 - y0) > Math.abs(x1 - x0);
        int a0 = steep ? y0 : x0;
        int b0 = steep ? x0 : y0;
        int a1 = steep ? y1 : x1;
        int b1 = steep ? x1 : y1;
        int da = Math.abs(a1 - a0);
        int db = -Math.abs(b1 - b0);
        int stepA = a0 > a1 ? -1 : 1;
        int stepB = b0 > b1 ? -1 : 1;
        int error = da + db;
        int a = a0;
        int b = b0;
        int runStart = a0;
        while (a != a1 || b != b1) {
            int twice = 2 * error;
            int nextA = a;
            int nextB = b;
            if (twice >= db) {
                error = error + db;
                nextA = a + stepA;
            }
            if (twice <= da) {
                error = error + da;
                nextB = b + stepB;
            }
            if (nextB != b) {
                fillRun(runStart, a, b, steep, color);
                runStart = nextA;
            }
            a = nextA;
            b = nextB;
        }
        fillRun(runStart, a, b, steep, color);
    }

    /** One run from {@code from} to {@code to} along the major axis, at {@code across} on the other. */
    private static void fillRun(int from, int to, int across, boolean steep, int color) {
        int low = Math.min(from, to);
        int length = Math.max(from, to) - low + 1;
        if (steep) {
            TftTouchShield.fillRect(across, low, 1, length, color);
        } else {
            TftTouchShield.fillRect(low, across, length, 1, color);
        }
        windows = windows + 1;
        pixels = pixels + length;
    }
}
