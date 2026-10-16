package io.github.jabrena.juno.api.thirdparty.grove.sensors;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.Gpio;

/** Shared allocation-free one-wire reader for DHT-family Grove modules. */
final class GroveDhtProtocol {
    static final int FRAME_LENGTH = 5;

    private static final int RESPONSE_TIMEOUT_MICROS = 100;
    private static final int BIT_ONE_THRESHOLD_MICROS = 50;
    private static final int START_LOW_MILLIS = 20;
    private static final int START_RELEASE_MICROS = 40;

    private GroveDhtProtocol() {
    }

    static boolean read(int signalPin, int[] frame, int frameLength) {
        if (frameLength < FRAME_LENGTH || frameLength > frame.length) {
            throw new IllegalArgumentException("frameLength must be at least 5 and fit frame");
        }

        Gpio.pinMode(signalPin, Gpio.OUTPUT);
        Gpio.digitalWrite(signalPin, false);
        Delay.millis(START_LOW_MILLIS);
        Gpio.pinMode(signalPin, Gpio.INPUT_PULLUP);
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
            int highStarted = Clock.micros();
            if (!waitForLevel(signalPin, false)) {
                return false;
            }
            int pulseMicros = Clock.micros() - highStarted;
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

    private static boolean waitForLevel(int pin, boolean high) {
        int started = Clock.micros();
        while (Gpio.digitalRead(pin) != high) {
            if (Clock.micros() - started >= RESPONSE_TIMEOUT_MICROS) {
                return false;
            }
        }
        return true;
    }
}
