package io.github.jabrena.juno.maven;

/**
 * The request lines exchanged by {@code juno:secret} and the board-side provisioning program. Secret names deliberately
 * do not live in the Maven plugin: the board stores each name next to its value, so any valid name can be written.
 */
final class SecretProtocol {
    /** The bytes one stored secret takes on the board: a length byte, the name, then the value. */
    static final int RECORD_BYTES = 63;

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private SecretProtocol() {
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

    static String clearAllRequest() {
        return "CLEAR ALL";
    }

    static String listRequest() {
        return "LIST";
    }

    /** Checks the name syntax; the board accepts any name that passes it. */
    static void validateName(String name) {
        if (name.isEmpty() || name.length() > 31) {
            throw new ArduinoCliException("Secret name must contain 1 to 31 characters. Nothing was uploaded or written.");
        }
        for (int i = 0; i < name.length(); i++) {
            char character = name.charAt(i);
            if (character <= 0x20 || character > 0x7E) {
                throw new ArduinoCliException("Secret name must contain printable non-space ASCII. Nothing was uploaded or written.");
            }
        }
    }

    /**
     * The bytes of a value typed at the prompt, checked before anything is uploaded or written: it must be printable ASCII
     * (the board builds {@code String}s from single bytes), not empty, and short enough to share a record with its name.
     */
    static byte[] valueBytes(String name, char[] typed) {
        int maxValueBytes = maxValueBytes(name);
        if (typed.length == 0) {
            throw new ArduinoCliException("The value for " + name + " is empty. Nothing was uploaded or written.");
        }
        for (char c : typed) {
            if (c < 0x20 || c > 0x7E) {
                throw new ArduinoCliException("The value for " + name + " has a character outside printable ASCII. "
                        + "Nothing was uploaded or written.");
            }
        }
        if (typed.length > maxValueBytes) {
            throw new ArduinoCliException("The value for " + name + " is " + typed.length + " characters; the board "
                    + "allows at most " + maxValueBytes + " next to that name. Nothing was uploaded or written.");
        }
        byte[] bytes = new byte[typed.length];
        for (int i = 0; i < typed.length; i++) {
            bytes[i] = (byte) typed[i];
        }
        return bytes;
    }

    /** The longest value the board stores under {@code name}: what is left of a record after its name. */
    static int maxValueBytes(String name) {
        return RECORD_BYTES - 1 - name.length();
    }
}
