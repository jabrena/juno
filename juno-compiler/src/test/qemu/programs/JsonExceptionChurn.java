package demo;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.AiThinkerEsp32CamBoard;
import io.github.jabrena.juno.annotations.M5StackCardputerBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.net.http.Json;
import io.github.jabrena.juno.hardware.net.http.JsonValue;
import io.github.jabrena.juno.hardware.serial.Serial;

/**
 * JEP 540's failure channel on the board. A JSON value of the wrong type throws a new exception, across a
 * method boundary, thousands of times: far more exception and view objects than the arena holds at once, so the
 * program only finishes if the collector reclaims each caught exception (and each view) once nothing refers to it.
 */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoNanoEsp32Board.class, M5StackCardputerBoard.class, AiThinkerEsp32CamBoard.class})
public final class JsonExceptionChurn {
    private static final String DOCUMENT = "{\"rating\":1712,\"fen\":\"8/8 w\",\"plays\":null}";
    private static final String[] NAMES = {"rating", "fen", "plays", "missing"};

    static final class JsonValueException extends RuntimeException {
        JsonValueException(String message) {
            super(message);
        }
    }

    /** JEP 540's {@code asInt()}: throws instead of returning a zero value. */
    static int requireInt(JsonValue value) {
        if (value.isMissing()) {
            throw new JsonValueException("missing member");
        }
        if (!value.isNumber()) {
            throw new JsonValueException("not a number");
        }
        return value.asInt();
    }

    static int read(JsonValue root, String name) {
        return requireInt(root.get(name));
    }

    public static void main(String[] args) {
        JsonValue root = Json.parse(DOCUMENT);
        int sum = 0;
        int notNumbers = 0;
        int missing = 0;
        for (int i = 0; i < 4000; i++) {
            try {
                sum += read(root, NAMES[i % NAMES.length]) % 1000;
            } catch (JsonValueException e) {
                if (e.getMessage().equals("missing member")) {
                    missing++;
                } else {
                    notNumbers++;
                }
            }
        }
        Serial.println(sum);
        Serial.println(notNumbers);
        Serial.println(missing);
        Serial.println(root.get("rating").asInt());
    }
}
