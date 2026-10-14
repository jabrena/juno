package io.github.jabrena.juno.usecases.miner;

/**
 * The other nodes this node has heard from, kept by their {@link ChainProtocol#HELLO} heartbeats and identified by
 * their {@link NodeId} UUID.
 *
 * <p>A peer's state follows only from how long ago its last heartbeat arrived, measured on this board's own clock
 * (the boards' clocks are not synchronized): <b>alive</b> for fewer than {@link #SUSPECT_AFTER_MILLIS}, then
 * <b>suspect</b> until {@link #DEAD_AFTER_MILLIS}, then <b>dead</b>. One missed heartbeat therefore never kills a
 * node, because UDP drops packets. A dead peer's heartbeat makes it alive again. The table has a fixed number of
 * slots and, when full, replaces the peer heard from longest ago.
 *
 * <p>Times are {@code int} milliseconds compared by subtraction, so a clock that wraps around does not matter.
 */
public final class PeerTable {
    public static final int CAPACITY = 8;
    /** How often a node broadcasts its heartbeat. */
    public static final int HELLO_INTERVAL_MILLIS = 5000;
    static final int SUSPECT_AFTER_MILLIS = 3 * HELLO_INTERVAL_MILLIS;
    static final int DEAD_AFTER_MILLIS = 5 * HELLO_INTERVAL_MILLIS;

    /** What {@link #state} says of a slot: it never held a peer, or its peer is alive, suspect or dead. */
    public static final int EMPTY = 0;
    public static final int ALIVE = 1;
    public static final int SUSPECT = 2;
    public static final int DEAD = 3;

    private final byte[] uuids = new byte[CAPACITY * NodeId.SIZE];
    /** Whether a slot holds a peer. */
    private final int[] used = new int[CAPACITY];
    private final int[] lastSeen = new int[CAPACITY];
    private final int[] heights = new int[CAPACITY];
    private final long[] works = new long[CAPACITY];
    private final int[] flags = new int[CAPACITY];
    /** Whether the death of the peer in a slot was already handed out by {@link #nextDead}. */
    private final int[] reported = new int[CAPACITY];

    /**
     * Records a heartbeat from the peer whose UUID is at {@code uuid[offset..)}, received at {@code now}, and returns
     * whether the peer is new or came back from the dead.
     */
    public boolean update(byte[] uuid, int offset, int height, long work, int peerFlags, int now) {
        int slot = find(uuid, offset);
        boolean joined = false;
        if (slot < 0) {
            slot = replaceable();
            BlockFormat.copy(uuid, offset, uuids, slot * NodeId.SIZE, NodeId.SIZE);
            used[slot] = 1;
            joined = true;
        } else if (now - lastSeen[slot] >= DEAD_AFTER_MILLIS) {
            joined = true;
        }
        lastSeen[slot] = now;
        heights[slot] = height;
        works[slot] = work;
        flags[slot] = peerFlags;
        reported[slot] = 0;
        return joined;
    }

    /** Nodes heard from recently enough to count as alive. */
    public int alive(int now) {
        int count = 0;
        for (int i = 0; i < CAPACITY; i++) {
            if (isAlive(i, now)) {
                count++;
            }
        }
        return count;
    }

    /** Nodes that missed enough heartbeats to be doubtful, but not enough to be declared dead. */
    public int suspect(int now) {
        int count = 0;
        for (int i = 0; i < CAPACITY; i++) {
            int age = now - lastSeen[i];
            if (used[i] != 0 && age >= SUSPECT_AFTER_MILLIS && age < DEAD_AFTER_MILLIS) {
                count++;
            }
        }
        return count;
    }

    /** Alive nodes that say they are mining. */
    public int mining(int now) {
        int count = 0;
        for (int i = 0; i < CAPACITY; i++) {
            if (isAlive(i, now) && (flags[i] & ChainProtocol.MINING) != 0) {
                count++;
            }
        }
        return count;
    }

    /** The most cumulative work any alive peer reports for its tip, or 0 with no peers. */
    public long bestWork(int now) {
        long best = 0L;
        for (int i = 0; i < CAPACITY; i++) {
            if (isAlive(i, now) && works[i] > best) {
                best = works[i];
            }
        }
        return best;
    }

    /** The greatest height any alive peer reports for its tip, or 0 with no peers. */
    int bestHeight(int now) {
        int best = 0;
        for (int i = 0; i < CAPACITY; i++) {
            if (isAlive(i, now) && heights[i] > best) {
                best = heights[i];
            }
        }
        return best;
    }

    /**
     * The slot of a peer that has just become dead, or -1 when there is none; call it repeatedly until it returns -1.
     * Each death is handed out once, so the caller can log it; {@link #uuids()} holds the peer's UUID at
     * {@code slot * NodeId.SIZE}.
     */
    public int nextDead(int now) {
        for (int i = 0; i < CAPACITY; i++) {
            if (used[i] != 0 && reported[i] == 0 && now - lastSeen[i] >= DEAD_AFTER_MILLIS) {
                reported[i] = 1;
                return i;
            }
        }
        return -1;
    }

    /** The UUIDs of the slots, {@code NodeId.SIZE} bytes each. */
    public byte[] uuids() {
        return uuids;
    }

    /** {@link #EMPTY}, or whether the peer in {@code slot} is {@link #ALIVE}, {@link #SUSPECT} or {@link #DEAD}. */
    public int state(int slot, int now) {
        if (used[slot] == 0) {
            return EMPTY;
        }
        int age = now - lastSeen[slot];
        return age < SUSPECT_AFTER_MILLIS ? ALIVE : age < DEAD_AFTER_MILLIS ? SUSPECT : DEAD;
    }

    /** The height of the tip the peer in {@code slot} announced last. */
    public int height(int slot) {
        return heights[slot];
    }

    /** Whether the peer in {@code slot} said in its last heartbeat that it was mining. */
    public boolean isMining(int slot) {
        return (flags[slot] & ChainProtocol.MINING) != 0;
    }

    private boolean isAlive(int slot, int now) {
        return used[slot] != 0 && now - lastSeen[slot] < SUSPECT_AFTER_MILLIS;
    }

    private int find(byte[] uuid, int offset) {
        for (int i = 0; i < CAPACITY; i++) {
            if (used[i] != 0 && NodeId.same(uuids, i * NodeId.SIZE, uuid, offset)) {
                return i;
            }
        }
        return -1;
    }

    /** An empty slot, else the one whose peer was heard from longest ago. */
    private int replaceable() {
        int oldest = 0;
        for (int i = 0; i < CAPACITY; i++) {
            if (used[i] == 0) {
                return i;
            }
            if (lastSeen[i] - lastSeen[oldest] < 0) {
                oldest = i;
            }
        }
        return oldest;
    }
}
