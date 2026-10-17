package io.github.jabrena.juno.language.concurrency;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/** JVM-only producer/consumer example using a blocking queue; Juno does not lower {@link BlockingQueue}. */
public final class BlockingQueueExample {
    private static final int ITEMS = 4;

    private BlockingQueueExample() {}

    /** Returns the sum of all values transferred through the queue. */
    public static int run() throws InterruptedException {
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(2);
        int[] sum = new int[1];
        Thread consumer = Thread.startVirtualThread(() -> consume(queue, sum));
        Thread producer = Thread.startVirtualThread(() -> produce(queue));
        producer.join();
        consumer.join();
        return sum[0];
    }

    private static void produce(BlockingQueue<Integer> queue) {
        try {
            for (int item = 1; item <= ITEMS; item++) {
                queue.put(item);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void consume(BlockingQueue<Integer> queue, int[] sum) {
        try {
            for (int item = 0; item < ITEMS; item++) {
                sum[0] += queue.take();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
