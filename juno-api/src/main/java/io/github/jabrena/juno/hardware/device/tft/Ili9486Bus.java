package io.github.jabrena.juno.hardware.device.tft;

import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.spi.Spi;
import io.github.jabrena.juno.util.Delay;

/**
 * The ILI9486 behind the SPI-to-parallel bridge of a Raspberry Pi 3.5-inch display (the MHS-3.5 and its relatives):
 * the bridge turns every 16 SPI clocks into one write on the controller's 16-bit parallel bus, so a command and each
 * of its parameters is a 16-bit word, its high byte zero, and a pixel is one RGB565 word. The data/command line selects
 * what the next word is. The bridge only writes, so the controller cannot be read back.
 *
 * <p>Words must stay whole: the bridge counts clocks from the moment its chip select falls, so every transfer with the
 * chip select low is a whole number of words. The display and the touch panel share the SPI bus, and each has its own
 * chip select and clock; the display sets its clock when it selects itself after the touch controller has lowered it,
 * and keeps its chip select high whenever it is not transferring.
 *
 * <p>Each call into the core's SPI driver costs far more than the bytes it sends, so the bus sends in blocks with
 * {@link Spi#write}, which a board may feed to its SPI peripheral faster than the core's transfer: a command word in
 * one block, a window's four parameter words in another, and pixels from a buffer that goes out when it fills and
 * when the stream ends. A drawing window and its memory write share one chip-select session.
 */
final class Ili9486Bus {
    static final int NATIVE_WIDTH = 320;
    static final int NATIVE_HEIGHT = 480;

    /** The bridge is rated far faster; the board rounds this down to a rate its SPI peripheral can make. */
    static final int CLOCK_HZ = 32_000_000;
    /** An even size, so a block transfer never splits a pixel word. */
    private static final int BUFFER_BYTES = 1024;

    private static final int SOFTWARE_RESET = 0x01;
    private static final int SLEEP_OUT = 0x11;
    private static final int DISPLAY_ON = 0x29;
    static final int COLUMN_ADDRESS_SET = 0x2A;
    static final int PAGE_ADDRESS_SET = 0x2B;
    static final int MEMORY_WRITE = 0x2C;
    private static final int MEMORY_ACCESS_CONTROL = 0x36;
    private static final int PIXEL_FORMAT = 0x3A;

    /** MADCTL for {@code NATIVE_WIDTH} x {@code NATIVE_HEIGHT}, with the top edge of the panel at the top. */
    static final int MADCTL_PORTRAIT = 0x48;
    /** MADCTL for a 480-by-320 landscape view, rotated from {@link #MADCTL_PORTRAIT}. */
    static final int MADCTL_LANDSCAPE = 0x28;

    /**
     * The interface, power and gamma settings that the Linux {@code waveshare35a} overlay sends to these bridged
     * ILI9486 panels. Each row is a command followed by its parameters; the rows run in order after the sleep-out.
     */
    private static final int[][] INITIALIZATION = {
        {0xB0, 0x00},
        {0xC2, 0x44},
        {0xC5, 0x00, 0x00, 0x00, 0x00},
        {0xE0, 0x0F, 0x1F, 0x1C, 0x0C, 0x0F, 0x08, 0x48, 0x98, 0x37, 0x0A, 0x13, 0x04, 0x11, 0x0D, 0x00},
        {0xE1, 0x0F, 0x32, 0x2E, 0x0B, 0x0D, 0x05, 0x47, 0x75, 0x37, 0x06, 0x10, 0x03, 0x24, 0x20, 0x00},
        {0xE2, 0x0F, 0x32, 0x2E, 0x0B, 0x0D, 0x05, 0x47, 0x75, 0x37, 0x06, 0x10, 0x03, 0x24, 0x20, 0x00},
    };

    private final Gpio gpio;
    private final Spi spi;
    private final int chipSelect;
    private final int dataCommand;
    private final int reset;
    private final byte[] buffer = new byte[BUFFER_BYTES];
    /** A command word or a range's four parameter words, sent as one block. */
    private final byte[] words = new byte[8];
    private int buffered;
    private boolean clockSet;
    // The column and page ranges last sent, or -1 when unknown. The controller keeps them until they are set again,
    // and every memory write restarts at the window's start, so an unchanged range need not be sent twice.
    private int windowLeft = -1;
    private int windowRight = -1;
    private int windowTop = -1;
    private int windowBottom = -1;

