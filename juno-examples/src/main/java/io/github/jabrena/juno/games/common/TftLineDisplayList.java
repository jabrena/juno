package io.github.jabrena.juno.games.common;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;

/**
 * Double-buffered, clipped line display list for the Arduino TFT shield. The caller owns the
 * primitive line buffer; this object only keeps the small amount of frame state needed to reuse it.
 */
public final class TftLineDisplayList {
    private static final int L_STRIDE = 5;

    private final int width;
    private final int height;
    private final int viewTop;
    private final int viewBottom;
    private final int maxLines;
    private final int listSize;
    private final int background;
    private int clipLeft;
    private int clipTop;
    private int clipRight;
    private int clipBottom;
    private int front;
    private int shown;
    private int built;

    public TftLineDisplayList(int width, int height, int viewTop, int maxLines, int background) {
        this.width = width;
        this.height = height;
        this.viewTop = viewTop;
        this.viewBottom = height - 1;
        this.maxLines = maxLines;
        this.listSize = maxLines * L_STRIDE;
        this.background = background;
        setClip(0, viewTop, width - 1, height - 1);
    }

    public int listSize() {
        return listSize;
    }

    /** Restores the frame counters used by a caller's deterministic test harness. */
    public void setState(int front, int shown, int built) {
        this.front = front;
        this.shown = shown;
        this.built = built;
    }

    public int front() {
        return front;
    }

    public int shown() {
        return shown;
    }

    public int built() {
        return built;
    }

    /** Starts building the next frame's list. */
    public void begin() {
        built = 0;
    }

    /** Forgets what the list had drawn, after the screen was cleared some other way. */
    public void forget() {
        shown = 0;
    }

    /** Selects the rectangular view that receives subsequent lines. */
    public void setClip(int left, int top, int right, int bottom) {
        clipLeft = left;
        clipTop = top;
        clipRight = right;
        clipBottom = bottom;
    }

    /** Clips a screen line to the current view and appends it to the list being built. */
    public void addLine(short[] lines, int x0, int y0, int x1, int y1, int color) {
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
        if (built == maxLines) {
            return;
        }
        int at = (1 - front) * listSize + built * L_STRIDE;
        lines[at] = (short) x0;
        lines[at + 1] = (short) y0;
        lines[at + 2] = (short) x1;
        lines[at + 3] = (short) y1;
        lines[at + 4] = (short) color;
        built = built + 1;
    }

    /** Shows the list just built, erasing changed lines and redrawing crossed lines. */
    public void present(short[] lines) {
        int oldBase = front * listSize;
        int newBase = (1 - front) * listSize;
        for (int i = 0; i < shown; i++) {
            if (changed(lines, i)) {
                drawListed(lines, oldBase + i * L_STRIDE, background);
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

    /** Blanks the view and forgets what the display list had drawn there. */
    public void clearView() {
        TftTouchShield.fillRect(0, viewTop, width, height - viewTop, background);
        shown = 0;
    }

    /** Draws a clipped-list-independent Bresenham line. */
    public void drawLine(int x0, int y0, int x1, int y1, int color) {
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

    private int outCode(int x, int y) {
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

    private boolean changed(short[] lines, int i) {
        if (i >= shown || i >= built) {
            return true;
        }
        int a = front * listSize + i * L_STRIDE;
        int b = (1 - front) * listSize + i * L_STRIDE;
        for (int k = 0; k < L_STRIDE; k++) {
            if (lines[a + k] != lines[b + k]) {
                return true;
            }
        }
        return false;
    }

    private boolean overlaps(short[] lines, int a, int b) {
        return Math.min(lines[a], lines[a + 2]) <= Math.max(lines[b], lines[b + 2])
                && Math.min(lines[b], lines[b + 2]) <= Math.max(lines[a], lines[a + 2])
                && Math.min(lines[a + 1], lines[a + 3]) <= Math.max(lines[b + 1], lines[b + 3])
                && Math.min(lines[b + 1], lines[b + 3]) <= Math.max(lines[a + 1], lines[a + 3]);
    }

    private void drawListed(short[] lines, int at, int color) {
        drawLine(lines[at], lines[at + 1], lines[at + 2], lines[at + 3], color);
    }

    private void fillRun(int from, int to, int y, int color) {
        int left = Math.min(from, to);
        int right = Math.max(from, to);
        TftTouchShield.fillRect(left, y, right - left + 1, 1, color);
    }
}
