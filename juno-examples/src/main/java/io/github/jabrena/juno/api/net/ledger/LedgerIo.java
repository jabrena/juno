package io.github.jabrena.juno.api.net.ledger;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.net.Udp;
import io.github.jabrena.juno.api.net.Wifi;

/** Wi-Fi/UDP start-up and serial formatting shared by the leader and the workers. */
final class LedgerIo {
    /** UDP port every node listens on and broadcasts to. */
    static final int PORT = 4210;

    private static final String HEX = "0123456789abcdef";

    private LedgerIo() {
    }

    /**
     * Joins Wi-Fi with build-time {@code JUNO_WIFI_SSID}/{@code JUNO_WIFI_PASSWORD} (UNO Q uses the
     * connection configured on its Linux side) and opens the UDP socket on {@link #PORT}.
     */
    static void connect() {
        Serial.println("Connecting Wi-Fi");
        Wifi.begin(System.getenv("JUNO_WIFI_SSID"), System.getenv("JUNO_WIFI_PASSWORD"));
        while (Wifi.status() != Wifi.STATUS_CONNECTED) {
            Delay.millis(500);
        }
        while (!Udp.listen(PORT)) {
            Serial.println("UDP port busy, retrying");
            Delay.millis(1000);
        }
        Serial.println("Listening on UDP port " + PORT);
    }

    /** Prints {@code p}, or {@code p^e} when {@code e > 1}, preceded by {@code " x "} unless it is the first factor. */
    static void printFactor(long prime, int exponent, boolean first) {
        if (!first) {
            Serial.print(" x ");
        }
        Serial.print(prime);
        if (exponent > 1) {
            Serial.print("^" + exponent);
        }
    }

    /** Prints {@code data[offset..offset + length)} in hex; {@code length} is a multiple of 4. */
    static void printHex(byte[] data, int offset, int length) {
        for (int i = offset; i < offset + length; i = i + 4) {
            Serial.print(hexByte(data[i]) + hexByte(data[i + 1]) + hexByte(data[i + 2]) + hexByte(data[i + 3]));
        }
    }

    private static String hexByte(byte value) {
        int unsigned = value & 0xff;
        return "" + HEX.charAt(unsigned >>> 4) + HEX.charAt(unsigned & 0xf);
    }
}
