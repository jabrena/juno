package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;

/** Virtual threads on the preemptive scheduler: start, join, isAlive, sleep and yield on each board's thread port. */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class VirtualThreadsApi {
    private static final int[] COUNT = new int[1];

    static void work() {
        COUNT[0] = COUNT[0] + 1;
        try {
            Thread.sleep(5);
        } catch (InterruptedException interrupted) {
            Serial.println("interrupted");
        }
        Thread.yield();
    }

    public static void main(String[] args) throws InterruptedException {
        Thread first = Thread.startVirtualThread(() -> work());
        Thread second = Thread.startVirtualThread(() -> work());
        first.join();
        second.join();
        Serial.println(first.isAlive() ? "alive" : "done");
        Serial.println(COUNT[0]);
    }
}
