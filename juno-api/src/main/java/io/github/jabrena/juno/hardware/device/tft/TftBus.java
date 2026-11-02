package io.github.jabrena.juno.hardware.device.tft;

import io.github.jabrena.juno.hardware.gpio.Gpio;

/** The ILI9341's 8-bit parallel bus protocol, split out of {@link TftTouchShield} to keep its cyclomatic complexity down. */
final class TftBus {
    /** The shield's data lines D0-D7, bit 0 first. */
    private static final int[] DATA_PINS = {8, 9, 2, 3, 4, 5, 6, 7};

    private final Gpio gpio;

    // The address window last sent, or -1 when unknown. The controller keeps its window until it is set again,
    // and every MEMORY_WRITE restarts at the window's start, so an unchanged range need not be sent twice.
    private int windowLeft = -1;
    private int windowRight = -1;
    private int windowTop = -1;
    private int windowBottom = -1;

    TftBus(Gpio gpio) {
        this.gpio = gpio;
    }

    /** Hands the data lines and the write strobe to {@link ParallelBus}; they must already be outputs. */
    void begin() {
        ParallelBus.begin(DATA_PINS, TftTouchShield.PIN_WR);
        forgetWindow();
    }

    /** Sets the window pixels are written into, sending only the column or page range that changed. */
    void setAddressWindow(int left, int top, int right, int bottom) {
        if (left != windowLeft || right != windowRight) {
            command(TftTouchShield.COLUMN_ADDRESS_SET);
            write16(left);
            write16(right);
            windowLeft = left;
            windowRight = right;
        }
        if (top != windowTop || bottom != windowBottom) {
            command(TftTouchShield.PAGE_ADDRESS_SET);
            write16(top);
            write16(bottom);
            windowTop = top;
            windowBottom = bottom;
        }
        command(TftTouchShield.MEMORY_WRITE);
    }

    private void forgetWindow() {
        windowLeft = -1;
        windowRight = -1;
        windowTop = -1;
        windowBottom = -1;
    }

    void writePixels(int color, int count) {
        ParallelBus.repeat16(color, count);
    }

    void writePixel(int color) {
        ParallelBus.repeat16(color, 1);
    }

    // Leaves RS high, so the parameter/pixel bytes that follow are sent as data.
    void command(int value) {
        gpio.digitalWrite(TftTouchShield.PIN_RS, false);
        ParallelBus.write(value);
        gpio.digitalWrite(TftTouchShield.PIN_RS, true);
    }

    // Configuration commands (rotation, power, reset) may move the controller's window: send it afresh next time.
    void command8(int register, int value) {
        forgetWindow();
        command(register);
        ParallelBus.write(value);
    }

    void command16(int register, int value) {
        forgetWindow();
        command(register);
        write16(value);
    }

    private void write16(int value) {
        ParallelBus.write(value >> 8);
        ParallelBus.write(value);
    }

    void restoreDataPins() {
        for (int i = 0; i < ParallelBus.WIDTH; i++) {
            gpio.pinMode(DATA_PINS[i], Gpio.getOutput());
        }
    }
}
