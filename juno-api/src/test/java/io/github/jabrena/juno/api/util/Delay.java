package io.github.jabrena.juno.api.util;

import io.github.jabrena.juno.api.time.Clock;

/**
 * Test-only stand-in for the delay intrinsics. A delay moves the simulated {@link Clock} forward instead of
 * waiting; the {@link #seconds(int)} wrapper is the production code, unchanged.
 */
public final class Delay {
    private Delay() {
    }

    public static void millis(int milliseconds) {
        Clock.now += milliseconds * 1000;
    }

    public static void micros(int microseconds) {
        Clock.now += microseconds;
    }

    /**
     * Delays {@code seconds} seconds. Not itself a compiler intrinsic — a plain wrapper around
     * {@link #millis}.
     */
    public static void seconds(int seconds) {
        millis(seconds * 1000);
    }
}
