package io.github.jabrena.juno.language.concurrency;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.hardware.serial.BaudRate;
import io.github.jabrena.juno.api.hardware.serial.Serial;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Four ways to increment one shared counter from two virtual threads. {@code counter++} is a read, an add and a write, and the scheduler may switch threads between
 * them, so the plain field can lose updates. {@code volatile} does not fix that: it makes each read and write visible
 * to the other thread, but the read-modify-write as a whole is still not atomic. A monitor, a lock or an atomic cell
 * makes the increment indivisible, so their totals are exact.
 *
 * <p>The plain and volatile totals depend on how the threads interleave, so they can fall short of the expected value
 * on any run. The other three always reach it.
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class LostUpdates {
    private static final int INCREMENTS = 1000;
    private static final int EXPECTED = 2 * INCREMENTS;

    private int plainCounter;
    private volatile int volatileCounter;
    private int synchronizedCounter;
    private int lockedCounter;
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicInteger atomicCounter = new AtomicInteger();

    public static void main(String[] args) throws InterruptedException {
        Serial.begin(BaudRate.BAUD_115200);

        LostUpdates example = new LostUpdates();
        example.countInParallel();
        example.report();
    }

    /** Two virtual threads each perform {@code INCREMENTS} increments of every counter, then the owner joins them. */
    private void countInParallel() throws InterruptedException {
        Thread first = Thread.startVirtualThread(this::increment);
        Thread second = Thread.startVirtualThread(this::increment);
        first.join();
        second.join();
    }

    private void increment() {
        for (int i = 0; i < INCREMENTS; i++) {
            plainCounter++;
            volatileCounter++;

            synchronized (this) {
                synchronizedCounter++;
            }

            lock.lock();
            try {
                lockedCounter++;
            } finally {
                lock.unlock();
            }

            atomicCounter.incrementAndGet();
        }
    }

    private void report() {
        Serial.println("expected " + EXPECTED);
        Serial.println("plain " + plainCounter);
        Serial.println("volatile " + volatileCounter);
        Serial.println("synchronized " + synchronizedCounter);
        Serial.println("locked " + lockedCounter);
        Serial.println("atomic " + atomicCounter.get());
    }
}
