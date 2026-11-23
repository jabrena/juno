package io.github.jabrena.juno.language.concurrency;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

/** A reusable barrier with a barrier action, run on the JVM by {@code Jls17ExamplesTest} and on the board alike. */
public final class CyclicBarrierExample {
    private static final int ROUNDS = 2;

    private CyclicBarrierExample() {}

    /** Returns the number of barrier actions run by two participants over two rounds. */
    public static int run() throws InterruptedException, BrokenBarrierException {
        AtomicInteger actions = new AtomicInteger();
        // A lambda, not actions::incrementAndGet: the action is a Runnable, and Juno needs its implementation to return
        // nothing.
        CyclicBarrier barrier = new CyclicBarrier(2, () -> actions.incrementAndGet());
        Thread first = Thread.startVirtualThread(() -> cross(barrier));
        Thread second = Thread.startVirtualThread(() -> cross(barrier));
        first.join();
        second.join();
        return actions.get();
    }

    private static void cross(CyclicBarrier barrier) {
        try {
            for (int round = 0; round < ROUNDS; round++) {
                barrier.await();
            }
        } catch (InterruptedException interrupted) {
            // Nothing interrupts the participants; one that were interrupted would stop crossing.
            return;
        } catch (BrokenBarrierException broken) {
            throw new IllegalStateException("barrier broken");
        }
    }
}
