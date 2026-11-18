package io.github.jabrena.juno.hardware.display;

/**
 * A board display with basic drawing operations and text output. Color displays use RGB565 colors. A monochrome
 * display (the Grove OLED, the LED matrix) lights a pixel for every color other than {@link #BLACK}, so the palette
 * works on every display. Coordinates outside the display are ignored. Use {@link #width()} and {@link #height()} to
 * place content: the LED matrix is 12 or 13 pixels wide and 8 high. Implementations are supplied by
 * {@link io.github.jabrena.juno.hardware.MCU#getDisplay()} so application code can receive a mock in unit tests.
 */
public interface Display {
    int BLACK = 0x0000;
    int WHITE = 0xFFFF;
    int RED = 0xF800;
    int GREEN = 0x07E0;
    int BLUE = 0x001F;
    int YELLOW = 0xFFE0;
    int CYAN = 0x07FF;
    int MAGENTA = 0xF81F;

    /** Packs 8-bit red, green and blue channels into an RGB565 color. */
    static int color(int red, int green, int blue) {
        return ((red & 0xF8) << 8) | ((green & 0xFC) << 3) | ((blue & 0xFF) >> 3);
    }

    /** Returns the display width in pixels for its current rotation. */
    int width();

    /** Returns the display height in pixels for its current rotation. */
    int height();

    /**
     * Shows the drawing so far. Color displays draw as they are called, so this does nothing there. A buffered display
     * shows its buffer only here.
     */
    default void flush() {
    }

    void fillScreen(int color);

    void drawPixel(int x, int y, int color);

    void fillRect(int x, int y, int width, int height, int color);

    /** Draws a horizontal line of {@code width} pixels starting at ({@code x}, {@code y}). */
    default void drawHorizontalLine(int x, int y, int width, int color) {
        fillRect(x, y, width, 1, color);
    }

    /** Draws a vertical line of {@code height} pixels starting at ({@code x}, {@code y}). */
    default void drawVerticalLine(int x, int y, int height, int color) {
        fillRect(x, y, 1, height, color);
    }

    /**
     * Draws the 1-pixel outline of a {@code width}x{@code height} rectangle whose top-left corner is
     * ({@code x}, {@code y}).
     */
    default void drawRect(int x, int y, int width, int height, int color) {
        drawHorizontalLine(x, y, width, color);
        drawHorizontalLine(x, y + height - 1, width, color);
        drawVerticalLine(x, y, height, color);
        drawVerticalLine(x + width - 1, y, height, color);
    }

    /** Fills a circle of radius {@code r} centered on ({@code cx}, {@code cy}). */
    default void fillCircle(int cx, int cy, int r, int color) {
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

    /** Draws the 1-pixel outline of a circle of radius {@code r} centered on ({@code cx}, {@code cy}). */
    default void drawCircle(int cx, int cy, int r, int color) {
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

    void setCursor(int x, int y);

    void setTextColor(int foreground, int background);

    void setTextSize(int size);

    void print(char character);

    void print(String text);

    void println(String text);
}
