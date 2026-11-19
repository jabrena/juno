package demo;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.AiThinkerEsp32CamBoard;
import io.github.jabrena.juno.annotations.M5StackCardputerBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;

/** Boolean operators, short-circuit evaluation, comparisons, and conditional expressions. */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoNanoEsp32Board.class, M5StackCardputerBoard.class, AiThinkerEsp32CamBoard.class})
public final class BooleanExpressions {
    private BooleanExpressions() {
    }

    public static void main(String[] args) {
        boolean left = true;
        boolean right = false;
        Serial.println(left && !right);
        Serial.println(left || right);
        Serial.println(3 < 4);
        Serial.println(3 >= 4);
        Serial.println(3 == 3);
        Serial.println(3 != 4);
        Serial.println(left ? 42 : 0);
        Serial.println(right ? 0 : 42);
    }
}
