package io.github.jabrena.juno.usecases.miner.services;

import io.github.jabrena.juno.api.hardware.net.Udp;
import io.github.jabrena.juno.api.hardware.net.Wifi;
import io.github.jabrena.juno.api.hardware.serial.Serial;
import io.github.jabrena.juno.games.blockchain.NodeIo;
import io.github.jabrena.juno.usecases.miner.chain.BlockPool;
import io.github.jabrena.juno.usecases.miner.chain.ChainProtocol;
import io.github.jabrena.juno.usecases.miner.commons.Miner2State;
import io.github.jabrena.juno.usecases.miner.peers.NodeId;
import io.github.jabrena.juno.usecases.miner.peers.PeerTable;

/**
 * The network side of the node: a packet receiver turned into a step the frame loop calls. Each
 * step serves a few packets, moves this node's claim on the blocks board, sends the heartbeat when it is due, asks again for a missing parent, logs lost peers and
 * the network counters, and restarts a Wi-Fi link that went silent. It runs on the main thread, between the mining
 * slices, so it shares the pool with the miner without a lock.
 */
final class NetworkService {
    /** Packets served per step; a step comes once a frame, so this is several hundred a second. */
    private static final int PACKETS_PER_STEP = 16;
    /** How often a node asks the network for the current tip, even when it believes it is up to date. */
    private static final int ASK_TIP_MILLIS = 30_000;
    /** How long to wait for a requested parent before asking everyone for it again. */
    private static final int FETCH_RETRY_MILLIS = 1500;
    /** The shortest time between two requests for a tip that a heartbeat said is heavier than ours. */
    private static final int TIP_REQUEST_MILLIS = 2000;
    /** How often the node logs its network counters on serial. */
    private static final int STATS_MILLIS = 10_000;
    /**
     * Silence after which the node assumes its Wi-Fi link is stuck (a UNO R4 WiFi's modem can stop delivering UDP
     * until power is cycled) and restarts it. Heartbeats arrive about every five seconds, so this is six of them.
     */
    private static final int SILENT_MILLIS = 30_000;
    /** The shortest time between two Wi-Fi restarts, so a network that is really empty does not restart forever. */
    private static final int RECOVER_BACKOFF_MILLIS = 120_000;

    private final Miner2State model;
    private final WifiLink link;
    private final ClaimService claims;
    private final BlockPool pool;
    private final PeerTable peers;
    private final int helloEvery;
    private final byte[] incoming = new byte[ChainProtocol.PACKET_SIZE];
    private final byte[] reply = new byte[ChainProtocol.PACKET_SIZE];
    private final int[] source = new int[Udp.ENDPOINT_SIZE];
    private int nextHello;
    private int nextAsk;
    private int nextStats;
    private int fetchRetryAt;
    private int tipRequestedAt;
    /** When a packet last arrived, and the earliest time another Wi-Fi restart may happen. */
    private int lastHeard;
    private int nextRecovery;

    NetworkService(Miner2State model, WifiLink link, ClaimService claims, int now) {
        this.model = model;
        this.link = link;
        this.claims = claims;
        this.pool = model.pool();
        this.peers = model.peers();
        // Nodes beat at slightly different rates (up to a second apart), so their heartbeats do not collide.
        this.helloEvery = PeerTable.HELLO_INTERVAL_MILLIS + (model.nodeId() & 1023);
        this.nextHello = now;
        this.nextAsk = now + ASK_TIP_MILLIS;
        this.nextStats = now + STATS_MILLIS;
        this.tipRequestedAt = now - TIP_REQUEST_MILLIS;
        this.lastHeard = now;
        this.nextRecovery = now;
    }

    /**
     * Asks the network for its tip, so a node that joins late catches up at once, and reads its own address. Starts
     * the silence count afresh, since the link may have been down for a while.
     */
    void begin(int now) {
        lastHeard = now;
        Wifi.localIP(model.address());
        ChainProtocol.writeRequest(reply, ChainProtocol.GET_TIP);
        broadcast();
    }

