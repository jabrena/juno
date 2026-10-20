package io.github.jabrena.juno.api.testing;

import io.github.jabrena.juno.api.hardware.gpio.Gpio;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * A scripted single-wire signal for a mocked {@link Gpio}: a run of levels, one per simulated microsecond,
 * starting at the {@link SimulatedTime} when {@link #install} is called. Build it with {@link #hold}, then install it;
 * every later sample past the script keeps the last level.
 */
public final class Wire {
    private final List<Boolean> levels = new ArrayList<>();

    /** Starts the simulated board at time zero, so a test's waveform and timeouts begin from a known clock. */
    public static void resetBoard() {
        SimulatedTime.reset();
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

    /**
     * Makes the scripted levels what {@code port} reads on every pin, sampled from the current simulated time; each
     * read takes one simulated microsecond, so code that times edges sees the clock move.
     */
    public void install(Gpio port) {
        boolean[] samples = new boolean[levels.size()];
        for (int index = 0; index < samples.length; index++) {
            samples[index] = levels.get(index);
        }
        int start = SimulatedTime.now;
        boolean last = samples[samples.length - 1];
        when(port.digitalRead(anyInt())).thenAnswer(invocation -> {
            int index = SimulatedTime.now - start;
            SimulatedTime.now++;
            return index >= 0 && index < samples.length ? samples[index] : last;
        });
    }
}
