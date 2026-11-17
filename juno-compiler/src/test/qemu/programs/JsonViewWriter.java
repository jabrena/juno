package demo;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.net.http.Json;
import io.github.jabrena.juno.hardware.net.http.JsonView;
import io.github.jabrena.juno.hardware.net.http.JsonWriter;
import io.github.jabrena.juno.hardware.serial.Serial;

/** JsonView navigation and JsonWriter output, compiled from Java like the program. */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class, ArduinoNanoEsp32Board.class})
public final class JsonViewWriter {
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

        JsonView root = Json.view(BODY, writer.length());
        Serial.println(root.type());
        Serial.println(root.size());
        Serial.println(root.get("uptime_ms").asInt());
        Serial.println(root.get("min").asLong() == Long.MIN_VALUE);
        JsonView moves = root.get("moves");
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
        Serial.println(Json.view(BODY, writer.length() - 1).type());

        Serial.println(writer.reset().beginArray().name("x").overflowed());
        Serial.println(new JsonWriter(TEXT).beginObject().name("long").value(1).endObject().overflowed());
    }
}
