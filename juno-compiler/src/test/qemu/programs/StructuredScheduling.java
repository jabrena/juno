package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.hardware.serial.Serial;
import java.util.concurrent.Callable;
import java.util.concurrent.StructuredTaskScope;

/**
 * How structured subtasks share the CPU: a subtask is cooperative (it gives the CPU up at its loop backedges once its
 * time slice is over, and at sleep, yield, join and contended monitors), while the owner, like any virtual thread,
 * is preemptive. So a spinning subtask still makes progress while the owner runs a loop-free recursion, two computing
 * subtasks interleave through their loops, and a fail-fast scope cancels a sibling that is spinning.
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class, ArduinoUnoR3WiFiRev2.class})
public final class StructuredScheduling {
    static volatile boolean stop;
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
            scope.fork(StructuredScheduling::spin);
            while (progress == 0) {
                // Wait until the subtask is demonstrably running.
            }
            snapshot = progress;
            Serial.println(search(24) == 1 ? "subtask progressed during owner recursion" : "subtask starved");
            stop = true;
            scope.join();
        }

        // Two computing subtasks, each writing its own slot, taking turns at their loop backedges.
        final int[] sums = new int[2];
        try (var scope = StructuredTaskScope.open()) {
            scope.fork(() -> { sums[0] = sumOfRemainders(7); });
            scope.fork(() -> { sums[1] = sumOfRemainders(11); });
            scope.join();
        }
        Serial.println(sums[0] + sums[1]);

        // Fail-fast: a failing subtask cancels a sibling that is spinning.
        stop = false;
        progress = 0;
        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow())) {
            scope.fork(StructuredScheduling::spin);
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
