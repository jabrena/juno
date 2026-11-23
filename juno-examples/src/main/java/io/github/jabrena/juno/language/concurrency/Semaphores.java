package io.github.jabrena.juno.language.concurrency;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A counting semaphore. A semaphore limits permits; unlike a monitor or a lock, it can allow several workers into the
 * protected region at once. Runs on the JVM ({@code Jls17ExamplesTest}) and on the board alike.
 */
public final class Semaphores {
    private static final int PERMITS = 2;
    private static final int WORKERS = 4;

    private Semaphores() {}

    /** Runs four workers and returns the number that completed while holding one of two permits. */
    public static int run() throws InterruptedException {
        Semaphore semaphore = new Semaphore(PERMITS);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();
        Thread[] workers = new Thread[WORKERS];
        for (int index = 0; index < WORKERS; index++) {
            workers[index] = Thread.startVirtualThread(() -> work(semaphore, active, maximum, completed));
        }
        for (Thread worker : workers) {
            worker.join();
        }
        if (maximum.get() > PERMITS) {
            throw new AssertionError("semaphore permits exceeded");
        }
        return completed.get();
    }

    private static void work(
            Semaphore semaphore, AtomicInteger active, AtomicInteger maximum, AtomicInteger completed)
            {
        try {
            semaphore.acquire();
        } catch (InterruptedException interrupted) {
            // Nothing interrupts the workers; one that were interrupted would leave without a permit.
            return;
        }
        try {
            int now = active.incrementAndGet();
            int seen = maximum.get();
            while (now > seen && !maximum.compareAndSet(seen, now)) {
                seen = maximum.get();
            }
            Thread.yield();
            active.decrementAndGet();
            completed.incrementAndGet();
        } finally {
            semaphore.release();
        }
    }
}
