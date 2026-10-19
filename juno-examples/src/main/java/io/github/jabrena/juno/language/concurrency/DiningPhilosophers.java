package io.github.jabrena.juno.language.concurrency;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.hardware.io.serial.BaudRate;
import io.github.jabrena.juno.api.hardware.io.serial.Serial;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Three philosophers share three forks. Each philosopher needs
 * the two forks on either side of them, so a cycle of philosophers each holding one fork and waiting for the next
 * would deadlock.
 *
 * <p>The resource hierarchy rule removes the cycle: every fork has a number, and each philosopher always takes the
 * lower-numbered fork first. Philosopher 2 sits between forks 0 and 2, so it takes fork 0 first even though fork 2 is
 * on its right. Each philosopher eats {@code MEALS} times, and each count must come out equal to {@code MEALS}.
 *
 * <p>The three forks are fields rather than an array, which keeps the lock lookup to a simple switch.
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class DiningPhilosophers {
    private static final int PHILOSOPHERS = 3;
    private static final int MEALS = 20;

    private final ReentrantLock forkZero = new ReentrantLock();
    private final ReentrantLock forkOne = new ReentrantLock();
    private final ReentrantLock forkTwo = new ReentrantLock();

    // Each philosopher writes only its own slot, and the owner reads them after join.
    private final int[] meals = new int[PHILOSOPHERS];

    public static void main(String[] args) throws InterruptedException {
        Serial.begin(BaudRate.BAUD_115200);

        DiningPhilosophers example = new DiningPhilosophers();
        example.dine();
        example.report();
    }

    private void dine() throws InterruptedException {
        Thread zero = Thread.startVirtualThread(() -> eat(0, 0, 1));
        Thread one = Thread.startVirtualThread(() -> eat(1, 1, 2));
        Thread two = Thread.startVirtualThread(() -> eat(2, 0, 2));
        zero.join();
        one.join();
        two.join();
    }

    /** Eats {@code MEALS} times, always locking the lower-numbered fork first. */
    private void eat(int seat, int lowFork, int highFork) {
        for (int meal = 0; meal < MEALS; meal++) {
            lockFork(lowFork);
            try {
                lockFork(highFork);
                try {
                    meals[seat] = meals[seat] + 1;
                } finally {
                    unlockFork(highFork);
                }
            } finally {
                unlockFork(lowFork);
            }
            Thread.yield();
        }
    }

    private void lockFork(int index) {
        if (index == 0) {
            forkZero.lock();
        } else if (index == 1) {
            forkOne.lock();
        } else {
            forkTwo.lock();
        }
    }

    private void unlockFork(int index) {
        if (index == 0) {
            forkZero.unlock();
        } else if (index == 1) {
            forkOne.unlock();
        } else {
            forkTwo.unlock();
        }
    }

    private void report() {
        int total = 0;
        for (int seat = 0; seat < PHILOSOPHERS; seat++) {
            Serial.println("philosopher " + seat + " ate " + meals[seat] + " times");
            total += meals[seat];
        }
        Serial.println("expected " + PHILOSOPHERS * MEALS + ", total " + total);
    }
}
