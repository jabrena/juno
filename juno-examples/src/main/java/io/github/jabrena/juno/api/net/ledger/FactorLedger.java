package io.github.jabrena.juno.api.net.ledger;

/**
 * The leader's state: which worker holds which task, and the hash chain of verified results.
 *
 * <p>It never touches the network itself. {@link #handle} takes one received packet and writes the
 * reply, so the protocol can be exercised off-board. Each worker holds at most one task. A worker
 * that stays silent longer than {@link #LEASE_MILLIS} is dropped and its task goes to the next worker
 * that asks. A result is accepted from any worker for any task still outstanding, but only once, and
 * only after {@link Factorizer#verify} has checked it.
 *
 * <p>Block {@code i} hashes {@code index, timestamp, previous hash, task id, worker id, number,
 * factor count, (prime, exponent)...} with SHA-256. Block 0 is the genesis block, with no task
 * and an all-zero previous hash. Changing any accepted result changes every later hash.
 */
final class FactorLedger {
    static final int MAX_WORKERS = 8;
    /** How long a worker may stay silent (including while factoring) before its task is reassigned. */
    static final int LEASE_MILLIS = 120_000;

    private static final int MAX_BLOCK_SIZE = 57 + FactorProtocol.MAX_FACTORS * 9;

    private final int bits;
    private long seed;
    private int nextTaskId = 1;

    private final int[] workerNodes = new int[MAX_WORKERS];
    private final int[] workerTasks = new int[MAX_WORKERS];
    private final long[] workerNumbers = new long[MAX_WORKERS];
    private final int[] workerHeardAt = new int[MAX_WORKERS];
    private final int[] orphanTasks = new int[MAX_WORKERS];
    private final long[] orphanNumbers = new long[MAX_WORKERS];

    private int height;
    private int blockTime;
    private int blockTask;
    private int blockNode;
    private long blockNumber;
    private int blockCount;
    private final long[] blockPrimes = new long[FactorProtocol.MAX_FACTORS];
    private final int[] blockExponents = new int[FactorProtocol.MAX_FACTORS];
    private final byte[] previousHash = new byte[Sha256.DIGEST_SIZE];
    private final byte[] hash = new byte[Sha256.DIGEST_SIZE];

    private final long[] receivedPrimes = new long[FactorProtocol.MAX_FACTORS];
    private final int[] receivedExponents = new int[FactorProtocol.MAX_FACTORS];
    private final byte[] message = new byte[MAX_BLOCK_SIZE];
    private final byte[] shaBlock = new byte[Sha256.BLOCK_SIZE];
    private final int[] shaSchedule = new int[Sha256.SCHEDULE_SIZE];
    private final int[] shaState = new int[Sha256.STATE_SIZE];

    /**
     * {@code bits} (2..62) is the exact bit length of every number handed out; {@code seed} (non-zero)
     * makes the sequence of numbers repeatable.
     */
    FactorLedger(int bits, long seed, int now) {
        this.bits = bits;
        this.seed = seed;
        append(0, 0, 0, 0, now);
    }

    /**
     * Handles one received packet and returns whether {@code reply} now holds a packet to send back
     * to its sender.
     */
    boolean handle(byte[] packet, int length, byte[] reply, int now) {
        if (!FactorProtocol.valid(packet, length)) {
            return false;
        }
        int type = FactorProtocol.type(packet);
        if (type != FactorProtocol.JOIN && type != FactorProtocol.RESULT) {
            return false;
        }
        int node = FactorProtocol.nodeId(packet);
        int worker = worker(node, now);
        if (worker < 0) {
            return false;
        }
        if (type == FactorProtocol.RESULT) {
            accept(node, FactorProtocol.taskId(packet), FactorProtocol.number(packet), packet, now);
        }
        if (workerTasks[worker] == 0) {
            assign(worker);
        }
        FactorProtocol.write(reply, FactorProtocol.TASK, 0, workerTasks[worker], workerNumbers[worker]);
        return true;
    }

    /** Drops workers silent for longer than {@link #LEASE_MILLIS}, keeping their tasks for others. */
    void expire(int now) {
        for (int i = 0; i < MAX_WORKERS; i++) {
            if (workerNodes[i] != 0 && now - workerHeardAt[i] > LEASE_MILLIS) {
                if (workerTasks[i] != 0) {
                    int slot = freeOrphan();
                    orphanTasks[slot] = workerTasks[i];
                    orphanNumbers[slot] = workerNumbers[i];
                }
                workerNodes[i] = 0;
                workerTasks[i] = 0;
            }
        }
    }

    int height() {
        return height;
    }

