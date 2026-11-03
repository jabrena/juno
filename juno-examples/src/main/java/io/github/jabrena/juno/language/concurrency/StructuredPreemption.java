package io.github.jabrena.juno.language.concurrency;

import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;

import java.util.concurrent.Callable;
import java.util.concurrent.StructuredTaskScope;

/**
 * Structured subtasks run on the same preemptive scheduler as virtual threads: a {@link StructuredTaskScope} only adds
 * the lifecycle (fork, join, cancel), not a scheduling policy of its own.
 *
 * <p>The first round forks a subtask that spins on a counter and a sibling that runs a deep recursion with no loop
 * anywhere, which only returns early once the counter has moved. A subtask is switched out when its 1 ms slice is
 * over, even in the middle of that recursion, so the spinner keeps counting and the board prints
 * {@code recursion: shared}; a scheduler that only switched subtasks at loops would print {@code recursion: starved}.
 * The second round lets a failing subtask cancel a sibling that is busy spinning, and prints {@code fail-fast: ok}.
 */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class})
public final class StructuredPreemption {
    private static final int DEPTH = 24;
    private static final int ROUNDS = 5;

    private static volatile boolean stop;
    private static volatile int progress;
    private static volatile int found;
    private static int snapshot;

    public static void main(String[] args) throws Exception {
        Serial.begin(BaudRate.BAUD_115200);
        // The rounds repeat so a serial monitor opened after the upload still sees the results.
        for (int round = 1; round <= ROUNDS; round++) {
            Thread.sleep(3000);
            Serial.println("Structured preemption round " + round);
            Serial.println(siblingRunsDuringRecursion() ? "recursion: shared" : "recursion: starved");
            Serial.println(failureCancelsBusySibling() ? "fail-fast: ok" : "fail-fast: missed");
        }
    }

    /** A subtask recurses without a loop while its sibling spins: only preemption lets the sibling run. */
    private static boolean siblingRunsDuringRecursion() throws Exception {
        stop = false;
        progress = 0;
        found = 0;
        try (var scope = StructuredTaskScope.open()) {
            scope.fork(StructuredPreemption::spin);
            scope.fork(() -> {
                while (progress == 0) {
                    // Wait until the sibling is demonstrably running.
                }
                snapshot = progress;
                found = search(DEPTH);
                stop = true;
            });
            scope.join();
        }
        return found == 1;
    }

    /** A failing subtask makes the join throw and cancels a sibling that is still spinning. */
    private static boolean failureCancelsBusySibling() throws InterruptedException {
        stop = false;
        progress = 0;
        boolean failed = false;
        try (var scope = StructuredTaskScope.open()) {
            scope.fork(StructuredPreemption::spin);
            Callable<String> failing = () -> {
                while (progress == 0) {
                    // Wait until the sibling is demonstrably running.
                }
                throw new IllegalStateException("sensor offline");
            };
            scope.fork(failing);
            try {
                scope.join();
            } catch (Exception failure) {
                failed = true;
            }
            // The board has already cancelled the spinner; on the JVM, cancellation only interrupts it.
            stop = true;
        }
        return failed;
    }

    private static void spin() {
        while (!stop) {
            progress++;
        }
    }

    /** No loop anywhere: 1 as soon as the counter has moved past the snapshot, 0 if it never did. */
    private static int search(int depth) {
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
}
