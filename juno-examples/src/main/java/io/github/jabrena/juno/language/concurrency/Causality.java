package io.github.jabrena.juno.language.concurrency;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;

/**
 * JLS 17.4.8: circular reasoning cannot create values out of thin air. Neither conditional write can occur unless a
 * read first observes the value that only the other conditional write could produce.
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class, ArduinoUnoR3WiFiRev2.class})
public final class Causality {
    private int x;
    private int y;
    private int firstRead;
    private int secondRead;

    public static void main(String[] args) throws InterruptedException {
        Serial.begin(BaudRate.BAUD_115200);
        Thread.sleep(100);

        int result = new Causality().verify();
        Serial.print("out of thin air ");
        Serial.println(result != 0);
    }

    int verify() throws InterruptedException {
        Thread first = Thread.startVirtualThread(this::first);
        Thread second = Thread.startVirtualThread(this::second);
        first.join();
        second.join();
        return firstRead | secondRead | x | y;
    }

    private void first() {
        firstRead = x;
        if (firstRead != 0) {
            y = 1;
        }
    }

    private void second() {
        secondRead = y;
        if (secondRead != 0) {
            x = 1;
        }
    }
}
