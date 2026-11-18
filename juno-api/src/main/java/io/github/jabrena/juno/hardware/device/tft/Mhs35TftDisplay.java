package io.github.jabrena.juno.hardware.device.tft;

import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.carrier.PortentaHatCarrier;
import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.spi.Spi;
import io.github.jabrena.juno.hardware.touch.TouchScreen;

/**
 * Driver for the MHS-3.5-inch Raspberry Pi display: a 480x320 TFT on the Pi's 40-pin header with an ILI9486
 * controller and a resistive XPT2046 touch panel. The controller sits behind an SPI-to-parallel bridge, so every
 * command, parameter and pixel is a 16-bit word on the SPI bus, and the display cannot be read back. It is a HAT, so
 * the Portenta Hat Carrier's header carries it: {@link PortentaHatCarrier} names its data/command, reset and
 * chip-select pins, and the SPI bus is {@code MCU.getSpi()}.
 *
 * <p>Create one instance at startup and pass it to every class that draws. The display starts in
 * {@link #LANDSCAPE}, 480 pixels wide. A program laid out for a smaller screen, such as the 320x240 of a 2.8-inch
 * panel, calls {@link #fitLogicalSize} once: it keeps drawing in its own coordinates, which the display scales up
 * uniformly to fill the panel, centered, and its touches arrive in those coordinates too.
 *
 * <p>Pixels are 16-bit RGB565, sent most significant byte first. A full-screen fill sends 307200 bytes, so it takes
 * a noticeable fraction of a second at the core's default SPI clock; small rectangles and text are quick.
 *
 * <p>The backlight is not switched by this driver: the MHS board's backlight line is not on a pin Juno names, so the
 * panel stays lit whenever the board has power.
 */
public final class Mhs35TftDisplay implements Display, TouchScreen {
    /** Portrait, 320 pixels wide and 480 high. */
    public static final int PORTRAIT = 0;
    /** Landscape, 480 pixels wide and 320 high. */
    public static final int LANDSCAPE = 1;

    /** Width of one character cell at text size 1 (5-pixel glyph plus 1 pixel of spacing). */
    public static final int CHAR_WIDTH = 6;
    /** Height of one character cell at text size 1 (7-pixel glyph plus 1 pixel of spacing). */
    public static final int CHAR_HEIGHT = 8;

    // The raw XPT2046 readings at the panel's edges, as the Raspberry Pi calibration for these 3.5-inch HATs gives
    // them: in landscape the screen's width runs along the raw Y axis, from high at the left to low at the right.
    private static final int RAW_LOW_X = 268;
    private static final int RAW_HIGH_X = 3880;
    private static final int RAW_LOW_Y = 227;
    private static final int RAW_HIGH_Y = 3936;

    private final Ili9486Bus bus;
    private final Xpt2046Touch touch;
    private final Viewport viewport = new Viewport();
    private final TouchCalibration calibration = new TouchCalibration();
    private final Mhs35Text text;

    private int panelWidth;
    private int panelHeight;
    private int width;
    private int height;
    private int touchX;
    private int touchY;
    private int pixelsRemaining;

    /** A display on the board's own pins, on the Portenta Hat Carrier. Create it once, at startup. */
    public Mhs35TftDisplay(PortentaHatCarrier carrier) {
        this(MCU.getGpio(), MCU.getSpi(), carrier);
    }

    /** A display driven through {@code gpio} and {@code spi}: mocked ones in a unit test. */
    public Mhs35TftDisplay(Gpio gpio, Spi spi, PortentaHatCarrier carrier) {
        bus = new Ili9486Bus(gpio, spi, carrier.getLcdChipSelect(), carrier.getLcdDataCommand(), carrier.getLcdReset());
        touch = new Xpt2046Touch(gpio, spi, carrier.getTouchChipSelect());
        bus.begin();
        touch.begin();
        text = new Mhs35Text(this, bus, viewport);
        setRotation(LANDSCAPE);
    }

