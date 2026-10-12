package io.github.jabrena.juno.usecases.miner;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.net.Udp;
import io.github.jabrena.juno.api.net.Wifi;
import io.github.jabrena.juno.api.tft.TftTouchShield;
import io.github.jabrena.juno.games.blockchain.Bytes;
import io.github.jabrena.juno.games.blockchain.GameScreen;
import io.github.jabrena.juno.games.blockchain.NodeIo;
import io.github.jabrena.juno.games.blockchain.Sha256;

/**
 * Miner, a network game about <b>decentralized consensus</b> for boards with the ELEGOO 2.8" TFT touch
 * shield: a leaderless, Bitcoin-style chain. Every board runs this same program and is an equal node
 * that mines, validates and relays; nothing marks one board as special, and there is no role to pick.
 *
 * <p>Blocks are secured by proof-of-work: a nonce that gives the 80-byte header a SHA-256 hash with
 * at least {@code bits} leading zero bits. Each block also has to carry the factorization of a
 * number derived from its parent's hash, so mining does useful work that every node checks. Every
 * eight blocks the difficulty moves one bit toward a block every 30 seconds, and the branch with
 * the most cumulative work is the chain. See {@link BlockPool} for validation and fork choice,
 * {@link BlockFormat} for the block layout and {@link ChainProtocol} for the packets.
 *
 * <p>The node runs two preemptive threads. A virtual thread ({@link Receiver}) serves the network: each pass
 * it takes a few packets and answers them, and every few seconds it broadcasts a {@code HELLO} heartbeat. The main
 * thread rebuilds the block template when the tip moved and tries {@link #SLICE} nonces. They share one
 * {@link BlockPool} and one {@link PeerTable}, guarded by the pool's monitor; either thread can be preempted
 * anywhere else.
 *
 * <p>Heartbeats tell each node who else is alive: a peer that misses a few is suspect, then lost (see
 * {@link PeerTable}), and the screen counts the nodes. A new node asks for the tip when it starts and every
 * {@link #ASK_TIP_MILLIS}, asks again when a missing parent does not arrive, and, when a heartbeat announces
 * a heavier tip than its own, asks that peer for it and pauses mining for up to
 * {@value SyncGate#GIVE_UP_MILLIS} ms ({@link SyncGate}) instead of building a rival branch. The screen shows
 * the tip; serial at 115200 baud logs it:
 *
 * <pre>
 * TIP #12 bits 14 work 98304 miner 48213377 (mined here)
 *   n = 780104467343 = 17 x 45888498079
 *   hash 0003a1f2...
 * </pre>
 *
 * <p>For the centralized counterpart, where one leader coordinates, see
 * {@code io.github.jabrena.juno.usecases.ledger.LedgerGame}. Phase 1 of the plan in issue #43: blocks are not signed, so a node can claim another node's id,
 * and block times are not bounded against the future.
 */
@Board({ArduinoUnoQ.class, ArduinoUnoR4WiFi.class})
public final class MinerGame {
    private static final int INITIAL_BITS = 12;
    private static final int RETARGET_INTERVAL = 8;
    private static final int TARGET_SECONDS = 30;
    /** Nonces tried between two looks at the network. */
    private static final int SLICE = 128;
    /** Packets served per pass of the receiver; a pass is about a millisecond, so this is a few thousand per second. */
    private static final int PACKETS_PER_PASS = 16;
    /** How often a node asks the network for the current tip, even when it believes it is up to date. */
    private static final int ASK_TIP_MILLIS = 30_000;
    /** How long to wait for a requested parent before asking everyone for it again. */
    private static final int FETCH_RETRY_MILLIS = 1500;
    /** The shortest time between two requests for a tip that a heartbeat said is heavier than ours. */
    private static final int TIP_REQUEST_MILLIS = 2000;
    /** How often the receiver logs its network counters on serial. */
    private static final int STATS_MILLIS = 10_000;
    /**
     * Silence after which the receiver assumes its Wi-Fi link is stuck (a UNO R4 WiFi's modem can stop delivering UDP
     * until power is cycled) and restarts it. Heartbeats arrive about every five seconds, so this is six of them.
     */
    private static final int SILENT_MILLIS = 30_000;
    /** The shortest time between two Wi-Fi restarts, so a network that is really empty does not restart forever. */
    private static final int RECOVER_BACKOFF_MILLIS = 120_000;

