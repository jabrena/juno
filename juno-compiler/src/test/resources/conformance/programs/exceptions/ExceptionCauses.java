package demo;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;

/**
 * Exception kinds and the causes the target raises itself: integer division by zero and array index failures, caught
 * by their own class and by a superclass, plus unchecked and checked exceptions thrown by the program.
 */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class, ArduinoUnoR3WiFiRev2Board.class,
        ArduinoNanoEsp32Board.class})
public final class ExceptionCauses {
    static final class ProbeException extends RuntimeException {
        ProbeException(String message) {
            super(message);
        }
    }

    static final class ProbeCheckedException extends Exception {
        ProbeCheckedException(String message) {
            super(message);
        }
    }

    private ExceptionCauses() {
    }

    static int divide(int numerator, int denominator) {
        return numerator / denominator;
    }

    static int readAt(int[] values, int index) {
        return values[index];
    }

    static void mayFail(int code) throws ProbeCheckedException {
        if (code < 0) {
            throw new ProbeCheckedException("checked " + code);
        }
    }

    public static void main(String[] args) {
        int[] values = {5, 6, 7};
        try {
            Serial.println(divide(9, 3));
            Serial.println(divide(1, 0));
            Serial.println("unreachable");
        } catch (ArithmeticException exception) {
            Serial.println("caught arithmetic");
        }
        try {
            Serial.println(readAt(values, 2));
            Serial.println(readAt(values, 3));
        } catch (IndexOutOfBoundsException exception) {
            Serial.println("caught index as superclass");
        }
        try {
            throw new ProbeException("probe");
        } catch (RuntimeException exception) {
            Serial.println("caught runtime");
        }
        try {
            mayFail(-2);
            Serial.println("unreachable");
        } catch (ProbeCheckedException exception) {
            Serial.println(exception.getMessage());
        }
        Serial.println("done");
    }
}
