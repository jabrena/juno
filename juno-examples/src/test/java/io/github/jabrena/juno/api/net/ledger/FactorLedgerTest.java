package io.github.jabrena.juno.api.net.ledger;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.assertj.core.api.Assertions.assertThat;

class FactorLedgerTest {
    private static final int BITS = 40;
    private static final int A = 0x0A;
    private static final int B = 0x0B;

    private final FactorLedger ledger = new FactorLedger(BITS, 0x2545F4914F6CDD1DL, 100);
    private final byte[] reply = new byte[FactorProtocol.PACKET_SIZE];

    @Test
    void startsWithAGenesisBlockHashedFromTheDocumentedLayout() throws NoSuchAlgorithmException {
        assertThat(ledger.height()).isZero();
        assertThat(ledger.blockTask()).isZero();
        assertThat(ledger.previousHash()).containsOnly(0);
        assertThat(ledger.hash()).isEqualTo(expectedHash(0, 100, new byte[32], 0, 0, 0, new long[0], new int[0]));
    }

    @Test
    void handsOutANumberOfExactlyTheConfiguredSizeToAJoiningWorker() {
        assertThat(join(A, 0)).isTrue();

        assertThat(FactorProtocol.type(reply)).isEqualTo(FactorProtocol.TASK);
        assertThat(FactorProtocol.taskId(reply)).isEqualTo(1);
        assertThat(64 - Long.numberOfLeadingZeros(FactorProtocol.number(reply))).isEqualTo(BITS);
        assertThat(ledger.workers()).isEqualTo(1);
        // Asking again before answering gets the same task, not a new one.
        join(A, 10);
        assertThat(FactorProtocol.taskId(reply)).isEqualTo(1);
    }

    @Test
    void chainsAVerifiedResultAndHandsOutTheNextTask() throws NoSuchAlgorithmException {
        join(A, 0);
        long n = FactorProtocol.number(reply);
        byte[] genesis = ledger.hash().clone();

        assertThat(answer(A, 1, n, 500)).isTrue();

        assertThat(ledger.height()).isEqualTo(1);
        assertThat(ledger.blockTask()).isEqualTo(1);
        assertThat(ledger.blockNode()).isEqualTo(A);
        assertThat(ledger.blockNumber()).isEqualTo(n);
        assertThat(ledger.previousHash()).isEqualTo(genesis);
        long[] primes = new long[ledger.blockCount()];
        int[] exponents = new int[ledger.blockCount()];
        for (int i = 0; i < primes.length; i++) {
            primes[i] = ledger.blockPrime(i);
            exponents[i] = ledger.blockExponent(i);
        }
        assertThat(Factorizer.verify(n, primes, exponents, primes.length)).isTrue();
        assertThat(ledger.hash()).isEqualTo(expectedHash(1, 500, genesis, 1, A, n, primes, exponents));
        assertThat(FactorProtocol.taskId(reply)).isEqualTo(2);
    }

    @Test
    void ignoresARepeatedResultAndRejectsAWrongOne() {
        join(A, 0);
        long first = FactorProtocol.number(reply);
        answer(A, 1, first, 10);
        long second = FactorProtocol.number(reply);

        answer(A, 1, first, 20);
        assertThat(ledger.height()).as("a resent result is not chained twice").isEqualTo(1);
        assertThat(FactorProtocol.taskId(reply)).isEqualTo(2);

        byte[] packet = new byte[FactorProtocol.PACKET_SIZE];
        FactorProtocol.write(packet, FactorProtocol.RESULT, A, 2, second);
        FactorProtocol.writeFactors(packet, new long[] {second}, new int[] {1}, 1);
        assertThat(ledger.handle(packet, packet.length, reply, 30)).isTrue();
        assertThat(ledger.height()).as("claiming a composite is prime").isEqualTo(1);
        assertThat(FactorProtocol.taskId(reply)).as("the same task is handed back").isEqualTo(2);
    }

    @Test
    void reassignsASilentWorkersTaskAndStillAcceptsItsLateAnswerOnce() {
        join(A, 0);
        long n = FactorProtocol.number(reply);

        ledger.expire(FactorLedger.LEASE_MILLIS + 1);
        assertThat(ledger.workers()).isZero();
        join(B, FactorLedger.LEASE_MILLIS + 2);
        assertThat(FactorProtocol.taskId(reply)).as("B takes over A's task").isEqualTo(1);
        assertThat(FactorProtocol.number(reply)).isEqualTo(n);

        answer(A, 1, n, FactorLedger.LEASE_MILLIS + 3);
        assertThat(ledger.height()).isEqualTo(1);
        assertThat(ledger.blockNode()).isEqualTo(A);

        answer(B, 1, n, FactorLedger.LEASE_MILLIS + 4);
        assertThat(ledger.height()).isEqualTo(1);
        assertThat(FactorProtocol.taskId(reply)).as("B moves on to a fresh task").isEqualTo(3);
    }

    @Test
    void ignoresPacketsItDoesNotServeAndWorkersBeyondItsTable() {
        byte[] packet = new byte[FactorProtocol.PACKET_SIZE];
        FactorProtocol.write(packet, FactorProtocol.TASK, A, 1, 15);
        assertThat(ledger.handle(packet, packet.length, reply, 0)).isFalse();
        FactorProtocol.write(packet, FactorProtocol.JOIN, A, 0, 0);
        assertThat(ledger.handle(packet, 10, reply, 0)).isFalse();

        for (int node = 1; node <= FactorLedger.MAX_WORKERS; node++) {
            assertThat(join(node, 0)).isTrue();
        }
        assertThat(join(FactorLedger.MAX_WORKERS + 1, 0)).isFalse();
    }

    private boolean join(int node, int now) {
        byte[] packet = new byte[FactorProtocol.PACKET_SIZE];
        FactorProtocol.write(packet, FactorProtocol.JOIN, node, 0, 0);
        return ledger.handle(packet, packet.length, reply, now);
    }

    private boolean answer(int node, int task, long n, int now) {
        long[] primes = new long[FactorProtocol.MAX_FACTORS];
        int[] exponents = new int[FactorProtocol.MAX_FACTORS];
        int count = Factorizer.factorize(n, primes, exponents, new long[Factorizer.STACK_SIZE]);
        byte[] packet = new byte[FactorProtocol.PACKET_SIZE];
        FactorProtocol.write(packet, FactorProtocol.RESULT, node, task, n);
        FactorProtocol.writeFactors(packet, primes, exponents, count);
        return ledger.handle(packet, packet.length, reply, now);
    }

    private static byte[] expectedHash(int height, int time, byte[] previous, int task, int node, long n,
                                       long[] primes, int[] exponents) throws NoSuchAlgorithmException {
        ByteBuffer block = ByteBuffer.allocate(57 + primes.length * 9)
                .putInt(height).putInt(time).put(previous).putInt(task).putInt(node).putLong(n)
                .put((byte) primes.length);
        for (int i = 0; i < primes.length; i++) {
            block.putLong(primes[i]).put((byte) exponents[i]);
        }
        return MessageDigest.getInstance("SHA-256").digest(block.array());
    }
}