    private static int clockOffset;
    /** Whether the main thread is mining (not waiting to catch up); advertised in heartbeats. Guarded by the pool. */
    private static boolean mining = true;

    private MinerGame() {
    }

    /**
     * The network side of the node, run on a virtual thread so that mining never waits for the radio: it receives
     * packets and serves them with buffers of its own. Threads here are preemptive and can switch between any two
     * instructions, so every access to the shared {@link BlockPool} happens inside {@code synchronized (pool)}.
     */
    private static final class Receiver implements Runnable {
        private final BlockPool pool;
        private final PeerTable peers;
        private final byte[] uuid;
        private final int helloEvery;
        private final byte[] incoming = new byte[ChainProtocol.PACKET_SIZE];
        private final byte[] reply = new byte[ChainProtocol.PACKET_SIZE];
        private final int[] source = new int[Udp.ENDPOINT_SIZE];
        private final int[] address = new int[4];
        private int nextHello;
        private int nextAsk;
        private int nextStats;
        private int fetchRetryAt;
        private int tipRequestedAt;
        /** When a packet last arrived, and the earliest time another Wi-Fi restart may happen. */
        private int lastHeard;
        private int nextRecovery;
        private int recoveries;
        /** Packets received, packets that were not ours, and packets sent or that failed to send: for the logs. */
        private int received;
        private int ignored;
        private int sent;
        private int failed;

        Receiver(BlockPool pool, PeerTable peers, byte[] uuid, int nodeId) {
            this.pool = pool;
            this.peers = peers;
            this.uuid = uuid;
            // Nodes beat at slightly different rates (up to a second apart), so their heartbeats do not collide.
            this.helloEvery = PeerTable.HELLO_INTERVAL_MILLIS + (nodeId & 1023);
            int now = Clock.millis();
            this.nextHello = now;
            this.nextAsk = now + ASK_TIP_MILLIS;
            this.nextStats = now + STATS_MILLIS;
            this.tipRequestedAt = now - TIP_REQUEST_MILLIS;
            this.lastHeard = now;
            this.nextRecovery = now;
        }

        @Override
        public void run() {
            while (true) {
                for (int i = 0; i < PACKETS_PER_PASS; i++) {
                    int length = Udp.receive(incoming, ChainProtocol.PACKET_SIZE, source);
                    if (length <= 0) {
                        break;
                    }
                    received++;
                    int now = Clock.millis();
                    lastHeard = now;
                    synchronized (pool) {
                        serve(length, now);
                    }
                }
                int now = Clock.millis();
                heartbeat(now);
                retryFetch(now);
                reportLostPeers(now);
                reportNetwork(now);
                restartIfSilent(now);
                // A short pause per pass lets the mining thread and other tasks run, such as the UNO Q's bridge to Linux.
                Delay.millis(1);
            }
        }

        /** Tells the network this node is alive and where its tip is, and now and then asks for the network's tip. */
        private void heartbeat(int now) {
            if (now - nextHello >= 0) {
                nextHello = now + helloEvery;
                synchronized (pool) {
                    int tip = pool.tip();
                    ChainProtocol.writeHello(reply, uuid, pool.height(tip), pool.work(tip),
                            mining ? ChainProtocol.MINING : 0);
                }
                broadcast();
            }
            if (now - nextAsk >= 0) {
                nextAsk = now + ASK_TIP_MILLIS;
                ChainProtocol.writeRequest(reply, ChainProtocol.GET_TIP);
                broadcast();
            }
        }

        /** Sends {@code reply} to every node on the network. */
        private void broadcast() {
            count(Udp.broadcast(NodeIo.PORT, reply, ChainProtocol.PACKET_SIZE));
        }

        /** Sends {@code reply} to the node that sent the packet being served. */
        private void answer() {
            count(Udp.send(source, source[Udp.PORT], reply, ChainProtocol.PACKET_SIZE));
        }

        private void count(int result) {
            if (result < 0) {
                failed++;
            } else {
                sent++;
            }
        }

