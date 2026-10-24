package io.github.jabrena.juno.api.hardware.serial;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
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
 * <p>Each thread body is a small {@link Runnable} class rather than a lambda. All the concurrent work, and every
 * allocation it makes, happens before the reporting loop, which allocates nothing. The board's USB port
 * re-enumerates when the program starts, so it waits two seconds before starting.
 */
@Board(ArduinoNanoEsp32Board.class)
@SuppressWarnings("java:S2189")
public final class SerialThreads {
    private static final int LIMIT = 300;
    private static final int SLICE = LIMIT / 3;
    private static final int INCREMENTS = 2000;
    private static final ScopedValue<int[]> FACTOR = ScopedValue.newInstance();

    /** Counts the primes in {@code [from, to)} by trial division into {@code counts[slot]}. */
    static final class PrimeCounter implements Runnable {
        private final int[] counts;
        private final int slot;
        private final int from;
        private final int to;

        PrimeCounter(int[] counts, int slot, int from, int to) {
            this.counts = counts;
            this.slot = slot;
            this.from = from;
            this.to = to;
        }

        @Override
        public void run() {
            int found = 0;
            for (int candidate = from; candidate < to; candidate++) {
                if (isPrime(candidate)) {
                    found++;
                }
            }
            counts[slot] = found;
        }
    }

    /** Sets the flag {@code main} spins on. */
    static final class Releaser implements Runnable {
        private final SerialThreads owner;

        Releaser(SerialThreads owner) {
            this.owner = owner;
        }

        @Override
        public void run() {
            owner.released = true;
        }
    }

    /** Increments the shared counter under the owner's monitor. */
    static final class SynchronizedIncrementer implements Runnable {
        private final SerialThreads owner;

        SynchronizedIncrementer(SerialThreads owner) {
            this.owner = owner;
        }

        @Override
        public void run() {
            for (int index = 0; index < INCREMENTS; index++) {
                synchronized (owner) {
                    owner.synchronizedCounter = owner.synchronizedCounter + 1;
                }
            }
        }
    }

    /** Increments the shared counter under the owner's lock. */
    static final class LockedIncrementer implements Runnable {
        private final SerialThreads owner;

        LockedIncrementer(SerialThreads owner) {
            this.owner = owner;
        }

        @Override
        public void run() {
            for (int index = 0; index < INCREMENTS; index++) {
                owner.lock.lock();
                try {
                    owner.lockedCounter = owner.lockedCounter + 1;
                } finally {
                    owner.lock.unlock();
                }
            }
        }
    }

    /** Reads the bound {@link #FACTOR} inside its scope. */
    static final class ScopedReader implements Runnable {
        private final SerialThreads owner;

        ScopedReader(SerialThreads owner) {
            this.owner = owner;
        }

        @Override
        public void run() {
            owner.scoped = FACTOR.get()[0] * 6;
        }
    }

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
        Delay.millis(2000);

        SerialThreads example = new SerialThreads();
        example.virtualThreads();
        example.preemption();
        example.monitors();
        example.structuredTasks();
        ScopedValue.where(FACTOR, new int[] {7}).run(new ScopedReader(example));

        int round = 0;
        while (true) {
            Serial.print("round ");
            Serial.println(round);
            Serial.print("threads ");
            Serial.println(total(example.threadCounts));
            Serial.print("preempted ");
            Serial.println(example.spins > 0 ? 1 : 0);
            Serial.print("synchronized ");
            Serial.println(example.synchronizedCounter);
            Serial.print("locked ");
            Serial.println(example.lockedCounter);
            Serial.print("tasks ");
            Serial.println(total(example.taskCounts));
            Serial.print("scoped ");
            Serial.println(example.scoped);
            Delay.millis(2000);
            round = round + 1;
        }
    }

    private static int total(int[] counts) {
        return counts[0] + counts[1] + counts[2];
    }

    private void virtualThreads() throws InterruptedException {
        Thread first = Thread.startVirtualThread(new PrimeCounter(threadCounts, 0, 2, SLICE));
        Thread second = Thread.startVirtualThread(new PrimeCounter(threadCounts, 1, SLICE, 2 * SLICE));
        Thread third = Thread.startVirtualThread(new PrimeCounter(threadCounts, 2, 2 * SLICE, LIMIT));
        first.join();
        second.join();
        third.join();
    }

    /** main never sleeps or yields here, so the flag is set only if its time slice runs out and it is preempted. */
    private void preemption() throws InterruptedException {
        Thread releaser = Thread.startVirtualThread(new Releaser(this));
        while (!released) {
            spins = spins + 1;
        }
        releaser.join();
    }

    /** Two threads at a time: with main that stays within the scheduler's default of four slots. */
    private void monitors() throws InterruptedException {
        Thread first = Thread.startVirtualThread(new SynchronizedIncrementer(this));
        Thread second = Thread.startVirtualThread(new SynchronizedIncrementer(this));
        first.join();
        second.join();
        Thread third = Thread.startVirtualThread(new LockedIncrementer(this));
        Thread fourth = Thread.startVirtualThread(new LockedIncrementer(this));
        third.join();
        fourth.join();
    }

    private void structuredTasks() throws InterruptedException, ExecutionException {
        try (var scope = StructuredTaskScope.open()) {
            scope.fork(new PrimeCounter(taskCounts, 0, 2, SLICE));
            scope.fork(new PrimeCounter(taskCounts, 1, SLICE, 2 * SLICE));
            scope.fork(new PrimeCounter(taskCounts, 2, 2 * SLICE, LIMIT));
            scope.join();
        }
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
