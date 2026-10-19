package io.github.jabrena.juno.language.concurrency;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.hardware.io.serial.BaudRate;
import io.github.jabrena.juno.api.hardware.io.serial.Serial;

/**
 * JLS 17.5: when a reference published after construction is observed, the constructor's write to its final field and
 * the initialized contents of the array reached through another final field are observed too. Publication itself is
 * deliberately racy here, so the bounded reader is allowed not to see it.
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class, ArduinoUnoR3WiFiRev2.class})
public final class FinalFieldPublication {
    private static final int ATTEMPTS = 10_000;

    static final class Value {
        final int answer;
        final int[] nested = new int[1];

        Value(int answer) {
            this.answer = answer;
            nested[0] = answer;
        }
    }

    private volatile boolean readerStarted;
    private Value published;
    private int observed = -1;

    public static void main(String[] args) throws InterruptedException {
        Serial.begin(BaudRate.BAUD_115200);
        Thread.sleep(100);

        int result = new FinalFieldPublication().verify();
        Serial.print("final value ");
        Serial.println(result);
    }

    int verify() throws InterruptedException {
        Thread reader = Thread.startVirtualThread(this::readPublished);
        while (!readerStarted) {
            Thread.yield();
        }
        published = new Value(42);
        reader.join();
        return observed;
    }

    private void readPublished() {
        readerStarted = true;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            Value value = published;
            if (value != null) {
                observed = value.answer == value.nested[0] ? value.answer : -2;
                return;
            }
            Thread.yield();
        }
    }
}
