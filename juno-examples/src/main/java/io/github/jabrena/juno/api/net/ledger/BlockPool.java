package io.github.jabrena.juno.api.net.ledger;

/**
 * A node's view of the chain: a small tree of recent blocks from every branch it has seen, and the
 * tip of the branch with the most cumulative work (Nakamoto consensus).
 *
 * <p>Every block is checked against its parent: version, the difficulty the retarget rule expects,
 * the proof-of-work, a time later than the parent's, the payload hash, the bounty derived from the
 * parent hash and its factorization. Blocks carry no balances, so a reorganization never has
 * state to undo; it only moves the tip. Each stored block keeps the metadata its children need
 * (height, cumulative work, bits, time, start of the retarget period), which is why old blocks
 * can be pruned without losing the ability to validate new ones.
 *
 * <p>A block whose parent is unknown waits as pending while the node fetches the parent. When
 * {@link #MAX_FETCH} ancestors in a row are still missing, the oldest of them is adopted as a
 * checkpoint, trusting the height and work its sender claimed, and everything after it is
 * validated in full. That is how a node joins a network whose history it was not there for.
 */
final class BlockPool {
    static final int CAPACITY = 12;
    static final int MAX_FETCH = 4;
    static final int MIN_BITS = 4;
    static final int MAX_BITS = 40;

    static final int INVALID = 0;
    static final int DUPLICATE = 1;
    static final int STORED = 2;
    static final int NEW_TIP = 3;
    static final int NEED_PARENT = 4;

    private static final int EMPTY = 0;
    private static final int PENDING = 1;
    private static final int VALID = 2;

    private final int initialBits;
    private final int retargetInterval;
    private final int targetSeconds;

    private final byte[] blocks = new byte[CAPACITY * BlockFormat.MAX_SIZE];
    private final byte[] hashes = new byte[CAPACITY * Sha256.DIGEST_SIZE];
    private final int[] status = new int[CAPACITY];
    private final int[] arrival = new int[CAPACITY];
    private final int[] heights = new int[CAPACITY];
    private final long[] works = new long[CAPACITY];
    private final int[] bits = new int[CAPACITY];
    private final int[] times = new int[CAPACITY];
    private final int[] periodStarts = new int[CAPACITY];
    private final int[] claimedHeights = new int[CAPACITY];
    private final long[] claimedWorks = new long[CAPACITY];
    private final int[] claimedPeriodStarts = new int[CAPACITY];
    private int tip;
    private int tipChanges;
    private int arrivals;
    private int needed;

    private final byte[] digest = new byte[Sha256.DIGEST_SIZE];
    private final byte[] shaBlock = new byte[Sha256.BLOCK_SIZE];
    private final int[] shaSchedule = new int[Sha256.SCHEDULE_SIZE];
    private final int[] shaState = new int[Sha256.STATE_SIZE];
    private final long[] primes = new long[FactorProtocol.MAX_FACTORS];
    private final int[] exponents = new int[FactorProtocol.MAX_FACTORS];

    /**
     * {@code initialBits} is the genesis difficulty; every {@code retargetInterval} blocks it moves one
     * bit toward a block every {@code targetSeconds}.
     */
    BlockPool(int initialBits, int retargetInterval, int targetSeconds) {
        this.initialBits = initialBits;
        this.retargetInterval = retargetInterval;
        this.targetSeconds = targetSeconds;
        createGenesis();
    }

