package io.github.jabrena.juno.exceptions;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.usb.BaudRate;
import io.github.jabrena.juno.api.io.usb.Serial;

/**
 * Demonstrates exceptions that cross method boundaries: a {@code catch} in {@code main} handles what a
 * method two calls down throws, {@code finally} runs while the exception unwinds, and try-with-resources
 * closes its resource on both the normal and the exceptional path.
 *
 * <p>Expected serial output at 115200 baud:
 * <pre>
 * parsed 42
 * close 1
 * caught negative reading
 * cleanup
 * caught negative reading
 * close 2
 * done
 * </pre>
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class ExceptionUnwinding {
    /** A resource whose {@code close()} is observable on the serial monitor. */
    static final class Channel implements AutoCloseable {
        private final int id;

        Channel(int id) {
            this.id = id;
        }

        @Override
        public void close() {
            Serial.print("close ");
            Serial.println(id);
        }
    }

    static int parse(int reading) {
        if (reading < 0) {
            throw new IllegalArgumentException("negative reading");
        }
        return reading;
    }

    static int sample(int reading) {
        return parse(reading) + 1;
    }

    static void withFinally(int reading) {
        try {
            sample(reading);
        } finally {
            Serial.println("cleanup");
        }
    }

    static void withResource(int id, int reading) {
        try (Channel channel = new Channel(id)) {
            sample(reading);
        }
    }

    /**
     * Runs the scenarios and prints what each one did.
     *
     * @param args ignored; Juno programs do not receive command-line arguments
     */
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Serial.print("parsed ");
        Serial.println(sample(41));
        try {
            withResource(1, -1);
        } catch (IllegalArgumentException e) {
            Serial.print("caught ");
            Serial.println(e.getMessage());
        }
        try {
            withFinally(-1);
        } catch (IllegalArgumentException e) {
            Serial.print("caught ");
            Serial.println(e.getMessage());
        }
        withResource(2, 5);
        Serial.println("done");
    }
}
