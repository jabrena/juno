package io.github.jabrena.juno.usecases.miner;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChainProtocolTest {
    private static byte[] uuid() {
        byte[] uuid = new byte[NodeId.SIZE];
        for (int i = 0; i < NodeId.SIZE; i++) {
            uuid[i] = (byte) (0xA0 + i);
        }
        return uuid;
    }

    @Test
    void aHeartbeatCarriesTheNodeAndItsTipAndKeepsTheCommonPacketSize() {
        byte[] packet = new byte[ChainProtocol.PACKET_SIZE];

        ChainProtocol.writeHello(packet, uuid(), 51, 3_108_864L, ChainProtocol.MINING);

        assertThat(ChainProtocol.valid(packet, ChainProtocol.PACKET_SIZE)).as("well-formed for older nodes too").isTrue();
        assertThat(ChainProtocol.type(packet)).isEqualTo(ChainProtocol.HELLO);
        assertThat(NodeId.same(packet, ChainProtocol.HELLO_UUID, uuid(), 0)).as("the whole UUID travels").isTrue();
        assertThat(ChainProtocol.height(packet)).isEqualTo(51);
        assertThat(ChainProtocol.work(packet)).isEqualTo(3_108_864L);
        assertThat(ChainProtocol.helloFlags(packet) & ChainProtocol.MINING).isNotZero();
    }

    @Test
    void aHeartbeatWithoutTheMiningFlagSaysTheNodeIsWaiting() {
        byte[] packet = new byte[ChainProtocol.PACKET_SIZE];

        ChainProtocol.writeHello(packet, uuid(), 3, 48L, 0);

        assertThat(ChainProtocol.helloFlags(packet) & ChainProtocol.MINING).isZero();
    }

    @Test
    void aHeartbeatDoesNotLookLikeABlockOrARequest() {
        byte[] packet = new byte[ChainProtocol.PACKET_SIZE];

        ChainProtocol.writeHello(packet, uuid(), 3, 48L, 0);

        assertThat(ChainProtocol.type(packet)).isNotIn(ChainProtocol.BLOCK, ChainProtocol.GET_BLOCK,
                ChainProtocol.GET_TIP);
    }
}
