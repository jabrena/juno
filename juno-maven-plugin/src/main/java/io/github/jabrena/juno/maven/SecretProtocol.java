package io.github.jabrena.juno.maven;

import java.util.List;

/**
 * The secret names {@code juno:secret} may send, and the request lines the provisioning program understands. The names
 * are the ones {@code SecretProvisioner} maps to slots; the program still rejects any name it does not know.
 */
final class SecretProtocol {
    static final List<String> KNOWN_NAMES = List.of("wifi.ssid", "wifi.password", "mailbox.apikey");
    /** The longest value the chip stores, in bytes: one length byte precedes it in a 64-byte slot. */
    static final int MAX_VALUE_BYTES = 63;

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private SecretProtocol() {
    }

    static boolean isKnown(String name) {
        return KNOWN_NAMES.contains(name);
    }

    /** {@code SET <name> <hex>} for the bytes of {@code value}; the value is hex so no byte can end the line. */
    static String setRequest(String name, byte[] value) {
        StringBuilder request = new StringBuilder("SET ").append(name).append(' ');
        for (byte b : value) {
            request.append(HEX[(b >> 4) & 0xF]).append(HEX[b & 0xF]);
        }
        return request.toString();
    }

    static String clearRequest(String name) {
        return "CLEAR " + name;
    }

    static String listRequest() {
        return "LIST";
    }

    /**
     * The longest value, in characters, that the board can hold as a runtime {@code String}: the example builds the Wi-Fi
     * SSID and password as {@code String}s, and a longer one stops the board without a message.
     */
    static final int RUNTIME_STRING_CHARS = 31;

    /** The longest value the program may store under {@code name}, in bytes. */
    static int maxValueBytes(String name) {
        return "mailbox.apikey".equals(name) ? MAX_VALUE_BYTES : RUNTIME_STRING_CHARS;
    }

    /**
     * The bytes of a value typed at the prompt, checked before anything is uploaded or written: it must be printable ASCII
     * (the board builds {@code String}s from single bytes), not empty, and within {@link #maxValueBytes} for its name.
     */
    static byte[] valueBytes(String name, char[] typed) {
        int limit = maxValueBytes(name);
        if (typed.length == 0) {
            throw new ArduinoCliException("The value for " + name + " is empty. Nothing was uploaded or written.");
        }
        for (char c : typed) {
            if (c < 0x20 || c > 0x7E) {
                throw new ArduinoCliException("The value for " + name + " has a character outside printable ASCII. "
                        + "Nothing was uploaded or written.");
            }
        }
        if (typed.length > limit) {
            throw new ArduinoCliException("The value for " + name + " is " + typed.length + " characters; the board "
                    + "allows at most " + limit + " for it. Nothing was uploaded or written.");
        }
        byte[] bytes = new byte[typed.length];
        for (int i = 0; i < typed.length; i++) {
            bytes[i] = (byte) typed[i];
        }
        return bytes;
    }
}
