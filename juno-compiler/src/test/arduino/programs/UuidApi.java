package demo;

import io.github.jabrena.juno.api.util.UuidCodec;
import io.github.jabrena.juno.api.hardware.serial.Serial;
import java.util.UUID;

/** The portable UUID subset and its caller-buffer network representation. */
public final class UuidApi {
    private UuidApi() {
    }

    public static void main(String[] args) {
        UUID value = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        byte[] bytes = new byte[UuidCodec.BYTES];
        UuidCodec.toBytes(value, bytes, 0);
        UUID copy = UuidCodec.fromBytes(bytes, 0);
        Serial.println(copy.toString());
        Serial.println(copy.version());
        Serial.println(copy.variant());
        Serial.println(copy.equals(value));
        Serial.println(copy.compareTo(value));
    }
}
