package io.github.jabrena.juno.api.net.ledger;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.net.Udp;

/**
 * Leader of a small factorization network: hands out numbers to factor and keeps a SHA-256 hash
 * chain of the verified answers.
 *
 * <p>Meant for an UNO Q, with {@link FactorWorker} running on one or more UNO R4 WiFi boards (it
 * builds for either board). Workers find the leader by broadcasting {@code JOIN} on UDP port
 * {@link LedgerIo#PORT}, so nothing is configured by address and workers can join at any time.
 * Each verified result becomes a block, printed over serial at 115200 baud:
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
@Board({ArduinoUnoQ.class, ArduinoUnoR4WiFi.class})
public final class FactorLedgerLeader {
    /** Bit length of every number handed out; at 40 bits a worker answers in well under a second. */
    private static final int BITS = 40;
    private static final long SEED = 0x2545F4914F6CDD1DL;

    private FactorLedgerLeader() {
    }

    public static void main(String[] args) {
        byte[] incoming = new byte[FactorProtocol.PACKET_SIZE];
        byte[] outgoing = new byte[FactorProtocol.PACKET_SIZE];
        int[] source = new int[Udp.ENDPOINT_SIZE];

        Serial.begin(BaudRate.BAUD_115200);
        Serial.println("Factor ledger leader");
        LedgerIo.connect();

        FactorLedger ledger = new FactorLedger(BITS, SEED, Clock.millis());
        printBlock(ledger);
        int printed = ledger.height();
        int workers = 0;
        while (true) {
            int now = Clock.millis();
            int length = Udp.receive(incoming, FactorProtocol.PACKET_SIZE, source);
            if (length > 0 && ledger.handle(incoming, length, outgoing, now)) {
                Udp.send(source, source[Udp.PORT], outgoing, FactorProtocol.PACKET_SIZE);
            }
            if (ledger.height() != printed) {
                printed = ledger.height();
                printBlock(ledger);
            }
            ledger.expire(now);
            if (ledger.workers() != workers) {
                workers = ledger.workers();
                Serial.println("Workers: " + workers);
            }
            if (length <= 0) {
                Delay.millis(5);
            }
        }
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
                LedgerIo.printFactor(ledger.blockPrime(i), ledger.blockExponent(i), i == 0);
            }
            Serial.println("");
        }
        Serial.print("  prev ");
        LedgerIo.printHex(ledger.previousHash(), Sha256.DIGEST_SIZE);
        Serial.println("");
        Serial.print("  hash ");
        LedgerIo.printHex(ledger.hash(), Sha256.DIGEST_SIZE);
        Serial.println("");
    }
}
