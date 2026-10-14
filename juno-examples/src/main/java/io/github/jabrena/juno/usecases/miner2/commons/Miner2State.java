package io.github.jabrena.juno.usecases.miner2.commons;

import io.github.jabrena.juno.usecases.miner.BlockPool;
import io.github.jabrena.juno.usecases.miner.NodeId;
import io.github.jabrena.juno.usecases.miner.PeerTable;

/**
 * The model Miner 2's views render: the node's chain and peers, which the node service updates, plus the counters
 * that the original game only logged on serial. Everything runs on the main thread, so nothing here is guarded.
 */
public final class Miner2State {
    private final BlockPool pool;
    private final PeerTable peers = new PeerTable();
    private final byte[] uuid;
    private final int nodeId;
    private final int[] address = new int[4];
    private int mined;
    private boolean syncing;
    private int hashes;
    private int received;
    private int ignored;
    private int sent;
    private int failed;
    private int restarts;

    /** The state of the node identified by {@code uuid}, holding its chain in {@code pool}. */
    public Miner2State(BlockPool pool, byte[] uuid) {
        this.pool = pool;
        this.uuid = uuid;
        this.nodeId = NodeId.shortId(uuid, 0);
    }

    public BlockPool pool() {
        return pool;
    }

    public PeerTable peers() {
        return peers;
    }

    /** This node's {@link NodeId#SIZE}-byte UUID. */
    public byte[] uuid() {
        return uuid;
    }

    /** The 32-bit id this node records as the miner of its blocks. */
    public int nodeId() {
        return nodeId;
    }

    /** This node's IPv4 address, four octets, as the network service last read it. */
    public int[] address() {
        return address;
    }

    /** Blocks this node mined that became the tip. */
    public int mined() {
        return mined;
    }

    public void countMined() {
        mined = mined + 1;
    }

    /** Whether the node paused mining to catch up with a heavier chain that peers announced. */
    public boolean syncing() {
        return syncing;
    }

    public void publishSyncing(boolean waiting) {
        syncing = waiting;
    }

    /** Hashes tried since the node started; wraps around, so compare readings by subtraction. */
    public int hashes() {
        return hashes;
    }

    public void countHashes(int tried) {
        hashes = hashes + tried;
    }

    /** Packets received, including the ones rejected. */
    public int received() {
        return received;
    }

    public void countReceived() {
        received = received + 1;
    }

    /** Packets received that were not well-formed chain packets. */
    public int ignored() {
        return ignored;
    }

    public void countIgnored() {
        ignored = ignored + 1;
    }

    public int sent() {
        return sent;
    }

    public int failed() {
        return failed;
    }

    /** Counts the {@code result} of a UDP send or broadcast: negative is a failure. */
    public void countSend(int result) {
        if (result < 0) {
            failed = failed + 1;
        } else {
            sent = sent + 1;
        }
    }

    /** Wi-Fi restarts after the link went silent. */
    public int restarts() {
        return restarts;
    }

    public void countRestart() {
        restarts = restarts + 1;
    }
}
