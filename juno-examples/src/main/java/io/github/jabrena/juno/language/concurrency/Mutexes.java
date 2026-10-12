package io.github.jabrena.juno.language.concurrency;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Two mutual exclusion locks protecting one non-atomic counter: Peterson's algorithm and a spin lock built on
 * {@link AtomicBoolean}.
 *
 * <p>Peterson's algorithm needs no atomic read-modify-write: each thread announces interest in a volatile flag, gives
 * the other thread the turn, and waits only while the other thread is interested and holds the turn. The volatile
 * fields keep those reads and writes visible across threads, so the two threads never overlap in the critical section.
 *
 * <p>The spin lock takes an {@link AtomicBoolean} with {@code compareAndSet} and releases it with {@code set}. Both
 * locks yield while they wait, so the other thread can run. Each total must equal {@code 2 * ROUNDS}.
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class Mutexes {
    private static final int ROUNDS = 200;

    private volatile boolean firstWants;
    private volatile boolean secondWants;
    private volatile int turn;
    private int petersonCounter;

    private final AtomicBoolean taken = new AtomicBoolean();
    private int spinCounter;

    public static void main(String[] args) throws InterruptedException {
        Serial.begin(BaudRate.BAUD_115200);

        Mutexes example = new Mutexes();
        example.petersonRun();
        example.spinLockRun();
        example.report();
    }

    private void petersonRun() throws InterruptedException {
        Thread first = Thread.startVirtualThread(() -> petersonWorker(0));
        Thread second = Thread.startVirtualThread(() -> petersonWorker(1));
        first.join();
        second.join();
    }

    private void spinLockRun() throws InterruptedException {
        Thread first = Thread.startVirtualThread(this::spinWorker);
        Thread second = Thread.startVirtualThread(this::spinWorker);
        first.join();
        second.join();
    }

    private void petersonWorker(int self) {
        for (int round = 0; round < ROUNDS; round++) {
            enterPeterson(self);
            petersonCounter++;
            exitPeterson(self);
        }
    }

    /** Peterson's entry protocol for thread 0 or thread 1; the flag and the turn are both volatile. */
    private void enterPeterson(int self) {
        if (self == 0) {
            firstWants = true;
            turn = 1;
            while (secondWants && turn == 1) {
                Thread.yield();
            }
        } else {
            secondWants = true;
            turn = 0;
            while (firstWants && turn == 0) {
                Thread.yield();
            }
        }
    }

    private void exitPeterson(int self) {
        if (self == 0) {
            firstWants = false;
        } else {
            secondWants = false;
        }
    }

    private void spinWorker() {
        for (int round = 0; round < ROUNDS; round++) {
            while (!taken.compareAndSet(false, true)) {
                Thread.yield();
            }
            spinCounter++;
            taken.set(false);
        }
    }

    private void report() {
        Serial.println("expected " + 2 * ROUNDS);
        Serial.println("peterson " + petersonCounter);
        Serial.println("spin lock " + spinCounter);
    }
}