    /**
     * Selects {@link #PORTRAIT} or {@link #LANDSCAPE}, draws at the panel's own resolution again, and restores that
     * rotation's default touch calibration. The existing screen contents are not redrawn.
     */
    public void setRotation(int value) {
        if ((value & 1) == LANDSCAPE) {
            panelWidth = Ili9486Bus.NATIVE_HEIGHT;
            panelHeight = Ili9486Bus.NATIVE_WIDTH;
            bus.setRotation(Ili9486Bus.MADCTL_LANDSCAPE);
            calibration.set(true, RAW_HIGH_Y, RAW_LOW_Y, RAW_LOW_X, RAW_HIGH_X);
        } else {
            panelWidth = Ili9486Bus.NATIVE_WIDTH;
            panelHeight = Ili9486Bus.NATIVE_HEIGHT;
            bus.setRotation(Ili9486Bus.MADCTL_PORTRAIT);
            calibration.set(false, RAW_LOW_X, RAW_HIGH_X, RAW_LOW_Y, RAW_HIGH_Y);
        }
        viewport.identity();
        width = panelWidth;
        height = panelHeight;
    }

    /**
     * Lets a program laid out for a {@code logicalWidth} x {@code logicalHeight} screen fill this one: from now on it
     * draws, prints and reads touches in those coordinates, scaled up uniformly as far as the panel allows and
     * centered, and {@link #width()}/{@link #height()} return them. The bars beside a scaled area are cleared to
     * black. {@link #setRotation} returns to the panel's own resolution.
     */
    public void fitLogicalSize(int logicalWidth, int logicalHeight) {
        viewport.identity();
        fillPanel(0, 0, panelWidth, panelHeight, BLACK);
        viewport.fit(panelWidth, panelHeight, logicalWidth, logicalHeight);
        width = logicalWidth;
        height = logicalHeight;
    }

    /** The current width in drawing coordinates: the panel's, or the size passed to {@link #fitLogicalSize}. */
    @Override
    public int width() {
        return width;
    }

    /** The current height in drawing coordinates: the panel's, or the size passed to {@link #fitLogicalSize}. */
    @Override
    public int height() {
        return height;
    }

    @Override
    public void fillScreen(int color) {
        fillRect(0, 0, width, height, color);
    }

    @Override
    public void drawPixel(int x, int y, int color) {
        fillRect(x, y, 1, 1, color);
    }

    @Override
    public void fillRect(int x, int y, int rectWidth, int rectHeight, int color) {
        int left = Math.max(x, 0);
        int top = Math.max(y, 0);
        int right = Math.min(x + rectWidth, width);
        int bottom = Math.min(y + rectHeight, height);
        if (right <= left || bottom <= top) {
            return;
        }
        fillPanel(viewport.panelX(left), viewport.panelY(top), viewport.panelX(right), viewport.panelY(bottom), color);
    }

    /** Nothing to do: every drawing call reaches the panel before it returns. */
    @Override
    public void flush() {
    }

    @Override
    public void drawHorizontalLine(int x, int y, int lineWidth, int color) {
        fillRect(x, y, lineWidth, 1, color);
    }

    @Override
    public void drawVerticalLine(int x, int y, int lineHeight, int color) {
        fillRect(x, y, 1, lineHeight, color);
    }

    @Override
    public void drawRect(int x, int y, int rectWidth, int rectHeight, int color) {
        fillRect(x, y, rectWidth, 1, color);
        fillRect(x, y + rectHeight - 1, rectWidth, 1, color);
        fillRect(x, y, 1, rectHeight, color);
        fillRect(x + rectWidth - 1, y, 1, rectHeight, color);
    }

    @Override
    public void fillCircle(int cx, int cy, int r, int color) {
        int dx = r;
        for (int dy = 0; dy <= r; dy++) {
            while (dx * dx + dy * dy > r * r) {
                dx = dx - 1;
            }
            fillRect(cx - dx, cy - dy, 2 * dx + 1, 1, color);
            if (dy != 0) {
                fillRect(cx - dx, cy + dy, 2 * dx + 1, 1, color);
            }
        }
    }

