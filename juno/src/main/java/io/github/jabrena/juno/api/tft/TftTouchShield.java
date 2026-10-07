package io.github.jabrena.juno.api.tft;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.Gpio;
import io.github.jabrena.juno.api.led.LedMatrixFontAscii;

/**
 * Driver for the ELEGOO 2.8" TFT touch screen shield ("Pantalla Táctil TFT de 2,8 pulgadas"): a
 * 240x320 ILI9341 display on an 8-bit parallel bus, a 4-wire resistive touch panel, and a microSD
 * socket, wired for the shield's standard UNO pinout as used by ELEGOO's {@code Elegoo_TFTLCD} and
 * {@code TouchScreen} libraries.
 *
 * <p>Like {@link io.github.jabrena.juno.api.lcd.LcdKeypadShield}, this shield needs no new compiler
 * intrinsic: every operation is built from {@link Gpio#pinMode}, {@link Gpio#digitalWrite},
 * {@link Gpio#analogRead}, and {@link Delay}. The price is speed — each bus byte costs a handful
 * of {@code digitalWrite} calls, so clearing the whole screen takes on the order of a second while
 * drawing a line of text takes tens of milliseconds. That suits status screens and simple UIs,
 * not animation. The driver only rewrites data pins whose level actually changes, so solid fills
 * in colors whose two bytes are equal (e.g. {@link #BLACK}, {@link #WHITE}) are the fastest.
 *
 * <p>The SD socket is an ordinary SPI card reader with chip select on D10: use
 * {@link io.github.jabrena.juno.api.io.storage.SdCard#begin()} as-is. It shares no pins with the
 * display or touch panel.
 *
 * <p>The touch panel shares its pins with the display bus (X- = display RS, Y+ = display CS, X+/Y-
 * = data lines 0/1), so {@link #readTouch()} temporarily reconfigures them and restores the display
 * bus before returning. Touch coordinates are mapped with ELEGOO's published calibration for this
 * panel; use {@link #calibrateTouch} (from {@link #touchRawX()}/{@link #touchRawY()} readings at
 * the screen's edges) if a particular panel reports offset or mirrored positions.
 */
public final class TftTouchShield {
    /** Native (portrait) width in pixels. */
    public static final int NATIVE_WIDTH = 240;
    /** Native (portrait) height in pixels. */
    public static final int NATIVE_HEIGHT = 320;

    /** Portrait, USB/power connectors at the top. */
    public static final int PORTRAIT = 0;
    /** Landscape, rotated 90 degrees clockwise from {@link #PORTRAIT}. */
    public static final int LANDSCAPE = 1;
    /** Portrait, rotated 180 degrees from {@link #PORTRAIT}. */
    public static final int PORTRAIT_FLIPPED = 2;
    /** Landscape, rotated 180 degrees from {@link #LANDSCAPE}. */
    public static final int LANDSCAPE_FLIPPED = 3;

    // RGB565 colors.
    public static final int BLACK = 0x0000;
    public static final int WHITE = 0xFFFF;
    public static final int RED = 0xF800;
    public static final int GREEN = 0x07E0;
    public static final int BLUE = 0x001F;
    public static final int YELLOW = 0xFFE0;
    public static final int CYAN = 0x07FF;
    public static final int MAGENTA = 0xF81F;
    public static final int ORANGE = 0xFD20;
    public static final int GRAY = 0x8410;
    public static final int NAVY = 0x000F;

    /** Width of one character cell at text size 1 (5-pixel glyph plus 1 pixel of spacing). */
    public static final int CHAR_WIDTH = 6;
    /** Height of one character cell at text size 1 (7-pixel glyph plus 1 pixel of spacing). */
    public static final int CHAR_HEIGHT = 8;

    // Display bus: control lines on A0-A4, data bits 0-1 on D8-D9 and bits 2-7 on D2-D7.
    // Package-private: shared with TftBus (PIN_RS/PIN_WR) and TftTouch (PIN_RS/PIN_CS/PIN_WR).
    private static final int PIN_RD = 14;    // A0
    static final int PIN_WR = 15;    // A1
    static final int PIN_RS = 16;    // A2, command (low) / data (high)
    static final int PIN_CS = 17;    // A3
    private static final int PIN_RESET = 18; // A4

