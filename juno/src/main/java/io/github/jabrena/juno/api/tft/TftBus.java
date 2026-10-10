package io.github.jabrena.juno.api.tft;

import io.github.jabrena.juno.api.io.Gpio;
import io.github.jabrena.juno.api.io.ParallelBus;

/** The ILI9341's 8-bit parallel bus protocol, split out of {@link TftTouchShield} to keep its cyclomatic complexity down. */
final class TftBus {
    /** The shield's data lines D0-D7, bit 0 first. */
    private static final int[] DATA_PINS = {8, 9, 2, 3, 4, 5, 6, 7};

    private TftBus() {
    }

    /** Hands the data lines and the write strobe to {@link ParallelBus}; they must already be outputs. */
    static void begin() {
        ParallelBus.begin(DATA_PINS, TftTouchShield.PIN_WR);
    }

    static void setAddressWindow(int left, int top, int right, int bottom) {
        command(TftTouchShield.COLUMN_ADDRESS_SET);
        write16(left);
        write16(right);
        command(TftTouchShield.PAGE_ADDRESS_SET);
        write16(top);
        write16(bottom);
        command(TftTouchShield.MEMORY_WRITE);
    }

    static void writePixels(int color, int count) {
        ParallelBus.repeat16(color, count);
    }

    static void writePixel(int color) {
        ParallelBus.repeat16(color, 1);
    }

    // Leaves RS high, so the parameter/pixel bytes that follow are sent as data.
    static void command(int value) {
        Gpio.digitalWrite(TftTouchShield.PIN_RS, false);
        ParallelBus.write(value);
        Gpio.digitalWrite(TftTouchShield.PIN_RS, true);
    }

    static void command8(int register, int value) {
        command(register);
        ParallelBus.write(value);
    }

    static void command16(int register, int value) {
        command(register);
        write16(value);
    }

    private static void write16(int value) {
        ParallelBus.write(value >> 8);
        ParallelBus.write(value);
    }

    static void restoreDataPins() {
        for (int i = 0; i < ParallelBus.WIDTH; i++) {
            Gpio.pinMode(DATA_PINS[i], Gpio.OUTPUT);
        }
    }
}
