package io.github.jabrena.juno.usecases.miner;

import io.github.jabrena.juno.games.blockchain.Bytes;

/**
 * Fixed-size UDP packets exchanged by {@link MinerGame} nodes, big-endian:
 *
 * <pre>
 *  0..3   "JUNO"
 *  4      version (1)
 *  5      service (3 = chain)
 *  6      type: BLOCK, GET_BLOCK, GET_TIP or HELLO
 *  7      HELLO: flags ({@link #MINING}); otherwise reserved
 *  8..11  BLOCK: height the sender gives the block; HELLO: the height of the sender's tip
 * 12..19  BLOCK: cumulative work the sender gives it; HELLO: the work of the sender's tip
 * 20..23  BLOCK: start time of its retarget period
 * 24..    BLOCK: the block ({@link BlockFormat}); GET_BLOCK: the 32-byte hash wanted;
 *         HELLO: the sender's 16-byte node UUID ({@link NodeId})
 * </pre>
 *
 * The three metadata fields are what the sender computed. A receiver that holds the parent
 * recomputes them; only a node adopting a checkpoint relies on them.
 *
 * <p>HELLO is the heartbeat each node broadcasts every few seconds: its UUID and the height and cumulative
 * work of its tip. Nodes that predate it ignore the type; the packet keeps the common size so they still accept
 * it as well-formed before skipping it.
 */
final class ChainProtocol {
    static final int BLOCK = 1;
    static final int GET_BLOCK = 2;
    static final int GET_TIP = 3;
    static final int HELLO = 4;

    /** HELLO flag: the sender is mining, as opposed to waiting for a heavier chain it has heard of. */
    static final int MINING = 1;

    static final int BODY = 24;
    static final int PACKET_SIZE = BODY + BlockFormat.MAX_SIZE;
    /** Where a HELLO packet carries its sender's UUID. */
    static final int HELLO_UUID = BODY;

    private static final int TYPE = 6;
    private static final int HEIGHT = 8;
    private static final int WORK = 12;
    private static final int PERIOD_START = 20;
    private static final int FLAGS = 7;
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
        Bytes.writeInt(packet, HEIGHT, pool.height(slot));
        Bytes.writeLong(packet, WORK, pool.work(slot));
        Bytes.writeInt(packet, PERIOD_START, pool.periodStart(slot));
        int base = slot * BlockFormat.MAX_SIZE;
        BlockFormat.copy(pool.blocks(), base, packet, BODY, BlockFormat.size(pool.blocks(), base));
    }

    /**
     * A HELLO packet: the node with {@code uuid} says it is alive, with the {@code height} and {@code work} of its
     * tip. Read the tip back with {@link #height} and {@link #work}, and the sender with {@link #HELLO_UUID}.
     */
    static void writeHello(byte[] packet, byte[] uuid, int height, long work, int flags) {
        writeRequest(packet, HELLO);
        packet[FLAGS] = (byte) flags;
        Bytes.writeInt(packet, HEIGHT, height);
        Bytes.writeLong(packet, WORK, work);
        BlockFormat.copy(uuid, 0, packet, HELLO_UUID, NodeId.SIZE);
    }

    static int helloFlags(byte[] packet) {
        return Bytes.unsigned(packet[FLAGS]);
    }

    static boolean valid(byte[] packet, int length) {
        return length == PACKET_SIZE
                && packet[0] == 'J' && packet[1] == 'U' && packet[2] == 'N' && packet[3] == 'O'
                && Bytes.unsigned(packet[4]) == VERSION
                && Bytes.unsigned(packet[5]) == CHAIN_SERVICE;
    }

    static int type(byte[] packet) {
        return Bytes.unsigned(packet[TYPE]);
    }

    static int height(byte[] packet) {
        return Bytes.readInt(packet, HEIGHT);
    }

    static long work(byte[] packet) {
        return Bytes.readLong(packet, WORK);
    }

    static int periodStart(byte[] packet) {
        return Bytes.readInt(packet, PERIOD_START);
    }
}