    // ELEGOO's published calibration for the 2.8" panel (raw ADC readings at the screen edges). Raw X
    // grows with the display's x while raw Y runs opposite to its y, as confirmed on real hardware.
    private static final int DEFAULT_TOUCH_MIN_X = 120;
    private static final int DEFAULT_TOUCH_MAX_X = 900;
    private static final int DEFAULT_TOUCH_MIN_Y = 70;
    private static final int DEFAULT_TOUCH_MAX_Y = 920;
    private static final int MIN_PRESSURE = 10;
    private static final int MAX_PRESSURE = 1000;
    private static final int TOUCH_TOLERANCE = 16;

    // ILI9341 commands and the flags this driver sets on them.
    private static final int SOFT_RESET = 0x01;
    private static final int SLEEP_OUT = 0x11;
    private static final int DISPLAY_OFF = 0x28;
    private static final int DISPLAY_ON = 0x29;
    // Package-private: shared with TftBus.setAddressWindow.
    static final int COLUMN_ADDRESS_SET = 0x2A;
    static final int PAGE_ADDRESS_SET = 0x2B;
    static final int MEMORY_WRITE = 0x2C;
    private static final int MEMORY_ACCESS_CONTROL = 0x36;
    private static final int PIXEL_FORMAT = 0x3A;
    private static final int FRAME_CONTROL = 0xB1;
    private static final int ENTRY_MODE = 0xB7;
    private static final int POWER_CONTROL_1 = 0xC0;
    private static final int POWER_CONTROL_2 = 0xC1;
    private static final int VCOM_CONTROL_1 = 0xC5;
    private static final int VCOM_CONTROL_2 = 0xC7;
    private static final int MADCTL_MY = 0x80;
    private static final int MADCTL_MX = 0x40;
    private static final int MADCTL_MV = 0x20;
    private static final int MADCTL_BGR = 0x08;
    private static final int PIXEL_FORMAT_16_BIT = 0x55;

    private static int width;
    private static int height;
    private static int rotation;
    private static int cursorX;
    private static int cursorY;
    private static int textSize;
    private static int textColor;
    private static int textBackground;

    private static int touchMinX;
    private static int touchMaxX;
    private static int touchMinY;
    private static int touchMaxY;
    private static int touchX;
    private static int touchY;
    private static int touchRawX;
    private static int touchRawY;
    private static int touchPressure;

    private TftTouchShield() {
    }

    /**
     * Configures the shield's pins, resets and initializes the ILI9341 in 16-bit color, and selects
     * {@link #PORTRAIT} rotation, a size-1 white-on-black text style, and ELEGOO's default touch
     * calibration. The screen contents are undefined until the first {@link #fillScreen}.
     */
    public static void begin() {
        Gpio.pinMode(PIN_RD, Gpio.OUTPUT);
        Gpio.pinMode(PIN_WR, Gpio.OUTPUT);
        Gpio.pinMode(PIN_RS, Gpio.OUTPUT);
        Gpio.pinMode(PIN_CS, Gpio.OUTPUT);
        Gpio.pinMode(PIN_RESET, Gpio.OUTPUT);
        Gpio.digitalWrite(PIN_RD, true);
        Gpio.digitalWrite(PIN_WR, true);
        Gpio.digitalWrite(PIN_RS, true);
        Gpio.digitalWrite(PIN_CS, true);
        TftBus.restoreDataPins();

        Gpio.digitalWrite(PIN_RESET, true);
        Delay.millis(5);
        Gpio.digitalWrite(PIN_RESET, false);
        Delay.millis(20);
        Gpio.digitalWrite(PIN_RESET, true);
        Delay.millis(150);
        Gpio.digitalWrite(PIN_CS, false);

        TftBus.command(SOFT_RESET);
        Delay.millis(50);
        TftBus.command(DISPLAY_OFF);
        TftBus.command8(POWER_CONTROL_1, 0x23);
        TftBus.command8(POWER_CONTROL_2, 0x10);
        TftBus.command16(VCOM_CONTROL_1, 0x2B2B);
        TftBus.command8(VCOM_CONTROL_2, 0xC0);
        TftBus.command8(PIXEL_FORMAT, PIXEL_FORMAT_16_BIT);
        TftBus.command16(FRAME_CONTROL, 0x001B);
        TftBus.command8(ENTRY_MODE, 0x07);
        TftBus.command(SLEEP_OUT);
        Delay.millis(150);
        TftBus.command(DISPLAY_ON);
        Delay.millis(50);

        setRotation(PORTRAIT);
        cursorX = 0;
        cursorY = 0;
        textSize = 1;
        textColor = WHITE;
        textBackground = BLACK;
        calibrateTouch(DEFAULT_TOUCH_MIN_X, DEFAULT_TOUCH_MAX_X, DEFAULT_TOUCH_MAX_Y, DEFAULT_TOUCH_MIN_Y);
    }

