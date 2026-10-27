package io.github.jabrena.juno.language;

import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

import java.util.UUID;

/** Demonstrates parsing and formatting a UUID without the larger object catalogue. */
public final class UuidValues {
    private UuidValues() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        UUID value = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        Serial.println("UUID: " + value.toString());
    }
}
