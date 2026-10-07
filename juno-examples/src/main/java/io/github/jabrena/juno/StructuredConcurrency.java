package io.github.jabrena.juno;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.usb.BaudRate;
import io.github.jabrena.juno.api.io.usb.Serial;
import java.util.concurrent.StructuredTaskScope;

/**
 * JDK 25 preview structured concurrency lowered onto Juno's cooperative task runtime: three subtasks each count
 * the primes in a slice of 2..299, the scope waits for all of them, and the owner adds up the partial counts.
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class StructuredConcurrency {
    private static final int LIMIT = 300;
    private static final int SLICES = 3;

    // Subtasks cannot return an int, so each one stores its partial result in its own slot.
    private final int[] counts = new int[SLICES];

    public static void main(String[] args) throws InterruptedException {
        Serial.begin(BaudRate.BAUD_115200);
        Serial.println("Counting primes below " + LIMIT);

        StructuredConcurrency example = new StructuredConcurrency();
        example.countInParallel();
        example.report();
    }

    /** Forks one subtask per slice of the range and waits for all of them. */
    private void countInParallel() throws InterruptedException {
        int slice = LIMIT / SLICES;

        try (var scope = StructuredTaskScope.open()) {
            scope.fork(() -> countPrimes(0, 2, slice));
            scope.fork(() -> countPrimes(1, slice, 2 * slice));
            scope.fork(() -> countPrimes(2, 2 * slice, LIMIT));
            scope.join();
        }
    }

    /** The owner adds up the partial counts once every subtask has finished. */
    private void report() {
        int total = 0;
        for (int index = 0; index < SLICES; index++) {
            Serial.println("slice " + index + ": " + counts[index]);
            total += counts[index];
        }
        Serial.println("primes below " + LIMIT + ": " + total);
    }

    /** Counts the primes in {@code [from, to)} by trial division and stores the result in {@code counts[slot]}. */
    private void countPrimes(int slot, int from, int to) {
        int found = 0;
        for (int candidate = from; candidate < to; candidate++) {
            if (isPrime(candidate)) {
                found++;
            }
        }
        counts[slot] = found;
    }

    private boolean isPrime(int value) {
        if (value < 2) {
            return false;
        }
        for (int divisor = 2; divisor * divisor <= value; divisor++) {
            if (value % divisor == 0) {
                return false;
            }
        }
        return true;
    }
}