    /**
     * Selects {@link #PORTRAIT}, {@link #LANDSCAPE}, {@link #PORTRAIT_FLIPPED}, or
     * {@link #LANDSCAPE_FLIPPED}. Coordinates, {@link #width()}/{@link #height()}, and touch
     * positions all follow the current rotation. Existing screen contents are not redrawn.
     */
    public static void setRotation(int value) {
        rotation = value & 3;
        int madctl = MADCTL_MY | MADCTL_BGR;
        width = NATIVE_WIDTH;
        height = NATIVE_HEIGHT;
        if (rotation == LANDSCAPE) {
            madctl = MADCTL_MX | MADCTL_MY | MADCTL_MV | MADCTL_BGR;
            width = NATIVE_HEIGHT;
            height = NATIVE_WIDTH;
        } else if (rotation == PORTRAIT_FLIPPED) {
            madctl = MADCTL_MX | MADCTL_BGR;
        } else if (rotation == LANDSCAPE_FLIPPED) {
            madctl = MADCTL_MV | MADCTL_BGR;
            width = NATIVE_HEIGHT;
            height = NATIVE_WIDTH;
        }
        TftBus.command8(MEMORY_ACCESS_CONTROL, madctl);
    }

    /** Current width in pixels, for the current rotation. */
    public static int width() {
        return width;
    }

    /** Current height in pixels, for the current rotation. */
    public static int height() {
        return height;
    }

    /** Packs 8-bit-per-channel {@code red}, {@code green}, {@code blue} into an RGB565 color. */
    public static int color(int red, int green, int blue) {
        return TftGraphics.color(red, green, blue);
    }

    /** Fills the whole screen with {@code color}. */
    public static void fillScreen(int color) {
        TftGraphics.fillScreen(color);
    }

    /** Sets the pixel at ({@code x}, {@code y}) to {@code color}; off-screen pixels are ignored. */
    public static void drawPixel(int x, int y, int color) {
        TftGraphics.drawPixel(x, y, color);
    }

    /** Fills a {@code w}x{@code h} rectangle whose top-left corner is ({@code x}, {@code y}), clipped to the screen. */
    public static void fillRect(int x, int y, int w, int h, int color) {
        TftGraphics.fillRect(x, y, w, h, color);
    }

    /** Draws a horizontal line of {@code w} pixels starting at ({@code x}, {@code y}). */
    public static void drawHorizontalLine(int x, int y, int w, int color) {
        TftGraphics.drawHorizontalLine(x, y, w, color);
    }

    /** Draws a vertical line of {@code h} pixels starting at ({@code x}, {@code y}). */
    public static void drawVerticalLine(int x, int y, int h, int color) {
        TftGraphics.drawVerticalLine(x, y, h, color);
    }

    /** Draws the 1-pixel outline of a {@code w}x{@code h} rectangle whose top-left corner is ({@code x}, {@code y}). */
    public static void drawRect(int x, int y, int w, int h, int color) {
        TftGraphics.drawRect(x, y, w, h, color);
    }

    /** Fills a circle of radius {@code r} centered on ({@code cx}, {@code cy}), clipped to the screen. */
    public static void fillCircle(int cx, int cy, int r, int color) {
        TftGraphics.fillCircle(cx, cy, r, color);
    }

