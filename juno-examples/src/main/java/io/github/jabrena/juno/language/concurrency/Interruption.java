package io.github.jabrena.juno.language.concurrency;

/**
 * JLS 17.2.3 and 17.2.4 source example for the JVM. Juno does not lower interruption APIs, so this deliberately has
 * no board entry point.
 */
public final class Interruption {
    private Interruption() {
    }

    /** Restores the status when this method cannot propagate {@link InterruptedException}. */
    public static boolean sleepAndRestoreStatus() {
        try {
            Thread.sleep(1_000);
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return true;
        }
    }
}
