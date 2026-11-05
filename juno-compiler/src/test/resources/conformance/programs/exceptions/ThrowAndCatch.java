package demo;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;

/**
 * The throw statement and catch clauses: the first matching clause wins, a subclass clause precedes its superclass,
 * multi-catch matches either alternative, a rethrown exception reaches the outer handler, and a throw inside a catch
 * replaces the original exception.
 */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class, ArduinoUnoR3WiFiRev2Board.class,
        ArduinoNanoEsp32Board.class})
public final class ThrowAndCatch {
    static final class StageException extends IllegalStateException {
        final int stage;

        StageException(int stage) {
            super("stage");
            this.stage = stage;
        }
    }

    private ThrowAndCatch() {
    }

    static int classify(int stage) {
        try {
            if (stage == 0) {
                throw new StageException(7);
            }
            if (stage == 1) {
                throw new IllegalArgumentException("argument");
            }
            if (stage == 2) {
                throw new IllegalStateException("state");
            }
            return stage;
        } catch (StageException exception) {
            return 100 + exception.stage;
        } catch (IllegalStateException | IllegalArgumentException exception) {
            return -1;
        }
    }

    static void rethrowing(int stage) {
        try {
            if (stage > 0) {
                throw new IllegalStateException("inner");
            }
        } catch (IllegalStateException exception) {
            Serial.println("inner caught");
            throw exception;
        }
    }

    static void replacing() {
        try {
            throw new IllegalArgumentException("first");
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("second");
        }
    }

    public static void main(String[] args) {
        Serial.println(classify(0));
        Serial.println(classify(1));
        Serial.println(classify(2));
        Serial.println(classify(3));
        try {
            rethrowing(1);
            Serial.println("unreachable");
        } catch (RuntimeException exception) {
            Serial.println("outer caught");
        }
        try {
            replacing();
            Serial.println("unreachable");
        } catch (IllegalArgumentException exception) {
            Serial.println("wrong handler");
        } catch (IllegalStateException exception) {
            Serial.println("replaced");
        }
        Serial.println("done");
    }
}
