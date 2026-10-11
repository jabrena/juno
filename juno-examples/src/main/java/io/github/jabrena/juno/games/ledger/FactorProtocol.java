package io.github.jabrena.juno.games.ledger;

import io.github.jabrena.juno.games.blockchain.Bytes;
import io.github.jabrena.juno.games.blockchain.Factorizer;

/**
 * Fixed-size UDP wire protocol shared by {@link LeaderFlow} and {@link WorkerFlow}.
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

    static final int MAX_FACTORS = Factorizer.MAX_FACTORS;
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
        Bytes.writeInt(packet, NODE_ID, nodeId);
        Bytes.writeInt(packet, TASK_ID, taskId);
        Bytes.writeLong(packet, NUMBER, number);
    }

    /** Appends the factorization to a packet already started with {@link #write}. */
    static void writeFactors(byte[] packet, long[] primes, int[] exponents, int count) {
        packet[COUNT] = (byte) count;
        for (int i = 0; i < count; i++) {
            int offset = FACTORS + i * 9;
            Bytes.writeLong(packet, offset, primes[i]);
            packet[offset + 8] = (byte) exponents[i];
        }
    }

    static boolean valid(byte[] packet, int length) {
        return length == PACKET_SIZE
                && packet[0] == 'J' && packet[1] == 'U' && packet[2] == 'N' && packet[3] == 'O'
                && Bytes.unsigned(packet[4]) == VERSION && Bytes.unsigned(packet[5]) == FACTOR_SERVICE
                && Bytes.unsigned(packet[COUNT]) <= MAX_FACTORS;
    }

    static int type(byte[] packet) {
        return Bytes.unsigned(packet[TYPE]);
    }

    static int nodeId(byte[] packet) {
        return Bytes.readInt(packet, NODE_ID);
    }

    static int taskId(byte[] packet) {
        return Bytes.readInt(packet, TASK_ID);
    }

    static long number(byte[] packet) {
        return Bytes.readLong(packet, NUMBER);
    }

    /** Copies a RESULT's factors into the caller's arrays and returns how many there are. */
    static int readFactors(byte[] packet, long[] primes, int[] exponents) {
        int count = Bytes.unsigned(packet[COUNT]);
        for (int i = 0; i < count; i++) {
            int offset = FACTORS + i * 9;
            primes[i] = Bytes.readLong(packet, offset);
            exponents[i] = Bytes.unsigned(packet[offset + 8]);
        }
        return count;
    }
}
