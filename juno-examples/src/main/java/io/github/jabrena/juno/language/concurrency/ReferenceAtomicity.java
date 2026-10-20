package io.github.jabrena.juno.language.concurrency;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;

/** JLS 17.7: every reference read and write is atomic, even while another thread repeatedly replaces the reference. */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class, ArduinoUnoR3WiFiRev2.class})
public final class ReferenceAtomicity {
    private static final int READS = 50_000;

    static final class Cell {
    }

    private final Cell first = new Cell();
    private final Cell second = new Cell();
    private volatile boolean started;
    private volatile boolean writing;
    private Cell current;

    public static void main(String[] args) throws InterruptedException {
        Serial.begin(BaudRate.BAUD_115200);
        Thread.sleep(100);

        int result = new ReferenceAtomicity().verify();
        Serial.print("invalid references ");
        Serial.println(result);
    }

    int verify() throws InterruptedException {
        writing = true;
        Thread writer = Thread.startVirtualThread(this::write);
        while (!started) {
            Thread.yield();
        }
        int invalid = 0;
        for (int read = 0; read < READS; read++) {
            Cell observed = current;
            if (observed != null && observed != first && observed != second) {
                invalid++;
            }
        }
        writing = false;
        writer.join();
        return invalid;
    }

    private void write() {
        current = first;
        started = true;
        while (writing) {
            current = first;
            current = second;
        }
    }
}
