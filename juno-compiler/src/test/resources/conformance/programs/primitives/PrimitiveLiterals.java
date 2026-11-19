package demo;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.AiThinkerEsp32CamBoard;
import io.github.jabrena.juno.annotations.M5StackCardputerBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;

/** Primitive literal forms from JLS 3.10. */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoNanoEsp32Board.class, M5StackCardputerBoard.class, AiThinkerEsp32CamBoard.class})
public final class PrimitiveLiterals {
    private PrimitiveLiterals() {
    }

    public static void main(String[] args) {
        Serial.println(0b1010);
        Serial.println(0x7fff_ffff);
        Serial.println(0123);
        Serial.println(1_000_000);
        Serial.println((int) 1.25f);
        Serial.println((int) 2.5d);
        Serial.println(true);
        Serial.println((int) 'A');
    }
}
