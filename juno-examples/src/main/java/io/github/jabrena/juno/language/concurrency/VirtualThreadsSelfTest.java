package io.github.jabrena.juno.language.concurrency;

import io.github.jabrena.juno.annotations.AiThinkerEsp32CamBoard;
import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.ArduinoPortentaH7Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.M5StackCardputerBoard;
import io.github.jabrena.juno.annotations.RaspberryPico2WifiBoard;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;

/**
 * Checks virtual threads and {@link StructuredTaskScope} subtasks on their own, with no device or network code, so a
 * failure points at the scheduler rather than at a library: a value handed back through {@code join}, locals that
 * survive a thread being parked and resumed, two subtasks joined by their scope, a deep call chain parked at its
 * bottom, and more threads alive at once than the scheduler's initial slots. On a board whose threads are
 * continuations on a shared carrier stack (UNO R4 WiFi, Pico 2 W on one carrier), every park copies the thread's frames
 * out and back, so those checks exercise exactly that.
 *
 * <p>Every check prints PASS or FAIL with what it got and what it expected; each round ends with how many passed.
 * Rounds repeat after a pause long enough for a serial monitor started after the upload to attach.
 */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoNanoEsp32Board.class, ArduinoPortentaH7Board.class,
        RaspberryPico2WifiBoard.class, AiThinkerEsp32CamBoard.class, M5StackCardputerBoard.class})
public final class VirtualThreadsSelfTest {
    private static final int ROUND_PAUSE_MILLIS = 3_000;
    private static final int CHECKS = 5;
    private static final int PARKS = 5;
    private static final int DEPTH = 20;
    // An array length must be a compile-time constant on the board.
    private static final int MANY = 6;

    private final int[] subtaskResults = new int[2];
    private final int[] manyResults = new int[MANY];
    private final Thread[] manyThreads = new Thread[MANY];
    private int handedBack;
    private int parkedLocals;
    private int deepSum;
    private int passed;

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        Serial.begin(BaudRate.BAUD_115200);
        VirtualThreadsSelfTest test = new VirtualThreadsSelfTest();
        for (int round = 1; ; round++) {
            Thread.sleep(ROUND_PAUSE_MILLIS);
            Serial.println("Virtual threads self-test round " + round);
            test.run();
        }
    }

    private void run() throws InterruptedException, ExecutionException {
        passed = 0;
        report(1, "start and join", startAndJoin(), 42);
        report(2, "locals across parks", localsAcrossParks(), 7 * 11 * 13);
        report(3, "two scope subtasks", twoScopeSubtasks(), 3 + 4);
        report(4, "deep frames across a park", deepFramesAcrossAPark(), expectedDeepSum());
        report(5, "six threads at once", sixThreadsAtOnce(), expectedManySum());
        Serial.println("passed " + passed + "/" + CHECKS);
    }

    /** A thread's result is visible to the thread that joins it. */
    private int startAndJoin() throws InterruptedException {
        handedBack = 0;
        Thread worker = Thread.startVirtualThread(() -> handedBack = 21 * 2);
        worker.join();
        return handedBack;
    }

    /** Locals held across several parks come back unchanged when the thread resumes. */
    private int localsAcrossParks() throws InterruptedException {
        parkedLocals = 0;
        Thread worker = Thread.startVirtualThread(() -> {
            int first = 7;
            int second = 11;
            int third = 13;
            try {
                for (int park = 0; park < PARKS; park++) {
                    Thread.sleep(5);
                }
            } catch (InterruptedException interrupted) {
                first = -1;
            }
            parkedLocals = first * second * third;
        });
        worker.join();
        return parkedLocals;
    }

    /** Both subtasks of a scope have finished, and written their results, when the scope's join returns. */
    private int twoScopeSubtasks() throws InterruptedException, ExecutionException {
        subtaskResults[0] = 0;
        subtaskResults[1] = 0;
        try (var scope = StructuredTaskScope.open()) {
            scope.fork(() -> {
                subtaskResults[0] = sleepThen(10, 3);
            });
            scope.fork(() -> {
                subtaskResults[1] = sleepThen(5, 4);
            });
            scope.join();
        }
        return subtaskResults[0] + subtaskResults[1];
    }

    /** Every frame of a deep call chain keeps its value while the thread is parked at the bottom. */
    private int deepFramesAcrossAPark() throws InterruptedException {
        deepSum = 0;
        Thread worker = Thread.startVirtualThread(() -> deepSum = descend(DEPTH));
        worker.join();
        return deepSum;
    }

    /** More threads alive at once than the scheduler starts with, each finishing after the ones started after it. */
    private int sixThreadsAtOnce() throws InterruptedException {
        for (int index = 0; index < MANY; index++) {
            manyResults[index] = 0;
        }
        for (int index = 0; index < MANY; index++) {
            int slot = index;
            manyThreads[index] = Thread.startVirtualThread(
                    () -> manyResults[slot] = sleepThen(MANY - slot, slot * slot));
        }
        for (int index = 0; index < MANY; index++) {
            manyThreads[index].join();
        }
        int sum = 0;
        for (int index = 0; index < MANY; index++) {
            sum += manyResults[index];
        }
        return sum;
    }

    /** Sleeps {@code millis}, then answers {@code value}; -1 when interrupted. */
    private static int sleepThen(int millis, int value) {
        try {
            Thread.sleep(millis);
            return value;
        } catch (InterruptedException interrupted) {
            return -1;
        }
    }

    /** Each frame holds {@code depth * 3}; the bottom frame parks before the sum is returned up the chain. */
    private static int descend(int depth) {
        int mine = depth * 3;
        if (depth == 0) {
            return mine + sleepThen(5, 0);
        }
        return mine + descend(depth - 1);
    }

    private static int expectedDeepSum() {
        int sum = 0;
        for (int depth = 0; depth <= DEPTH; depth++) {
            sum += depth * 3;
        }
        return sum;
    }

    private static int expectedManySum() {
        int sum = 0;
        for (int index = 0; index < MANY; index++) {
            sum += index * index;
        }
        return sum;
    }

    private void report(int number, String name, int actual, int expected) {
        boolean ok = actual == expected;
        if (ok) {
            passed++;
        }
        Serial.println("[" + number + "] " + name + (ok ? " PASS" : " FAIL") + " (" + actual + ", expected " + expected
                + ")");
    }
}
