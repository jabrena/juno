package io.github.jabrena.juno.language.concurrency;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.hardware.io.serial.BaudRate;
import io.github.jabrena.juno.api.hardware.io.serial.Serial;

import java.util.concurrent.locks.ReentrantLock;

/**
 * A producer and a consumer share a four-slot ring buffer. The usual approach blocks on semaphores; Juno has neither
 * semaphores nor {@code wait}/{@code notify}, so each side
 * instead takes the lock, tries its operation, releases the lock and yields when the buffer is full or empty.
 *
 * <p>The lock makes each offer and poll atomic, and the buffer never holds more than {@code CAPACITY} items or hands
 * out an item twice. The consumer keeps its running sum in its own field, which the owner reads after joining both
 * threads. The sum of 1..{@code ITEMS} must come out as {@code ITEMS * (ITEMS + 1) / 2}.
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class BoundedBuffer {
    private static final int CAPACITY = 4;
    private static final int ITEMS = 100;
    private static final int EMPTY = -1;

    private final ReentrantLock lock = new ReentrantLock();
    private final int[] buffer = new int[CAPACITY];
    private int putIndex;
    private int takeIndex;
    private int size;

    private int consumed;
    private int consumedSum;

    public static void main(String[] args) throws InterruptedException {
        Serial.begin(BaudRate.BAUD_115200);

        BoundedBuffer example = new BoundedBuffer();
        example.transfer();
        example.report();
    }

    private void transfer() throws InterruptedException {
        Thread producer = Thread.startVirtualThread(this::produce);
        Thread consumer = Thread.startVirtualThread(this::consume);
        producer.join();
        consumer.join();
    }

    private void produce() {
        for (int item = 1; item <= ITEMS; item++) {
            while (!offer(item)) {
                Thread.yield();
            }
        }
    }

    private void consume() {
        while (consumed < ITEMS) {
            int item = poll();
            if (item == EMPTY) {
                Thread.yield();
            } else {
                consumedSum += item;
                consumed++;
            }
        }
    }

    /** Adds an item unless the buffer is full; returns whether it was added. */
    private boolean offer(int item) {
        lock.lock();
        try {
            if (size == CAPACITY) {
                return false;
            }
            buffer[putIndex] = item;
            putIndex = (putIndex + 1) % CAPACITY;
            size++;
            return true;
        } finally {
            lock.unlock();
        }
    }

    /** Removes the oldest item, or returns {@code EMPTY} when there is none. */
    private int poll() {
        lock.lock();
        try {
            if (size == 0) {
                return EMPTY;
            }
            int item = buffer[takeIndex];
            takeIndex = (takeIndex + 1) % CAPACITY;
            size--;
            return item;
        } finally {
            lock.unlock();
        }
    }

    private void report() {
        int expected = ITEMS * (ITEMS + 1) / 2;
        Serial.println("consumed " + consumed + " items");
        Serial.println("sum " + consumedSum + ", expected " + expected);
    }
}
