package io.github.jabrena.juno.games.blockchain;

import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.serial.Serial;

/** Wi-Fi/UDP start-up and serial formatting shared by the blockchain games. */
public final class NodeIo {
    private static final Wifi WIFI = ArduinoUno.getWifi();

    /** UDP port every node listens on and broadcasts to. */
    public static final int PORT = 4210;

    private static final String HEX = "0123456789abcdef";

    private NodeIo() {
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
