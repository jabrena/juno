package io.github.jabrena.juno.language.concurrency;

import io.github.jabrena.juno.annotations.ArduinoOneQBoard;
import io.github.jabrena.juno.annotations.ArduinoOneR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.ArduinoOneR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;

/** JLS 17.3: sleeping and yielding do not release an owned monitor and add no memory-model synchronization edge. */
@Board({ArduinoOneR4WiFiBoard.class, ArduinoOneQBoard.class, ArduinoOneR3WiFiRev2Board.class})
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

    @SuppressWarnings("java:S2276")
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
