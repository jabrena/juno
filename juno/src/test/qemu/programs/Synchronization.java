package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.serial.Serial;

import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.locks.ReentrantLock;

/** Deterministic synchronization behavior shared by OpenJDK and Juno's cooperative runtime. */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class Synchronization {
    static final class Guard {
        private final int marker;

        Guard(int marker) {
            this.marker = marker;
        }

        int marker() {
            return marker;
        }
    }

    record Cell(int value) {
    }

    static final Guard GUARD = new Guard(7);
    static final ReentrantLock LOCK = new ReentrantLock();
    static volatile boolean ready;
    static int value;
    static int monitorCount;
    static int lockCount;

    static void count() {
        for (int i = 0; i < 40; i++) {
            synchronized (GUARD) {
                monitorCount++;
            }
            LOCK.lock();
            try {
                lockCount++;
                if (LOCK.tryLock()) {
                    LOCK.unlock();
                } else {
                    lockCount += 1000;
                }
            } finally {
                LOCK.unlock();
            }
        }
    }

    public static void main() throws Exception {
        for (int i = 0; i < 1200; i++) {
            new Cell(i);
        }
        Serial.println(GUARD.marker());

        try (var scope = StructuredTaskScope.open()) {
            scope.fork(() -> {
                value = 42;
                ready = true;
            });
            while (!ready) {
            }
            scope.join();
        }
        Serial.println(value);

        try (var scope = StructuredTaskScope.open()) {
            scope.fork(Synchronization::count);
            scope.fork(Synchronization::count);
            scope.join();
        }
        Serial.println(monitorCount);
        Serial.println(lockCount);
    }
}
