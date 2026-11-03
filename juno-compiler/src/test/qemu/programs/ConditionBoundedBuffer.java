package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Deterministic Condition behavior shared by OpenJDK and Juno: a two-slot buffer filled by a virtual thread and
 * drained by main, with one condition for "not full" and one for "not empty". Whatever the interleaving, main takes
 * the twenty values 1 to 20 exactly once, so the sum is 210.
 */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class})
public final class ConditionBoundedBuffer {
    static final ReentrantLock LOCK = new ReentrantLock();
    static final Condition NOT_FULL = LOCK.newCondition();
    static final Condition NOT_EMPTY = LOCK.newCondition();
    static final int[] SLOTS = new int[2];
    static int count;
    static int head;
    static int tail;

    static void put(int value) throws InterruptedException {
        LOCK.lock();
        try {
            while (count == SLOTS.length) {
                NOT_FULL.await();
            }
            SLOTS[tail] = value;
            tail = (tail + 1) % SLOTS.length;
            count++;
            NOT_EMPTY.signal();
        } finally {
            LOCK.unlock();
        }
    }

    static int take() throws InterruptedException {
        LOCK.lock();
        try {
            while (count == 0) {
                NOT_EMPTY.await();
            }
            int value = SLOTS[head];
            head = (head + 1) % SLOTS.length;
            count--;
            NOT_FULL.signal();
            return value;
        } finally {
            LOCK.unlock();
        }
    }

    public static void main() throws Exception {
        Thread producer = Thread.startVirtualThread(() -> {
            try {
                for (int value = 1; value <= 20; value++) {
                    put(value);
                }
            } catch (InterruptedException e) {
                Serial.println(-1);
            }
        });
        int sum = 0;
        for (int i = 0; i < 20; i++) {
            sum += take();
        }
        producer.join();
        Serial.println(sum);
        Serial.println(count);
    }
}
