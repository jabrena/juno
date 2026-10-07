package io.github.jabrena.juno;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;

import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/** Volatile publication, intrinsic monitor blocks, atomic counters, and Juno's restricted {@link ReentrantLock} subset. */
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
    private final AtomicInteger atomicCounter = new AtomicInteger();

    public static void main(String[] args) throws Exception {
        Serial.begin(BaudRate.BAUD_115200);

        Synchronization example = new Synchronization();
        example.volatilePublication();
        example.monitorAndLock();
    }

    /** A volatile flag publishes a value from one subtask to its parent. */
    private void volatilePublication() throws Exception {
        try (var scope = StructuredTaskScope.open()) {
            scope.fork(this::publish);
            while (!ready) {
                // Volatile is reloaded and this backedge lets the publisher run.
            }
            scope.join();
        }
        Serial.print("published ");
        Serial.println(publishedValue);
    }

    /** Two subtasks increment shared counters under a monitor, under a lock, and with an atomic. */
    private void monitorAndLock() throws Exception {
        try (var scope = StructuredTaskScope.open()) {
            scope.fork(this::count);
            scope.fork(this::count);
            scope.join();
        }
        Serial.print("synchronized ");
        Serial.println(synchronizedCounter);
        Serial.print("locked ");
        Serial.println(lockedCounter);
        Serial.print("atomic ");
        Serial.println(atomicCounter.get());
    }

    private void publish() {
        publishedValue = 42;
        ready = true;
    }

    private void count() {
        for (int i = 0; i < 50; i++) {
            atomicCounter.incrementAndGet();

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