    @Override
    public void drawCircle(int cx, int cy, int r, int color) {
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

    /**
     * Opens a {@code w}x{@code h} window with its top-left corner at ({@code x}, {@code y}) for streaming pixels with
     * {@link #pushPixel}, row by row from the top-left: the fast way to draw a custom bitmap, or a row whose pixels
     * differ, in one pass with no flicker. Returns {@code false}, opening nothing, unless the window lies entirely on
     * screen and the display draws at the panel's own resolution (no {@link #fitLogicalSize}). Push exactly
     * {@code w * h} pixels before any other drawing call; the last one ends the stream.
     */
    public boolean beginPixels(int x, int y, int w, int h) {
        if (!viewport.isIdentity() || w <= 0 || h <= 0 || x < 0 || y < 0 || x + w > width || y + h > height) {
            return false;
        }
        bus.beginWindow(x, y, x + w - 1, y + h - 1);
        pixelsRemaining = w * h;
        return true;
    }

    /** Writes the next pixel of the window opened by {@link #beginPixels}; pixels beyond the window are ignored. */
    public void pushPixel(int color) {
        if (pixelsRemaining <= 0) {
            return;
        }
        bus.pixel(color);
        pixelsRemaining = pixelsRemaining - 1;
        if (pixelsRemaining == 0) {
            bus.endPixels();
        }
    }

    /** Moves the text cursor so the next character's top-left corner is at ({@code x}, {@code y}) pixels. */
    @Override
    public void setCursor(int x, int y) {
        text.setCursor(x, y);
    }

    /** Sets the text colors; every character paints its whole cell, so printing over older text replaces it. */
    @Override
    public void setTextColor(int foreground, int background) {
        text.setColors(foreground, background);
    }

    /** Scales text by an integer factor (1 = 6x8-pixel cells, 2 = 12x16, ...); values below 1 are treated as 1. */
    @Override
    public void setTextSize(int size) {
        text.setSize(size);
    }

    @Override
    public void print(char character) {
        text.write(character);
    }

    @Override
    public void print(String message) {
        text.print(message);
    }

    /** Prints {@code value} in decimal at the cursor, digit by digit, without building a string. */
    public void print(int value) {
        text.print(value);
    }

    @Override
    public void println(String message) {
        text.print(message);
        text.newLine();
    }

    /**
     * Samples the touch panel, returning whether it is pressed; when it is, {@link #touchX()} and {@link #touchY()}
     * hold the position in drawing coordinates, clamped to the screen.
     */
    @Override
    public boolean readTouch() {
        int pressure = touch.readPressure();
        bus.clockChanged();
        if (pressure < Xpt2046Touch.PRESSED_THRESHOLD) {
            return false;
        }
        int rawX = touch.readX();
        int rawY = touch.readY();
        touchX = TouchCalibration.clamp(viewport.logicalX(calibration.column(rawX, rawY, panelWidth)), width - 1);
        touchY = TouchCalibration.clamp(viewport.logicalY(calibration.row(rawX, rawY, panelHeight)), height - 1);
        return true;
    }

    @Override
    public int touchX() {
        return touchX;
    }

    @Override
    public int touchY() {
        return touchY;
    }

    /**
     * Replaces the touch calibration of the current rotation: the raw readings at the panel's left, right, top and
     * bottom edges, along the axes that rotation uses. A larger "left" than "right" reading mirrors that axis.
     */
    public void calibrateTouch(int rawLeft, int rawRight, int rawTop, int rawBottom) {
        calibration.edges(rawLeft, rawRight, rawTop, rawBottom);
    }

    /** Fills the panel rectangle from ({@code left}, {@code top}) up to, not including, ({@code right}, {@code bottom}). */
    private void fillPanel(int left, int top, int right, int bottom, int color) {
        if (right <= left || bottom <= top) {
            return;
        }
        bus.beginWindow(left, top, right - 1, bottom - 1);
        int count = (right - left) * (bottom - top);
        for (int i = 0; i < count; i++) {
            bus.pixel(color);
        }
        bus.endPixels();
    }
}
