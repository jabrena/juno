package io.github.jabrena.juno.api.io.storage;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.usb.BaudRate;
import io.github.jabrena.juno.api.io.usb.Serial;

import java.io.IOException;
import java.io.InputStream;

/**
 * Exercises every {@link SdCard} file operation end to end: creates a file by appending its first
 * line, appends a second line, reads the whole file back, deletes it, then confirms the deletion —
 * each step reported over USB serial so the behavior can be checked remotely with a serial monitor
 * instead of pulling the card to inspect it by hand.
 *
 * <p>Wire a standard SPI SD card breakout with its chip-select line on D10 (the default
 * {@link SdCard#begin()} pin), upload, then watch the result:
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:upload \
 *   -Djuno.main=io.github.jabrena.juno.api.io.storage.SdFileOperations
 * ./mvnw -f juno-examples/pom.xml juno:monitor
 * }</pre>
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class SdFileOperations {
    private static final String PATH = "juno-sd-demo.txt";

    public static void main(String[] args) throws IOException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        if (!SdCard.begin()) {
            Serial.println("SD card initialization failed");
            return;
        }
        Serial.println("SD card ready");

        SdCard.remove(PATH); // start from a clean slate on every run
        report("exists before create", SdCard.exists(PATH));

        report("create (append line 1)", SdCard.append(PATH, "line 1"));
        report("append line 2", SdCard.append(PATH, "line 2"));
        report("exists after append", SdCard.exists(PATH));

        Serial.println("reading back:");
        readBack();

        report("remove", SdCard.remove(PATH));
        report("exists after remove", SdCard.exists(PATH));

        Serial.println("done");
        while (true) {
            Delay.millis(1000);
        }
    }

    private static void report(String label, boolean value) {
        Serial.print(label);
        Serial.print(": ");
        Serial.println(value ? "true" : "false");
    }

    private static void readBack() throws IOException {
        InputStream file = SdCard.open(PATH);
        if (file == null) {
            Serial.println("  (could not open)");
            return;
        }
        StringBuilder line = new StringBuilder(32);
        while (file.available() > 0) {
            char next = (char) file.read();
            if (next == '\n') {
                Serial.print("  ");
                Serial.println(line.toString());
                line = new StringBuilder(32);
            } else if (next != '\r') {
                line.append(next);
            }
        }
        file.close();
    }
}
