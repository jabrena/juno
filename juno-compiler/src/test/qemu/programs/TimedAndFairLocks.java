package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Deterministic timed, interruptible and fair locking shared by OpenJDK and Juno. Every printed value is fixed whatever
 * the interleaving: a timed tryLock or await on a held or unsignalled resource gives false, an interrupted
 * lockInterruptibly throws, and the fair lock and fair semaphore protect a counter that ends at 40.
 */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class})
public final class TimedAndFairLocks {
    static final ReentrantLock HELD = new ReentrantLock();
    static final ReentrantLock FAIR = new ReentrantLock(true);
    static final Semaphore FAIR_PERMITS = new Semaphore(1, true);
    static final ReentrantReadWriteLock RW = new ReentrantReadWriteLock();
    static final CountDownLatch LATCH = new CountDownLatch(1);
    static final Condition NEVER = HELD.newCondition();
    static int counter;
    static int tryLockOutcome;
    static int interruptOutcome;

    static void fairIncrement() {
        for (int i = 0; i < 20; i++) {
            FAIR.lock();
            try {
                counter++;
            } finally {
                FAIR.unlock();
            }
            try {
                FAIR_PERMITS.acquire();
                counter++;
                FAIR_PERMITS.release();
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    public static void main() throws Exception {
        HELD.lock();
        Thread tryer = Thread.startVirtualThread(() -> {
            try {
                tryLockOutcome = HELD.tryLock(20, TimeUnit.MILLISECONDS) ? 1 : 0;
            } catch (InterruptedException e) {
                tryLockOutcome = 2;
            }
        });
        tryer.join();
        Serial.println(tryLockOutcome);
        Thread waiter = Thread.startVirtualThread(() -> {
            try {
                HELD.lockInterruptibly();
                interruptOutcome = 9;
            } catch (InterruptedException e) {
                interruptOutcome = 1;
            }
        });
        waiter.interrupt();
        waiter.join();
        Serial.println(interruptOutcome);
        Serial.println(NEVER.await(5, TimeUnit.MILLISECONDS) ? 1 : 0);
        HELD.unlock();

        Serial.println(LATCH.await(1, TimeUnit.NANOSECONDS) ? 1 : 0);
        LATCH.countDown();
        Serial.println(LATCH.await(1, TimeUnit.SECONDS) ? 1 : 0);

        FAIR_PERMITS.acquire();
        Serial.println(FAIR_PERMITS.tryAcquire(5, TimeUnit.MILLISECONDS) ? 1 : 0);
        FAIR_PERMITS.release();

        Lock write = RW.writeLock();
        write.lock();
        Condition writeCondition = write.newCondition();
        Serial.println(writeCondition.await(5, TimeUnit.MILLISECONDS) ? 1 : 0);
        write.unlock();

        Thread first = Thread.startVirtualThread(TimedAndFairLocks::fairIncrement);
        Thread second = Thread.startVirtualThread(TimedAndFairLocks::fairIncrement);
        first.join();
        second.join();
        Serial.println(counter);
    }
}
