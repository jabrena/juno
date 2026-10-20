package io.github.jabrena.juno.thirdparty.tft;

import io.github.jabrena.juno.hardware.gpio.EmulatedShield;

/**
 * Test double for the {@code juno} artifact's native {@code ParallelBus}, shadowing it on the test classpath (test
 * classes come first). It drives the bus pin by pin through {@link EmulatedShield}, the same levels and strobes the
 * runtime writes a port at a time, so the emulated display decodes them unchanged.
 */
public final class ParallelBus {
    public static final int WIDTH = 8;

    private static final int[] PINS = new int[WIDTH];
    private static int strobe;

    private ParallelBus() {
    }

    public static void begin(int[] dataPins, int strobePin) {
        if (dataPins.length != WIDTH) {
            throw new IllegalArgumentException("a parallel bus has " + WIDTH + " data pins, not " + dataPins.length);
        }
        System.arraycopy(dataPins, 0, PINS, 0, WIDTH);
        strobe = strobePin;
        EmulatedShield.INSTANCE.digitalWrite(strobe, true);
    }

    public static void write(int value) {
        for (int bit = 0; bit < WIDTH; bit++) {
            EmulatedShield.INSTANCE.digitalWrite(PINS[bit], ((value >> bit) & 1) != 0);
        }
        EmulatedShield.INSTANCE.digitalWrite(strobe, false);
        EmulatedShield.INSTANCE.digitalWrite(strobe, true);
    }

    public static void repeat16(int value, int count) {
        for (int i = 0; i < count; i++) {
            write(value >> 8);
            write(value);
        }
    }
}
