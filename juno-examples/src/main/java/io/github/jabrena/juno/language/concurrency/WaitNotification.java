package io.github.jabrena.juno.language.concurrency;

/**
 * JLS 17.2 source example for the JVM. Juno does not lower {@link Object#wait()}, {@link Object#notify()} or
 * {@link Object#notifyAll()}, so this deliberately has no board entry point.
 */
public final class WaitNotification {
    private boolean ready;
    private int value;

    /** Waits in a loop because the JVM permits spurious wake-ups. */
    public synchronized int await() throws InterruptedException {
        while (!ready) {
            wait();
        }
        return value;
    }

    /** Changes the guarded state before waking every waiter. */
    public synchronized void publish(int newValue) {
        value = newValue;
        ready = true;
        notifyAll();
    }
}
