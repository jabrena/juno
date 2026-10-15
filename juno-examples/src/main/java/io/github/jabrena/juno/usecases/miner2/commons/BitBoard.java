package io.github.jabrena.juno.usecases.miner2.commons;

import io.github.jabrena.juno.usecases.miner.BlockFormat;
import io.github.jabrena.juno.usecases.miner.PeerTable;

/**
 * The blocks board: one cell per bounty bit length, from {@link #FIRST} to {@link #LAST}, that the nodes share out so
 * no two mine the same length at once. A cell is free, claimed by the node mining it, or mined, in the color of the
 * node whose block carried it ({@link #NO_COLOR} until some node says which). When every cell is mined the board
 * starts a new round.
 *
 * <p>The board also keeps the nodes it has heard of: each one's color, one of {@link #COLORS} that the nodes keep
 * unique between them, and the length it claims. Arrays are allocated once, here.
 */
public final class BitBoard {
    public static final int FIRST = BlockFormat.MIN_BOUNTY_BITS;
    public static final int LAST = BlockFormat.MAX_BOUNTY_BITS;
    public static final int SIZE = LAST - FIRST + 1;
    /** No node: {@code NodeId.shortId} is never 0. */
    public static final int UNKNOWN = 0;
    /** How many node colors there are. */
    public static final int COLORS = 8;
    /** The color of a cell or node not known yet. */
    public static final int NO_COLOR = -1;
    /** How long a claim holds without being heard again; owners repeat theirs in every heartbeat. */
    public static final int CLAIM_MILLIS = 15_000;
    /** How long a node's color stays taken after it was last heard: as long as the node counts as alive or suspect. */
    public static final int COLOR_MILLIS = 25_000;

    /** Bit {@code n} set for every bit length {@code n} on the board. */
    private static final long FULL = ((1L << SIZE) - 1) << FIRST;
    /** The peers a node can know, plus itself. */
    private static final int NODES = PeerTable.CAPACITY + 1;

    /** Each mined cell's color plus one, so 0 is {@link #NO_COLOR}. */
    private final byte[] cells = new byte[SIZE];
    private final int[] nodes = new int[NODES];
    private final int[] nodeColors = new int[NODES];
    private final int[] heardAt = new int[NODES];
    private final int[] claimBits = new int[NODES];
    private final int[] claimTimes = new int[NODES];
    private long mined;
    private int round = 1;
    private int roundStart;
    private int ownBit;
    private boolean asking;
    private int ownColor;

    /** The color a node starts with before it has saved one: spread by its id, so few nodes start on the same one. */
    public static int defaultColor(int nodeId) {
        // Fibonacci hashing: the top three bits of the id times the golden ratio.
        return (nodeId * 0x9E3779B9) >>> 29;
    }

    /** The round being played; it starts at 1 and moves on when the board fills up. */
    public int round() {
        return round;
    }

    /** The chain height the round started at: blocks at or below it count for an earlier round. */
    public int roundStart() {
        return roundStart;
    }

    /** Bit {@code n} set for every bit length {@code n} mined this round. */
    public long minedMask() {
        return mined;
    }

    public static boolean onBoard(int bit) {
        return bit >= FIRST && bit <= LAST;
    }

    public boolean isMined(int bit) {
        return onBoard(bit) && (mined & (1L << bit)) != 0;
    }

    /** The color of the node that mined {@code bit}, or {@link #NO_COLOR}. */
    public int cellColor(int bit) {
        return cells[bit - FIRST] - 1;
    }

    /** How many cells are mined. */
    public int count() {
        int count = 0;
        for (int bit = FIRST; bit <= LAST; bit++) {
            if (isMined(bit)) {
                count++;
            }
        }
        return count;
    }

    public boolean full() {
        return (mined & FULL) == FULL;
    }

    /** Records that a block with a bounty of {@code bit} bits, mined by {@code minerId}, joined the chain. */
    public void markMined(int bit, int minerId) {
        if (!onBoard(bit)) {
            return;
        }
        mined = mined | (1L << bit);
        learnCell(bit, colorOf(minerId));
    }

    /** Colors a mined cell whose color was not known yet, with what a peer said. */
    public void learnCell(int bit, int color) {
        if (isMined(bit) && cells[bit - FIRST] == 0 && color >= 0 && color < COLORS) {
            cells[bit - FIRST] = (byte) (color + 1);
        }
    }

