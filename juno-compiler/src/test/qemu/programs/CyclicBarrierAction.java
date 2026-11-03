package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;

import java.util.concurrent.CyclicBarrier;

/**
 * Deterministic CyclicBarrier trip action shared by OpenJDK and Juno: a barrier of two (main and a virtual thread) makes
 * three trips, and the action runs exactly once per trip, on whichever thread arrives last. The count of trips is 3
 * whatever the order of arrivals.
 */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class})
public final class CyclicBarrierAction {
    static int trips;
    static final CyclicBarrier BARRIER = new CyclicBarrier(2, () -> trips++);

    static void work() throws Exception {
        for (int i = 0; i < 3; i++) {
            BARRIER.await();
        }
    }

    public static void main() throws Exception {
        Thread worker = Thread.startVirtualThread(() -> {
            try {
                work();
            } catch (Exception e) {
                trips = -100;
            }
        });
        work();
        worker.join();
        Serial.println(trips);
        Serial.println(BARRIER.getParties());
    }
}
