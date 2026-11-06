package io.github.jabrena.juno.remote.lego.poweredup;

/** Byte-level reads of LEGO Wireless Protocol messages: unsigned bytes and little-endian values. */
final class LegoBytes {
    private LegoBytes() {
    }

    /** A byte as an unsigned value, 0 to 255. */
    static int unsigned(byte value) {
        return value & 0xFF;
    }

    /**
     * A signed little-endian integer of {@code width} bytes (1, 2 or 4) starting at {@code offset}; any other width
     * reads one signed byte.
     */
    static int valueAt(byte[] bytes, int offset, int width) {
        return switch (width) {
            case 4 -> unsigned(bytes[offset]) | (unsigned(bytes[offset + 1]) << 8)
                    | (unsigned(bytes[offset + 2]) << 16) | (unsigned(bytes[offset + 3]) << 24);
            case 2 -> (short) (unsigned(bytes[offset]) | (unsigned(bytes[offset + 1]) << 8));
            default -> (int) bytes[offset];
        };
    }
}
