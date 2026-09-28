package io.github.jabrena.juno.api.tft;

/**
 * Pixel/shape drawing primitives over {@link TftBus}, split out of {@link TftTouchShield} to keep
 * its cyclomatic complexity down. Every coordinate is clipped against
 * {@link TftTouchShield#width()}/{@link TftTouchShield#height()} for the current rotation.
 */
final class TftGraphics {
    private TftGraphics() {
    }

    static int color(int red, int green, int blue) {
        return ((red & 0xF8) << 8) | ((green & 0xFC) << 3) | ((blue & 0xFF) >> 3);
    }

    static void fillScreen(int color) {
        fillRect(0, 0, TftTouchShield.width(), TftTouchShield.height(), color);
    }

    static void drawPixel(int x, int y, int color) {
        if (x < 0 || y < 0 || x >= TftTouchShield.width() || y >= TftTouchShield.height()) {
            return;
        }
        TftBus.setAddressWindow(x, y, x, y);
        TftBus.writePixels(color, 1);
    }

    static void fillRect(int x, int y, int w, int h, int color) {
        int left = Math.max(x, 0);
        int top = Math.max(y, 0);
        int right = Math.min(x + w, TftTouchShield.width()) - 1;
        int bottom = Math.min(y + h, TftTouchShield.height()) - 1;
        if (right < left || bottom < top) {
            return;
        }
        TftBus.setAddressWindow(left, top, right, bottom);
        TftBus.writePixels(color, (right - left + 1) * (bottom - top + 1));
    }

    static void drawHorizontalLine(int x, int y, int w, int color) {
        fillRect(x, y, w, 1, color);
    }

    static void drawVerticalLine(int x, int y, int h, int color) {
        fillRect(x, y, 1, h, color);
    }

    static void drawRect(int x, int y, int w, int h, int color) {
        drawHorizontalLine(x, y, w, color);
        drawHorizontalLine(x, y + h - 1, w, color);
        drawVerticalLine(x, y, h, color);
        drawVerticalLine(x + w - 1, y, h, color);
    }

    static void fillCircle(int cx, int cy, int r, int color) {
        int dx = r;
        for (int dy = 0; dy <= r; dy++) {
            while (dx * dx + dy * dy > r * r) {
                dx = dx - 1;
            }
            drawHorizontalLine(cx - dx, cy - dy, 2 * dx + 1, color);
            if (dy != 0) {
                drawHorizontalLine(cx - dx, cy + dy, 2 * dx + 1, color);
            }
        }
    }

    static void drawCircle(int cx, int cy, int r, int color) {
        int x = r;
        int y = 0;
        int error = 1 - r;
        while (x >= y) {
            drawPixel(cx + x, cy + y, color);
            drawPixel(cx + y, cy + x, color);
            drawPixel(cx - y, cy + x, color);
            drawPixel(cx - x, cy + y, color);
            drawPixel(cx - x, cy - y, color);
            drawPixel(cx - y, cy - x, color);
            drawPixel(cx + y, cy - x, color);
            drawPixel(cx + x, cy - y, color);
            y = y + 1;
            if (error < 0) {
                error = error + 2 * y + 1;
            } else {
                x = x - 1;
                error = error + 2 * (y - x) + 1;
            }
        }
    }

    static boolean beginPixels(int x, int y, int w, int h) {
        if (w <= 0 || h <= 0 || x < 0 || y < 0 || x + w > TftTouchShield.width() || y + h > TftTouchShield.height()) {
            return false;
        }
        TftBus.setAddressWindow(x, y, x + w - 1, y + h - 1);
        return true;
    }

    static void pushPixel(int color) {
        TftBus.writePixel(color);
    }
}
