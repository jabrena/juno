package io.github.jabrena.juno.language.concurrency;

import io.github.jabrena.juno.annotations.ArduinoOneQBoard;
import io.github.jabrena.juno.annotations.ArduinoOneR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.ArduinoOneR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;

/**
 * JLS 17.1 and 17.4.5: a monitor is reentrant, and an unlock happens-before a later lock of the same monitor.
 */
@Board({ArduinoOneR4WiFiBoard.class, ArduinoOneQBoard.class, ArduinoOneR3WiFiRev2Board.class})
public final class MonitorHappensBefore {
    static final class Guard {
    }

    private final Guard guard = new Guard();
    private int value;
    private int observed;

    public static void main(String[] args) throws InterruptedException {
        Serial.begin(BaudRate.BAUD_115200);
        Thread.sleep(100);

        int result = new MonitorHappensBefore().verify();
        Serial.print("monitor value ");
        Serial.println(result);
    }

    int verify() throws InterruptedException {
        Thread reader;
        synchronized (guard) {
            reader = Thread.startVirtualThread(this::readUnderMonitor);
            writeReentrantly();
        }
        reader.join();
        return observed;
    }

    private void writeReentrantly() {
        synchronized (guard) {
            value = 42;
        }
    }

    private void readUnderMonitor() {
        synchronized (guard) {
            observed = value;
        }
    }
}
