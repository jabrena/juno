package io.github.jabrena.juno.hardware.display;

/**
 * A board display with RGB565 colors, basic drawing operations and text output. Implementations are supplied by a
 * board facade such as {@link io.github.jabrena.juno.hardware.M5StackCardputer#getDisplay()} so application code can receive a mock
 * in unit tests.
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

    /** Initializes the display hardware. */
    void begin();

    /** Returns the display width in pixels for its current rotation. */
    int width();

    /** Returns the display height in pixels for its current rotation. */
    int height();

    void fillScreen(int color);

    void drawPixel(int x, int y, int color);

    void fillRect(int x, int y, int width, int height, int color);

    void setCursor(int x, int y);

    void setTextColor(int foreground, int background);

    void setTextSize(int size);

    void print(char character);

    void print(String text);

    void println(String text);
}
