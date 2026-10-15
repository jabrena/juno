package io.github.jabrena.juno.usecases.miner.commons;

import io.github.jabrena.juno.api.UuidCodec;
import io.github.jabrena.juno.usecases.miner.chain.BlockPool;
import io.github.jabrena.juno.usecases.miner.peers.NodeId;
import io.github.jabrena.juno.usecases.miner.peers.PeerTable;

import java.util.UUID;

/**
 * The model Miner 2's views render: the node's chain, peers and blocks board, which the node service updates, plus
 * the counters it also logs on serial. Everything runs on the main thread, so nothing here is
 * guarded.
 */
public final class Miner2State {
    private final BlockPool pool;
    private final PeerTable peers = new PeerTable();
    private final BitBoard board = new BitBoard();
    private final UUID uuid;
    private final byte[] uuidBytes = new byte[UuidCodec.BYTES];
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
    private boolean wifiConnected;
    private boolean sdCardReady;

    /** The state of the node identified by {@code uuid}, holding its chain in {@code pool}. */
    public Miner2State(BlockPool pool, UUID uuid) {
        this.pool = pool;
        this.uuid = uuid;
        UuidCodec.toBytes(uuid, uuidBytes, 0);
        this.nodeId = NodeId.shortId(uuidBytes, 0);
    }

    public BlockPool pool() {
        return pool;
    }

    public PeerTable peers() {
        return peers;
    }

    /** Which bounty bit lengths are mined, and which nodes are mining which. */
    public BitBoard board() {
        return board;
    }

    /** This node's UUID, loaded from persistent storage when one was previously saved. */
    public UUID uuid() {
        return uuid;
    }

    /** This node's fixed {@link NodeId#SIZE}-byte network representation. */
    public byte[] uuidBytes() {
        return uuidBytes;
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

    /** Whether the Wi-Fi adapter currently reports an established connection. */
    public boolean wifiConnected() {
        return wifiConnected;
    }

    public void publishWifiConnected(boolean connected) {
        wifiConnected = connected;
    }

    /** Whether the SD card was initialized and can persist this node's identity and chain journal. */
    public boolean sdCardReady() {
        return sdCardReady;
    }

    public void publishSdCardReady(boolean ready) {
        sdCardReady = ready;
    }
}
