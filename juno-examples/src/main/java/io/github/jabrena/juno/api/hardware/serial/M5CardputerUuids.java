package io.github.jabrena.juno.api.hardware.serial;

import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.M5StackCardputerBoard;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

import java.util.UUID;

/**
 * Prints a fresh random UUID from {@link UUID#randomUUID()} every two seconds at 115200 baud. On the Cardputer the bytes
 * come from the ESP32-S3's hardware random-number generator, the same source as on the Nano ESP32.
 */
@Board({M5StackCardputerBoard.class})
public final class M5CardputerUuids {
    private M5CardputerUuids() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        while (true) {
            Serial.println(UUID.randomUUID().toString());
            Delay.millis(2000);
        }
    }
}
