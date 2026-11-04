package demo;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;

/** Switch expressions: arrow and colon forms, multi-label cases, yield from a nested block, and use as an operand. */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class, ArduinoUnoR3WiFiRev2Board.class,
        ArduinoNanoEsp32Board.class})
public final class SwitchExpression {
    private SwitchExpression() {
    }

    static int score(int n) {
        return switch (n) {
            case 0, 1 -> 10;
            case 2 -> {
                int t = n * 7;
                yield t;
            }
            case 1000000 -> -5;
            default -> -1;
        };
    }

    static int colon(int n) {
        int v = switch (n) {
            case 3:
                yield 33;
            default:
                yield 0;
        };
        return v;
    }

    static int nestedYield(int n) {
        return switch (n) {
            default -> {
                if (n > 10) {
                    yield 1;
                }
                int inner = 0;
                for (int i = 0; i < n; i++) {
                    inner += i;
                }
                yield inner;
            }
        };
    }

    public static void main(String[] args) {
        Serial.println(score(0));
        Serial.println(score(1));
        Serial.println(score(2));
        Serial.println(score(1000000));
        Serial.println(score(9));
        Serial.println(colon(3));
        Serial.println(colon(4));
        Serial.println(nestedYield(20));
        Serial.println(nestedYield(4));
        Serial.println(1 + switch (2) {
            case 2 -> 40;
            default -> 0;
        });
    }
}
