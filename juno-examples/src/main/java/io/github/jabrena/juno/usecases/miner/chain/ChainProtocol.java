package io.github.jabrena.juno.usecases.miner.chain;

import io.github.jabrena.juno.games.blockchain.Bytes;
import io.github.jabrena.juno.usecases.miner.peers.NodeId;

/**
 * Fixed-size UDP packets exchanged by {@link io.github.jabrena.juno.usecases.miner.Miner2} nodes, big-endian:
 *
 * <pre>
 *  0..3   "JUNO"
 *  4      version (1)
 *  5      service (3 = chain)
 *  6      type: BLOCK, GET_BLOCK, GET_TIP, HELLO, CLAIM or TAKEN
 *  7      HELLO: flags ({@link #MINING}); otherwise reserved
 *  8..11  BLOCK: height the sender gives the block; HELLO: the height of the sender's tip
 * 12..19  BLOCK: cumulative work the sender gives it; HELLO: the work of the sender's tip
 * 20..23  BLOCK: start time of its retarget period
 * 24..    BLOCK: the block ({@link BlockFormat}); GET_BLOCK: the 32-byte hash wanted;
 *         HELLO, CLAIM, TAKEN: the sender's 16-byte node UUID ({@link NodeId}), then the bit board:
 * 40..43  board round
 * 44      HELLO: the bounty bit length the sender owns (0 for none); CLAIM, TAKEN: the bit length asked about
 * 45..52  mask of the bit lengths the sender has seen mined this round (bit {@code n} for length {@code n})
 * 53      the sender's color plus one (0 for none)
 * 54..85  the color plus one of the node that mined each bit length, a nibble per length, high nibble first
 * </pre>
 *
 * The three metadata fields are what the sender computed. A receiver that holds the parent
 * recomputes them; only a node adopting a checkpoint relies on them.
 *
 * <p>HELLO is the heartbeat each node broadcasts every few seconds: its UUID and the height and cumulative
 * work of its tip. Nodes that predate it ignore the type; the packet keeps the common size so they still accept
 * it as well-formed before skipping it.
 *
 * <p>CLAIM and TAKEN share out bounty bit lengths (Miner 2's blocks board): a node broadcasts CLAIM for the length it
 * wants to mine next, and a node that owns that length, or has seen it mined, answers TAKEN. Nodes that predate them
 * skip both types, and their HELLO leaves the board fields zero: round 0, no board.
 */
public final class ChainProtocol {
    public static final int BLOCK = 1;
    public static final int GET_BLOCK = 2;
    public static final int GET_TIP = 3;
    public static final int HELLO = 4;
    public static final int CLAIM = 5;
    public static final int TAKEN = 6;

    /** HELLO flag: the sender is mining, as opposed to waiting for a heavier chain it has heard of. */
    public static final int MINING = 1;

    public static final int BODY = 24;
    public static final int PACKET_SIZE = BODY + BlockFormat.MAX_SIZE;
    /** Where a HELLO packet carries its sender's UUID. */
    public static final int HELLO_UUID = BODY;

    private static final int BOARD_ROUND = HELLO_UUID + NodeId.SIZE;
    private static final int BOARD_BIT = BOARD_ROUND + 4;
    private static final int BOARD_MINED = BOARD_BIT + 1;
    private static final int BOARD_COLOR = BOARD_MINED + 8;
    private static final int BOARD_CELLS = BOARD_COLOR + 1;
    private static final int TYPE = 6;
    private static final int HEIGHT = 8;
    private static final int WORK = 12;
    private static final int PERIOD_START = 20;
    private static final int FLAGS = 7;
    private static final int VERSION = 1;
    private static final int CHAIN_SERVICE = 3;

    private ChainProtocol() {
    }

    public static void writeRequest(byte[] packet, int type) {
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
    public static void writeBlock(byte[] packet, BlockPool pool, int slot) {
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
    public static void writeHello(byte[] packet, byte[] uuid, int height, long work, int flags) {
        writeRequest(packet, HELLO);
        packet[FLAGS] = (byte) flags;
        Bytes.writeInt(packet, HEIGHT, height);
        Bytes.writeLong(packet, WORK, work);
        BlockFormat.copy(uuid, 0, packet, HELLO_UUID, NodeId.SIZE);
    }

    /**
     * Adds the bit board to a HELLO that {@link #writeHello} or a claim that {@link #writeClaim} wrote: the board's
     * {@code round}, the bit length the sender owns or asks about ({@code bit}, 0 for none), the {@code mined} mask
     * and the sender's {@code color} plus one. The cells' colors follow with {@link #writeBoardCell}.
     */
    public static void writeBoard(byte[] packet, int round, int bit, long mined, int color) {
        Bytes.writeInt(packet, BOARD_ROUND, round);
        packet[BOARD_BIT] = (byte) bit;
        Bytes.writeLong(packet, BOARD_MINED, mined);
        packet[BOARD_COLOR] = (byte) color;
    }

    /** Writes {@code value} (0 to 15) as the color of bit length {@code length} (0 to 63). */
    public static void writeBoardCell(byte[] packet, int length, int value) {
        int at = BOARD_CELLS + length / 2;
        int shift = length % 2 == 0 ? 4 : 0;
        packet[at] = (byte) (packet[at] & ~(15 << shift) | (value & 15) << shift);
    }

    /** A {@link #CLAIM} or {@link #TAKEN} packet from the node with {@code uuid}; its board follows. */
    public static void writeClaim(byte[] packet, int type, byte[] uuid) {
        writeRequest(packet, type);
        BlockFormat.copy(uuid, 0, packet, HELLO_UUID, NodeId.SIZE);
    }

    /** The sender's color plus one, 0 for none. */
    public static int boardColor(byte[] packet) {
        return Bytes.unsigned(packet[BOARD_COLOR]);
    }

    /** What {@link #writeBoardCell} wrote for bit length {@code length}. */
    public static int boardCell(byte[] packet, int length) {
        int shift = length % 2 == 0 ? 4 : 0;
        return Bytes.unsigned(packet[BOARD_CELLS + length / 2]) >>> shift & 15;
    }

    public static int boardRound(byte[] packet) {
        return Bytes.readInt(packet, BOARD_ROUND);
    }

    public static int boardBit(byte[] packet) {
        return Bytes.unsigned(packet[BOARD_BIT]);
    }

    public static long boardMined(byte[] packet) {
        return Bytes.readLong(packet, BOARD_MINED);
    }

    public static int helloFlags(byte[] packet) {
        return Bytes.unsigned(packet[FLAGS]);
    }

    public static boolean valid(byte[] packet, int length) {
        return length == PACKET_SIZE
                && packet[0] == 'J' && packet[1] == 'U' && packet[2] == 'N' && packet[3] == 'O'
                && Bytes.unsigned(packet[4]) == VERSION
                && Bytes.unsigned(packet[5]) == CHAIN_SERVICE;
    }

    public static int type(byte[] packet) {
        return Bytes.unsigned(packet[TYPE]);
    }

    public static int height(byte[] packet) {
        return Bytes.readInt(packet, HEIGHT);
    }

    public static long work(byte[] packet) {
        return Bytes.readLong(packet, WORK);
    }

    public static int periodStart(byte[] packet) {
        return Bytes.readInt(packet, PERIOD_START);
    }
}