        /**
         * Logs, every {@link #STATS_MILLIS}, what this node can tell about its network: its address and how many
         * packets it has received, rejected, sent and failed to send, with the peers it knows and its tip's height.
         * A board that hears nothing (rx stays 0) is cut off from the others; one that hears but stays alone has
         * a problem in the program.
         */
        private void reportNetwork(int now) {
            if (now - nextStats < 0) {
                return;
            }
            nextStats = now + STATS_MILLIS;
            Wifi.localIP(address);
            Serial.print("NET " + address[0] + "." + address[1] + "." + address[2] + "." + address[3]);
            Serial.print(" rx " + received + " bad " + ignored + " restarts " + recoveries);
            Serial.print(" tx " + sent + " fail " + failed);
            synchronized (pool) {
                Serial.println(" peers " + peers.alive(now) + "+" + peers.suspect(now) + " h " + pool.height(pool.tip()));
            }
        }

        /** Asks everyone again for a parent that is still missing: the request or its answer may have been lost. */
        private void retryFetch(int now) {
            boolean due;
            synchronized (pool) {
                due = pool.waitingForParent() && now - fetchRetryAt >= 0;
                if (due) {
                    ChainProtocol.writeRequest(reply, ChainProtocol.GET_BLOCK);
                    pool.missingParent(reply, ChainProtocol.BODY);
                }
            }
            if (due) {
                fetchRetryAt = now + FETCH_RETRY_MILLIS;
                broadcast();
            }
        }

        /**
         * Restarts Wi-Fi and the UDP socket when no packet has arrived for {@link #SILENT_MILLIS}. Only this thread
         * touches the network, so the mining thread keeps running during a restart; the pool is not involved.
         */
        private void restartIfSilent(int now) {
            if (now - lastHeard < SILENT_MILLIS || now - nextRecovery < 0) {
                return;
            }
            recoveries++;
            nextRecovery = now + RECOVER_BACKOFF_MILLIS;
            lastHeard = now;
            Serial.println("NET silent for " + SILENT_MILLIS / 1000 + " s: restarting Wi-Fi (restart " + recoveries + ")");
            NodeIo.connect();
        }

        private void reportLostPeers(int now) {
            synchronized (pool) {
                int lost = peers.nextDead(now);
                while (lost >= 0) {
                    Serial.print("Peer ");
                    Serial.print(NodeIo.hexWord(peers.uuids(), lost * NodeId.SIZE));
                    Serial.println(" lost");
                    lost = peers.nextDead(now);
                }
            }
        }

        private void serve(int length, int now) {
            if (!ChainProtocol.valid(incoming, length)) {
                ignored++;
                return;
            }
            int type = ChainProtocol.type(incoming);
            if (type == ChainProtocol.BLOCK) {
                serveBlock(now);
            } else if (type == ChainProtocol.GET_BLOCK) {
                int slot = pool.find(incoming, ChainProtocol.BODY);
                if (slot >= 0 && pool.isValid(slot)) {
                    ChainProtocol.writeBlock(reply, pool, slot);
                    answer();
                }
            } else if (type == ChainProtocol.GET_TIP && pool.height(pool.tip()) > 0) {
                ChainProtocol.writeBlock(reply, pool, pool.tip());
                answer();
            } else if (type == ChainProtocol.HELLO) {
                serveHello(now);
            }
        }

        private void serveBlock(int now) {
            int result = pool.add(incoming, ChainProtocol.BODY, ChainProtocol.height(incoming),
                    ChainProtocol.work(incoming), ChainProtocol.periodStart(incoming));
            if (result == BlockPool.NEED_PARENT) {
                ChainProtocol.writeRequest(reply, ChainProtocol.GET_BLOCK);
                pool.missingParent(reply, ChainProtocol.BODY);
                answer();
                fetchRetryAt = now + FETCH_RETRY_MILLIS;
            } else if (result == BlockPool.INVALID) {
                Serial.println("Rejected an invalid block");
            }
        }

