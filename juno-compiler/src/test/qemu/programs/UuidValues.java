package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR3;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.util.UuidCodec;
import io.github.jabrena.juno.hardware.serial.Serial;
import java.util.UUID;

/** UUID construction, value semantics, canonical text, parsing and 16-byte network representation. */
@Board({ArduinoUnoR3.class, ArduinoUnoR3WiFiRev2.class, ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class UuidValues {
    private UuidValues() {
    }

    public static void main() {
        UUID value = UUID.fromString("123E4567-E89B-12D3-A456-426614174000");
        UUID copy = new UUID(value.getMostSignificantBits(), value.getLeastSignificantBits());
        Serial.println(value.toString());
        Serial.println(value.version());
        Serial.println(value.variant());
        Serial.println(value.equals(copy));
        Serial.println(value.equals("not a uuid"));
        Serial.println(value.hashCode() == copy.hashCode());
        Serial.println(value.compareTo(copy));
        Serial.println(value.compareTo(new UUID(value.getMostSignificantBits(), value.getLeastSignificantBits() + 1)));

        byte[] bytes = new byte[18];
        UuidCodec.toBytes(value, bytes, 1);
        UUID decoded = UuidCodec.fromBytes(bytes, 1);
        Serial.println(decoded.toString());
        Serial.println(bytes[1] & 0xff);
        Serial.println(bytes[16] & 0xff);

        try {
            UUID.fromString("not-a-canonical-uuid");
        } catch (IllegalArgumentException expected) {
            Serial.println("invalid");
        }
    }
}
