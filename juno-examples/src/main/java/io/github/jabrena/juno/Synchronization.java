package io.github.jabrena.juno;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.usb.BaudRate;
import io.github.jabrena.juno.api.io.usb.Serial;

import java.util.concurrent.locks.ReentrantLock;

/** Volatile publication, intrinsic monitor blocks, and Juno's restricted {@link ReentrantLock} subset. */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class Synchronization {
    static final class Guard {
    }

    private final Guard guard = new Guard();
    private final ReentrantLock lock = new ReentrantLock();
    private volatile boolean ready;
    private int publishedValue;
    private int synchronizedCounter;
    private int lockedCounter;

    public static void main(String[] args) throws InterruptedException {
        Serial.begin(BaudRate.BAUD_115200);

        Synchronization example = new Synchronization();
        example.volatilePublication();
        example.monitorAndLock();
    }

    /** A volatile flag publishes a value from one thread to another. */
    private void volatilePublication() throws InterruptedException {
        Thread publisher = new Thread(this::publish);
        publisher.start();
        while (!ready) {
            // Volatile is reloaded and this backedge lets the publisher run.
        }
        publisher.join();
        Serial.print("published ");
        Serial.println(publishedValue);
    }

    /** Two threads increment shared counters under a monitor and under a lock. */
    private void monitorAndLock() throws InterruptedException {
        Thread first = new Thread(this::count);
        Thread second = new Thread(this::count);
        first.start();
        second.start();
        first.join();
        second.join();
        Serial.print("synchronized ");
        Serial.println(synchronizedCounter);
        Serial.print("locked ");
        Serial.println(lockedCounter);
    }

    private void publish() {
        publishedValue = 42;
        ready = true;
    }

    private void count() {
        for (int i = 0; i < 50; i++) {
            synchronized (guard) {
                synchronizedCounter++;
            }

            lock.lock();
            try {
                lockedCounter++;
                if (lock.tryLock()) {
                    lock.unlock();
                }
            } finally {
                lock.unlock();
            }
        }
    }
}
