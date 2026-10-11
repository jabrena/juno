package io.github.jabrena.juno.api.net.ledger;

/**
 * Fixed-size UDP packets exchanged by {@link ChainNode}s, big-endian:
 *
 * <pre>
 *  0..3   "JUNO"
 *  4      version (1)
 *  5      service (3 = chain)
 *  6      type: BLOCK, GET_BLOCK or GET_TIP
 *  7      reserved
 *  8..11  BLOCK: height the sender gives the block
 * 12..19  BLOCK: cumulative work the sender gives it
 * 20..23  BLOCK: start time of its retarget period
 * 24..    BLOCK: the block ({@link BlockFormat}); GET_BLOCK: the 32-byte hash wanted
 * </pre>
 *
 * The three metadata fields are what the sender computed. A receiver that holds the parent
 * recomputes them; only a node adopting a checkpoint relies on them.
 */
final class ChainProtocol {
    static final int BLOCK = 1;
    static final int GET_BLOCK = 2;
    static final int GET_TIP = 3;

    static final int BODY = 24;
    static final int PACKET_SIZE = BODY + BlockFormat.MAX_SIZE;

    private static final int TYPE = 6;
    private static final int HEIGHT = 8;
    private static final int WORK = 12;
    private static final int PERIOD_START = 20;
    private static final int VERSION = 1;
    private static final int CHAIN_SERVICE = 3;

    private ChainProtocol() {
    }

    static void writeRequest(byte[] packet, int type) {
        for (int i = 0; i < PACKET_SIZE; i++) {
            packet[i] = 0;
        }
        packet[0] = 'J';
        packet[1] = 'U';
        packet[2] = 'N';
        packet[3] = 'O';
        packet[4] = VERSION;
        packet[5] = CHAIN_SERVICE;
        packet[TYPE] = (byte) type;
    }

    /** A BLOCK packet carrying {@code pool}'s block in {@code slot}. */
    static void writeBlock(byte[] packet, BlockPool pool, int slot) {
        writeRequest(packet, BLOCK);
        FactorProtocol.writeInt(packet, HEIGHT, pool.height(slot));
        FactorProtocol.writeLong(packet, WORK, pool.work(slot));
        FactorProtocol.writeInt(packet, PERIOD_START, pool.periodStart(slot));
        int base = slot * BlockFormat.MAX_SIZE;
        BlockFormat.copy(pool.blocks(), base, packet, BODY, BlockFormat.size(pool.blocks(), base));
    }

    static boolean valid(byte[] packet, int length) {
        return length == PACKET_SIZE
                && packet[0] == 'J' && packet[1] == 'U' && packet[2] == 'N' && packet[3] == 'O'
                && FactorProtocol.unsigned(packet[4]) == VERSION
                && FactorProtocol.unsigned(packet[5]) == CHAIN_SERVICE;
    }

    static int type(byte[] packet) {
        return FactorProtocol.unsigned(packet[TYPE]);
    }

    static int height(byte[] packet) {
        return FactorProtocol.readInt(packet, HEIGHT);
    }

    static long work(byte[] packet) {
        return FactorProtocol.readLong(packet, WORK);
    }

    static int periodStart(byte[] packet) {
        return FactorProtocol.readInt(packet, PERIOD_START);
    }
}
