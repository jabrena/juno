package io.github.jabrena.juno.util;

import io.github.jabrena.juno.testing.SimulatedTime;

/**
 * Test-only stand-in for the delay intrinsics. A delay moves the simulated {@link SimulatedTime} clock forward instead of
 * waiting; the {@link #seconds(int)} wrapper is the production code, unchanged.
 */
public final class Delay {
    private Delay() {
    }

    public static void millis(int milliseconds) {
        SimulatedTime.now += milliseconds * 1000;
    }

    public static void micros(int microseconds) {
        SimulatedTime.now += microseconds;
    }

    /**
     * Delays {@code seconds} seconds. Not itself a compiler intrinsic — a plain wrapper around
     * {@link #millis}.
     */
    public static void seconds(int seconds) {
        millis(seconds * 1000);
    }
}
