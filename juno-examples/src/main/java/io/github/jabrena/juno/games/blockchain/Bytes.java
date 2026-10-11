package io.github.jabrena.juno.games.blockchain;

/** Big-endian integer encoding shared by every packet, block and digest in the blockchain games. */
public final class Bytes {
    private Bytes() {
    }

    public static int unsigned(byte value) {
        return value & 0xff;
    }

    public static void writeInt(byte[] buffer, int offset, int value) {
        buffer[offset] = (byte) (value >>> 24);
        buffer[offset + 1] = (byte) (value >>> 16);
        buffer[offset + 2] = (byte) (value >>> 8);
        buffer[offset + 3] = (byte) value;
    }

    public static void writeLong(byte[] buffer, int offset, long value) {
        writeInt(buffer, offset, (int) (value >>> 32));
        writeInt(buffer, offset + 4, (int) value);
    }

    public static int readInt(byte[] buffer, int offset) {
        return unsigned(buffer[offset]) << 24
                | unsigned(buffer[offset + 1]) << 16
                | unsigned(buffer[offset + 2]) << 8
                | unsigned(buffer[offset + 3]);
    }

    public static long readLong(byte[] buffer, int offset) {
        long high = readInt(buffer, offset);
        long low = readInt(buffer, offset + 4) & 0xffffffffL;
        return high << 32 | low;
    }
}