    /**
     * Offers the block at {@code buffer[offset..)} with the metadata its sender claimed, and returns
     * {@link #NEW_TIP}, {@link #STORED} (valid, on a lighter branch), {@link #NEED_PARENT} (the caller
     * should ask the sender for {@link #missingParent}), {@link #DUPLICATE} or {@link #INVALID}.
     */
    int add(byte[] buffer, int offset, int claimedHeight, long claimedWork, int claimedPeriodStart) {
        if (!BlockFormat.wellFormed(buffer, offset)) {
            return INVALID;
        }
        hashHeader(buffer, offset);
        if (find(digest, 0) >= 0) {
            return DUPLICATE;
        }
        int headerBits = FactorProtocol.readInt(buffer, offset + BlockFormat.BITS);
        if (headerBits < MIN_BITS || headerBits > MAX_BITS || !BlockFormat.meetsTarget(digest, headerBits)) {
            return INVALID;
        }
        // Check the payload before storing: a copy with a valid header but a mutated payload has the same
        // hash, and must never be kept where it would shadow the genuine block (Bitcoin's CVE-2012-2459).
        if (!payloadValid(buffer, offset)) {
            return INVALID;
        }
        hashHeader(buffer, offset);

        int slot = allocate();
        BlockFormat.copy(buffer, offset, blocks, slot * BlockFormat.MAX_SIZE, BlockFormat.size(buffer, offset));
        BlockFormat.copy(digest, 0, hashes, slot * Sha256.DIGEST_SIZE, Sha256.DIGEST_SIZE);
        status[slot] = PENDING;
        arrival[slot] = arrivals;
        arrivals++;
        claimedHeights[slot] = claimedHeight;
        claimedWorks[slot] = claimedWork;
        claimedPeriodStarts[slot] = claimedPeriodStart;

        int parent = parentOf(slot);
        if (parent >= 0 && status[parent] == VALID) {
            if (!validate(slot, parent)) {
                status[slot] = EMPTY;
                return INVALID;
            }
            connectPending();
            return selectTip() ? NEW_TIP : STORED;
        }
        // Measure the run of pending blocks this one belongs to: missing ancestors behind it, children ahead.
        int oldest = slot;
        int missing = 1;
        int ancestor = parentOf(slot);
        while (ancestor >= 0 && status[ancestor] == PENDING) {
            oldest = ancestor;
            missing++;
            ancestor = parentOf(ancestor);
        }
        int head = slot;
        int child = pendingChildOf(slot);
        while (child >= 0) {
            head = child;
            missing++;
            child = pendingChildOf(child);
        }
        if (claimedWorks[head] <= works[tip]) {
            // A branch that is not heavier could never become the tip; there is no point fetching its history.
            return STORED;
        }
        if (missing < MAX_FETCH) {
            needed = oldest;
            return NEED_PARENT;
        }
        if (!adoptCheckpoint(oldest)) {
            status[oldest] = EMPTY;
            return INVALID;
        }
        connectPending();
        return selectTip() ? NEW_TIP : STORED;
    }

    /** Copies the hash of the block a {@link #NEED_PARENT} answer asks for into {@code hash[offset..)}. */
    void missingParent(byte[] hash, int offset) {
        BlockFormat.copy(blocks, needed * BlockFormat.MAX_SIZE + BlockFormat.PREVIOUS, hash, offset,
                Sha256.DIGEST_SIZE);
    }

    /** The slot holding the block whose hash is at {@code hash[offset..)}, or -1. */
    int find(byte[] hash, int offset) {
        for (int i = 0; i < CAPACITY; i++) {
            if (status[i] != EMPTY && BlockFormat.sameHash(hashes, i * Sha256.DIGEST_SIZE, hash, offset)) {
                return i;
            }
        }
        return -1;
    }

    /** Whether {@code slot} holds a block that passed validation (only those are served to peers). */
    boolean isValid(int slot) {
        return status[slot] == VALID;
    }

    /** The difficulty the next block on top of the tip must have. */
    int nextBits() {
        return expectedBits(tip, heights[tip] + 1);
    }

    int tip() {
        return tip;
    }

    /** Increments every time the tip moves, so a miner knows to rebuild its template. */
    int tipChanges() {
        return tipChanges;
    }

    int height(int slot) {
        return heights[slot];
    }

    long work(int slot) {
        return works[slot];
    }

    int bits(int slot) {
        return bits[slot];
    }

    int time(int slot) {
        return times[slot];
    }

    int periodStart(int slot) {
        return periodStarts[slot];
    }

    /** The block bytes; slot {@code s} starts at {@code s * BlockFormat.MAX_SIZE}. */
    byte[] blocks() {
        return blocks;
    }

    /** The block hashes; slot {@code s} starts at {@code s * Sha256.DIGEST_SIZE}. */
    byte[] hashes() {
        return hashes;
    }

    private void createGenesis() {
        int base = 0;
        FactorProtocol.writeInt(blocks, base, BlockFormat.VERSION);
        FactorProtocol.writeInt(blocks, base + BlockFormat.BITS, initialBits);
        hashPayload(blocks, base);
        BlockFormat.copy(digest, 0, blocks, base + BlockFormat.PAYLOAD_HASH, Sha256.DIGEST_SIZE);
        hashHeader(blocks, base);
        BlockFormat.copy(digest, 0, hashes, 0, Sha256.DIGEST_SIZE);
        status[0] = VALID;
        bits[0] = initialBits;
        tip = 0;
    }

    private boolean validate(int slot, int parent) {
        int base = slot * BlockFormat.MAX_SIZE;
        int height = heights[parent] + 1;
        int headerBits = FactorProtocol.readInt(blocks, base + BlockFormat.BITS);
        int time = FactorProtocol.readInt(blocks, base + BlockFormat.TIME);
        if (headerBits != expectedBits(parent, height) || time <= times[parent]) {
            return false;
        }
        heights[slot] = height;
        works[slot] = works[parent] + (1L << headerBits);
        bits[slot] = headerBits;
        times[slot] = time;
        periodStarts[slot] = height % retargetInterval == 0 ? time : periodStarts[parent];
        status[slot] = VALID;
        return true;
    }

