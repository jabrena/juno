package io.github.jabrena.juno.language.concurrency;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;

/**
 * JLS 17.4.4 and 17.4.5: writes before {@code startVirtualThread} are visible in the new thread, and the thread's
 * writes are visible after {@code join} returns.
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class, ArduinoUnoR3WiFiRev2.class})
public final class ThreadStartAndJoin {
    private int input;
    private int output;

    public static void main(String[] args) throws InterruptedException {
        Serial.begin(BaudRate.BAUD_115200);
        Thread.sleep(100);

        int result = new ThreadStartAndJoin().verify();
        Serial.print("start/join value ");
        Serial.println(result);
    }

    int verify() throws InterruptedException {
        input = 21;
        Thread worker = Thread.startVirtualThread(() -> output = input * 2);
        worker.join();
        return output;
    }
}
