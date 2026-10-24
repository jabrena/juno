package io.github.jabrena.juno.testing;

import java.util.function.Supplier;

/** Runs code with its JVM sleeps interrupted, restoring the test thread's previous interrupt state afterward. */
public final class ImmediateDelay {
    private ImmediateDelay() {
    }

    /** Runs {@code action} without waiting for board-initialization delays. */
    public static void run(Runnable action) {
        boolean wasInterrupted = Thread.interrupted();
        Thread.currentThread().interrupt();
        try {
            action.run();
        } finally {
            restore(wasInterrupted);
        }
    }

    /** Gets the result of {@code action} without waiting for board-initialization delays. */
    public static <T> T get(Supplier<T> action) {
        boolean wasInterrupted = Thread.interrupted();
        Thread.currentThread().interrupt();
        try {
            return action.get();
        } finally {
            restore(wasInterrupted);
        }
    }

    private static void restore(boolean wasInterrupted) {
        Thread.interrupted();
        if (wasInterrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
