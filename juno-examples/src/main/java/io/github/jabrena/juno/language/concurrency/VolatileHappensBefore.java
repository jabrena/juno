package io.github.jabrena.juno.language.concurrency;

import io.github.jabrena.juno.annotations.ArduinoOneQBoard;
import io.github.jabrena.juno.annotations.ArduinoOneR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.ArduinoOneR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;

/** JLS 17.4.4 and 17.4.5: a volatile write publishes earlier ordinary writes to a later volatile reader. */
@Board({ArduinoOneR4WiFiBoard.class, ArduinoOneQBoard.class, ArduinoOneR3WiFiRev2Board.class})
public final class VolatileHappensBefore {
    private int value;
    private volatile boolean ready;

    public static void main(String[] args) throws InterruptedException {
        Serial.begin(BaudRate.BAUD_115200);
        Thread.sleep(100);

        int result = new VolatileHappensBefore().verify();
        Serial.print("volatile value ");
        Serial.println(result);
    }

    int verify() throws InterruptedException {
        Thread writer = Thread.startVirtualThread(this::publish);
        while (!ready) {
            Thread.yield();
        }
        int observed = value;
        writer.join();
        return observed;
    }

    private void publish() {
        value = 42;
        ready = true;
    }
}
