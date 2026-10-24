package io.github.jabrena.juno.hardware.device.grove.sensors;

import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.gpio.Gpio;

/**
 * A Grove PIR motion sensor with a digital {@code SIG} output. Configure its socket once at startup
 * with {@link #begin(int)}, then poll {@link #motionDetected(int)} without allocating.
 */
public final class GrovePirMotion {
    private final Gpio gpio;

    /** A sensor on the board's own pins. Create it once, at startup. */
    public GrovePirMotion() {
        this(ArduinoUno.getGpio());
    }

    /** A sensor read through {@code gpio}: a mocked one in a unit test. */
    public GrovePirMotion(Gpio gpio) {
        this.gpio = gpio;
    }

    /** Configures the signal pin as a digital input. */
    public void begin(int signalPin) {
        gpio.pinMode(signalPin, Gpio.getInput());
    }

    /** Returns {@code true} while the sensor's signal is high. */
    public boolean motionDetected(int signalPin) {
        return gpio.digitalRead(signalPin);
    }
}
