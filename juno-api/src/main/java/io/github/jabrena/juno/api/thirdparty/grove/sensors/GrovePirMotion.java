package io.github.jabrena.juno.api.thirdparty.grove.sensors;

import io.github.jabrena.juno.api.io.gpio.Gpio;

/**
 * A Grove PIR motion sensor with a digital {@code SIG} output. Configure its socket once at startup
 * with {@link #begin(int)}, then poll {@link #motionDetected(int)} without allocating.
 */
public final class GrovePirMotion {
    private GrovePirMotion() {
    }

    /** Configures the signal pin as a digital input. */
    public static void begin(int signalPin) {
        Gpio.pinMode(signalPin, Gpio.INPUT);
    }

    /** Returns {@code true} while the sensor's signal is high. */
    public static boolean motionDetected(int signalPin) {
        return Gpio.digitalRead(signalPin);
    }
}
