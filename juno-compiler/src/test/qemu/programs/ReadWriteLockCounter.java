package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Deterministic ReentrantReadWriteLock behavior shared by OpenJDK and Juno: two writers each add to a counter twenty
 * times under the write lock while a reader reads it under the read lock. Every read sees a value between 0 and 40,
 * and the final value is 40 whatever the interleaving.
 */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class})
public final class ReadWriteLockCounter {
    static final ReentrantReadWriteLock RW = new ReentrantReadWriteLock();
    static final ReadWriteLock VIEW = RW;
    static int counter;
    static int readsInRange;

    static void writer() {
        for (int i = 0; i < 20; i++) {
            Lock write = VIEW.writeLock();
            write.lock();
            try {
                counter++;
            } finally {
                write.unlock();
            }
        }
    }

    static void reader() {
        for (int i = 0; i < 10; i++) {
            RW.readLock().lock();
            try {
                if (counter >= 0 && counter <= 40) {
                    readsInRange++;
                }
            } finally {
                RW.readLock().unlock();
            }
        }
    }

    public static void main() throws Exception {
        Thread first = Thread.startVirtualThread(ReadWriteLockCounter::writer);
        Thread second = Thread.startVirtualThread(ReadWriteLockCounter::writer);
        Thread third = Thread.startVirtualThread(ReadWriteLockCounter::reader);
        first.join();
        second.join();
        third.join();
        Serial.println(counter);
        Serial.println(readsInRange);
        Serial.println(RW.writeLock().tryLock() ? 1 : 0);
    }
}
