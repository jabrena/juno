package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

/**
 * Deterministic CyclicBarrier breakage shared by OpenJDK and Juno: a barrier of three never trips because only two
 * threads arrive, and one of them is interrupted. The interrupted thread gets InterruptedException, the other waiter
 * and main (which calls await afterwards) get BrokenBarrierException. Whether the interrupt lands before or during the
 * wait, the outcome is the same.
 */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class})
public final class CyclicBarrierBreak {
    static final CyclicBarrier BARRIER = new CyclicBarrier(3);
    static volatile int interruptedOutcome;
    static volatile int brokenOutcome;

    static void interruptedWaiter() {
        try {
            BARRIER.await();
            interruptedOutcome = 9;
        } catch (InterruptedException e) {
            interruptedOutcome = 1;
        } catch (BrokenBarrierException e) {
            interruptedOutcome = 8;
        }
    }

    static void otherWaiter() {
        try {
            BARRIER.await();
            brokenOutcome = 9;
        } catch (BrokenBarrierException e) {
            brokenOutcome = 2;
        } catch (InterruptedException e) {
            brokenOutcome = 8;
        }
    }

    public static void main() throws Exception {
        Thread interrupted = Thread.startVirtualThread(CyclicBarrierBreak::interruptedWaiter);
        Thread other = Thread.startVirtualThread(CyclicBarrierBreak::otherWaiter);
        interrupted.interrupt();
        interrupted.join();
        other.join();
        Serial.println(interruptedOutcome);
        Serial.println(brokenOutcome);
        try {
            BARRIER.await();
            Serial.println(9);
        } catch (BrokenBarrierException e) {
            Serial.println(3);
        }
    }
}
