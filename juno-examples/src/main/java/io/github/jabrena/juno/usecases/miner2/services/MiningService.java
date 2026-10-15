package io.github.jabrena.juno.usecases.miner2.services;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.net.Udp;
import io.github.jabrena.juno.games.blockchain.Bytes;
import io.github.jabrena.juno.games.blockchain.NodeIo;
import io.github.jabrena.juno.games.blockchain.Sha256;
import io.github.jabrena.juno.usecases.miner.BlockFormat;
import io.github.jabrena.juno.usecases.miner.BlockPool;
import io.github.jabrena.juno.usecases.miner.ChainProtocol;
import io.github.jabrena.juno.usecases.miner.Miner;
import io.github.jabrena.juno.usecases.miner.SyncGate;
import io.github.jabrena.juno.usecases.miner2.commons.Miner2State;

/**
 * The mining side of the node: the original Miner's main loop, turned into a step the frame loop calls. Each step
 * decides whether to wait for a heavier chain ({@link SyncGate}), logs a tip that moved, rebuilds the block template
 * when it did, and then tries nonces {@link #SLICE} at a time until {@link #BUDGET_MILLIS} have passed, leaving the
 * rest of the frame to the views and the network.
 */
final class MiningService {
    /** Nonces tried between two looks at the clock. */
    static final int SLICE = 32;
    /** How long one step mines; the frame is a little longer. */
    static final int BUDGET_MILLIS = 20;

    private final Miner2State model;
    private final Miner2Persistence persistence;
    private final BlockPool pool;
    private final Miner miner = new Miner();
    private final SyncGate gate = new SyncGate();
    private final byte[] outgoing = new byte[ChainProtocol.PACKET_SIZE];
    private int template = -1;
    private int loggedTip;
    private int clockOffset;

    MiningService(Miner2State model, Miner2Persistence persistence) {
        this.model = model;
        this.persistence = persistence;
        this.pool = model.pool();
        this.loggedTip = pool.tipChanges();
    }

    void step() {
        int start = Clock.millis();
        // Peers that announced more work than our tip holds: wait for that chain instead of mining a rival.
        boolean behind = model.peers().bestWork(start) > pool.work(pool.tip());
        boolean waiting = gate.shouldWait(behind, pool.tipChanges(), start);
        model.publishSyncing(waiting);
        if (pool.tipChanges() != loggedTip) {
            loggedTip = pool.tipChanges();
            persistence.appendChain(pool, pool.tip());
            followNetworkTime(pool.time(pool.tip()));
            printTip();
        }
        if (waiting) {
            return;
        }
        if (pool.tipChanges() != template) {
            template = pool.tipChanges();
            miner.prepare(pool, model.nodeId(), now());
        }
        do {
            int before = miner.attempts();
            boolean found = miner.mine(SLICE);
            model.countHashes(miner.attempts() - before);
            if (found) {
                publish();
                return;
            }
        } while (Clock.millis() - start < BUDGET_MILLIS);
    }

    /** Adds the block just mined to the pool and, as it is the new tip, broadcasts it. */
    private void publish() {
        if (pool.add(miner.block(), 0, 0, 0L, 0) == BlockPool.NEW_TIP) {
            ChainProtocol.writeBlock(outgoing, pool, pool.tip());
            model.countSend(Udp.broadcast(NodeIo.PORT, outgoing, ChainProtocol.PACKET_SIZE));
            Serial.print("Mined after ");
            Serial.print(miner.attempts());
            Serial.println(" hashes");
            model.countMined();
            persistence.appendMining(pool, pool.tip());
        }
    }

    /** Network time in seconds: this board's uptime, moved forward to never lag the chain's tip. */
    private int now() {
        return Clock.millis() / 1000 + clockOffset;
    }

    private void followNetworkTime(int tipTime) {
        int now = now();
        if (tipTime > now) {
            clockOffset = clockOffset + tipTime - now;
        }
    }

    private void printTip() {
        int tip = pool.tip();
        byte[] blocks = pool.blocks();
        int base = tip * BlockFormat.MAX_SIZE;
        int blockMiner = Bytes.readInt(blocks, base + BlockFormat.MINER);
        Serial.print("TIP #" + pool.height(tip) + " bits " + pool.bits(tip) + " work ");
        Serial.print(pool.work(tip));
        Serial.print(" miner ");
        Serial.print(blockMiner);
        Serial.println(blockMiner == model.nodeId() ? " (mined here)" : "");
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
}
