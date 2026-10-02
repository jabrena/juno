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

    private static final Guard GUARD = new Guard();
    private static final ReentrantLock LOCK = new ReentrantLock();
    private static volatile boolean ready;
    private static int publishedValue;
    private static int synchronizedCounter;
    private static int lockedCounter;

    private Synchronization() {
    }

    static void publish() {
        publishedValue = 42;
        ready = true;
    }

    static void count() {
        for (int i = 0; i < 50; i++) {
            synchronized (GUARD) {
                synchronizedCounter++;
            }

            LOCK.lock();
            try {
                lockedCounter++;
                if (LOCK.tryLock()) {
                    LOCK.unlock();
                }
            } finally {
                LOCK.unlock();
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Serial.begin(BaudRate.BAUD_115200);

        Thread publisher = new Thread(Synchronization::publish);
        publisher.start();
        while (!ready) {
            // Volatile is reloaded and this backedge lets the publisher run.
        }
        publisher.join();
        Serial.print("published ");
        Serial.println(publishedValue);

        Thread first = new Thread(Synchronization::count);
        Thread second = new Thread(Synchronization::count);
        first.start();
        second.start();
        first.join();
        second.join();
        Serial.print("synchronized ");
        Serial.println(synchronizedCounter);
        Serial.print("locked ");
        Serial.println(lockedCounter);
    }
}