    int blockTime() {
        return blockTime;
    }

    int blockTask() {
        return blockTask;
    }

    int blockNode() {
        return blockNode;
    }

    long blockNumber() {
        return blockNumber;
    }

    int blockCount() {
        return blockCount;
    }

    long blockPrime(int i) {
        return blockPrimes[i];
    }

    int blockExponent(int i) {
        return blockExponents[i];
    }

    byte[] previousHash() {
        return previousHash;
    }

    byte[] hash() {
        return hash;
    }

    /** Number of workers currently holding a lease. */
    int workers() {
        int count = 0;
        for (int i = 0; i < MAX_WORKERS; i++) {
            if (workerNodes[i] != 0) {
                count++;
            }
        }
        return count;
    }

    private void accept(int node, int task, long number, byte[] packet, int now) {
        int holder = -1;
        for (int i = 0; i < MAX_WORKERS; i++) {
            if (workerNodes[i] != 0 && workerTasks[i] == task && workerNumbers[i] == number) {
                holder = i;
            }
        }
        int orphan = -1;
        for (int i = 0; i < MAX_WORKERS; i++) {
            if (orphanTasks[i] == task && orphanNumbers[i] == number) {
                orphan = i;
            }
        }
        if (task == 0 || (holder < 0 && orphan < 0)) {
            return;
        }
        int count = FactorProtocol.readFactors(packet, receivedPrimes, receivedExponents);
        if (!Factorizer.verify(number, receivedPrimes, receivedExponents, count)) {
            return;
        }
        if (holder >= 0) {
            workerTasks[holder] = 0;
        }
        if (orphan >= 0) {
            orphanTasks[orphan] = 0;
        }
        for (int i = 0; i < count; i++) {
            blockPrimes[i] = receivedPrimes[i];
            blockExponents[i] = receivedExponents[i];
        }
        append(task, node, number, count, now);
    }

    private void append(int task, int node, long number, int count, int now) {
        if (task != 0) {
            height++;
            for (int i = 0; i < Sha256.DIGEST_SIZE; i++) {
                previousHash[i] = hash[i];
            }
        }
        blockTime = now;
        blockTask = task;
        blockNode = node;
        blockNumber = number;
        blockCount = count;

        FactorProtocol.writeInt(message, 0, height);
        FactorProtocol.writeInt(message, 4, now);
        for (int i = 0; i < Sha256.DIGEST_SIZE; i++) {
            message[8 + i] = previousHash[i];
        }
        FactorProtocol.writeInt(message, 40, task);
        FactorProtocol.writeInt(message, 44, node);
        FactorProtocol.writeLong(message, 48, number);
        message[56] = (byte) count;
        for (int i = 0; i < count; i++) {
            FactorProtocol.writeLong(message, 57 + i * 9, blockPrimes[i]);
            message[57 + i * 9 + 8] = (byte) blockExponents[i];
        }
        Sha256.digest(message, 57 + count * 9, hash, shaBlock, shaSchedule, shaState);
    }

    /** The slot of {@code node}, registering it and refreshing its lease; -1 when the table is full. */
    private int worker(int node, int now) {
        int found = -1;
        int free = -1;
        for (int i = 0; i < MAX_WORKERS; i++) {
            if (workerNodes[i] == node && node != 0) {
                found = i;
            } else if (workerNodes[i] == 0 && free < 0) {
                free = i;
            }
        }
        if (found < 0) {
            if (free < 0 || node == 0) {
                return -1;
            }
            found = free;
            workerNodes[found] = node;
            workerTasks[found] = 0;
        }
        workerHeardAt[found] = now;
        return found;
    }

    private void assign(int worker) {
        for (int i = 0; i < MAX_WORKERS; i++) {
            if (orphanTasks[i] != 0) {
                workerTasks[worker] = orphanTasks[i];
                workerNumbers[worker] = orphanNumbers[i];
                orphanTasks[i] = 0;
                return;
            }
        }
        workerTasks[worker] = nextTaskId;
        workerNumbers[worker] = nextNumber();
        nextTaskId++;
    }

    private int freeOrphan() {
        for (int i = 0; i < MAX_WORKERS; i++) {
            if (orphanTasks[i] == 0) {
                return i;
            }
        }
        // Unreachable: there is one orphan slot per worker slot, and a worker holds at most one task.
        return 0;
    }

    /** xorshift64, scaled to exactly {@link #bits} bits. */
    private long nextNumber() {
        seed = seed ^ (seed << 13);
        seed = seed ^ (seed >>> 7);
        seed = seed ^ (seed << 17);
        return (seed >>> (64 - bits)) | (1L << (bits - 1));
    }
}