        /** Notes the peer's heartbeat and, when it holds a heavier tip than ours, asks it for that tip. */
        private void serveHello(int now) {
            if (NodeId.same(incoming, ChainProtocol.HELLO_UUID, uuid, 0)) {
                // This node's own heartbeat, echoed back by the network.
                return;
            }
            long work = ChainProtocol.work(incoming);
            if (peers.update(incoming, ChainProtocol.HELLO_UUID, ChainProtocol.height(incoming), work,
                    ChainProtocol.helloFlags(incoming), now)) {
                Serial.print("Peer ");
                Serial.print(NodeIo.hexWord(incoming, ChainProtocol.HELLO_UUID));
                Serial.println(" joined");
            }
            if (work > pool.work(pool.tip()) && now - tipRequestedAt >= TIP_REQUEST_MILLIS) {
                tipRequestedAt = now;
                ChainProtocol.writeRequest(reply, ChainProtocol.GET_TIP);
                answer();
            }
        }
    }

    public static void main(String[] args) {
        byte[] outgoing = new byte[ChainProtocol.PACKET_SIZE];

        Serial.begin(BaudRate.BAUD_115200);
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        GameScreen.message("CONNECTING WIFI", TftTouchShield.CYAN);
        NodeIo.connect();


        byte[] uuid = new byte[NodeId.SIZE];
        NodeId.generate(uuid);
        int nodeId = NodeId.shortId(uuid, 0);
        Serial.print("Miner node ");
        NodeId.print(uuid);
        Serial.println("");
        GameScreen.header("MINER", TftTouchShield.GREEN);

        BlockPool pool = new BlockPool(INITIAL_BITS, RETARGET_INTERVAL, TARGET_SECONDS);
        PeerTable peers = new PeerTable();
        SyncGate gate = new SyncGate();
        Miner miner = new Miner();
        ChainProtocol.writeRequest(outgoing, ChainProtocol.GET_TIP);
        Udp.broadcast(NodeIo.PORT, outgoing, ChainProtocol.PACKET_SIZE);
        int template = -1;
        int printedTip = pool.tipChanges();
        int shownNodes = -1;
        int mined = 0;
        showTip(pool, nodeId, mined);

        Thread.startVirtualThread(new Receiver(pool, peers, uuid, nodeId));

        while (true) {
            int millis = Clock.millis();
            boolean waiting;
            synchronized (pool) {
                // Peers that announced more work than our tip holds: wait for that chain instead of mining a rival.
                boolean behind = peers.bestWork(millis) > pool.work(pool.tip());
                waiting = gate.shouldWait(behind, pool.tipChanges(), millis);
                mining = !waiting;
                if (pool.tipChanges() != printedTip) {
                    printedTip = pool.tipChanges();
                    followNetworkTime(pool.time(pool.tip()));
                    printTip(pool, nodeId);
                    showTip(pool, nodeId, mined);
                    shownNodes = -1;
                }
                if (pool.tipChanges() != template) {
                    template = pool.tipChanges();
                    miner.prepare(pool, nodeId, now());
                }
                int nodes = nodesKey(peers, waiting, millis);
                if (nodes != shownNodes) {
                    shownNodes = nodes;
                    showNodes(peers, waiting, millis);
                }
            }
            // The mining slice touches only the miner's own block, so it runs outside the pool's monitor.
            if (!waiting && miner.mine(SLICE)) {
                synchronized (pool) {
                    int result = pool.add(miner.block(), 0, 0, 0L, 0);
                    if (result == BlockPool.NEW_TIP) {
                        ChainProtocol.writeBlock(outgoing, pool, pool.tip());
                        Udp.broadcast(NodeIo.PORT, outgoing, ChainProtocol.PACKET_SIZE);
                        Serial.println("Mined after " + miner.attempts() + " hashes");
                        mined++;
                    }
                }
            }
            // A short pause per slice lets the receiver thread run, and other tasks such as the UNO Q's bridge to Linux.
            Delay.millis(1);
        }
    }

    /** One number that changes whenever what {@link #showNodes} would draw changes. */
    private static int nodesKey(PeerTable peers, boolean waiting, int millis) {
        return peers.alive(millis) * 1000 + peers.suspect(millis) * 100 + peers.mining(millis) * 10
                + (waiting ? 1 : 0);
    }