    /** Trusts the claimed position of {@code slot} in a history this node does not hold. */
    private boolean adoptCheckpoint(int slot) {
        if (claimedHeights[slot] <= 0 || claimedWorks[slot] <= 0) {
            return false;
        }
        int base = slot * BlockFormat.MAX_SIZE;
        heights[slot] = claimedHeights[slot];
        works[slot] = claimedWorks[slot];
        bits[slot] = FactorProtocol.readInt(blocks, base + BlockFormat.BITS);
        times[slot] = FactorProtocol.readInt(blocks, base + BlockFormat.TIME);
        periodStarts[slot] = claimedPeriodStarts[slot];
        status[slot] = VALID;
        return true;
    }

    /** Context-free checks: payload hash, bounty derived from the parent hash, and its factorization. */
    private boolean payloadValid(byte[] buffer, int base) {
        hashPayload(buffer, base);
        if (!BlockFormat.sameHash(digest, 0, buffer, base + BlockFormat.PAYLOAD_HASH)) {
            return false;
        }
        long number = FactorProtocol.readLong(buffer, base + BlockFormat.NUMBER);
        if (number != BlockFormat.bounty(buffer, base + BlockFormat.PREVIOUS)) {
            return false;
        }
        int count = FactorProtocol.unsigned(buffer[base + BlockFormat.COUNT]);
        for (int i = 0; i < count; i++) {
            primes[i] = FactorProtocol.readLong(buffer, base + BlockFormat.FACTORS + i * 9);
            exponents[i] = FactorProtocol.unsigned(buffer[base + BlockFormat.FACTORS + i * 9 + 8]);
        }
        return Factorizer.verify(number, primes, exponents, count);
    }

    /** Validates every pending block whose parent has become valid, repeating until nothing changes. */
    private void connectPending() {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < CAPACITY; i++) {
                if (status[i] == PENDING) {
                    int parent = parentOf(i);
                    if (parent >= 0 && status[parent] == VALID) {
                        if (!validate(i, parent)) {
                            status[i] = EMPTY;
                        }
                        changed = true;
                    }
                }
            }
        }
    }

    /** Moves the tip to the valid block with the most work; on a tie the current tip stays. */
    private boolean selectTip() {
        int best = tip;
        for (int i = 0; i < CAPACITY; i++) {
            if (status[i] == VALID && works[i] > works[best]) {
                best = i;
            }
        }
        if (best == tip) {
            return false;
        }
        tip = best;
        tipChanges++;
        return true;
    }

    private int expectedBits(int parent, int height) {
        if (height % retargetInterval != 0) {
            return bits[parent];
        }
        int span = times[parent] - periodStarts[parent];
        int target = (retargetInterval - 1) * targetSeconds;
        if (span < target / 2 && bits[parent] < MAX_BITS) {
            return bits[parent] + 1;
        }
        if (span > target * 2 && bits[parent] > MIN_BITS) {
            return bits[parent] - 1;
        }
        return bits[parent];
    }

    private int parentOf(int slot) {
        return find(blocks, slot * BlockFormat.MAX_SIZE + BlockFormat.PREVIOUS);
    }

    private int pendingChildOf(int slot) {
        for (int i = 0; i < CAPACITY; i++) {
            if (status[i] == PENDING && parentOf(i) == slot) {
                return i;
            }
        }
        return -1;
    }

    /** A free slot, else the oldest pending block, else the lowest valid block that is not the tip. */
    private int allocate() {
        int victim = -1;
        for (int i = 0; i < CAPACITY; i++) {
            if (status[i] == EMPTY) {
                return i;
            }
            if (status[i] == PENDING && (victim < 0 || arrival[i] < arrival[victim])) {
                victim = i;
            }
        }
        if (victim >= 0) {
            return victim;
        }
        for (int i = 0; i < CAPACITY; i++) {
            if (i != tip && (victim < 0 || heights[i] < heights[victim])) {
                victim = i;
            }
        }
        return victim;
    }

    private void hashHeader(byte[] buffer, int offset) {
        Sha256.digest(buffer, offset, BlockFormat.HEADER_SIZE, digest, shaBlock, shaSchedule, shaState);
    }

    private void hashPayload(byte[] buffer, int base) {
        Sha256.digest(buffer, base + BlockFormat.PAYLOAD, BlockFormat.size(buffer, base) - BlockFormat.PAYLOAD, digest,
                shaBlock, shaSchedule, shaState);
    }
}
