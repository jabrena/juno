package io.github.jabrena.juno.usecases.miner;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.net.Udp;
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
 * <p>Each pass of the loop serves a few packets, rebuilds the block template when the tip moved,
 * and tries {@link #SLICE} nonces. A new node broadcasts {@code GET_TIP} and
 * catches up from the answers. The screen shows the tip; serial at 115200 baud logs it:
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
    private static final int PACKETS_PER_PASS = 4;
    private static final int ASK_TIP_MILLIS = 10_000;

    private static int clockOffset;

    private MinerGame() {
    }

    public static void main(String[] args) {
        byte[] incoming = new byte[ChainProtocol.PACKET_SIZE];
        byte[] outgoing = new byte[ChainProtocol.PACKET_SIZE];
        int[] source = new int[Udp.ENDPOINT_SIZE];

        Serial.begin(BaudRate.BAUD_115200);
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        GameScreen.message("CONNECTING WIFI", TftTouchShield.CYAN);
        NodeIo.connect();


        int nodeId = Clock.micros();
        if (nodeId == 0) {
            nodeId = 1;
        }
        Serial.println("Miner node " + nodeId);
        GameScreen.header("MINER", TftTouchShield.GREEN);

        BlockPool pool = new BlockPool(INITIAL_BITS, RETARGET_INTERVAL, TARGET_SECONDS);
        Miner miner = new Miner();
        ChainProtocol.writeRequest(outgoing, ChainProtocol.GET_TIP);
        Udp.broadcast(NodeIo.PORT, outgoing, ChainProtocol.PACKET_SIZE);
        int lastAsk = Clock.millis();
        int template = -1;
        int printedTip = pool.tipChanges();
        int mined = 0;
        showTip(pool, nodeId, mined);

        while (true) {
            for (int i = 0; i < PACKETS_PER_PASS; i++) {
                int length = Udp.receive(incoming, ChainProtocol.PACKET_SIZE, source);
                if (length <= 0) {
                    break;
                }
                serve(pool, incoming, length, source, outgoing);
            }

            if (pool.tipChanges() != printedTip) {
                printedTip = pool.tipChanges();
                followNetworkTime(pool.time(pool.tip()));
                printTip(pool, nodeId);
                showTip(pool, nodeId, mined);
            }
            if (pool.tipChanges() != template) {
                template = pool.tipChanges();
                miner.prepare(pool, nodeId, now());
            }
            if (miner.mine(SLICE)) {
                int result = pool.add(miner.block(), 0, 0, 0L, 0);
                if (result == BlockPool.NEW_TIP) {
                    ChainProtocol.writeBlock(outgoing, pool, pool.tip());
                    Udp.broadcast(NodeIo.PORT, outgoing, ChainProtocol.PACKET_SIZE);
                    Serial.println("Mined after " + miner.attempts() + " hashes");
                    mined++;
                }
            }

            int millis = Clock.millis();
            if (pool.height(pool.tip()) == 0 && millis - lastAsk >= ASK_TIP_MILLIS) {
                ChainProtocol.writeRequest(outgoing, ChainProtocol.GET_TIP);
                Udp.broadcast(NodeIo.PORT, outgoing, ChainProtocol.PACKET_SIZE);
                lastAsk = millis;
            }
            // A short pause per slice lets other tasks run, such as the UNO Q's bridge to Linux.
            Delay.millis(1);
        }
    }

    private static void serve(BlockPool pool, byte[] packet, int length, int[] source, byte[] reply) {
        if (!ChainProtocol.valid(packet, length)) {
            return;
        }
        int type = ChainProtocol.type(packet);
        if (type == ChainProtocol.BLOCK) {
            int result = pool.add(packet, ChainProtocol.BODY, ChainProtocol.height(packet), ChainProtocol.work(packet),
                    ChainProtocol.periodStart(packet));
            if (result == BlockPool.NEED_PARENT) {
                ChainProtocol.writeRequest(reply, ChainProtocol.GET_BLOCK);
                pool.missingParent(reply, ChainProtocol.BODY);
                Udp.send(source, source[Udp.PORT], reply, ChainProtocol.PACKET_SIZE);
            } else if (result == BlockPool.INVALID) {
                Serial.println("Rejected an invalid block");
            }
        } else if (type == ChainProtocol.GET_BLOCK) {
            int slot = pool.find(packet, ChainProtocol.BODY);
            if (slot >= 0 && pool.isValid(slot)) {
                ChainProtocol.writeBlock(reply, pool, slot);
                Udp.send(source, source[Udp.PORT], reply, ChainProtocol.PACKET_SIZE);
            }
        } else if (type == ChainProtocol.GET_TIP && pool.height(pool.tip()) > 0) {
            ChainProtocol.writeBlock(reply, pool, pool.tip());
            Udp.send(source, source[Udp.PORT], reply, ChainProtocol.PACKET_SIZE);
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
