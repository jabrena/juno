package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.ArduinoUnoWiFiRev2;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.serial.Serial;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Preemption, not cooperation: a thread that never sleeps, yields, joins or even loops still has to share the CPU.
 * The main thread runs a loop-free recursion that only returns once a spinning virtual thread has moved its counter;
 * under a cooperative scheduler nothing could switch threads inside that recursion and the search would exhaust
 * itself and report starvation. Then three threads hammer shared counters through a monitor, an atomic and a lock,
 * so a preemption between a read and its write would show up as a lost update.
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class, ArduinoUnoWiFiRev2.class})
public final class Preemption {
    static final Guard MONITOR = new Guard();
    static final ReentrantLock LOCK = new ReentrantLock();
    static final AtomicInteger ATOMIC = new AtomicInteger();
    static volatile boolean stop;
    static volatile int progress;
    static int snapshot;
    static int monitored;
    static int locked;

    static final class Guard {
    }

    /** No loop anywhere: returns 1 as soon as the counter has moved past the snapshot, 0 if it never did. */
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

    static void hammer(int rounds) {
        for (int i = 0; i < rounds; i++) {
            synchronized (MONITOR) {
                int seen = monitored;
                monitored = seen + 1;
            }
            ATOMIC.incrementAndGet();
            LOCK.lock();
            try {
                int seen = locked;
                locked = seen + 1;
            } finally {
                LOCK.unlock();
            }
        }
    }

    public static void main() throws Exception {
        Thread counter = Thread.startVirtualThread(() -> {
            while (!stop) {
                progress++;
            }
        });
        while (progress == 0) {
            // Wait until the counter is demonstrably running.
        }
        snapshot = progress;
        Serial.println(search(24) == 1 ? "counter progressed during recursion" : "counter starved");
        stop = true;
        counter.join();

        Thread first = Thread.startVirtualThread(() -> hammer(300));
        Thread second = Thread.startVirtualThread(() -> hammer(300));
        hammer(300);
        first.join();
        second.join();
        Serial.println(monitored);
        Serial.println(ATOMIC.get());
        Serial.println(locked);
    }
}
