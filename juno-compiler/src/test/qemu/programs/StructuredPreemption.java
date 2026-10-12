package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.ArduinoUnoWiFiRev2;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.serial.Serial;
import java.util.concurrent.Callable;
import java.util.concurrent.StructuredTaskScope;

/**
 * Structured concurrency is preemptive too: forked subtasks run on the same scheduler as virtual threads. A spinning
 * subtask makes progress while the owner runs a loop-free recursion; two sibling subtasks preempt each other while
 * the owner waits in {@code join} (one recurses without looping, the other spins until the first is done); two
 * computing subtasks interleave; and a fail-fast scope cancels a sibling that
 * was preempted in the middle of its loop. Under a cooperative scheduler the recursions would starve the spinners.
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class, ArduinoUnoWiFiRev2.class})
public final class StructuredPreemption {
    static volatile boolean stop;
    static volatile boolean recursed;
    static volatile int progress;
    static int snapshot;

    /** No loop anywhere: 1 as soon as the counter moved past the snapshot, 0 if it never did. */
    static int search(int depth) {
        if (progress != snapshot) {
            return 1;
        }
        if (depth == 0) {
            return 0;
        }
        if (search(depth - 1) == 1) {
            return 1;
        }
        return search(depth - 1);
    }

    static int sumOfRemainders(int divisor) {
        int total = 0;
        for (int i = 1; i <= 20000; i++) {
            total += i % divisor;
        }
        return total;
    }

    static void spin() {
        while (!stop) {
            progress++;
        }
    }

    public static void main() throws Exception {
        // The owner recurses while a forked subtask spins.
        try (var scope = StructuredTaskScope.open()) {
            scope.fork(StructuredPreemption::spin);
            while (progress == 0) {
                // Wait until the subtask is demonstrably running.
            }
            snapshot = progress;
            Serial.println(search(24) == 1 ? "subtask progressed during owner recursion" : "subtask starved");
            stop = true;
            scope.join();
        }

        // Siblings preempt each other while the owner waits in join.
        stop = false;
        progress = 0;
        try (var scope = StructuredTaskScope.open()) {
            scope.fork(StructuredPreemption::spin);
            scope.fork(() -> {
                while (progress == 0) {
                    // Wait until the sibling is demonstrably running.
                }
                snapshot = progress;
                recursed = search(24) == 1;
                stop = true;
            });
            scope.join();
        }
        Serial.println(recursed ? "sibling progressed during sibling recursion" : "sibling starved");

        // Two computing subtasks, each writing its own slot, preempting each other while the owner joins.
        final int[] sums = new int[2];
        try (var scope = StructuredTaskScope.open()) {
            scope.fork(() -> { sums[0] = sumOfRemainders(7); });
            scope.fork(() -> { sums[1] = sumOfRemainders(11); });
            scope.join();
        }
        Serial.println(sums[0] + sums[1]);

        // Fail-fast: a failing subtask cancels a sibling that is spinning (and so preempted mid-loop).
        stop = false;
        progress = 0;
        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow())) {
            scope.fork(StructuredPreemption::spin);
            Callable<String> failing = () -> {
                while (progress == 0) {
                    // Wait until the sibling is demonstrably running.
                }
                stop = true;
                throw new IllegalStateException("fail fast");
            };
            scope.fork(failing);
            scope.join();
            Serial.println("no failure");
        } catch (java.util.concurrent.ExecutionException failure) {
            Serial.println("failed fast");
        }
    }
}
