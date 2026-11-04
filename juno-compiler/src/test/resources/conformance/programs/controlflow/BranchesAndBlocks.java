package demo;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;

/** if, else if, a dangling else, nested blocks with sibling-scoped names, an empty statement, and a labeled block. */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class, ArduinoUnoR3WiFiRev2Board.class,
        ArduinoNanoEsp32Board.class})
public final class BranchesAndBlocks {
    private BranchesAndBlocks() {
    }

    static int classify(int n) {
        if (n < 0) {
            return -1;
        } else if (n == 0) {
            return 0;
        } else {
            return 1;
        }
    }

    public static void main(String[] args) {
        Serial.println(classify(-5));
        Serial.println(classify(0));
        Serial.println(classify(7));
        int a = 1;
        if (a > 0)
            if (a > 5) {
                Serial.println(100);
            } else {
                Serial.println(200);
            }
        {
            int inner = 10;
            Serial.println(inner + a);
        }
        {
            int inner = 20;
            Serial.println(inner);
        }
        ;
        found: {
            if (a == 1) {
                break found;
            }
            Serial.println(999);
        }
        Serial.println(42);
    }
}
