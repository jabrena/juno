package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;

import java.util.UUID;

/** Board-native UUID-v4 generation: SCE5 on R4, ATECC608 on Rev2, and the STM32 RNG on UNO Q. */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoR3WiFiRev2.class, ArduinoUnoQ.class})
public final class UuidRandom {
    private UuidRandom() {
    }

    public static void main() {
        UUID value = UUID.randomUUID();
        Serial.println(value.version());
        Serial.println(value.variant());
    }
}
