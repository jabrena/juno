package io.github.jabrena.juno.language;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;

/**
 * Virtual threads lowered onto Juno's preemptive task runtime: three virtual threads each count the primes in a
 * slice of 2..299, the owner joins all of them, and then adds up the partial counts.
 *
 * <p>Virtual threads are preemptive on the board: each runs for 1 ms time slices and can be switched out in the middle
 * of a statement, exactly as on the JVM, so each thread writes only its own slot of {@code counts} and the owner reads
 * them after {@code join}. At most four threads, the owner included, run at once.
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class VirtualThreads {
    private static final int LIMIT = 300;
    private static final int SLICES = 3;

    // Threads cannot return an int, so each one stores its partial result in its own slot.
    private final int[] counts = new int[SLICES];

    public static void main(String[] args) throws InterruptedException {
        Serial.begin(BaudRate.BAUD_115200);
        Serial.println("Counting primes below " + LIMIT);

        VirtualThreads example = new VirtualThreads();
        example.countInParallel();
        example.report();
    }

    /** Starts one virtual thread per slice of the range and waits for all of them. */
    private void countInParallel() throws InterruptedException {
        int slice = LIMIT / SLICES;

        Thread first = Thread.startVirtualThread(() -> countPrimes(0, 2, slice));
        Thread second = Thread.startVirtualThread(() -> countPrimes(1, slice, 2 * slice));
        Thread third = Thread.startVirtualThread(() -> countPrimes(2, 2 * slice, LIMIT));
        first.join();
        second.join();
        third.join();
    }

    /** The owner adds up the partial counts once every virtual thread has finished. */
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
