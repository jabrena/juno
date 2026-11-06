package io.github.jabrena.juno.hardware.device.grove.display;

import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.i2c.I2c;
import io.github.jabrena.juno.hardware.led.LedMatrixFontAscii;

/**
 * Grove 0.96-inch 128×64 yellow/blue OLED with an SSD1315 controller, connected over I2C.
 *
 * <p>Pass a 1024-byte, caller-owned framebuffer at startup. All drawing and text calls then allocate nothing. The
 * display is monochrome, so {@link Display#BLACK} clears pixels and every other {@link Display} color lights them.
 * The top 16 pixel rows are physically yellow; the remaining rows are blue.
 */
public final class GroveOledSsd1315 implements Display {
    /** Display pixel width. */
    public static final int WIDTH = 128;
    /** Display pixel height. */
    public static final int HEIGHT = 64;
    /** Required length of the caller-owned framebuffer. */
    public static final int FRAMEBUFFER_SIZE = WIDTH * HEIGHT / 8;

    private static final int ADDRESS_DEFAULT = 0x3C;
    private static final int CONTROL_COMMAND = 0x00;
    private static final int CONTROL_DATA = 0x40;

    private final I2c i2c;
    private final byte[] framebuffer;
    private int cursorX;
    private int cursorY;
    private int textSize = 1;
    private boolean foreground = true;
    private boolean background;

    /** A display over {@code i2c}; pass a mock bus in a unit test. */
    public GroveOledSsd1315(I2c i2c, byte[] framebuffer) {
        if (framebuffer.length < FRAMEBUFFER_SIZE) {
            throw new IllegalArgumentException("SSD1315 framebuffer must contain at least 1024 bytes");
        }
        this.i2c = i2c;
        this.framebuffer = framebuffer;
    }

    /** Initializes the SSD1315 and transfers the current framebuffer. */
    @Override
    public void begin() {
        i2c.begin();
        command(0xAE); // display off
        command(0xA8);
        command(0x3F); // 1:64 multiplex ratio
        command(0xD3);
        command(0x00); // no display offset
        command(0x40); // start line 0
        command(0x8D);
        command(0x14); // charge pump on
        command(0xA1); // segment remap
        command(0xC8); // COM scan reversed
        command(0xDA);
        command(0x12); // sequential COM pins
        command(0x81);
        command(0x7F); // contrast
        command(0xD9);
        command(0xF1); // pre-charge period
        command(0xDB);
        command(0x40); // VCOMH deselect level
        command(0xA4); // use RAM contents
        command(0xA6); // normal pixels
        command(0xAF); // display on
        flush();
    }

    /** Returns 128. */
    @Override
    public int width() {
        return WIDTH;
    }

    /** Returns 64. */
    @Override
    public int height() {
        return HEIGHT;
    }

    /** Fills the framebuffer and immediately transfers it. */
    @Override
    public void fillScreen(int color) {
        byte value = lit(color) ? (byte) 0xFF : 0;
        for (int index = 0; index < FRAMEBUFFER_SIZE; index++) {
            framebuffer[index] = value;
        }
        flush();
    }

    /** Sets one pixel and transfers its containing byte. Coordinates outside the display are ignored. */
    @Override
    public void drawPixel(int x, int y, int color) {
        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT) {
            return;
        }
        int index = y / 8 * WIDTH + x;
        int mask = 1 << (y & 7);
        int value = framebuffer[index] & 0xFF;
        framebuffer[index] = (byte) (lit(color) ? value | mask : value & ~mask);
        setAddress(x, y / 8);
        data(framebuffer[index]);
    }

    /** Fills the clipped rectangle with the requested monochrome color. */
    @Override
    public void fillRect(int x, int y, int width, int height, int color) {
        for (int row = y; row < y + height; row++) {
            for (int column = x; column < x + width; column++) {
                drawPixel(column, row, color);
            }
        }
    }

    /** Sets the top-left text position in pixels. */
    @Override
    public void setCursor(int x, int y) {
        cursorX = x;
        cursorY = y;
    }

    /** Selects monochrome foreground and background pixels; non-black colors are on. */
    @Override
    public void setTextColor(int foreground, int background) {
        this.foreground = lit(foreground);
        this.background = lit(background);
    }

    /** Selects a positive integer scale for the built-in 5×7 ASCII font. */
    @Override
    public void setTextSize(int size) {
        textSize = Math.max(1, size);
    }

    /** Draws one ASCII character and advances the cursor by six scaled pixels. */
    @Override
    public void print(char character) {
        if (character == '\n') {
            cursorX = 0;
            cursorY += 8 * textSize;
            return;
        }
        for (int column = 0; column < LedMatrixFontAscii.GLYPH_WIDTH; column++) {
            for (int row = 0; row < LedMatrixFontAscii.GLYPH_HEIGHT; row++) {
                fillRect(cursorX + column * textSize, cursorY + row * textSize, textSize, textSize,
                        LedMatrixFontAscii.charPixel(character, column, row) ? color(foreground) : color(background));
            }
        }
        cursorX += 6 * textSize;
    }

    /** Draws a string without allocating. */
    @Override
    public void print(String text) {
        for (int index = 0; index < text.length(); index++) {
            print(text.charAt(index));
        }
    }

    /** Draws a string then moves the cursor to the first column of the next text row. */
    @Override
    public void println(String text) {
        print(text);
        cursorX = 0;
        cursorY += 8 * textSize;
    }

    /** Transfers all eight display pages from the framebuffer. */
    public void flush() {
        for (int page = 0; page < HEIGHT / 8; page++) {
            setAddress(0, page);
            int offset = page * WIDTH;
            for (int column = 0; column < WIDTH; column++) {
                data(framebuffer[offset + column]);
            }
        }
    }

    private void command(int value) {
        i2c.writeRegister(ADDRESS_DEFAULT, CONTROL_COMMAND, value);
    }

    private void data(int value) {
        i2c.writeRegister(ADDRESS_DEFAULT, CONTROL_DATA, value);
    }

    private void setAddress(int column, int page) {
        command(0xB0 | page);
        command(column & 0x0F);
        command(0x10 | column >> 4);
    }

    private boolean lit(int color) {
        return color != BLACK;
    }

    private int color(boolean lit) {
        return lit ? WHITE : BLACK;
    }

}
