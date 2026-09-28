package io.github.jabrena.juno.api.tft;

import io.github.jabrena.juno.api.io.Gpio;

/** The ILI9341's 8-bit parallel bus protocol, split out of {@link TftTouchShield} to keep its cyclomatic complexity down. */
final class TftBus {
    private static final int PIN_D0 = 8;
    private static final int PIN_D1 = 9;
    private static final int PIN_D2 = 2;
    private static final int PIN_D3 = 3;
    private static final int PIN_D4 = 4;
    private static final int PIN_D5 = 5;
    private static final int PIN_D6 = 6;
    private static final int PIN_D7 = 7;

    // Last byte driven onto the data pins, or -1 when their levels are unknown (after touch reads).
    private static int busData;

    private TftBus() {
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
        for (int i = 0; i < count; i++) {
            writePixel(color);
        }
    }

    static void writePixel(int color) {
        write8(color >> 8);
        write8(color);
    }

    // Leaves RS high, so the parameter/pixel bytes that follow are sent as data.
    static void command(int value) {
        Gpio.digitalWrite(TftTouchShield.PIN_RS, false);
        write8(value);
        Gpio.digitalWrite(TftTouchShield.PIN_RS, true);
    }

    static void command8(int register, int value) {
        command(register);
        write8(value);
    }

    static void command16(int register, int value) {
        command(register);
        write16(value);
    }

    private static void write16(int value) {
        write8(value >> 8);
        write8(value);
    }

    // Drives one byte onto the data pins and strobes WR, rewriting only the pins whose level changes.
    private static void write8(int value) {
        int data = value & 0xFF;
        int changed = data ^ busData;
        if (busData < 0) {
            changed = 0xFF;
        }
        if (changed != 0) {
            if ((changed & 0x01) != 0) {
                Gpio.digitalWrite(PIN_D0, (data & 0x01) != 0);
            }
            if ((changed & 0x02) != 0) {
                Gpio.digitalWrite(PIN_D1, (data & 0x02) != 0);
            }
            if ((changed & 0x04) != 0) {
                Gpio.digitalWrite(PIN_D2, (data & 0x04) != 0);
            }
            if ((changed & 0x08) != 0) {
                Gpio.digitalWrite(PIN_D3, (data & 0x08) != 0);
            }
            if ((changed & 0x10) != 0) {
                Gpio.digitalWrite(PIN_D4, (data & 0x10) != 0);
            }
            if ((changed & 0x20) != 0) {
                Gpio.digitalWrite(PIN_D5, (data & 0x20) != 0);
            }
            if ((changed & 0x40) != 0) {
                Gpio.digitalWrite(PIN_D6, (data & 0x40) != 0);
            }
            if ((changed & 0x80) != 0) {
                Gpio.digitalWrite(PIN_D7, (data & 0x80) != 0);
            }
            busData = data;
        }
        Gpio.digitalWrite(TftTouchShield.PIN_WR, false);
        Gpio.digitalWrite(TftTouchShield.PIN_WR, true);
    }

    static void restoreDataPins() {
        Gpio.pinMode(PIN_D0, Gpio.OUTPUT);
        Gpio.pinMode(PIN_D1, Gpio.OUTPUT);
        Gpio.pinMode(PIN_D2, Gpio.OUTPUT);
        Gpio.pinMode(PIN_D3, Gpio.OUTPUT);
        Gpio.pinMode(PIN_D4, Gpio.OUTPUT);
        Gpio.pinMode(PIN_D5, Gpio.OUTPUT);
        Gpio.pinMode(PIN_D6, Gpio.OUTPUT);
        Gpio.pinMode(PIN_D7, Gpio.OUTPUT);
        busData = -1;
    }
}
