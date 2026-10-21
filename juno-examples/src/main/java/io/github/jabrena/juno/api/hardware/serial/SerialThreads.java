package io.github.jabrena.juno.api.hardware.serial;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Exercises the Arduino Nano ESP32's thread runtime once at startup and then reports the results over the native USB
 * serial port at 115200 baud every two seconds, so a serial monitor attached late still sees them: three virtual
 * threads counting primes, a spin loop on {@code main} that only ends if a busy thread is preempted, two threads
 * incrementing one counter under {@code synchronized} and two under a {@link ReentrantLock}, a
 * {@link StructuredTaskScope} with three subtasks, and a {@link ScopedValue} binding.
 *
 * <p>All the concurrent work, and every allocation it makes, happens before the reporting loop, which allocates
 * nothing. The board's USB port re-enumerates when the program starts, so it waits two seconds before starting.
 */
@Board(ArduinoNanoEsp32.class)
public final class SerialThreads {
    private static final int LIMIT = 300;
    private static final int INCREMENTS = 2000;
    private static final ScopedValue<int[]> FACTOR = ScopedValue.newInstance();

    private final int[] threadCounts = new int[3];
    private final int[] taskCounts = new int[3];
    private final ReentrantLock lock = new ReentrantLock();
    private volatile boolean released;
    private int spins;
    private int synchronizedCounter;
    private int lockedCounter;
    private int scoped;

    /**
     * Opens the serial port, runs every concurrency check once, and prints their results indefinitely.
     *
     * @param args ignored; Juno programs do not receive command-line arguments
     * @throws InterruptedException never on the board; {@code join} declares it
     * @throws ExecutionException never here; a failing subtask would make the scope's {@code join} throw it
     */
    public static void main(String[] args) throws InterruptedException, ExecutionException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(10000);
        Serial.println("step start");
        Delay.millis(200);

        SerialThreads example = new SerialThreads();
        Thread probe = Thread.startVirtualThread(() -> Serial.println("step probe"));
        Serial.println("step started");
        Delay.millis(200);
        probe.join();
        Serial.println("step joined");
        Delay.millis(200);
        example.virtualThreads();
        Serial.println("step vt");
        Delay.millis(200);
        example.preemption();
        Serial.println("step pre");
        Delay.millis(200);
        example.monitors();
        Serial.println("step mon");
        Delay.millis(200);
        example.structuredTasks();
        Serial.println("step sts");
        Delay.millis(200);
        ScopedValue.where(FACTOR, new int[] {7}).run(() -> example.scoped = FACTOR.get()[0] * 6);

        int round = 0;
        while (true) {
            Serial.print("round ");
            Serial.println(round);
            Serial.print("threads ");
            Serial.println(threadCounts(example.threadCounts));
            Serial.print("preempted ");
            Serial.println(example.spins > 0 ? 1 : 0);
            Serial.print("synchronized ");
            Serial.println(example.synchronizedCounter);
            Serial.print("locked ");
            Serial.println(example.lockedCounter);
            Serial.print("tasks ");
            Serial.println(threadCounts(example.taskCounts));
            Serial.print("scoped ");
            Serial.println(example.scoped);
            Delay.millis(2000);
            round = round + 1;
        }
    }

    private static int threadCounts(int[] counts) {
        return counts[0] + counts[1] + counts[2];
    }

    private void virtualThreads() throws InterruptedException {
        int slice = LIMIT / 3;
        Thread first = Thread.startVirtualThread(() -> countPrimes(threadCounts, 0, 2, slice));
        Thread second = Thread.startVirtualThread(() -> countPrimes(threadCounts, 1, slice, 2 * slice));
        Thread third = Thread.startVirtualThread(() -> countPrimes(threadCounts, 2, 2 * slice, LIMIT));
        first.join();
        second.join();
        third.join();
    }

    /** main never sleeps or yields here, so the flag is set only if its time slice runs out and it is preempted. */
    private void preemption() throws InterruptedException {
        Thread releaser = Thread.startVirtualThread(() -> released = true);
        while (!released) {
            spins = spins + 1;
        }
        releaser.join();
    }

    private void monitors() throws InterruptedException {
        Thread first = Thread.startVirtualThread(this::incrementSynchronized);
        Thread second = Thread.startVirtualThread(this::incrementSynchronized);
        Thread third = Thread.startVirtualThread(this::incrementLocked);
        Thread fourth = Thread.startVirtualThread(this::incrementLocked);
        first.join();
        second.join();
        third.join();
        fourth.join();
    }

    private void incrementSynchronized() {
        for (int index = 0; index < INCREMENTS; index++) {
            synchronized (this) {
                synchronizedCounter = synchronizedCounter + 1;
            }
        }
    }

    private void incrementLocked() {
        for (int index = 0; index < INCREMENTS; index++) {
            lock.lock();
            try {
                lockedCounter = lockedCounter + 1;
            } finally {
                lock.unlock();
            }
        }
    }

    private void structuredTasks() throws InterruptedException, ExecutionException {
        int slice = LIMIT / 3;
        try (var scope = StructuredTaskScope.open()) {
            scope.fork(() -> countPrimes(taskCounts, 0, 2, slice));
            scope.fork(() -> countPrimes(taskCounts, 1, slice, 2 * slice));
            scope.fork(() -> countPrimes(taskCounts, 2, 2 * slice, LIMIT));
            scope.join();
        }
    }

    /** Counts the primes in {@code [from, to)} by trial division into {@code counts[slot]}. */
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
