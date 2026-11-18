package io.github.jabrena.juno.hardware.device.tft;

import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.spi.Spi;

/**
 * The XPT2046 resistive touch controller on the SPI bus: a 12-bit X, Y and pressure reading per command. It has its own
 * chip select, and the display's chip select must be high while it runs.
 */
final class Xpt2046Touch {
    /** The command that measures the X position with the panel driven from its X axis. */
    private static final int MEASURE_X = 0xD0;
    /** The command that measures the Y position. */
    private static final int MEASURE_Y = 0x90;
    /** The command that measures the first pressure (Z1). */
    private static final int MEASURE_Z1 = 0xB0;
    /** The command that measures the second pressure (Z2). */
    private static final int MEASURE_Z2 = 0xC0;
    /** Z1 readings at or above this count as touched; an untouched panel reads close to zero. */
    static final int PRESSED_THRESHOLD = 100;
    /** The XPT2046's clock limit is 2.5 MHz; it shares the bus with a display that runs far faster. */
    static final int CLOCK_HZ = 2_000_000;

    private final Gpio gpio;
    private final Spi spi;
    private final int chipSelect;

    Xpt2046Touch(Gpio gpio, Spi spi, int chipSelect) {
        this.gpio = gpio;
        this.spi = spi;
        this.chipSelect = chipSelect;
    }

    void begin() {
        gpio.pinMode(chipSelect, gpio.getOutput());
        gpio.digitalWrite(chipSelect, true);
    }

    /** The raw 12-bit X reading. */
    int readX() {
        return measure(MEASURE_X);
    }

    /** The raw 12-bit Y reading. */
    int readY() {
        return measure(MEASURE_Y);
    }

    /** The raw pressure estimate: Z1, which rises with the pressure of the stylus or finger. */
    int readPressure() {
        return measure(MEASURE_Z1);
    }

    /** The raw second pressure reading (Z2), for calibration. */
    int readSecondPressure() {
        return measure(MEASURE_Z2);
    }

    private int measure(int command) {
        spi.setClock(CLOCK_HZ);
        gpio.digitalWrite(chipSelect, false);
        spi.transfer(command);
        int high = spi.transfer(0);
        int low = spi.transfer(0);
        gpio.digitalWrite(chipSelect, true);
        return (((high << 8) | low) >> 3) & 0xFFF;
    }
}
