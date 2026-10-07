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
public class StructuredConcurrency {
    private static final int LIMIT = 300;
    private static final int SLICES = 3;

    public static void main(String[] args) throws InterruptedException {
        Serial.begin(BaudRate.BAUD_115200);
        Serial.println("Counting primes below " + LIMIT);

        // Subtasks cannot return an int, so each one stores its partial result in its own slot.
        final int[] counts = new int[SLICES];
        int slice = LIMIT / SLICES;

        try (var scope = StructuredTaskScope.open()) {
            scope.fork(() -> countPrimes(counts, 0, 2, slice));
            scope.fork(() -> countPrimes(counts, 1, slice, 2 * slice));
            scope.fork(() -> countPrimes(counts, 2, 2 * slice, LIMIT));
            scope.join();
        }

        int total = 0;
        for (int index = 0; index < SLICES; index++) {
            Serial.println("slice " + index + ": " + counts[index]);
            total += counts[index];
        }
        Serial.println("primes below " + LIMIT + ": " + total);
    }

    /** Counts the primes in {@code [from, to)} by trial division and stores the result in {@code counts[slot]}. */
    private static void countPrimes(int[] counts, int slot, int from, int to) {
        int found = 0;
        for (int candidate = from; candidate < to; candidate++) {
            if (isPrime(candidate)) {
                found++;
            }
        }
        counts[slot] = found;
    }

    private static boolean isPrime(int value) {
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
