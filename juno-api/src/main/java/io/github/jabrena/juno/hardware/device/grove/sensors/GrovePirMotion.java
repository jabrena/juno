package io.github.jabrena.juno.hardware.device.grove.sensors;

import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.robotics.MotionDetector;
import io.github.jabrena.juno.robotics.SampleProvider;

/**
 * A Grove PIR motion sensor with a digital {@code SIG} output. Configure its socket once at startup
 * with {@link #begin(int)}, then poll {@link #motionDetected(int)} without allocating.
 */
public final class GrovePirMotion implements MotionDetector, SampleProvider {
    private static final int NO_PIN = -1;

    private final Gpio gpio;
    private final int signalPin;

    /** A sensor read through {@code gpio}: a mocked one in a unit test. */
    public GrovePirMotion(Gpio gpio) {
        this(gpio, NO_PIN);
    }

    /**
     * A sensor on {@code signalPin} read through {@code gpio}; the pin is configured as an input here. This is the form
     * that works as a {@link MotionDetector} and a {@link SampleProvider}. Create it once, at startup.
     */
    public GrovePirMotion(Gpio gpio, int signalPin) {
        this.gpio = gpio;
        this.signalPin = signalPin;
        if (signalPin != NO_PIN) {
            gpio.pinMode(signalPin, gpio.getInput());
        }
    }

    /** Configures the signal pin as a digital input. */
    public void begin(int signalPin) {
        gpio.pinMode(signalPin, gpio.getInput());
    }

    /** Returns {@code true} while the sensor's signal is high. */
    public boolean motionDetected(int signalPin) {
        return gpio.digitalRead(signalPin);
    }

    /**
     * Returns whether the bound signal is high.
     *
     * @throws IllegalStateException if the sensor was created without its signal pin
     */
    @Override
    public boolean isMotionDetected() {
        if (signalPin == NO_PIN) {
            throw new IllegalStateException("create GrovePirMotion with its signal pin to read it as a MotionDetector");
        }
        return gpio.digitalRead(signalPin);
    }

    /** Returns 1: the sample is one motion flag. */
    @Override
    public int sampleSize() {
        return 1;
    }

    /** Writes 1 when motion is detected and 0 otherwise, at {@code offset}. */
    @Override
    public void fetchSample(float[] sample, int offset) {
        sample[offset] = isMotionDetected() ? 1f : 0f;
    }
}
