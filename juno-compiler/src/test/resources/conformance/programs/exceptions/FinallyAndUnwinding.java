package demo;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;

/**
 * Run-time handling of exceptions: a finally block runs on normal completion, on return, and while an exception
 * unwinds through several calls; nested finally blocks run innermost first, before the outer handler sees the
 * exception.
 */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class, ArduinoUnoR3WiFiRev2Board.class,
        ArduinoNanoEsp32Board.class})
public final class FinallyAndUnwinding {
    private FinallyAndUnwinding() {
    }

    static int withReturn() {
        try {
            Serial.println("body");
            return 1;
        } finally {
            Serial.println("finally after return");
        }
    }

    static void deepest() {
        throw new IllegalStateException("deepest");
    }

    static void middle() {
        try {
            deepest();
        } finally {
            Serial.println("middle finally");
        }
    }

    static void top() {
        try {
            middle();
        } finally {
            Serial.println("top finally");
        }
    }

    public static void main(String[] args) {
        int total = 5;
        try {
            total += 1;
        } finally {
            total *= 2;
        }
        Serial.println(total);
        Serial.println(withReturn());
        try {
            top();
            Serial.println("unreachable");
        } catch (IllegalStateException exception) {
            Serial.println("caught after unwinding");
        }
        try {
            try {
                throw new IllegalArgumentException("inner");
            } finally {
                Serial.println("inner finally");
            }
        } catch (IllegalArgumentException exception) {
            Serial.println("outer catch");
        } finally {
            Serial.println("outer finally");
        }
        Serial.println("done");
    }
}
