package io.github.jabrena.juno.api.net.ledger;

/**
 * Fixed-size UDP wire protocol shared by {@link FactorLedgerLeader} and {@link FactorWorker}.
 *
 * <p>Every packet is {@link #PACKET_SIZE} bytes, big-endian:
 *
 * <pre>
 *  0..3   "JUNO"
 *  4      version (1)
 *  5      service (2 = factor ledger)
 *  6      type: JOIN, TASK or RESULT
 *  7      number of (prime, exponent) entries that follow (RESULT only)
 *  8..11  node id of the sender
 * 12..15  task id (TASK, RESULT)
 * 16..23  number to factor (TASK, RESULT)
 * 24..    MAX_FACTORS entries of 8-byte prime + 1-byte exponent (RESULT only)
 * </pre>
 *
 * A worker broadcasts {@code JOIN} until a leader answers with a {@code TASK}; it then replies with
 * a {@code RESULT}, repeating it until the leader's next {@code TASK} acknowledges it. The format is
 * plain bytes on purpose, so a node Juno cannot compile for (an UNO R3 WiFi, a laptop) can join with
 * a hand-written client.
 */
final class FactorProtocol {
    static final int JOIN = 1;
    static final int TASK = 2;
    static final int RESULT = 3;

    /** A number below 2^62 has at most 15 distinct prime factors (the product of the first 16 primes exceeds it). */
    static final int MAX_FACTORS = 15;
    static final int PACKET_SIZE = 24 + MAX_FACTORS * 9;

    static final int TYPE = 6;
    static final int COUNT = 7;
    private static final int NODE_ID = 8;
    private static final int TASK_ID = 12;
    private static final int NUMBER = 16;
    private static final int FACTORS = 24;

    private static final int VERSION = 1;
    private static final int FACTOR_SERVICE = 2;

    private FactorProtocol() {
    }

    static void write(byte[] packet, int type, int nodeId, int taskId, long number) {
        for (int i = 0; i < PACKET_SIZE; i++) {
            packet[i] = 0;
        }
        packet[0] = 'J';
        packet[1] = 'U';
        packet[2] = 'N';
        packet[3] = 'O';
        packet[4] = VERSION;
        packet[5] = FACTOR_SERVICE;
        packet[TYPE] = (byte) type;
        writeInt(packet, NODE_ID, nodeId);
        writeInt(packet, TASK_ID, taskId);
        writeLong(packet, NUMBER, number);
    }

    /** Appends the factorization to a packet already started with {@link #write}. */
    static void writeFactors(byte[] packet, long[] primes, int[] exponents, int count) {
        packet[COUNT] = (byte) count;
        for (int i = 0; i < count; i++) {
            int offset = FACTORS + i * 9;
            writeLong(packet, offset, primes[i]);
            packet[offset + 8] = (byte) exponents[i];
        }
    }

    static boolean valid(byte[] packet, int length) {
        return length == PACKET_SIZE
                && packet[0] == 'J' && packet[1] == 'U' && packet[2] == 'N' && packet[3] == 'O'
                && unsigned(packet[4]) == VERSION && unsigned(packet[5]) == FACTOR_SERVICE
                && unsigned(packet[COUNT]) <= MAX_FACTORS;
    }

    static int type(byte[] packet) {
        return unsigned(packet[TYPE]);
    }

    static int nodeId(byte[] packet) {
        return readInt(packet, NODE_ID);
    }

    static int taskId(byte[] packet) {
        return readInt(packet, TASK_ID);
    }

    static long number(byte[] packet) {
        return readLong(packet, NUMBER);
    }

    /** Copies a RESULT's factors into the caller's arrays and returns how many there are. */
    static int readFactors(byte[] packet, long[] primes, int[] exponents) {
        int count = unsigned(packet[COUNT]);
        for (int i = 0; i < count; i++) {
            int offset = FACTORS + i * 9;
            primes[i] = readLong(packet, offset);
            exponents[i] = unsigned(packet[offset + 8]);
        }
        return count;
    }

    static int unsigned(byte value) {
        return value & 0xff;
    }

    static void writeInt(byte[] buffer, int offset, int value) {
        buffer[offset] = (byte) (value >>> 24);
        buffer[offset + 1] = (byte) (value >>> 16);
        buffer[offset + 2] = (byte) (value >>> 8);
        buffer[offset + 3] = (byte) value;
    }

    static void writeLong(byte[] buffer, int offset, long value) {
        writeInt(buffer, offset, (int) (value >>> 32));
        writeInt(buffer, offset + 4, (int) value);
    }

    static int readInt(byte[] buffer, int offset) {
        return unsigned(buffer[offset]) << 24
                | unsigned(buffer[offset + 1]) << 16
                | unsigned(buffer[offset + 2]) << 8
                | unsigned(buffer[offset + 3]);
    }

    static long readLong(byte[] buffer, int offset) {
        long high = readInt(buffer, offset);
        long low = readInt(buffer, offset + 4) & 0xffffffffL;
        return high << 32 | low;
    }
}
