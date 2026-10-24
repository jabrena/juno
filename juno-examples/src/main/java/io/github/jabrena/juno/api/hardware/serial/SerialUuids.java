package io.github.jabrena.juno.api.hardware.serial;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

import java.util.UUID;

/**
 * Prints a fresh random UUID from {@link UUID#randomUUID()} every two seconds at 115200 baud. On the Nano ESP32 the bytes
 * come from the chip's hardware random-number generator, so the values differ on every run.
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:upload \
 *   -Djuno.main=io.github.jabrena.juno.api.hardware.serial.SerialUuids -Djuno.board=arduino-nano-esp32
 * ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-nano-esp32 -Djuno.baudRate=115200
 * }</pre>
 */
@Board({ArduinoNanoEsp32Board.class})
@SuppressWarnings("java:S2189")
public final class SerialUuids {
    private static final int INTERVAL_MS = 2000;

    private SerialUuids() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        while (true) {
            Serial.println(UUID.randomUUID().toString());
            Delay.millis(INTERVAL_MS);
        }
    }
}
