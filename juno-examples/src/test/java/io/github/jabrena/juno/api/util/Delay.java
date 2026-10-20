package io.github.jabrena.juno.api.util;

import io.github.jabrena.juno.api.hardware.time.SimulatedClock;

/**
 * Test double shadowing the {@code juno} artifact's native {@code Delay}: waiting advances
 * {@link SimulatedClock}'s simulated time instantly and then runs {@link #afterAdvance}, which the TFT
 * emulator uses to script touches and to stop a program at a chosen moment.
 *
 * <p>Simulated time belongs to the program's main thread. On the board, virtual threads take turns on one core and share
 * one timeline that the owner's waits drive, but on the JVM they run truly in parallel; letting them advance the clock
 * too would make runs unrepeatable and starve the owner of simulated time. A virtual thread's wait therefore sleeps
 * a real millisecond and leaves simulated time alone.
 */
public final class Delay {
    /** Runs every time simulated time advances. */
    public static Runnable afterAdvance = () -> { };

    private static int pendingMicros;

    private Delay() {
    }

    public static void millis(int milliseconds) {
        if (Thread.currentThread().isVirtual()) {
            pauseVirtualThread();
            return;
        }
        SimulatedClock.advance(milliseconds);
        afterAdvance.run();
    }

    private static void pauseVirtualThread() {
        try {
            Thread.sleep(1);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    public static void micros(int microseconds) {
        pendingMicros = pendingMicros + microseconds;
        if (pendingMicros >= 1000) {
            SimulatedClock.advance(pendingMicros / 1000);
            pendingMicros = pendingMicros % 1000;
            afterAdvance.run();
        }
    }

    public static void seconds(int seconds) {
        millis(seconds * 1000);
    }
}