    /** Draws the 1-pixel outline of a circle of radius {@code r} centered on ({@code cx}, {@code cy}). */
    public static void drawCircle(int cx, int cy, int r, int color) {
        TftGraphics.drawCircle(cx, cy, r, color);
    }

    /**
     * Opens a {@code w}x{@code h} window with its top-left corner at ({@code x}, {@code y}) for
     * streaming pixels with {@link #pushPixel}, row by row from the top-left — the fast way to draw
     * a small custom bitmap. Returns {@code false}, opening nothing, unless the window lies entirely
     * on screen. Push exactly {@code w * h} pixels before any other drawing call.
     */
    public static boolean beginPixels(int x, int y, int w, int h) {
        return TftGraphics.beginPixels(x, y, w, h);
    }

    /** Writes the next pixel of the window opened by {@link #beginPixels}. */
    public static void pushPixel(int color) {
        TftGraphics.pushPixel(color);
    }

    /** Moves the text cursor so the next character's top-left corner is at ({@code x}, {@code y}) pixels. */
    public static void setCursor(int x, int y) {
        cursorX = x;
        cursorY = y;
    }

    /** Scales text by an integer factor (1 = 6x8-pixel cells, 2 = 12x16, ...); values below 1 are treated as 1. */
    public static void setTextSize(int size) {
        textSize = Math.max(size, 1);
    }

    /**
     * Sets the text foreground and background colors. Characters always paint their whole cell, so
     * printing over older text replaces it — pad shorter strings with spaces to erase leftovers.
     */
    public static void setTextColor(int foreground, int background) {
        textColor = foreground;
        textBackground = background;
    }

    /**
     * Prints {@code text} at the cursor with the current text size and colors, advancing the
     * cursor. {@code '\n'} moves to the start of the next text line, and text wraps at the right
     * edge. Characters outside printable ASCII (32-126) are drawn as spaces.
     */
    public static void print(String text) {
        int length = text.length();
        for (int i = 0; i < length; i++) {
            writeChar(text.charAt(i));
        }
    }

    /** Prints {@code value} in decimal at the cursor. */
    public static void print(int value) {
        print(String.valueOf(value));
    }

    /**
     * Prints the first {@code length} bytes of {@code buffer} as ASCII characters at the cursor,
     * like {@link #print(String)} — for text that already lives in a caller-owned byte buffer (e.g.
     * {@link io.github.jabrena.juno.api.net.http.Json#getString(byte[], int, String, byte[], int)}'s
     * output) and would otherwise need a runtime {@code String}.
     */
    public static void print(byte[] buffer, int length) {
        for (int i = 0; i < length; i++) {
            writeChar(buffer[i] & 0xFF);
        }
    }

    /** Prints {@code text} and then moves the cursor to the start of the next text line. */
    public static void println(String text) {
        print(text);
        newLine();
    }

    /**
     * Samples the touch panel, returning whether it is currently pressed — only when two position
     * and pressure samples agree, which rejects noise from the pins it shares with the display bus.
     * When it is,
     * {@link #touchX()}/{@link #touchY()} hold the pressed position in screen coordinates for the
     * current rotation. Always leaves the display bus ready for drawing again.
     */
    public static boolean readTouch() {
        touchPressure = TftTouch.readTouchPressure();
        boolean pressed = touchPressure >= MIN_PRESSURE && touchPressure <= MAX_PRESSURE;
        if (pressed) {
            int firstX = TftTouch.readTouchRawX();
            int firstY = TftTouch.readTouchRawY();
            int secondX = TftTouch.readTouchRawX();
            int secondY = TftTouch.readTouchRawY();
            int secondPressure = TftTouch.readTouchPressure();
            // A real press gives steady readings; residual charge or a glancing contact does not.
            pressed = secondPressure >= MIN_PRESSURE && secondPressure <= MAX_PRESSURE
                    && Math.abs(firstX - secondX) <= TOUCH_TOLERANCE
                    && Math.abs(firstY - secondY) <= TOUCH_TOLERANCE;
            touchRawX = (firstX + secondX) / 2;
            touchRawY = (firstY + secondY) / 2;
            if (!pressed) {
                touchPressure = 0;
            }
        }
        TftTouch.restoreBusAfterTouch();
        if (!pressed) {
            return false;
        }

        int portraitX = TftTouch.clamp(TftTouch.map(touchRawX, touchMinX, touchMaxX, 0, NATIVE_WIDTH), 0, NATIVE_WIDTH - 1);
        int portraitY = TftTouch.clamp(TftTouch.map(touchRawY, touchMinY, touchMaxY, 0, NATIVE_HEIGHT), 0, NATIVE_HEIGHT - 1);
        touchX = portraitX;
        touchY = portraitY;
        if (rotation == LANDSCAPE) {
            touchX = portraitY;
            touchY = NATIVE_WIDTH - 1 - portraitX;
        } else if (rotation == PORTRAIT_FLIPPED) {
            touchX = NATIVE_WIDTH - 1 - portraitX;
            touchY = NATIVE_HEIGHT - 1 - portraitY;
        } else if (rotation == LANDSCAPE_FLIPPED) {
            touchX = NATIVE_HEIGHT - 1 - portraitY;
            touchY = portraitX;
        }
        return true;
    }

