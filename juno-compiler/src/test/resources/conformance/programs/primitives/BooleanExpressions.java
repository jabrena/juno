package demo;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;

/** Boolean operators, short-circuit evaluation, comparisons, and conditional expressions. */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class, ArduinoUnoR3WiFiRev2Board.class,
        ArduinoNanoEsp32Board.class})
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
