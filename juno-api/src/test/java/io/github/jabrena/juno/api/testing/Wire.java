package io.github.jabrena.juno.api.testing;

import io.github.jabrena.juno.api.io.gpio.Gpio;
import io.github.jabrena.juno.api.time.Clock;

import java.util.ArrayList;
import java.util.List;

/**
 * A scripted single-wire signal for the test-only {@link Gpio} stand-in: a run of levels, one per simulated
 * microsecond, starting at the simulated time when {@link #install()} is called. Build it with {@link #hold}, then
 * install it; every later sample past the script keeps the last level.
 */
public final class Wire {
    private final List<Boolean> levels = new ArrayList<>();

    /** Clears the simulated clock and pin state so a test starts from time zero. */
    public static void resetBoard() {
        Clock.reset();
        Gpio.reset();
    }

    /** Appends {@code micros} microseconds of the given level. */
    public Wire hold(boolean high, int micros) {
        for (int index = 0; index < micros; index++) {
            levels.add(high);
        }
        return this;
    }

    /**
     * The one-wire reply of a DHT-family sensor to a start pulse: the sensor's 80 us low and 80 us high
     * acknowledgement, then 40 bits, each a 50 us low followed by a high of 26 us for a zero or 70 us for a one, MSB
     * first. The host's 20 ms start pulse and 40 us release come first and are never sampled, so they are only
     * padding here.
     */
    public static Wire dhtReply(int... bytes) {
        Wire wire = new Wire().hold(true, 20_040).hold(false, 80).hold(true, 80);
        for (int bit = 0; bit < bytes.length * 8; bit++) {
            boolean one = ((bytes[bit / 8] >> (7 - (bit % 8))) & 1) != 0;
            wire.hold(false, 50).hold(true, one ? 70 : 26);
        }
        return wire.hold(false, 50);
    }

    /** Makes the scripted levels the pin's waveform, sampled from the current simulated time. */
    public void install() {
        boolean[] samples = new boolean[levels.size()];
        for (int index = 0; index < samples.length; index++) {
            samples[index] = levels.get(index);
        }
        int start = Clock.now;
        boolean last = samples[samples.length - 1];
        Gpio.highAt = time -> {
            int index = time - start;
            return index >= 0 && index < samples.length ? samples[index] : last;
        };
    }
}
