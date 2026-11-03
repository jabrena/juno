package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Semaphore;

/**
 * Deterministic CyclicBarrier behavior shared by OpenJDK and Juno: three virtual threads meet at a barrier, twice.
 * Each thread must see all three arrivals after the barrier, the arrival indices of one trip must be 0, 1 and 2
 * (their sum is 3), and exactly one thread per trip is the last to arrive. None of that depends on scheduling order.
 */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class})
public final class CyclicBarrierRendezvous {
    static final CyclicBarrier BARRIER = new CyclicBarrier(3);
    static final Semaphore LOCK = new Semaphore(1);
    static int arrived;
    static int seenAll;
    static int indexSum;
    static int lastArrivals;

    static void work() throws Exception {
        for (int trip = 0; trip < 2; trip++) {
            LOCK.acquire();
            arrived++;
            LOCK.release();
            int index = BARRIER.await();
            LOCK.acquire();
            if (arrived == 3 * (trip + 1)) {
                seenAll++;
            }
            indexSum += index;
            if (index == 0) {
                lastArrivals++;
            }
            LOCK.release();
            BARRIER.await();
        }
    }

    public static void main() throws Exception {
        Thread first = Thread.startVirtualThread(() -> {
            try {
                work();
            } catch (Exception e) {
                seenAll = -100;
            }
        });
        Thread second = Thread.startVirtualThread(() -> {
            try {
                work();
            } catch (Exception e) {
                seenAll = -100;
            }
        });
        Thread third = Thread.startVirtualThread(() -> {
            try {
                work();
            } catch (Exception e) {
                seenAll = -100;
            }
        });
        first.join();
        second.join();
        third.join();
        Serial.println(seenAll);
        Serial.println(indexSum);
        Serial.println(lastArrivals);
        Serial.println(BARRIER.getParties());
    }
}
