package io.github.jabrena.juno.language;

import io.github.jabrena.juno.annotations.ArduinoOneQBoard;
import io.github.jabrena.juno.annotations.ArduinoOneR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;

/**
 * Structured concurrency: a {@link StructuredTaskScope} owns three subtasks that each count the primes in a slice of
 * 2..299, and the owner reads the partial counts only after the scope has joined them.
 *
 * <p>The scope is opened in a try-with-resources block, so no subtask outlives it: {@code join} waits for every fork,
 * and leaving the block closes the scope. Each subtask writes only its own slot of {@code counts}, so the owner needs
 * no locks to read them.
 */
@Board({ArduinoOneR4WiFiBoard.class, ArduinoOneQBoard.class})
public final class StructuredConcurrency {
    private static final int LIMIT = 300;
    private static final int SLICES = 3;

    // A subtask cannot return an int, so each one stores its partial result in its own slot.
    private final int[] counts = new int[SLICES];

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        Serial.begin(BaudRate.BAUD_115200);
        Serial.println("Counting primes below " + LIMIT);

        StructuredConcurrency example = new StructuredConcurrency();
        example.countInScope();
        example.report();
    }

    /** Forks one subtask per slice inside a scope, and leaves the scope only once every subtask has finished. */
    private void countInScope() throws InterruptedException, ExecutionException {
        int slice = LIMIT / SLICES;
        try (var scope = StructuredTaskScope.open()) {
            scope.fork(() -> countPrimes(0, 2, slice));
            scope.fork(() -> countPrimes(1, slice, 2 * slice));
            scope.fork(() -> countPrimes(2, 2 * slice, LIMIT));
            scope.join();
        }
        Serial.println("scope closed");
    }

    /** The owner adds up the partial counts once the scope has been joined. */
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
