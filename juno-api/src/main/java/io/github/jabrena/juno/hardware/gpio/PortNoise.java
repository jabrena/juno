package io.github.jabrena.juno.hardware.gpio;

import io.github.jabrena.juno.hardware.ArduinoOne;
import io.github.jabrena.juno.hardware.time.Clock;

/**
 * Collects a best-effort startup seed from a user-provided physical noise source attached to Arduino pins.
 *
 * <p>This is deliberately <strong>not</strong> a cryptographic entropy API. A floating or attacker-controlled pin can
 * be biased or predictable; use a board-native provider through {@link java.util.UUID#randomUUID()} on supported
 * boards, or attach an independently characterised noise circuit. A return value of zero means the simple health
 * checks rejected the sampled stream.
 */
public final class PortNoise {
    private final Gpio gpio;
    private final Clock clock;

    /** Samples the board's own pins and timer. */
    public PortNoise() {
        this(ArduinoOne.getGpio(), ArduinoOne.getClock());
    }

    /** Samples {@code gpio} and {@code clock}: mocked ones in a unit test. */
    public PortNoise(Gpio gpio, Clock clock) {
        this.gpio = gpio;
        this.clock = clock;
    }

    /**
     * Samples the two selected pins and timer jitter without allocating. Call once at startup, not in a hot loop.
     *
     * @param analogPin analog input wired to the physical noise source
     * @param digitalPin digital input wired to a comparator or other independent noise source
     * @param samples number of samples; must be at least 32
     * @return a mixed seed, or zero when the repetition or proportion health check fails
     */
    public int collectSeed(int analogPin, int digitalPin, int samples) {
        if (samples < 32) {
            throw new IllegalArgumentException("samples must be at least 32");
        }
        int state = 0x6d2b79f5;
        int previous = -1;
        int repetitions = 0;
        int ones = 0;
        for (int index = 0; index < samples; index++) {
            int analog = gpio.analogRead(analogPin);
            int digital = gpio.digitalRead(digitalPin) ? 1 : 0;
            int sample = (analog & 3) | (digital << 2) | ((clock.micros() & 31) << 3);
            if (sample == previous) {
                repetitions++;
                if (repetitions == 32) {
                    return 0;
                }
            } else {
                repetitions = 0;
                previous = sample;
            }
            ones += sample & 1;
            state ^= sample + 0x9e3779b9 + (state << 6) + (state >>> 2);
            state ^= state << 13;
            state ^= state >>> 17;
            state ^= state << 5;
        }
        if (ones * 4 < samples || ones * 4 > samples * 3) {
            return 0;
        }
        return state;
    }
}
