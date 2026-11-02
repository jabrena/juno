package io.github.jabrena.juno.hardware.device.tft;

/**
 * Pixel/shape drawing primitives over {@link TftBus}, split out of {@link TftTouchShield} to keep
 * its cyclomatic complexity down. Every coordinate is clipped against
 * {@link TftTouchShield#width()}/{@link TftTouchShield#height()} for the current rotation.
 */
final class TftGraphics {
    private final TftTouchShield shield;
    private final TftBus bus;

    TftGraphics(TftTouchShield shield, TftBus bus) {
        this.shield = shield;
        this.bus = bus;
    }

    static int color(int red, int green, int blue) {
        return ((red & 0xF8) << 8) | ((green & 0xFC) << 3) | ((blue & 0xFF) >> 3);
    }

    void fillScreen(int color) {
        fillRect(0, 0, shield.width(), shield.height(), color);
    }

    void drawPixel(int x, int y, int color) {
        if (x < 0 || y < 0 || x >= shield.width() || y >= shield.height()) {
            return;
        }
        bus.setAddressWindow(x, y, x, y);
        bus.writePixels(color, 1);
    }

    void fillRect(int x, int y, int w, int h, int color) {
        int left = Math.max(x, 0);
        int top = Math.max(y, 0);
        int right = Math.min(x + w, shield.width()) - 1;
        int bottom = Math.min(y + h, shield.height()) - 1;
        if (right < left || bottom < top) {
            return;
        }
        bus.setAddressWindow(left, top, right, bottom);
        bus.writePixels(color, (right - left + 1) * (bottom - top + 1));
    }

    void drawHorizontalLine(int x, int y, int w, int color) {
        fillRect(x, y, w, 1, color);
    }

    void drawVerticalLine(int x, int y, int h, int color) {
        fillRect(x, y, 1, h, color);
    }

    void drawRect(int x, int y, int w, int h, int color) {
        drawHorizontalLine(x, y, w, color);
        drawHorizontalLine(x, y + h - 1, w, color);
        drawVerticalLine(x, y, h, color);
        drawVerticalLine(x + w - 1, y, h, color);
    }

    void fillCircle(int cx, int cy, int r, int color) {
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

    void drawCircle(int cx, int cy, int r, int color) {
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

    boolean beginPixels(int x, int y, int w, int h) {
        if (w <= 0 || h <= 0 || x < 0 || y < 0 || x + w > shield.width() || y + h > shield.height()) {
            return false;
        }
        bus.setAddressWindow(x, y, x + w - 1, y + h - 1);
        return true;
    }

    void pushPixel(int color) {
        bus.writePixel(color);
    }
}