    /** Row 2: how many nodes this node knows of (itself included), how many mine, and whether it is catching up. */
    private static void showNodes(PeerTable peers, boolean waiting, int millis) {
        int alive = peers.alive(millis);
        int suspect = peers.suspect(millis);
        int miners = peers.mining(millis) + (waiting ? 0 : 1);
        GameScreen.row(2, 1, waiting ? TftTouchShield.YELLOW : TftTouchShield.GREEN);
        TftTouchShield.print("Nodes " + (alive + 1));
        TftTouchShield.print("  mining " + miners);
        if (suspect > 0) {
            TftTouchShield.print("  lost? " + suspect);
        }
        if (waiting) {
            TftTouchShield.print("  SYNC");
        }
    }

    /** Network time in seconds: this board's uptime, moved forward to never lag the chain's tip. */
    private static int now() {
        return Clock.millis() / 1000 + clockOffset;
    }

    private static void followNetworkTime(int tipTime) {
        int now = now();
        if (tipTime > now) {
            clockOffset = clockOffset + tipTime - now;
        }
    }

    private static void printTip(BlockPool pool, int nodeId) {
        int tip = pool.tip();
        byte[] blocks = pool.blocks();
        int base = tip * BlockFormat.MAX_SIZE;
        int miner = Bytes.readInt(blocks, base + BlockFormat.MINER);
        Serial.print("TIP #" + pool.height(tip) + " bits " + pool.bits(tip) + " work ");
        Serial.print(pool.work(tip));
        Serial.println(miner == nodeId ? " miner " + miner + " (mined here)" : " miner " + miner);
        Serial.print("  n = ");
        Serial.print(Bytes.readLong(blocks, base + BlockFormat.NUMBER));
        Serial.print(" = ");
        int count = Bytes.unsigned(blocks[base + BlockFormat.COUNT]);
        for (int i = 0; i < count; i++) {
            NodeIo.printFactor(Bytes.readLong(blocks, base + BlockFormat.FACTORS + i * 9),
                    Bytes.unsigned(blocks[base + BlockFormat.FACTORS + i * 9 + 8]), i == 0);
        }
        Serial.println("");
        Serial.print("  hash ");
        NodeIo.printHex(pool.hashes(), tip * Sha256.DIGEST_SIZE, Sha256.DIGEST_SIZE);
        Serial.println("");
    }

    private static void showTip(BlockPool pool, int nodeId, int mined) {
        int tip = pool.tip();
        byte[] blocks = pool.blocks();
        int base = tip * BlockFormat.MAX_SIZE;
        GameScreen.row(0, 2, TftTouchShield.WHITE);
        TftTouchShield.print("Height " + pool.height(tip));
        GameScreen.row(1, 2, TftTouchShield.WHITE);
        TftTouchShield.print("Bits " + pool.bits(tip) + "  Mined " + mined);
        if (pool.height(tip) == 0) {
            GameScreen.row(3, 1, TftTouchShield.GRAY);
            TftTouchShield.print("Genesis: mining the first block");
            return;
        }
        int blockMiner = Bytes.readInt(blocks, base + BlockFormat.MINER);
        GameScreen.row(3, 1, blockMiner == nodeId ? TftTouchShield.GREEN : TftTouchShield.CYAN);
        TftTouchShield.print(blockMiner == nodeId ? "Tip mined here" : "Tip mined by " + blockMiner);
        GameScreen.row(4, 1, TftTouchShield.WHITE);
        TftTouchShield.print("n = " + Bytes.readLong(blocks, base + BlockFormat.NUMBER));
        GameScreen.row(5, 1, TftTouchShield.WHITE);
        TftTouchShield.print("= ");
        int count = Bytes.unsigned(blocks[base + BlockFormat.COUNT]);
        for (int i = 0; i < count; i++) {
            GameScreen.factor(Bytes.readLong(blocks, base + BlockFormat.FACTORS + i * 9),
                    Bytes.unsigned(blocks[base + BlockFormat.FACTORS + i * 9 + 8]), i == 0);
        }
        GameScreen.row(6, 1, TftTouchShield.YELLOW);
        TftTouchShield.print("hash ");
        GameScreen.hashPrefix(pool.hashes(), tip * Sha256.DIGEST_SIZE);
        GameScreen.row(7, 1, TftTouchShield.GRAY);
        TftTouchShield.print("Work ");
        TftTouchShield.print("" + pool.work(tip));
    }
}