    Ili9486Bus(Gpio gpio, Spi spi, int chipSelect, int dataCommand, int reset) {
        this.gpio = gpio;
        this.spi = spi;
        this.chipSelect = chipSelect;
        this.dataCommand = dataCommand;
        this.reset = reset;
    }

    /** Configures the pins and the bus, resets the chip, and runs the initialization. */
    void begin() {
        gpio.pinMode(chipSelect, gpio.getOutput());
        gpio.pinMode(dataCommand, gpio.getOutput());
        gpio.pinMode(reset, gpio.getOutput());
        gpio.digitalWrite(chipSelect, true);
        gpio.digitalWrite(dataCommand, true);
        spi.begin();

        gpio.digitalWrite(reset, true);
        Delay.millis(5);
        gpio.digitalWrite(reset, false);
        Delay.millis(20);
        gpio.digitalWrite(reset, true);
        Delay.millis(120);

        command(SOFTWARE_RESET);
        Delay.millis(120);
        command(SLEEP_OUT);
        Delay.millis(120);
        for (int[] row : INITIALIZATION) {
            select();
            commandWord(row[0]);
            for (int i = 1; i < row.length; i++) {
                word(row[i]);
            }
            gpio.digitalWrite(chipSelect, true);
        }
        command8(PIXEL_FORMAT, 0x55);
        setRotation(MADCTL_PORTRAIT);
        command(DISPLAY_ON);
        Delay.millis(20);
    }

    void setRotation(int madctl) {
        command8(MEMORY_ACCESS_CONTROL, madctl);
        windowLeft = -1;
        windowRight = -1;
        windowTop = -1;
        windowBottom = -1;
    }

    /** Sends a command with no parameters. */
    void command(int value) {
        select();
        commandWord(value);
        gpio.digitalWrite(chipSelect, true);
    }

    /** Sends a command with one parameter. */
    void command8(int value, int parameter) {
        select();
        commandWord(value);
        word(parameter);
        gpio.digitalWrite(chipSelect, true);
    }

    /**
     * Opens the window from its top-left to its bottom-right corner and starts a memory write into it, with the chip
     * select held low, for {@link #pixel} and {@link #endPixels}.
     */
    void beginWindow(int left, int top, int right, int bottom) {
        select();
        if (left != windowLeft || right != windowRight) {
            commandWord(COLUMN_ADDRESS_SET);
            range(left, right);
            windowLeft = left;
            windowRight = right;
        }
        if (top != windowTop || bottom != windowBottom) {
            commandWord(PAGE_ADDRESS_SET);
            range(top, bottom);
            windowTop = top;
            windowBottom = bottom;
        }
        commandWord(MEMORY_WRITE);
        buffered = 0;
    }

    /** Queues one 16-bit RGB565 pixel, most significant byte first: one whole word. */
    void pixel(int color) {
        buffer[buffered] = (byte) (color >> 8);
        buffer[buffered + 1] = (byte) color;
        buffered = buffered + 2;
        if (buffered == BUFFER_BYTES) {
            flush();
        }
    }

    /** Sends the queued pixels and releases the chip select at the end of a pixel stream. */
    void endPixels() {
        flush();
        gpio.digitalWrite(chipSelect, true);
    }

    /** Called after another device on the bus changed its clock: the next selection sets the display's again. */
    void clockChanged() {
        clockSet = false;
    }

    private void flush() {
        if (buffered > 0) {
            spi.write(buffer, buffered);
            buffered = 0;
        }
    }

    /** Sets the display's clock unless it is still set, and pulls the chip select low. */
    private void select() {
        if (!clockSet) {
            spi.setClock(CLOCK_HZ);
            clockSet = true;
        }
        gpio.digitalWrite(chipSelect, false);
    }

    /** One command word in one transfer, with the data/command line low for it and high again after it. */
    private void commandWord(int value) {
        gpio.digitalWrite(dataCommand, false);
        words[0] = 0;
        words[1] = (byte) value;
        spi.write(words, 2);
        gpio.digitalWrite(dataCommand, true);
    }

    /** One parameter word whose low byte is {@code value}. */
    private void word(int value) {
        words[0] = 0;
        words[1] = (byte) value;
        spi.write(words, 2);
    }

    /** A column or page range as four parameter words in one transfer: the start's high and low bytes, then the end's. */
    private void range(int first, int second) {
        words[0] = 0;
        words[1] = (byte) (first >> 8);
        words[2] = 0;
        words[3] = (byte) first;
        words[4] = 0;
        words[5] = (byte) (second >> 8);
        words[6] = 0;
        words[7] = (byte) second;
        spi.write(words, 8);
    }
}
