package io.github.jabrena.juno.games.blockchain;

import io.github.jabrena.juno.hardware.ArduinoOne;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.net.udp.Udp;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/** Wi-Fi/UDP start-up and serial formatting shared by the blockchain games. */
public final class NodeIo {
    private static final Wifi WIFI = ArduinoOne.getWifi();

    /** UDP port every node listens on and broadcasts to. */
    public static final int PORT = 4210;

    private static final String HEX = "0123456789abcdef";

    private NodeIo() {
    }

    /**
     * Joins Wi-Fi with build-time {@code JUNO_WIFI_SSID}/{@code JUNO_WIFI_PASSWORD} (UNO Q uses the
     * connection configured on its Linux side) and opens the UDP socket on {@link #PORT}.
     */
    public static void connect() {
        Serial.println("Connecting Wi-Fi");
        WIFI.begin(System.getenv("JUNO_WIFI_SSID"), System.getenv("JUNO_WIFI_PASSWORD"));
        while (WIFI.status() != Wifi.STATUS_CONNECTED) {
            Delay.millis(500);
        }
        while (!Udp.listen(PORT)) {
            Serial.println("UDP port busy, retrying");
            Delay.millis(1000);
        }
        Serial.println("Listening on UDP port " + PORT);
    }

    /** Prints {@code p}, or {@code p^e} when {@code e > 1}, preceded by {@code " x "} unless it is the first factor. */
    public static void printFactor(long prime, int exponent, boolean first) {
        if (!first) {
            Serial.print(" x ");
        }
        Serial.print(prime);
        if (exponent > 1) {
            Serial.print("^" + exponent);
        }
    }

    /** Prints {@code data[offset..offset + length)} in hex; {@code length} is a multiple of 4. */
    public static void printHex(byte[] data, int offset, int length) {
        for (int i = offset; i < offset + length; i = i + 4) {
            Serial.print(hexWord(data, i));
        }
    }

    /** The four bytes at {@code data[offset..offset + 4)} as eight hex digits. */
    public static String hexWord(byte[] data, int offset) {
        return hexByte(data[offset]) + hexByte(data[offset + 1]) + hexByte(data[offset + 2]) + hexByte(data[offset + 3]);
    }

    private static String hexByte(byte value) {
        int unsigned = value & 0xff;
        return "" + HEX.charAt(unsigned >>> 4) + HEX.charAt(unsigned & 0xf);
    }
}
