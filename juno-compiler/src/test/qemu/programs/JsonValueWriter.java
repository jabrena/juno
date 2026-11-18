package demo;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.net.http.Json;
import io.github.jabrena.juno.hardware.net.http.JsonValue;
import io.github.jabrena.juno.hardware.net.http.JsonWriter;
import io.github.jabrena.juno.hardware.serial.Serial;

/** JsonValue navigation and JsonWriter output, compiled from Java like the program. */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoNanoEsp32Board.class})
public final class JsonValueWriter {
    private static final byte[] BODY = new byte[96];
    private static final byte[] TEXT = new byte[8];

    public static void main(String[] args) {
        JsonWriter writer = new JsonWriter(BODY);
        writer.beginObject()
                .name("uptime_ms").value(2000000000)
                .name("min").value(Long.MIN_VALUE)
                .name("moves").beginArray().value("e2e4").value("q\"\n").nullValue().value(false).endArray()
                .name("empty").beginObject().endObject()
                .endObject();
        Serial.println(writer.length());
        Serial.println(writer.overflowed());
        int checksum = 0;
        for (int i = 0; i < writer.length(); i++) {
            checksum = checksum * 31 + BODY[i];
        }
        Serial.println(checksum);

        JsonValue root = Json.parse(BODY, writer.length());
        Serial.println(root.type());
        Serial.println(root.size());
        Serial.println(root.get("uptime_ms").asInt());
        Serial.println(root.get("min").asLong() == Long.MIN_VALUE);
        JsonValue moves = root.get("moves");
        Serial.println(moves.size());
        int length = moves.get(1).copyString(TEXT);
        Serial.println(length);
        for (int i = 0; i < length; i++) {
            Serial.println(TEXT[i]);
        }
        Serial.println(moves.get(2).isNull());
        Serial.println(moves.get(3).asBoolean());
        Serial.println(moves.get(4).isMissing());
        Serial.println(root.get("empty").size());
        Serial.println(Json.parse(BODY, writer.length() - 1).type());

        writer.reset().beginArray().value(21.4).value(1.5E300).value(0.001).value(-0.0).value(100.0)
                .value(new char[] {'a', '\t', 'b'}, 3).endArray();
        String numbers = writer.toString();
        Serial.println(numbers);
        JsonValue parsed = Json.parse(numbers);
        Serial.println(parsed.get(0).toString());
        Serial.println(parsed.get(0).asDouble() == 21.4);
        Serial.println(parsed.get(5).asString());
        Serial.println(Json.parse("[2.3E2, 123.0, 1.5, \"q\\\"x\"]").get(0).asInt());
        Serial.println(Json.parse("[2.3E2, 123.0, 1.5, \"q\\\"x\"]").get(1).asLong());
        Serial.println(Json.parse("[2.3E2, 123.0, 1.5, \"q\\\"x\"]").get(2).asInt());
        Serial.println(Json.parse("[2.3E2, 123.0, 1.5, \"q\\\"x\"]").get(3).asString());
        Serial.println(Json.parse("[2.3E2, 123.0, 1.5, \"q\\\"x\"]").get(3).toString());
        Serial.println(writer.reset().value(Double.NaN).overflowed());

        Serial.println(writer.reset().beginArray().name("x").overflowed());
        Serial.println(new JsonWriter(TEXT).beginObject().name("long").value(1).endObject().overflowed());
    }
}
