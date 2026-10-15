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

    @Test
    void aHeartbeatCarriesTheBitBoardAfterTheUuid() {
        byte[] packet = new byte[ChainProtocol.PACKET_SIZE];

        ChainProtocol.writeHello(packet, uuid(), 3, 48L, ChainProtocol.MINING);
        ChainProtocol.writeBoard(packet, 2, 17, 1L << 62 | 1L << 2, 4);
        ChainProtocol.writeBoardCell(packet, 2, 3);
        ChainProtocol.writeBoardCell(packet, 3, 9);
        ChainProtocol.writeBoardCell(packet, 62, 8);

        assertThat(NodeId.same(packet, ChainProtocol.HELLO_UUID, uuid(), 0)).as("the UUID is untouched").isTrue();
        assertThat(ChainProtocol.boardRound(packet)).isEqualTo(2);
        assertThat(ChainProtocol.boardBit(packet)).isEqualTo(17);
        assertThat(ChainProtocol.boardMined(packet)).isEqualTo(1L << 62 | 1L << 2);
        assertThat(ChainProtocol.boardColor(packet)).isEqualTo(4);
        assertThat(ChainProtocol.boardCell(packet, 2)).as("two cells share a byte").isEqualTo(3);
        assertThat(ChainProtocol.boardCell(packet, 3)).isEqualTo(9);
        assertThat(ChainProtocol.boardCell(packet, 62)).isEqualTo(8);
        assertThat(ChainProtocol.boardCell(packet, 4)).isZero();
    }

    @Test
    void aHeartbeatWithoutABoardReadsAsRoundZero() {
        byte[] packet = new byte[ChainProtocol.PACKET_SIZE];

        ChainProtocol.writeHello(packet, uuid(), 3, 48L, 0);

        assertThat(ChainProtocol.boardRound(packet)).isZero();
        assertThat(ChainProtocol.boardBit(packet)).isZero();
        assertThat(ChainProtocol.boardMined(packet)).isZero();
        assertThat(ChainProtocol.boardColor(packet)).isZero();
    }

    @Test
    void claimsAndAnswersAreWellFormedPacketsOfTheirOwnTypes() {
        byte[] packet = new byte[ChainProtocol.PACKET_SIZE];

        ChainProtocol.writeClaim(packet, ChainProtocol.CLAIM, uuid());
        ChainProtocol.writeBoard(packet, 1, 62, 4L, 1);

        assertThat(ChainProtocol.valid(packet, ChainProtocol.PACKET_SIZE)).isTrue();
        assertThat(ChainProtocol.type(packet)).isEqualTo(ChainProtocol.CLAIM);
        assertThat(NodeId.same(packet, ChainProtocol.HELLO_UUID, uuid(), 0)).isTrue();
        assertThat(ChainProtocol.boardBit(packet)).isEqualTo(62);

        ChainProtocol.writeClaim(packet, ChainProtocol.TAKEN, uuid());
        ChainProtocol.writeBoard(packet, 1, 5, 4L, 1);

        assertThat(ChainProtocol.type(packet)).isEqualTo(ChainProtocol.TAKEN);
        assertThat(ChainProtocol.boardMined(packet)).isEqualTo(4L);
    }
}
