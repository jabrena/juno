package io.github.jabrena.juno.api.io.gpio;


import io.github.jabrena.juno.api.hardware.internal.NativeGpio;

/**
 * The ELEGOO 2.8" TFT touch shield's hardware for whole-program tests: a {@link Gpio} that every
 * {@code MCU.getGpio()} uses once {@link #install()} has run.
 *
 * <p>The real {@code TftTouchShield} driver bit-bangs an ILI9341 display controller over an 8-bit
 * parallel bus and reads a resistive touch panel through the same pins. This double decodes that
 * bus — WR strobes, RS command/data, column and page address windows, memory writes — into
 * {@link #FRAMEBUFFER}, and answers the panel's analog reads from {@link #touch}, so the driver and
 * the games run unmodified. It models only what the driver uses.
 */
public final class EmulatedShield implements Gpio {
    /** The one emulated shield; its state is static so every program class and test sees the same panel. */
    public static final EmulatedShield INSTANCE = new EmulatedShield();

    /** Framebuffer stride: large enough for either orientation of the 240x320 panel. */
    public static final int STRIDE = 320;

    /**
     * RGB565 pixels in the current rotation's screen coordinates (the address window the driver
     * programs is already rotated by the controller), row-major with stride {@link #STRIDE}.
     */
    public static final int[] FRAMEBUFFER = new int[STRIDE * STRIDE];

    /** The finger's position in portrait panel coordinates (0-239, 0-319), or null when untouched. */
    public static int[] touch;

    // The shield's wiring, as in TftTouchShield: control lines on A0-A4, data on D8, D9, D2-D7.
    private static final int PIN_WR = 15;
    private static final int PIN_RS = 16;
    private static final int PIN_CS = 17;
    private static final int[] DATA_PINS = {8, 9, 2, 3, 4, 5, 6, 7};
    private static final int PIN_TOUCH_XP = 8;
    private static final int PIN_TOUCH_XM = 16;
    private static final int PIN_TOUCH_YP = 17;

    // ELEGOO's default calibration in TftTouchShield: raw readings at the panel's edges.
    private static final int RAW_LEFT = 120;
    private static final int RAW_RIGHT = 900;
    private static final int RAW_TOP = 920;
    private static final int RAW_BOTTOM = 70;

    private static final int COLUMN_ADDRESS_SET = 0x2A;
    private static final int PAGE_ADDRESS_SET = 0x2B;
    private static final int MEMORY_WRITE = 0x2C;

    private static final boolean[] LEVEL = new boolean[32];
    private static final int[] MODE = new int[32];
    private static final int[] PARAMETERS = new int[4];
    private static int command = -1;
    private static int parameterCount;
    private static int columnStart;
    private static int columnEnd;
    private static int pageStart;
    private static int column;
    private static int page;
    private static int pixelHighByte = -1;

    private EmulatedShield() {
    }

    /** Makes {@code MCU.getGpio()} from now on drive this emulated shield instead of the board's pins. */
    public static void install() {
        NativeGpio.setGpio(INSTANCE);
    }

    @Override
    public int getBuiltinLed() {
        return Gpio.getD13();
    }

    @Override
    public void pinMode(int pin, int mode) {
        MODE[pin] = mode;
    }

    @Override
    public boolean digitalRead(int pin) {
        return LEVEL[pin];
    }

    @Override
    public void analogWrite(int pin, int value) {
    }

    @Override
    public void toggle(int pin) {
        digitalWrite(pin, !LEVEL[pin]);
    }

    @Override
    public void digitalWrite(int pin, boolean high) {
        boolean wasHigh = LEVEL[pin];
        LEVEL[pin] = high;
        // The controller latches the data pins on WR's rising edge while CS is low.
        if (pin == PIN_WR && high && !wasHigh && !LEVEL[PIN_CS]) {
            int value = 0;
            for (int bit = 0; bit < 8; bit++) {
                if (LEVEL[DATA_PINS[bit]]) {
                    value = value | (1 << bit);
                }
            }
            if (LEVEL[PIN_RS]) {
                data(value);
            } else {
                command(value);
            }
        }
    }

    private void command(int value) {
        command = value;
        parameterCount = 0;
        pixelHighByte = -1;
        if (command == MEMORY_WRITE) {
            column = columnStart;
            page = pageStart;
        }
    }

    private void data(int value) {
        if (command == COLUMN_ADDRESS_SET || command == PAGE_ADDRESS_SET) {
            if (parameterCount < 4) {
                PARAMETERS[parameterCount] = value;
                parameterCount = parameterCount + 1;
            }
            if (parameterCount == 4) {
                int start = PARAMETERS[0] << 8 | PARAMETERS[1];
                int end = PARAMETERS[2] << 8 | PARAMETERS[3];
                if (command == COLUMN_ADDRESS_SET) {
                    columnStart = start;
                    columnEnd = end;
                } else {
                    pageStart = start;
                }
            }
        } else if (command == MEMORY_WRITE) {
            if (pixelHighByte < 0) {
                pixelHighByte = value;
                return;
            }
            int color = pixelHighByte << 8 | value;
            pixelHighByte = -1;
            if (column < STRIDE && page < STRIDE) {
                FRAMEBUFFER[page * STRIDE + column] = color;
            }
            column = column + 1;
            if (column > columnEnd) {
                column = columnStart;
                page = page + 1;
            }
        }
    }

    /**
     * The panel's analog reads. Which one the driver is taking follows from how it has driven the
     * plates: X+ high means it is measuring X through Y+, Y+ driven high means Y through X-, and
     * otherwise it is measuring pressure.
     */
    @Override
    public int analogRead(int pin) {
        int[] finger = touch;
        if (pin == PIN_TOUCH_YP) {
            if (LEVEL[PIN_TOUCH_XP] && MODE[PIN_TOUCH_XP] == Gpio.getOutput()) {
                return finger == null ? 512 : 1023 - (RAW_LEFT + finger[0] * (RAW_RIGHT - RAW_LEFT) / 240);
            }
            // Pressure is 1023 - (z2 - z1): 500 while touched, 0 (released) otherwise.
            return finger == null ? 1023 : 523;
        }
        if (pin == PIN_TOUCH_XM) {
            if (LEVEL[PIN_TOUCH_YP] && MODE[PIN_TOUCH_YP] == Gpio.getOutput()) {
                return finger == null ? 512 : 1023 - (RAW_TOP + finger[1] * (RAW_BOTTOM - RAW_TOP) / 320);
            }
            return 0;
        }
        return 0;
    }
}
