package io.github.jabrena.juno.language.concurrency;

import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;

/** JLS 17.6: writes to adjacent byte-array elements are independent and must not overwrite one another. */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class, ArduinoUnoR3WiFiRev2Board.class})
public final class WordTearing {
    private static final int ITERATIONS = 20_000;

    private final byte[] values = new byte[2];
    private final int[] failures = new int[2];

    public static void main(String[] args) throws InterruptedException {
        Serial.begin(BaudRate.BAUD_115200);
        Thread.sleep(100);

        int result = new WordTearing().verify();
        Serial.print("word tears ");
        Serial.println(result);
    }

    int verify() throws InterruptedException {
        Thread first = Thread.startVirtualThread(() -> update(0));
        Thread second = Thread.startVirtualThread(() -> update(1));
        first.join();
        second.join();
        return failures[0] + failures[1];
    }

    private void update(int slot) {
        byte expected = 0;
        for (int iteration = 0; iteration < ITERATIONS; iteration++) {
            if (values[slot] != expected) {
                failures[slot] = failures[slot] + 1;
                return;
            }
            expected++;
            values[slot] = expected;
        }
    }
}
