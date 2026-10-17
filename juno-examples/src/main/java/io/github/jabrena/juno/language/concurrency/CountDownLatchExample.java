package io.github.jabrena.juno.language.concurrency;

import java.util.concurrent.CountDownLatch;

/**
 * JVM-only one-shot gate. Two workers publish a value and count down; the coordinator proceeds only after the count
 * reaches zero. A {@link CountDownLatch} cannot be reset, and Juno does not lower it.
 */
public final class CountDownLatchExample {
    private CountDownLatchExample() {}

    /** Returns the sum after both workers have completed. */
    public static int run() throws InterruptedException {
        CountDownLatch done = new CountDownLatch(2);
        int[] values = new int[2];
        Thread first = Thread.startVirtualThread(() -> publish(values, done, 0, 19));
        Thread second = Thread.startVirtualThread(() -> publish(values, done, 1, 23));
        done.await();
        first.join();
        second.join();
        return values[0] + values[1];
    }

    private static void publish(int[] values, CountDownLatch done, int index, int value) {
        values[index] = value;
        done.countDown();
    }
}
