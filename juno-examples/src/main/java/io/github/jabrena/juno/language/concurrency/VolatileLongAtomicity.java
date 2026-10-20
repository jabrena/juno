package io.github.jabrena.juno.language.concurrency;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;

/** JLS 17.7: volatile 64-bit reads and writes are atomic and therefore cannot expose one half of each value. */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class, ArduinoUnoR3WiFiRev2.class})
public final class VolatileLongAtomicity {
    private static final int READS = 50_000;

    private volatile boolean started;
    private volatile boolean writing;
    private volatile long value;

    public static void main(String[] args) throws InterruptedException {
        Serial.begin(BaudRate.BAUD_115200);
        Thread.sleep(100);

        int result = new VolatileLongAtomicity().verify();
        Serial.print("torn longs ");
        Serial.println(result);
    }

    int verify() throws InterruptedException {
        writing = true;
        Thread writer = Thread.startVirtualThread(this::write);
        while (!started) {
            Thread.yield();
        }
        int torn = 0;
        for (int read = 0; read < READS; read++) {
            long observed = value;
            if ((int) observed != (int) (observed >>> 32)) {
                torn++;
            }
        }
        writing = false;
        writer.join();
        return torn;
    }

    private void write() {
        value = -1L;
        started = true;
        while (writing) {
            value = 0L;
            value = -1L;
        }
    }
}
