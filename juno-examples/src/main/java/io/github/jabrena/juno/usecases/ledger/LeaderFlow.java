package io.github.jabrena.juno.usecases.ledger;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.net.Udp;
import io.github.jabrena.juno.api.tft.TftTouchShield;
import io.github.jabrena.juno.games.blockchain.GameScreen;
import io.github.jabrena.juno.games.blockchain.NodeIo;
import io.github.jabrena.juno.games.blockchain.Sha256;

/**
 * The LEADER role of {@link LedgerGame}: hands out numbers to factor and keeps a SHA-256 hash
 * chain of the verified answers. Meant for an UNO Q, one per network, with {@link WorkerFlow}s on UNO
 * R4 WiFi boards.
 *
 * <p>Workers find the leader by broadcasting {@code JOIN} on UDP port {@link NodeIo#PORT}, so
 * nothing is configured by address and workers can join at any time. Each verified result becomes a
 * block, shown on screen and printed over serial at 115200 baud:
 *
 * <pre>
 * BLOCK #1 task 1 worker 1234567 at 48211 ms
 *   n = 780104467343 = 17 x 45888498079
 *   prev 6f1c...
 *   hash 0b9e...
 * </pre>
 *
 * The chain lives in this board's RAM and serial log; it is a tamper-evident log with one writer,
 * not a consensus protocol. See {@link FactorLedger} for leases and verification and
 * {@link FactorProtocol} for the wire format.
 */
final class LeaderFlow {
    /** Bit length of every number handed out; at 40 bits a worker answers in well under a second. */
    private static final int BITS = 40;
    private static final long SEED = 0x2545F4914F6CDD1DL;

    private LeaderFlow() {
    }

    /** Leads until the MENU button is tapped; the chain starts again from genesis on the next run. */
    static void run(byte[] incoming, byte[] outgoing, int[] source) {

        Serial.println("Role: leader");
        GameScreen.role("LEADER", TftTouchShield.YELLOW);
        FactorLedger ledger = new FactorLedger(BITS, SEED, Clock.millis());
        printBlock(ledger);
        showBlock(ledger);
        showWorkers(0);
        int printed = ledger.height();
        int workers = 0;
        while (!GameScreen.menuTapped()) {
            int now = Clock.millis();
            int length = Udp.receive(incoming, FactorProtocol.PACKET_SIZE, source);
            if (length > 0 && ledger.handle(incoming, length, outgoing, now)) {
                Udp.send(source, source[Udp.PORT], outgoing, FactorProtocol.PACKET_SIZE);
            }
            if (ledger.height() != printed) {
                printed = ledger.height();
                printBlock(ledger);
                showBlock(ledger);
            }
            ledger.expire(now);
            if (ledger.workers() != workers) {
                workers = ledger.workers();
                Serial.println("Workers: " + workers);
                showWorkers(workers);
            }
            if (length <= 0) {
                Delay.millis(5);
            }
        }
    }

    private static void showWorkers(int workers) {
        GameScreen.row(1, 2, workers > 0 ? TftTouchShield.GREEN : TftTouchShield.YELLOW);
        TftTouchShield.print(workers > 0 ? "Workers " + workers : "No workers yet");
    }

    private static void showBlock(FactorLedger ledger) {
        GameScreen.row(0, 2, TftTouchShield.WHITE);
        TftTouchShield.print("Height " + ledger.height());
        if (ledger.blockTask() == 0) {
            GameScreen.row(3, 1, TftTouchShield.GRAY);
            TftTouchShield.print("Genesis block");
        } else {
            GameScreen.row(3, 1, TftTouchShield.CYAN);
            TftTouchShield.print("Task " + ledger.blockTask() + " by " + ledger.blockNode());
            GameScreen.row(4, 1, TftTouchShield.WHITE);
            TftTouchShield.print("n = " + ledger.blockNumber());
            GameScreen.row(5, 1, TftTouchShield.WHITE);
            TftTouchShield.print("= ");
            for (int i = 0; i < ledger.blockCount(); i++) {
                GameScreen.factor(ledger.blockPrime(i), ledger.blockExponent(i), i == 0);
            }
        }
        GameScreen.row(6, 1, TftTouchShield.GRAY);
        TftTouchShield.print("prev ");
        GameScreen.hashPrefix(ledger.previousHash(), 0);
        GameScreen.row(7, 1, TftTouchShield.YELLOW);
        TftTouchShield.print("hash ");
        GameScreen.hashPrefix(ledger.hash(), 0);
    }

    private static void printBlock(FactorLedger ledger) {
        Serial.print("BLOCK #" + ledger.height());
        if (ledger.blockTask() == 0) {
            Serial.print(" genesis");
        } else {
            Serial.print(" task " + ledger.blockTask() + " worker " + ledger.blockNode());
        }
        Serial.println(" at " + ledger.blockTime() + " ms");
        if (ledger.blockTask() != 0) {
            Serial.print("  n = ");
            Serial.print(ledger.blockNumber());
            Serial.print(" = ");
            for (int i = 0; i < ledger.blockCount(); i++) {
                NodeIo.printFactor(ledger.blockPrime(i), ledger.blockExponent(i), i == 0);
            }
            Serial.println("");
        }
        Serial.print("  prev ");
        NodeIo.printHex(ledger.previousHash(), 0, Sha256.DIGEST_SIZE);
        Serial.println("");
        Serial.print("  hash ");
        NodeIo.printHex(ledger.hash(), 0, Sha256.DIGEST_SIZE);
        Serial.println("");
    }
}
