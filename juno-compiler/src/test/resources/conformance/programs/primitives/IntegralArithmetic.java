package demo;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.AiThinkerEsp32CamBoard;
import io.github.jabrena.juno.annotations.M5StackCardputerBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;

/** Integral operators, overflow, division, remainder, shifts, and bitwise operations. */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoNanoEsp32Board.class, M5StackCardputerBoard.class, AiThinkerEsp32CamBoard.class})
public final class IntegralArithmetic {
    private IntegralArithmetic() {
    }

    public static void main(String[] args) {
        Serial.println(7 * 6);
        Serial.println(7 / 2);
        Serial.println(-7 / 2);
        Serial.println(7 % -3);
        Serial.println(Integer.MAX_VALUE + 1);
        Serial.println(Integer.MIN_VALUE / -1);
        Serial.println(1 << 33);
        Serial.println(-256 >> 4);
        Serial.println(-256 >>> 28);
        Serial.println(0xf0f0 ^ 0x0ff0);
        Serial.println(~12345);
        Serial.println((5 & 3) | 8);
        Serial.println((1 < 2 ? 40 : 0) + (3 == 3 ? 2 : 0));
    }
}
