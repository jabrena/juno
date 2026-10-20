package io.github.jabrena.juno.language.concurrency;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.hardware.serial.BaudRate;
import io.github.jabrena.juno.api.hardware.serial.Serial;

/** JLS 17.3: sleeping and yielding do not release an owned monitor and add no memory-model synchronization edge. */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class, ArduinoUnoR3WiFiRev2.class})
public final class SleepAndYield {
    static final class Guard {
    }

    private final Guard guard = new Guard();
    private volatile boolean attempting;
    private volatile boolean entered;

    public static void main(String[] args) throws InterruptedException {
        Serial.begin(BaudRate.BAUD_115200);
        Thread.sleep(100);

        int result = new SleepAndYield().verify();
        Serial.print("monitor retained ");
        Serial.println(result == 1);
    }

    int verify() throws InterruptedException {
        Thread contender;
        boolean enteredWhileOwned;
        synchronized (guard) {
            contender = Thread.startVirtualThread(this::contend);
            while (!attempting) {
                Thread.yield();
            }
            Thread.yield();
            Thread.sleep(5);
            enteredWhileOwned = entered;
        }
        contender.join();
        return !enteredWhileOwned && entered ? 1 : 0;
    }

    private void contend() {
        attempting = true;
        synchronized (guard) {
            entered = true;
        }
    }
}
