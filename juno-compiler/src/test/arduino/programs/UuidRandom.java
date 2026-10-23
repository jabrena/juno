package demo;

import io.github.jabrena.juno.annotations.ArduinoOneQBoard;
import io.github.jabrena.juno.annotations.ArduinoOneR4WiFiBoard;
import io.github.jabrena.juno.annotations.ArduinoOneR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;

import java.util.UUID;

/** Board-native UUID-v4 generation: SCE5 on R4, ATECC608 on Rev2, and the STM32 RNG on UNO Q. */
@Board({ArduinoOneR4WiFiBoard.class, ArduinoOneR3WiFiRev2Board.class, ArduinoOneQBoard.class})
public final class UuidRandom {
    private UuidRandom() {
    }

    public static void main() {
        UUID value = UUID.randomUUID();
        Serial.println(value.version());
        Serial.println(value.variant());
    }
}
