package io.github.jabrena.juno.thirdparty.grove.sensors;

import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;

/**
 * Allocation-free one-wire reader for DHT-family Grove modules. It drives the signal pin through its {@link Gpio} and
 * times the sensor's pulses with its {@link Clock}, so a unit test can hand it a mocked or simulated pair of each.
 * Create one at startup; {@link #read} allocates nothing.
 */
final class GroveDhtProtocol {
    private static final int FRAME_LENGTH = 5;
    private static final int RESPONSE_TIMEOUT_MICROS = 100;
    private static final int BIT_ONE_THRESHOLD_MICROS = 50;
    private static final int START_LOW_MILLIS = 20;
    private static final int START_RELEASE_MICROS = 40;

    private final Gpio gpio;
    private final Clock clock;

    /** A reader on the board's own pins and timer. */
    GroveDhtProtocol() {
        this(MCU.getGpio(), MCU.getClock());
    }

    /** A reader on {@code gpio} and {@code clock}: mocked or simulated ones in a unit test. */
    GroveDhtProtocol(Gpio gpio, Clock clock) {
        this.gpio = gpio;
        this.clock = clock;
    }

    boolean read(int signalPin, int[] frame, int frameLength) {
        if (frameLength < FRAME_LENGTH || frameLength > frame.length) {
            throw new IllegalArgumentException("frameLength must be at least 5 and fit frame");
        }

        gpio.pinMode(signalPin, Gpio.getOutput());
        gpio.digitalWrite(signalPin, false);
        Delay.millis(START_LOW_MILLIS);
        gpio.pinMode(signalPin, Gpio.getInputPullup());
        Delay.micros(START_RELEASE_MICROS);

        if (!waitForLevel(signalPin, false) || !waitForLevel(signalPin, true)
                || !waitForLevel(signalPin, false)) {
            return false;
        }

        int byte0 = 0;
        int byte1 = 0;
        int byte2 = 0;
        int byte3 = 0;
        int checksum = 0;
        for (int bit = 0; bit < 40; bit++) {
            if (!waitForLevel(signalPin, true)) {
                return false;
            }
            int highStarted = clock.micros();
            if (!waitForLevel(signalPin, false)) {
                return false;
            }
            int pulseMicros = clock.micros() - highStarted;
            if (pulseMicros >= BIT_ONE_THRESHOLD_MICROS) {
                int mask = 1 << (7 - (bit & 7));
                switch (bit >>> 3) {
                    case 0 -> byte0 |= mask;
                    case 1 -> byte1 |= mask;
                    case 2 -> byte2 |= mask;
                    case 3 -> byte3 |= mask;
                    default -> checksum |= mask;
                }
            }
        }

        frame[0] = byte0;
        frame[1] = byte1;
        frame[2] = byte2;
        frame[3] = byte3;
        frame[4] = checksum;
        return ((byte0 + byte1 + byte2 + byte3) & 0xFF) == checksum;
    }

    private boolean waitForLevel(int pin, boolean high) {
        int started = clock.micros();
        while (gpio.digitalRead(pin) != high) {
            if (clock.micros() - started >= RESPONSE_TIMEOUT_MICROS) {
                return false;
            }
        }
        return true;
    }
}