    /** X coordinate of the last successful {@link #readTouch()}, for the current rotation. */
    public static int touchX() {
        return touchX;
    }

    /** Y coordinate of the last successful {@link #readTouch()}, for the current rotation. */
    public static int touchY() {
        return touchY;
    }

    /** Raw 10-bit X reading of the last successful {@link #readTouch()}, for calibration. */
    public static int touchRawX() {
        return touchRawX;
    }

    /** Raw 10-bit Y reading of the last successful {@link #readTouch()}, for calibration. */
    public static int touchRawY() {
        return touchRawY;
    }

    /** Pressure estimate of the last {@link #readTouch()} (0 when untouched; higher is firmer). */
    public static int touchPressure() {
        return touchPressure;
    }

    /**
     * Replaces the touch calibration: the raw readings at portrait x = 0, x = 239, y = 0, and
     * y = 319. Passing a larger "left" than "right" reading mirrors that axis.
     */
    public static void calibrateTouch(int rawLeft, int rawRight, int rawTop, int rawBottom) {
        touchMinX = rawLeft;
        touchMaxX = rawRight;
        touchMinY = rawTop;
        touchMaxY = rawBottom;
    }

    private static void writeChar(int character) {
        if (character == '\n') {
            newLine();
            return;
        }
        int cellWidth = CHAR_WIDTH * textSize;
        if (cursorX + cellWidth > width) {
            newLine();
        }
        drawChar(cursorX, cursorY, character);
        cursorX = cursorX + cellWidth;
    }

    private static void newLine() {
        cursorX = 0;
        cursorY = cursorY + CHAR_HEIGHT * textSize;
    }

    private static void drawChar(int x, int y, int character) {
        int code = character;
        if (code < 32 || code > 126) {
            code = 32;
        }
        int cellWidth = CHAR_WIDTH * textSize;
        int cellHeight = CHAR_HEIGHT * textSize;
        if (x < 0 || y < 0 || x + cellWidth > width || y + cellHeight > height) {
            return;
        }
        TftBus.setAddressWindow(x, y, x + cellWidth - 1, y + cellHeight - 1);
        for (int row = 0; row < CHAR_HEIGHT; row++) {
            int rowBits = glyphRowBits(code, row);
            for (int repeat = 0; repeat < textSize; repeat++) {
                for (int column = 0; column < CHAR_WIDTH; column++) {
                    int color = textBackground;
                    if (((rowBits >> column) & 1) != 0) {
                        color = textColor;
                    }
                    for (int dot = 0; dot < textSize; dot++) {
                        TftBus.writePixel(color);
                    }
                }
            }
        }
    }

    // Bit n set = glyph column n lit; the spacing column and row stay background.
    private static int glyphRowBits(int code, int row) {
        if (row >= LedMatrixFontAscii.GLYPH_HEIGHT) {
            return 0;
        }
        int bits = 0;
        for (int column = 0; column < LedMatrixFontAscii.GLYPH_WIDTH; column++) {
            if (LedMatrixFontAscii.charPixel(code, column, row)) {
                bits = bits | (1 << column);
            }
        }
        return bits;
    }
}
