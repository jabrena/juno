package io.github.jabrena.juno.language;

import io.github.jabrena.juno.annotations.ArduinoUnoR3Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.wifi.ArduinoWifiShield;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;
import io.github.jabrena.juno.util.Random;
import io.github.jabrena.juno.util.UuidCodec;

import java.util.UUID;

/**
 * A non-cryptographic UUID for the classic UNO R3, which has no hardware entropy source for
 * {@link UUID#randomUUID()}. The UNO R3 runtime mixes pseudorandom values, timer jitter and samples from the unused A5
 * pin. The result is suitable for a best-effort node identity, but never for a secret, token or authentication value.
 * The example also recreates a UUID from the node-ID algorithm used by {@code NodeId}: the Wi-Fi Shield's local IP
 * address is mixed with the boot clock before filling the UUID bytes with Arduino's pseudorandom generator.
 */
@Board(ArduinoUnoR3Board.class)
public final class WeakUuid {
    private static final Wifi WIFI = new ArduinoWifiShield().getWifi();
    private static final Clock CLOCK = ArduinoUno.getClock();
    private static final int[] ADDRESS = new int[4];
    private static final byte[] UUID_BYTES = new byte[UuidCodec.BYTES];

    private WeakUuid() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        UUID uuid = UUID.randomUUID();
        Serial.println("weak UUID: " + uuid.toString());

        UUID addressSeededUuid = recreateUuid();
        Serial.println("address-seeded UUID: " + addressSeededUuid.toString());
    }

    /** Creates a weak UUID by mixing the Wi-Fi address, boot clock and Arduino's pseudorandom generator. */
    private static UUID recreateUuid() {
        WIFI.localIP(ADDRESS);
        int address = ADDRESS[0] << 24 | ADDRESS[1] << 16 | ADDRESS[2] << 8 | ADDRESS[3];
        Random.seed(CLOCK.micros() ^ address);
        for (int i = 0; i < UuidCodec.BYTES; i++) {
            UUID_BYTES[i] = (byte) (Random.nextInt(256) ^ CLOCK.micros());
        }
        UUID_BYTES[6] = (byte) (UUID_BYTES[6] & 0x0f | 0x40);
        UUID_BYTES[8] = (byte) (UUID_BYTES[8] & 0x3f | 0x80);
        return UuidCodec.fromBytes(UUID_BYTES, 0);
    }
}
