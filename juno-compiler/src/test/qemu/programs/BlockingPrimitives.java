package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;

/**
 * Deterministic blocking behavior shared by OpenJDK and Juno's preemptive runtime: a latch, a semaphore that keeps
 * two threads from losing an update, wait/notifyAll, and interrupts that end a sleep and a latch await early. Every
 * printed value is the same whatever order the threads run in.
 */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class})
public final class BlockingPrimitives {
    static final class Gate {
    }

    static final Semaphore PERMITS = new Semaphore(1);
    static final Gate LOCK = new Gate();
    static int shared;
    static volatile boolean ready;
    static volatile int sleepOutcome;
    static volatile int awaitOutcome;

    static void bump() {
        for (int i = 0; i < 30; i++) {
            try {
                PERMITS.acquire();
            } catch (InterruptedException e) {
                return;
            }
            shared++;
            PERMITS.release();
        }
    }

    public static void main() throws Exception {
        CountDownLatch latch = new CountDownLatch(2);
        Thread first = Thread.startVirtualThread(() -> latch.countDown());
        Thread second = Thread.startVirtualThread(() -> latch.countDown());
        latch.await();
        first.join();
        second.join();
        Serial.println(latch.getCount() == 0 ? 1 : 0);

        Thread bumpA = Thread.startVirtualThread(BlockingPrimitives::bump);
        Thread bumpB = Thread.startVirtualThread(BlockingPrimitives::bump);
        bumpA.join();
        bumpB.join();
        Serial.println(shared);
        Serial.println(PERMITS.availablePermits());

        Thread notifier = Thread.startVirtualThread(() -> {
            synchronized (LOCK) {
                ready = true;
                LOCK.notifyAll();
            }
        });
        synchronized (LOCK) {
            while (!ready) {
                LOCK.wait();
            }
        }
        notifier.join();
        Serial.println(ready ? 1 : 0);

        Thread sleeper = Thread.startVirtualThread(() -> {
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException e) {
                sleepOutcome = 1;
            }
        });
        sleeper.interrupt();
        sleeper.join();
        Serial.println(sleepOutcome);

        CountDownLatch never = new CountDownLatch(1);
        Thread waiter = Thread.startVirtualThread(() -> {
            try {
                never.await();
            } catch (InterruptedException e) {
                awaitOutcome = 2;
            }
        });
        waiter.interrupt();
        waiter.join();
        Serial.println(awaitOutcome);

        Thread idle = Thread.startVirtualThread(() -> {
        });
        idle.interrupt();
        idle.join();
        Serial.println(idle.isInterrupted() ? 1 : 0);
    }
}
