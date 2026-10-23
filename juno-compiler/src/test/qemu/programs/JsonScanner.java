package demo;

import io.github.jabrena.juno.annotations.ArduinoOneQBoard;
import io.github.jabrena.juno.annotations.ArduinoOneR4WiFiBoard;
import io.github.jabrena.juno.annotations.ArduinoOneR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.net.http.Json;

/** Json's scanner, compiled from Java like the program: paths, validation, every getter and UTF-8 decoding. */
@Board({ArduinoOneR4WiFiBoard.class, ArduinoOneQBoard.class, ArduinoOneR3WiFiRev2Board.class})
public final class JsonScanner {
    private static final byte[] DOCUMENT = new byte[256];

    public static void main(String[] args) {
        int length = load("{\"users\":[{\"id\":1},{\"id\":2147483648,\"active\":false,\"name\":\"A\\n\\u00e9\\uD83D\\uDE00\"}],");
        length = append(length, "\"numbers\":[-12,1.25e2,0.1,-0.0,1e400,1e-400],\"nothing\":null,");
        length = append(length, "\"minimum\":-9223372036854775808,\"tooLarge\":9223372036854775808}");
        Serial.println(Json.type(DOCUMENT, length, "users"));
        Serial.println(Json.arraySize(DOCUMENT, length, "users"));
        Serial.println(Json.getInt(DOCUMENT, length, "users[0].id"));
        Serial.println(Json.getInt(DOCUMENT, length, "users[1].id"));
        Serial.println(Json.getLong(DOCUMENT, length, "users[1].id"));
        Serial.println(Json.type(DOCUMENT, length, "users[1].active"));
        Serial.println(Json.getBool(DOCUMENT, length, "users[1].active"));
        Serial.println(Json.getInt(DOCUMENT, length, "numbers[0]"));
        Serial.println(Json.getInt(DOCUMENT, length, "numbers[1]"));
        Serial.println(Json.getDouble(DOCUMENT, length, "numbers[1]") == 125.0);
        Serial.println(Json.getDouble(DOCUMENT, length, "numbers[2]") == 0.1);
        Serial.println(1.0 / Json.getDouble(DOCUMENT, length, "numbers[3]") < 0.0);
        Serial.println(Json.getDouble(DOCUMENT, length, "numbers[4]") == 0.0);
        Serial.println(Json.getDouble(DOCUMENT, length, "numbers[5]") == 0.0);
        Serial.println(Json.type(DOCUMENT, length, "nothing"));
        Serial.println(Json.type(DOCUMENT, length, "missing"));
        Serial.println(Json.type(DOCUMENT, length, "users[7]"));
        Serial.println(Json.type(DOCUMENT, length, "users[01]"));
        Serial.println(Json.type(DOCUMENT, length, "users."));
        Serial.println(Json.getLong(DOCUMENT, length, "minimum") == Long.MIN_VALUE);
        Serial.println(Json.getLong(DOCUMENT, length, "tooLarge"));

        byte[] out = new byte[16];
        int written = Json.getString(DOCUMENT, length, "users[1].name", out, out.length);
        Serial.println(written);
        for (int i = 0; i < written; i++) {
            Serial.println(out[i] & 0xFF);
        }
        Serial.println(Json.getString(DOCUMENT, length, "users[1].name", out, 4));

        int root = load("  [true, 3] ");
        Serial.println(Json.type(DOCUMENT, root, "[0]"));
        Serial.println(Json.getInt(DOCUMENT, root, "[1]"));
        Serial.println(Json.type(DOCUMENT, load("{\"x\":[1,2"), "x"));
        Serial.println(Json.type(DOCUMENT, load("{\"x\":01}"), "x"));
        Serial.println(Json.type(DOCUMENT, load("{\"x\":[1,]}"), "x"));
    }

    private static int load(String text) {
        return append(0, text);
    }

    private static int append(int start, String text) {
        for (int i = 0; i < text.length(); i++) {
            DOCUMENT[start + i] = (byte) text.charAt(i);
        }
        return start + text.length();
    }
}
