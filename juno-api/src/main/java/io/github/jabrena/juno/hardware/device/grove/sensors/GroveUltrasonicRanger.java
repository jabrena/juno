package io.github.jabrena.juno.hardware.device.grove.sensors;

import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.robotics.RangeFinder;
import io.github.jabrena.juno.robotics.SampleProvider;
import io.github.jabrena.juno.util.Delay;

/**
 * Grove Ultrasonic Ranger (v1 or v2) on one Grove digital socket.
 *
 * <p>The module uses one shared signal pin: it is driven high for at least 10 microseconds to trigger a reading, then
 * it becomes the echo input. {@link #getRange()} returns metres, or {@link Float#NaN} when no echo arrives within
 * 30 ms. It implements {@link RangeFinder} as well as {@link SampleProvider}; both paths allocate nothing.
 */
public final class GroveUltrasonicRanger implements RangeFinder, SampleProvider {
    private static final int TRIGGER_SETTLE_MICROS = 2;
    private static final int TRIGGER_MICROS = 10;
    private static final int ECHO_TIMEOUT_MICROS = 30_000;
    private static final float METRES_PER_MICROSECOND = 0.00017f;

    private final Gpio gpio;
    private final Clock clock;
    private final int signalPin;

    /** A ranger on {@code signalPin}; {@code gpio} and {@code clock} can be test doubles. */
    public GroveUltrasonicRanger(Gpio gpio, Clock clock, int signalPin) {
        if (signalPin < 0) {
            throw new IllegalArgumentException("Grove ultrasonic ranger signal pin must be non-negative");
        }
        this.gpio = gpio;
        this.clock = clock;
        this.signalPin = signalPin;
    }

    /** Sends one trigger pulse and returns the measured range in metres, or {@link Float#NaN} after a timeout. */
    @Override
    public float getRange() {
        gpio.pinMode(signalPin, gpio.getOutput());
        gpio.digitalWrite(signalPin, false);
        Delay.micros(TRIGGER_SETTLE_MICROS);
        gpio.digitalWrite(signalPin, true);
        Delay.micros(TRIGGER_MICROS);
        gpio.digitalWrite(signalPin, false);
        gpio.pinMode(signalPin, gpio.getInput());
        if (!waitForLevel(true)) {
            return Float.NaN;
        }
        int started = clock.micros();
        if (!waitForLevel(false)) {
            return Float.NaN;
        }
        return (clock.micros() - started) * METRES_PER_MICROSECOND;
    }

    /** Returns one range sample. */
    @Override
    public int sampleSize() {
        return 1;
    }

    /** Writes one range sample in metres, or {@link Float#NaN} after a timeout. */
    @Override
    public void fetchSample(float[] sample, int offset) {
        sample[offset] = getRange();
    }

    private boolean waitForLevel(boolean high) {
        int started = clock.micros();
        while (gpio.digitalRead(signalPin) != high) {
            if (clock.micros() - started >= ECHO_TIMEOUT_MICROS) {
                return false;
            }
        }
        return true;
    }
}
