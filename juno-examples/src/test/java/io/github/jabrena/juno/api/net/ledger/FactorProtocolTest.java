package io.github.jabrena.juno.api.net.ledger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FactorProtocolTest {
    @Test
    void roundTripsAResultWithTheLargestValues() {
        byte[] packet = new byte[FactorProtocol.PACKET_SIZE];
        long[] primes = {2, 4_611_686_018_427_387_847L};
        int[] exponents = {255, 1};

        FactorProtocol.write(packet, FactorProtocol.RESULT, 0xCAFEBABE, 0x7FFFFFFF, Factorizer.LIMIT - 1);
        FactorProtocol.writeFactors(packet, primes, exponents, 2);

        long[] readPrimes = new long[FactorProtocol.MAX_FACTORS];
        int[] readExponents = new int[FactorProtocol.MAX_FACTORS];
        assertThat(FactorProtocol.valid(packet, packet.length)).isTrue();
        assertThat(FactorProtocol.type(packet)).isEqualTo(FactorProtocol.RESULT);
        assertThat(FactorProtocol.nodeId(packet)).isEqualTo(0xCAFEBABE);
        assertThat(FactorProtocol.taskId(packet)).isEqualTo(0x7FFFFFFF);
        assertThat(FactorProtocol.number(packet)).isEqualTo(Factorizer.LIMIT - 1);
        assertThat(FactorProtocol.readFactors(packet, readPrimes, readExponents)).isEqualTo(2);
        assertThat(readPrimes[1]).isEqualTo(4_611_686_018_427_387_847L);
        assertThat(readExponents[0]).isEqualTo(255);
    }

    @Test
    void rejectsWrongLengthMagicServiceAndFactorCount() {
        byte[] packet = new byte[FactorProtocol.PACKET_SIZE];
        FactorProtocol.write(packet, FactorProtocol.JOIN, 1, 0, 0);
        assertThat(FactorProtocol.valid(packet, packet.length)).isTrue();
        assertThat(FactorProtocol.valid(packet, packet.length - 1)).isFalse();

        packet[3] = 'X';
        assertThat(FactorProtocol.valid(packet, packet.length)).isFalse();

        FactorProtocol.write(packet, FactorProtocol.JOIN, 1, 0, 0);
        packet[5] = 1;
        assertThat(FactorProtocol.valid(packet, packet.length)).as("the Pokemon battle's service").isFalse();

        FactorProtocol.write(packet, FactorProtocol.JOIN, 1, 0, 0);
        packet[FactorProtocol.COUNT] = (byte) (FactorProtocol.MAX_FACTORS + 1);
        assertThat(FactorProtocol.valid(packet, packet.length)).isFalse();
    }
}
