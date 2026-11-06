package io.github.jabrena.juno.hardware.device.tft;

import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.internal.NativeSdCard;
import io.github.jabrena.juno.hardware.led.LedMatrixFontAscii;
import io.github.jabrena.juno.util.Delay;
import org.jspecify.annotations.Nullable;

/**
 * Driver for the ELEGOO 2.8" TFT touch screen shield ("Pantalla Táctil TFT de 2,8 pulgadas"): a
 * 240x320 ILI9341 display on an 8-bit parallel bus, a 4-wire resistive touch panel, and a microSD
 * socket, wired for the shield's standard UNO pinout as used by ELEGOO's {@code Elegoo_TFTLCD} and
 * {@code TouchScreen} libraries.
 *
 * <p>Create one instance at startup and pass it to every class that draws: the display's rotation, cursor, text
 * style and touch calibration belong to that one screen, so two instances would disagree about them.
 *
 * <p>The display bus is a {@link io.github.jabrena.juno.hardware.device.tft.ParallelBus}: each byte is a few GPIO port writes
 * and a run of one color is a single call, so pixels stream from the runtime without a call per byte. Commands
 * still toggle the RS line with {@link Gpio#digitalWrite}, and the touch panel uses {@link Gpio#analogRead}.
 * Fills in colors whose two bytes are equal (e.g. {@link #BLACK}, {@link #WHITE}) are the fastest: the data pins
 * are set once and only the write strobe toggles.
 *
 * <p>The SD socket is an ordinary SPI card reader with chip select on D10: {@link #getSdCard()} is its card. It
 * shares no pins with the display or touch panel.
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
    /** The microSD socket's chip select. */
    private static final int SD_CHIP_SELECT = 10;

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

    private final Gpio gpio;
    private final TftBus bus;
    private final TftTouch touch;
    private final TftGraphics graphics;

    private int width;
    private int height;
    private int rotation;
    private int cursorX;
    private int cursorY;
    private int textSize;
    private int textColor;
    private int textBackground;

    private int touchMinX;
    private int touchMaxX;
    private int touchMinY;
    private int touchMaxY;
    private int touchX;
    private int touchY;
    private int touchRawX;
    private int touchRawY;
    private int touchPressure;

    private @Nullable SdCard sdCard;

    /** A shield on the board's own pins. Create it once, at startup, and pass it to every class that draws. */
    public TftTouchShield() {
        this(ArduinoUno.getGpio());
    }

    /** A shield driven through {@code gpio}: a mocked one in a unit test. */
    public TftTouchShield(Gpio gpio) {
        this.gpio = gpio;
        this.bus = new TftBus(gpio);
        this.touch = new TftTouch(gpio, bus);
        this.graphics = new TftGraphics(this, bus);
    }

    /**
     * The card in the shield's microSD socket; its {@link SdCard#begin()} selects it on D10. The same
     * instance every call.
     */
    public SdCard getSdCard() {
        SdCard card = sdCard;
        if (card == null) {
            card = NativeSdCard.sdCard(SD_CHIP_SELECT);
            sdCard = card;
        }
        return card;
    }

    /**
     * Configures the shield's pins, resets and initializes the ILI9341 in 16-bit color, and selects
     * {@link #PORTRAIT} rotation, a size-1 white-on-black text style, and ELEGOO's default touch
     * calibration. The screen contents are undefined until the first {@link #fillScreen}.
     */
    public void begin() {
        gpio.pinMode(PIN_RD, gpio.getOutput());
        gpio.pinMode(PIN_WR, gpio.getOutput());
        gpio.pinMode(PIN_RS, gpio.getOutput());
        gpio.pinMode(PIN_CS, gpio.getOutput());
        gpio.pinMode(PIN_RESET, gpio.getOutput());
        gpio.digitalWrite(PIN_RD, true);
        gpio.digitalWrite(PIN_WR, true);
        gpio.digitalWrite(PIN_RS, true);
        gpio.digitalWrite(PIN_CS, true);
        bus.restoreDataPins();
        bus.begin();

        gpio.digitalWrite(PIN_RESET, true);
        Delay.millis(5);
        gpio.digitalWrite(PIN_RESET, false);
        Delay.millis(20);
        gpio.digitalWrite(PIN_RESET, true);
        Delay.millis(150);
        gpio.digitalWrite(PIN_CS, false);

        bus.command(SOFT_RESET);
        Delay.millis(50);
        bus.command(DISPLAY_OFF);
        bus.command8(POWER_CONTROL_1, 0x23);
        bus.command8(POWER_CONTROL_2, 0x10);
        bus.command16(VCOM_CONTROL_1, 0x2B2B);
        bus.command8(VCOM_CONTROL_2, 0xC0);
        bus.command8(PIXEL_FORMAT, PIXEL_FORMAT_16_BIT);
        bus.command16(FRAME_CONTROL, 0x001B);
        bus.command8(ENTRY_MODE, 0x07);
        bus.command(SLEEP_OUT);
        Delay.millis(150);
        bus.command(DISPLAY_ON);
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
    public void setRotation(int value) {
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
        bus.command8(MEMORY_ACCESS_CONTROL, madctl);
    }

    /** Current width in pixels, for the current rotation. */
    public int width() {
        return width;
    }

    /** Current height in pixels, for the current rotation. */
    public int height() {
        return height;
    }

    /** Packs 8-bit-per-channel {@code red}, {@code green}, {@code blue} into an RGB565 color. */
    public static int color(int red, int green, int blue) {
        return TftGraphics.color(red, green, blue);
    }

    /** Fills the whole screen with {@code color}. */
    public void fillScreen(int color) {
        graphics.fillScreen(color);
    }

    /** Sets the pixel at ({@code x}, {@code y}) to {@code color}; off-screen pixels are ignored. */
    public void drawPixel(int x, int y, int color) {
        graphics.drawPixel(x, y, color);
    }

    /** Fills a {@code w}x{@code h} rectangle whose top-left corner is ({@code x}, {@code y}), clipped to the screen. */
    public void fillRect(int x, int y, int w, int h, int color) {
        graphics.fillRect(x, y, w, h, color);
    }

    /** Draws a horizontal line of {@code w} pixels starting at ({@code x}, {@code y}). */
    public void drawHorizontalLine(int x, int y, int w, int color) {
        graphics.drawHorizontalLine(x, y, w, color);
    }

    /** Draws a vertical line of {@code h} pixels starting at ({@code x}, {@code y}). */
    public void drawVerticalLine(int x, int y, int h, int color) {
        graphics.drawVerticalLine(x, y, h, color);
    }

    /** Draws the 1-pixel outline of a {@code w}x{@code h} rectangle whose top-left corner is ({@code x}, {@code y}). */
    public void drawRect(int x, int y, int w, int h, int color) {
        graphics.drawRect(x, y, w, h, color);
    }

    /** Fills a circle of radius {@code r} centered on ({@code cx}, {@code cy}), clipped to the screen. */
    public void fillCircle(int cx, int cy, int r, int color) {
        graphics.fillCircle(cx, cy, r, color);
    }

    /** Draws the 1-pixel outline of a circle of radius {@code r} centered on ({@code cx}, {@code cy}). */
    public void drawCircle(int cx, int cy, int r, int color) {
        graphics.drawCircle(cx, cy, r, color);
    }

    /**
     * Opens a {@code w}x{@code h} window with its top-left corner at ({@code x}, {@code y}) for
     * streaming pixels with {@link #pushPixel}, row by row from the top-left — the fast way to draw
     * a small custom bitmap. Returns {@code false}, opening nothing, unless the window lies entirely
     * on screen. Push exactly {@code w * h} pixels before any other drawing call.
     */
    public boolean beginPixels(int x, int y, int w, int h) {
        return graphics.beginPixels(x, y, w, h);
    }

    /** Writes the next pixel of the window opened by {@link #beginPixels}. */
    public void pushPixel(int color) {
        graphics.pushPixel(color);
    }

    /** Moves the text cursor so the next character's top-left corner is at ({@code x}, {@code y}) pixels. */
    public void setCursor(int x, int y) {
        cursorX = x;
        cursorY = y;
    }

    /** Scales text by an integer factor (1 = 6x8-pixel cells, 2 = 12x16, ...); values below 1 are treated as 1. */
    public void setTextSize(int size) {
        textSize = Math.max(size, 1);
    }

    /**
     * Sets the text foreground and background colors. Characters always paint their whole cell, so
     * printing over older text replaces it — pad shorter strings with spaces to erase leftovers.
     */
    public void setTextColor(int foreground, int background) {
        textColor = foreground;
        textBackground = background;
    }

    /**
     * Prints {@code text} at the cursor with the current text size and colors, advancing the
     * cursor. {@code '\n'} moves to the start of the next text line, and text wraps at the right
     * edge. Characters outside printable ASCII (32-126) are drawn as spaces.
     */
    public void print(String text) {
        int length = text.length();
        for (int i = 0; i < length; i++) {
            writeChar(text.charAt(i));
        }
    }

    /** Prints {@code value} in decimal at the cursor. */
    public void print(int value) {
        print(String.valueOf(value));
    }

    /**
     * Prints the first {@code length} bytes of {@code buffer} as ASCII characters at the cursor,
     * like {@link #print(String)} — for text that already lives in a caller-owned byte buffer (e.g.
     * {@link io.github.jabrena.juno.hardware.net.http.Json#getString(byte[], int, String, byte[], int)}'s
     * output) and would otherwise need a runtime {@code String}.
     */
    public void print(byte[] buffer, int length) {
        for (int i = 0; i < length; i++) {
            writeChar(buffer[i] & 0xFF);
        }
    }

    /** Prints {@code text} and then moves the cursor to the start of the next text line. */
    public void println(String text) {
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
    public boolean readTouch() {
        touchPressure = touch.readTouchPressure();
        boolean pressed = touchPressure >= MIN_PRESSURE && touchPressure <= MAX_PRESSURE;
        if (pressed) {
            int firstX = touch.readTouchRawX();
            int firstY = touch.readTouchRawY();
            int secondX = touch.readTouchRawX();
            int secondY = touch.readTouchRawY();
            int secondPressure = touch.readTouchPressure();
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
        touch.restoreBusAfterTouch();
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
    public int touchX() {
        return touchX;
    }

    /** Y coordinate of the last successful {@link #readTouch()}, for the current rotation. */
    public int touchY() {
        return touchY;
    }

    /** Raw 10-bit X reading of the last successful {@link #readTouch()}, for calibration. */
    public int touchRawX() {
        return touchRawX;
    }

    /** Raw 10-bit Y reading of the last successful {@link #readTouch()}, for calibration. */
    public int touchRawY() {
        return touchRawY;
    }

    /** Pressure estimate of the last {@link #readTouch()} (0 when untouched; higher is firmer). */
    public int touchPressure() {
        return touchPressure;
    }

    /**
     * Replaces the touch calibration: the raw readings at portrait x = 0, x = 239, y = 0, and
     * y = 319. Passing a larger "left" than "right" reading mirrors that axis.
     */
    public void calibrateTouch(int rawLeft, int rawRight, int rawTop, int rawBottom) {
        touchMinX = rawLeft;
        touchMaxX = rawRight;
        touchMinY = rawTop;
        touchMaxY = rawBottom;
    }

    private void writeChar(int character) {
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

    private void newLine() {
        cursorX = 0;
        cursorY = cursorY + CHAR_HEIGHT * textSize;
    }

    private void drawChar(int x, int y, int character) {
        int code = character;
        if (code < 32 || code > 126) {
            code = 32;
        }
        int cellWidth = CHAR_WIDTH * textSize;
        int cellHeight = CHAR_HEIGHT * textSize;
        if (x < 0 || y < 0 || x + cellWidth > width || y + cellHeight > height) {
            return;
        }
        bus.setAddressWindow(x, y, x + cellWidth - 1, y + cellHeight - 1);
        for (int row = 0; row < CHAR_HEIGHT; row++) {
            int rowBits = glyphRowBits(code, row);
            for (int repeat = 0; repeat < textSize; repeat++) {
                for (int column = 0; column < CHAR_WIDTH; column++) {
                    int color = textBackground;
                    if (((rowBits >> column) & 1) != 0) {
                        color = textColor;
                    }
                    for (int dot = 0; dot < textSize; dot++) {
                        bus.writePixel(color);
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
