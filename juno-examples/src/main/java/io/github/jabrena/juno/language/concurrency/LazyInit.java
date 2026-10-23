package io.github.jabrena.juno.language.concurrency;

import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;

/**
 * Lazy initialization with double-checked locking. Three virtual threads ask for the shared settings at once. The field is
 * {@code volatile}, so the fast path reads a fully constructed object, and the second check under the monitor makes
 * sure only one thread constructs it.
 *
 * <p>Without {@code volatile}, another thread can observe the reference before the constructor's writes are visible,
 * which is the failure this example guards against. Juno's backend keeps that rule, so the example does not depend
 * on the compiler's reordering. The owner checks that all three threads received the same instance and
 * that it was constructed exactly once.
 */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class})
public final class LazyInit {
    private static final int THRESHOLD = 42;
    private static final int READS = 100;

    private volatile Settings settings;

    // Each thread writes only its own slot, and the owner reads them after join.
    private final Settings[] seen = new Settings[3];

    public static void main(String[] args) throws InterruptedException {
        Serial.begin(BaudRate.BAUD_115200);

        LazyInit example = new LazyInit();
        example.readConcurrently();
        example.report();
    }

    private void readConcurrently() throws InterruptedException {
        Thread first = Thread.startVirtualThread(() -> read(0));
        Thread second = Thread.startVirtualThread(() -> read(1));
        Thread third = Thread.startVirtualThread(() -> read(2));
        first.join();
        second.join();
        third.join();
    }

    private void read(int slot) {
        for (int i = 0; i < READS; i++) {
            seen[slot] = settings();
        }
    }

    /** Double-checked locking: the volatile read is the fast path, and the monitor is taken only to construct. */
    private Settings settings() {
        Settings local = settings;
        if (local == null) {
            synchronized (this) {
                local = settings;
                if (local == null) {
                    local = new Settings(THRESHOLD);
                    settings = local;
                }
            }
        }
        return local;
    }

    private void report() {
        boolean shared = seen[0] == seen[1] && seen[1] == seen[2];
        Serial.println("constructions " + Settings.constructions);
        Serial.println("shared instance " + shared);
        Serial.println("threshold " + seen[0].threshold);
    }

    /** The shared configuration; its constructor counts how many times it runs. */
    static final class Settings {
        static int constructions;

        final int threshold;

        Settings(int threshold) {
            this.threshold = threshold;
            constructions++;
        }
    }
}
