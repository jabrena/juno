package io.github.jabrena.juno.usecases.miner.peers;

import io.github.jabrena.juno.usecases.miner.chain.ChainProtocol;
import io.github.jabrena.juno.util.Random;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NodeIdTest {
    @Test
    void generatesAVersion4UuidOfTheRightShape() {
        byte[] uuid = new byte[NodeId.SIZE];

        NodeId.generate(uuid);

        assertThat(uuid[6] >> 4 & 0xf).as("version 4").isEqualTo(4);
        assertThat(uuid[8] >> 6 & 0x3).as("RFC 4122 variant").isEqualTo(2);
    }

    @Test
    void sameComparesAllSixteenBytes() {
        byte[] a = new byte[NodeId.SIZE];
        NodeId.generate(a);
        byte[] b = a.clone();

        assertThat(NodeId.same(a, 0, b, 0)).isTrue();
        for (int i = 0; i < NodeId.SIZE; i++) {
            b[i] ^= 1;
            assertThat(NodeId.same(a, 0, b, 0)).as("byte %d differs", i).isFalse();
            b[i] ^= 1;
        }
    }

    @Test
    void sameComparesAtOffsetsInsideAPacket() {
        byte[] uuid = new byte[NodeId.SIZE];
        NodeId.generate(uuid);
        byte[] packet = new byte[ChainProtocol.PACKET_SIZE];

        ChainProtocol.writeHello(packet, uuid, 1, 1L, 0);

        assertThat(NodeId.same(packet, ChainProtocol.HELLO_UUID, uuid, 0)).isTrue();
    }

    @Test
    void theShortIdIsTheFirstFourBytesAndNeverZero() {
        byte[] uuid = new byte[NodeId.SIZE];
        uuid[3] = 5;
        assertThat(NodeId.shortId(uuid, 0)).isEqualTo(5);

        assertThat(NodeId.shortId(new byte[NodeId.SIZE], 0)).as("0 means 'no miner'").isEqualTo(1);
    }

    @Test
    void consecutiveUuidsOfTheSameBoardDiffer() {
        byte[] first = new byte[NodeId.SIZE];
        byte[] second = new byte[NodeId.SIZE];
        NodeId.generate(first);
        Random.seed(12345);
        NodeId.generate(second);

        assertThat(NodeId.same(first, 0, second, 0)).as("the generator keeps advancing").isFalse();
    }

    @Test
    void precedesOrdersUuidsByUnsignedBytes() {
        byte[] low = new byte[NodeId.SIZE];
        byte[] high = new byte[NodeId.SIZE];
        low[15] = 0x7f;
        high[15] = (byte) 0x80;

        assertThat(NodeId.precedes(low, 0, high, 0)).as("0x80 is above 0x7f, not negative").isTrue();
        assertThat(NodeId.precedes(high, 0, low, 0)).isFalse();
        assertThat(NodeId.precedes(low, 0, low.clone(), 0)).as("a UUID does not precede itself").isFalse();
    }
}
