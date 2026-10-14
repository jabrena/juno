package io.github.jabrena.juno.api;

import java.util.UUID;

/** Converts UUID values to and from their 16-byte, most-significant-byte-first network representation. */
public final class UuidCodec {
    /** The number of bytes in a UUID's network representation. */
    public static final int BYTES = 16;

    private UuidCodec() {
    }

    /** Reads 16 bytes starting at {@code offset}, most-significant byte first. */
    public static UUID fromBytes(byte[] source, int offset) {
        return new UUID(readLong(source, offset), readLong(source, offset + 8));
    }

    /** Writes {@code value} into caller-owned storage starting at {@code offset}, most-significant byte first. */
    public static void toBytes(UUID value, byte[] target, int offset) {
        writeLong(value.getMostSignificantBits(), target, offset);
        writeLong(value.getLeastSignificantBits(), target, offset + 8);
    }

    private static long readLong(byte[] source, int offset) {
        long value = 0L;
        for (int index = 0; index < 8; index++) {
            value = (value << 8) | (source[offset + index] & 0xffL);
        }
        return value;
    }

    private static void writeLong(long value, byte[] target, int offset) {
        for (int index = 7; index >= 0; index--) {
            target[offset + index] = (byte) value;
            value >>>= 8;
        }
    }
}
