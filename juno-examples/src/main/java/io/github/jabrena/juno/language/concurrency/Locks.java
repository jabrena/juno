package io.github.jabrena.juno.language.concurrency;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;

import java.util.concurrent.locks.ReentrantLock;

/**
 * A small, board-ready example of a concrete {@link ReentrantLock}.
 *
 * <p>The two workers increment one counter under the lock. {@code incrementWhileHeld} acquires the same lock again,
 * demonstrating reentrancy; the final increment uses the zero-argument {@code tryLock} form. Every acquisition is
 * paired with an unlock in a {@code finally} block.
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class, ArduinoUnoR3WiFiRev2.class})
public final class Locks {
    private static final int ROUNDS = 100;

    private final ReentrantLock lock = new ReentrantLock();
    private int counter;

    public static void main(String[] args) throws InterruptedException {
        Serial.begin(BaudRate.BAUD_115200);

        Locks example = new Locks();
        int value = example.verify();
        Serial.println("lock counter " + value + ", expected " + (2 * ROUNDS + 1));
    }

    int verify() throws InterruptedException {
        Thread first = Thread.startVirtualThread(this::worker);
        Thread second = Thread.startVirtualThread(this::worker);
        first.join();
        second.join();

        if (lock.tryLock()) {
            try {
                counter++;
            } finally {
                lock.unlock();
            }
        }
        return counter;
    }

    private void worker() {
        for (int round = 0; round < ROUNDS; round++) {
            lock.lock();
            try {
                incrementWhileHeld();
            } finally {
                lock.unlock();
            }
        }
    }

    private void incrementWhileHeld() {
        lock.lock();
        try {
            counter++;
        } finally {
            lock.unlock();
        }
    }
}
