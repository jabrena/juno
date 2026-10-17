package io.github.jabrena.juno.language.concurrency;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/** JVM-only read/write locking example; Juno lowers {@code ReentrantLock}, not {@link ReentrantReadWriteLock}. */
public final class ReadWriteLockExample {
    private ReadWriteLockExample() {}

    /** Returns two consistent reads after one write. */
    public static int run() throws InterruptedException {
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        int[] value = new int[1];
        Thread writer = Thread.startVirtualThread(() -> {
            lock.writeLock().lock();
            try {
                value[0] = 42;
            } finally {
                lock.writeLock().unlock();
            }
        });
        writer.join();

        int[] observations = new int[2];
        Thread first = Thread.startVirtualThread(() -> read(lock, value, observations, 0));
        Thread second = Thread.startVirtualThread(() -> read(lock, value, observations, 1));
        first.join();
        second.join();
        return observations[0] + observations[1];
    }

    private static void read(ReentrantReadWriteLock lock, int[] value, int[] observations, int index) {
        lock.readLock().lock();
        try {
            observations[index] = value[0];
        } finally {
            lock.readLock().unlock();
        }
    }
}