    /**
     * Takes in what a peer saw mined in its {@code peerRound}: a later round replaces this one, from {@code height}
     * on; round 0 is a peer without a board, and an earlier round is stale. Returns whether the peer's round is this
     * board's round now, so the rest of what it said applies.
     */
    public boolean merge(int peerRound, long peerMined, int height) {
        if (peerRound > round) {
            startRound(peerRound, height);
        }
        if (peerRound != round) {
            return false;
        }
        mined = mined | (peerMined & FULL);
        return true;
    }

    /** Clears the board for {@code next} round, which counts blocks above {@code height}. */
    public void startRound(int next, int height) {
        round = next;
        roundStart = height;
        mined = 0L;
        for (int i = 0; i < SIZE; i++) {
            cells[i] = 0;
        }
        for (int i = 0; i < NODES; i++) {
            claimBits[i] = 0;
        }
    }

    /** Records that node {@code nodeId} was heard at {@code now}, with its {@code color} ({@link #NO_COLOR} for none). */
    public void heard(int nodeId, int color, int now) {
        int slot = slotOf(nodeId, now);
        nodeColors[slot] = color;
        heardAt[slot] = now;
    }

    /** Records that node {@code nodeId} owns or asks for {@code bit} at {@code now}; bit 0 withdraws its claim. */
    public void claim(int nodeId, int bit, int now) {
        int slot = slotOf(nodeId, now);
        claimBits[slot] = bit;
        claimTimes[slot] = now;
    }

    /** The node that holds a live claim on {@code bit} at {@code now}, or {@link #UNKNOWN} when none does. */
    public int claimant(int bit, int now) {
        for (int i = 0; i < NODES; i++) {
            if (nodes[i] != UNKNOWN && claimBits[i] == bit && now - claimTimes[i] < CLAIM_MILLIS) {
                return nodes[i];
            }
        }
        return UNKNOWN;
    }

    /** The color node {@code nodeId} last said it has, or {@link #NO_COLOR}. */
    public int colorOf(int nodeId) {
        for (int i = 0; i < NODES; i++) {
            if (nodes[i] != UNKNOWN && nodes[i] == nodeId) {
                return nodeColors[i];
            }
        }
        return NO_COLOR;
    }

    /** The lowest color no node other than {@code self} has used within {@link #COLOR_MILLIS}, or {@link #NO_COLOR}. */
    public int freeColor(int self, int now) {
        for (int color = 0; color < COLORS; color++) {
            if (!colorTaken(color, self, now)) {
                return color;
            }
        }
        return NO_COLOR;
    }

    /** This node's color. */
    public int ownColor() {
        return ownColor;
    }

    public void publishOwnColor(int color) {
        ownColor = color;
    }

    /** The bit this node owns (0 for none), and whether it is still asking the network for it. */
    public void publishOwn(int bit, boolean waitingForAnswers) {
        ownBit = bit;
        asking = waitingForAnswers;
    }

    /** The bit this node owns or asks for, 0 for none. */
    public int ownBit() {
        return ownBit;
    }

    public boolean asking() {
        return asking;
    }

    private boolean colorTaken(int color, int self, int now) {
        for (int i = 0; i < NODES; i++) {
            if (nodes[i] != UNKNOWN && nodes[i] != self && nodeColors[i] == color
                    && now - heardAt[i] < COLOR_MILLIS) {
                return true;
            }
        }
        return false;
    }

    /** The slot of {@code nodeId}, else a free slot for it, else the slot of the node heard from longest ago. */
    private int slotOf(int nodeId, int now) {
        int free = -1;
        int oldest = 0;
        for (int i = 0; i < NODES; i++) {
            if (nodes[i] == nodeId) {
                return i;
            }
            if (nodes[i] == UNKNOWN && free < 0) {
                free = i;
            }
            if (heardAt[i] - heardAt[oldest] < 0) {
                oldest = i;
            }
        }
        int slot = free >= 0 ? free : oldest;
        nodes[slot] = nodeId;
        nodeColors[slot] = NO_COLOR;
        heardAt[slot] = now;
        claimBits[slot] = 0;
        return slot;
    }
}
