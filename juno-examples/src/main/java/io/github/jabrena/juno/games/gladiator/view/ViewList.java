package io.github.jabrena.juno.games.gladiator.view;

import io.github.jabrena.juno.api.thirdparty.tft.TftTouchShield;

/**
 * Double-buffered list of the first-person frame's lines, after DOOM's: presenting a frame erases only the lines
 * that changed since the last one and draws only the new ones, so the view never flickers through a full clear. The
 * gladius and shield in hand are added first and {@linkplain #pin() pinned}, keeping their slots every frame. One
 * list serves every screen of the game, whichever role runs.
 */
public final class ViewList {
    public static final int WIDTH = 320;
    public static final int HEIGHT = 240;
    public static final int VIEW_TOP = 1;
    /** The first row of the status bar under the view. */
    public static final int VIEW_BOTTOM = 200;
    public static final int BACKGROUND = TftTouchShield.BLACK;

    public static final int MAX_LINES = 256;
    private static final int STRIDE = 5;
    public static final int LIST_SIZE = MAX_LINES * STRIDE;

    /** The two frames' lines, front and back, and which slots changed. */
    private final short[] lines = new short[2 * LIST_SIZE];
    private final byte[] changes = new byte[MAX_LINES];
    private int front;
    private int shown;
    private int built;
    private int pinned;

    public void begin() {
        built = 0;
        pinned = 0;
    }

    /** Marks the lines added so far as the overlay: drawn after the rest, on top of it. */
    public void pin() {
        pinned = built;
    }

    /** How many lines this frame holds so far. */
    public int size() {
        return built;
    }

    /** Queues a line already inside the view. */
    public void add(int x0, int y0, int x1, int y1, int color) {
        if (built == MAX_LINES) {
            return;
        }
        int at = (1 - front) * LIST_SIZE + built * STRIDE;
        lines[at] = (short) x0;
        lines[at + 1] = (short) y0;
        lines[at + 2] = (short) x1;
        lines[at + 3] = (short) y1;
        lines[at + 4] = (short) color;
        built = built + 1;
    }

    /** Line {@code i} of the frame being built, as {@code x0, y0, x1, y1, color}, for tests. */
    public int built(int i, int field) {
        return lines[(1 - front) * LIST_SIZE + i * STRIDE + field];
    }

    /** Erases the lines that changed since the last frame and draws the new ones. */
    public void present() {
        int slots = Math.max(shown, built);
        for (int i = 0; i < slots; i++) {
            changes[i] = (byte) (changed(i) ? 1 : 0);
        }
        int oldBase = front * LIST_SIZE;
        for (int i = 0; i < shown; i++) {
            if (changes[i] != 0) {
                drawListed(oldBase + i * STRIDE, BACKGROUND);
            }
        }
        for (int j = pinned; j < built; j++) {
            show(j, false);
        }
        for (int j = 0; j < pinned; j++) {
            show(j, true);
        }
        front = 1 - front;
        shown = built;
    }

    public void clearView() {
        TftTouchShield.fillRect(0, VIEW_TOP, WIDTH, VIEW_BOTTOM - VIEW_TOP, BACKGROUND);
        shown = 0;
    }

    /** Draws new line {@code j} when it changed or an erased line crossed it; the overlay also under a new line. */
    private void show(int j, boolean overlay) {
        int oldBase = front * LIST_SIZE;
        int newBase = (1 - front) * LIST_SIZE;
        int at = newBase + j * STRIDE;
        boolean draw = changes[j] != 0;
        for (int i = 0; i < shown && !draw; i++) {
            draw = changes[i] != 0 && overlaps(oldBase + i * STRIDE, at);
        }
        for (int i = pinned; i < built && overlay && !draw; i++) {
            draw = changes[i] != 0 && overlaps(newBase + i * STRIDE, at);
        }
        if (draw) {
            drawListed(at, lines[at + 4] & 0xFFFF);
        }
    }

    private boolean changed(int i) {
        if (i >= shown || i >= built) {
            return true;
        }
        int a = front * LIST_SIZE + i * STRIDE;
        int b = (1 - front) * LIST_SIZE + i * STRIDE;
        for (int k = 0; k < STRIDE; k++) {
            if (lines[a + k] != lines[b + k]) {
                return true;
            }
        }
        return false;
    }

    private boolean overlaps(int a, int b) {
        return Math.min(lines[a], lines[a + 2]) <= Math.max(lines[b], lines[b + 2])
                && Math.min(lines[b], lines[b + 2]) <= Math.max(lines[a], lines[a + 2])
                && Math.min(lines[a + 1], lines[a + 3]) <= Math.max(lines[b + 1], lines[b + 3])
                && Math.min(lines[b + 1], lines[b + 3]) <= Math.max(lines[a + 1], lines[a + 3]);
    }

    private void drawListed(int at, int color) {
        drawLine(lines[at], lines[at + 1], lines[at + 2], lines[at + 3], color);
    }

    /** Bresenham, drawn as runs along the line's major axis: one address window per run. */
    public static void drawLine(int x0, int y0, int x1, int y1, int color) {
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

    private static void fillRun(int from, int to, int across, boolean steep, int color) {
        int low = Math.min(from, to);
        int length = Math.max(from, to) - low + 1;
        if (steep) {
            TftTouchShield.fillRect(across, low, 1, length, color);
        } else {
            TftTouchShield.fillRect(low, across, length, 1, color);
        }
    }
}
