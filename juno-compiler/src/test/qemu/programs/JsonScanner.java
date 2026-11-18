package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.net.http.Json;
import io.github.jabrena.juno.hardware.net.http.JsonValue;

/** Json's scanner, compiled from Java like the program: views, validation, every conversion and UTF-8 decoding. */
@Board(ArduinoUnoR4WiFiBoard.class)
public final class JsonScanner {
    private static final byte[] DOCUMENT = new byte[256];

    public static void main(String[] args) {
        int length = load("{\"users\":[{\"id\":1},{\"id\":2147483648,\"active\":false,\"name\":\"A\\n\\u00e9\\uD83D\\uDE00\"}],");
        length = append(length, "\"numbers\":[-12,1.25e2,0.1,-0.0,1e400,1e-400],\"nothing\":null,");
        length = append(length, "\"minimum\":-9223372036854775808,\"tooLarge\":9223372036854775808}");
        JsonValue doc = Json.parse(DOCUMENT, length);
        Serial.println(doc.get("users").type());
        Serial.println(doc.get("users").size());
        Serial.println(doc.get("users").get(0).get("id").asInt());
        Serial.println(doc.get("users").get(1).get("id").asInt());
        Serial.println(doc.get("users").get(1).get("id").asLong());
        Serial.println(doc.get("users").get(1).get("active").type());
        Serial.println(doc.get("users").get(1).get("active").asBoolean());
        Serial.println(doc.get("numbers").get(0).asInt());
        Serial.println(doc.get("numbers").get(1).asInt());
        Serial.println(doc.get("numbers").get(1).asDouble() == 125.0);
        Serial.println(doc.get("numbers").get(2).asDouble() == 0.1);
        Serial.println(1.0 / doc.get("numbers").get(3).asDouble() < 0.0);
        Serial.println(doc.get("numbers").get(4).asDouble() == 0.0);
        Serial.println(doc.get("numbers").get(5).asDouble() == 0.0);
        Serial.println(doc.get("nothing").type());
        Serial.println(doc.get("missing").type());
        Serial.println(doc.get("users").get(7).type());
        Serial.println(doc.get("nothing").get("x").type());
        Serial.println(doc.size());
        Serial.println(doc.get("minimum").asLong() == Long.MIN_VALUE);
        Serial.println(doc.get("tooLarge").asLong());

        byte[] out = new byte[16];
        int written = doc.get("users").get(1).get("name").copyString(out);
        Serial.println(written);
        for (int i = 0; i < written; i++) {
            Serial.println(out[i] & 0xFF);
        }
        Serial.println(doc.get("users").get(1).get("name").copyString(out, 4));

        int root = load("  [true, 3] ");
        Serial.println(Json.parse(DOCUMENT, root).get(0).type());
        Serial.println(Json.parse(DOCUMENT, root).get(1).asInt());
        Serial.println(Json.parse(DOCUMENT, load("{\"x\":[1,2")).get("x").type());
        Serial.println(Json.parse(DOCUMENT, load("{\"x\":01}")).get("x").type());
        Serial.println(Json.parse(DOCUMENT, load("{\"x\":[1,]}")).get("x").type());
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
