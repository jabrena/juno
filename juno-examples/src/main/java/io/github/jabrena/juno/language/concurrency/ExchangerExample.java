package io.github.jabrena.juno.language.concurrency;

import java.util.concurrent.Exchanger;

/** JVM-only rendezvous that swaps one value between exactly two participants; Juno does not lower {@link Exchanger}. */
public final class ExchangerExample {
    private ExchangerExample() {}

    /** Returns the two values observed after exchanging {@code left} and {@code right}. */
    public static String run() throws InterruptedException {
        Exchanger<String> exchanger = new Exchanger<>();
        String[] observed = new String[2];
        Thread left = Thread.startVirtualThread(() -> exchange(exchanger, observed, 0, "left"));
        Thread right = Thread.startVirtualThread(() -> exchange(exchanger, observed, 1, "right"));
        left.join();
        right.join();
        return observed[0] + ":" + observed[1];
    }

    private static void exchange(Exchanger<String> exchanger, String[] observed, int index, String value) {
        try {
            observed[index] = exchanger.exchange(value);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