    void step(int now) {
        for (int i = 0; i < PACKETS_PER_STEP; i++) {
            int length = Udp.receive(incoming, ChainProtocol.PACKET_SIZE, source);
            if (length <= 0) {
                break;
            }
            model.countReceived();
            lastHeard = now;
            serve(length, now);
        }
        if (claims.step(now, reply)) {
            broadcast();
        }
        heartbeat(now);
        retryFetch(now);
        reportLostPeers(now);
        reportNetwork(now);
        restartIfSilent(now);
    }

    /** Tells the network this node is alive and where its tip is, and now and then asks for the network's tip. */
    private void heartbeat(int now) {
        if (now - nextHello >= 0) {
            nextHello = now + helloEvery;
            int tip = pool.tip();
            ChainProtocol.writeHello(reply, model.uuidBytes(), pool.height(tip), pool.work(tip),
                    model.syncing() ? 0 : ChainProtocol.MINING);
            claims.stampHello(reply);
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
        model.countSend(Udp.broadcast(NodeIo.PORT, reply, ChainProtocol.PACKET_SIZE));
    }

    /** Sends {@code reply} to the node that sent the packet being served. */
    private void answer() {
        model.countSend(Udp.send(source, source[Udp.PORT], reply, ChainProtocol.PACKET_SIZE));
    }

    /**
     * Logs, every {@link #STATS_MILLIS}, one line: the address and the packet counters,
     * with the peers this node knows and its tip's height. The NET view shows the counters as they change.
     */
    private void reportNetwork(int now) {
        if (now - nextStats < 0) {
            return;
        }
        nextStats = now + STATS_MILLIS;
        int[] address = model.address();
        Wifi.localIP(address);
        Serial.print("NET " + address[0] + "." + address[1] + "." + address[2] + "." + address[3]);
        Serial.print(" rx " + model.received() + " bad " + model.ignored());
        Serial.print(" restarts " + model.restarts());
        Serial.print(" tx " + model.sent() + " fail " + model.failed());
        Serial.println(" peers " + peers.alive(now) + "+" + peers.suspect(now) + " h " + pool.height(pool.tip()));
    }

    /** Asks everyone again for a parent that is still missing: the request or its answer may have been lost. */
    private void retryFetch(int now) {
        if (pool.waitingForParent() && now - fetchRetryAt >= 0) {
            ChainProtocol.writeRequest(reply, ChainProtocol.GET_BLOCK);
            pool.missingParent(reply, ChainProtocol.BODY);
            fetchRetryAt = now + FETCH_RETRY_MILLIS;
            broadcast();
        }
    }

    /**
     * Restarts Wi-Fi and the UDP socket when no packet has arrived for {@link #SILENT_MILLIS}. The link rejoins step by
     * step, so the views keep redrawing; the node neither serves the network nor mines until it is back.
     */
    private void restartIfSilent(int now) {
        if (now - lastHeard < SILENT_MILLIS || now - nextRecovery < 0) {
            return;
        }
        model.countRestart();
        nextRecovery = now + RECOVER_BACKOFF_MILLIS;
        lastHeard = now;
        // In pieces: a runtime string longer than 31 characters overflows its slot.
        Serial.print("NET silent: restarting Wi-Fi");
        Serial.print(" (restart ");
        Serial.print(model.restarts());
        Serial.println(")");
        link.start(now);
    }

    private void reportLostPeers(int now) {
        int lost = peers.nextDead(now);
        while (lost >= 0) {
            Serial.print("Peer ");
            Serial.print(NodeIo.hexWord(peers.uuids(), lost * NodeId.SIZE));
            Serial.println(" lost");
            lost = peers.nextDead(now);
        }
    }

    private void serve(int length, int now) {
        if (!ChainProtocol.valid(incoming, length)) {
            model.countIgnored();
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
        } else if ((type == ChainProtocol.CLAIM || type == ChainProtocol.TAKEN) && claims.serve(incoming, reply, now)) {
            answer();
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
        if (NodeId.same(incoming, ChainProtocol.HELLO_UUID, model.uuidBytes(), 0)) {
            // This node's own heartbeat, echoed back by the network.
            return;
        }
        claims.serveHello(incoming, now);
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
