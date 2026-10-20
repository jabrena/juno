package io.github.jabrena.juno.api.testing;

import io.github.jabrena.juno.api.hardware.time.Clock;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Simulated board time in microseconds. The test stand-ins for {@code Delay} and {@code Infrared} advance {@link #now},
 * as does a {@link Wire} on every pin sample, and {@link #clock()} reads it through a mocked {@link Clock}, so
 * protocol code that times edges sees a believable clock without the board.
 */
public final class SimulatedTime {
    /** Microseconds since the simulated board started. */
    public static int now;

    private SimulatedTime() {
    }

    /** Starts the simulated board at time zero. */
    public static void reset() {
        now = 0;
    }

    /** A {@link Clock} whose readings follow {@link #now}. */
    public static Clock clock() {
        Clock port = mock(Clock.class);
        when(port.micros()).thenAnswer(invocation -> now);
        when(port.millis()).thenAnswer(invocation -> now / 1000);
        return port;
    }
}
