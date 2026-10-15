package io.github.jabrena.juno.usecases.miner2.services;

import io.github.jabrena.juno.usecases.miner.BlockPool;
import io.github.jabrena.juno.usecases.miner.ChainProtocol;
import io.github.jabrena.juno.usecases.miner.Miner;
import io.github.jabrena.juno.usecases.miner2.commons.BitBoard;
import io.github.jabrena.juno.usecases.miner2.commons.Miner2State;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimServiceTest {
    private static final int ASK = ClaimService.ASK_MILLIS;

    private final byte[] packet = new byte[ChainProtocol.PACKET_SIZE];
    private final byte[] reply = new byte[ChainProtocol.PACKET_SIZE];

    @Test
    void aLoneNodeAsksForTheFirstLengthAndOwnsItWhenNobodyAnswers() {
        Node a = new Node(0x01);

        assertThat(a.claims.step(0, packet)).as("broadcasts a CLAIM").isTrue();
        assertThat(ChainProtocol.type(packet)).isEqualTo(ChainProtocol.CLAIM);
        assertThat(ChainProtocol.boardBit(packet)).isEqualTo(BitBoard.FIRST);
        assertThat(a.claims.owned()).as("still asking").isZero();
        assertThat(a.model.board().asking()).isTrue();

        assertThat(a.claims.step(ASK, packet)).isFalse();
        assertThat(a.claims.owned()).isEqualTo(BitBoard.FIRST);
        assertThat(a.model.board().claimant(BitBoard.FIRST, ASK)).isEqualTo(a.model.nodeId());
    }

    @Test
    void anOwnerAnswersTakenAndTheClaimerMovesToTheNextLength() {
        Node a = new Node(0x01);
        Node b = new Node(0x02);
        a.own(0);

        b.claims.step(ASK, packet);
        assertThat(a.claims.serve(packet, reply, ASK)).as("A owns bit 2").isTrue();
        assertThat(ChainProtocol.type(reply)).isEqualTo(ChainProtocol.TAKEN);
        assertThat(b.claims.serve(reply, packet, ASK)).isFalse();

        assertThat(b.claims.step(ASK + 1, packet)).isTrue();
        assertThat(ChainProtocol.boardBit(packet)).isEqualTo(BitBoard.FIRST + 1);
        b.claims.step(2 * ASK + 1, packet);
        assertThat(b.claims.owned()).isEqualTo(BitBoard.FIRST + 1);
        assertThat(a.claims.owned()).isEqualTo(BitBoard.FIRST);
    }

    @Test
    void twoNodesAskingForOneLengthAtOnceLeaveItToTheLowerUuid() {
        Node low = new Node(0x01);
        Node high = new Node(0x02);
        byte[] lowClaim = new byte[ChainProtocol.PACKET_SIZE];
        byte[] highClaim = new byte[ChainProtocol.PACKET_SIZE];
        low.claims.step(0, lowClaim);
        high.claims.step(0, highClaim);

        assertThat(low.claims.serve(highClaim, reply, 10)).as("the lower UUID keeps it").isTrue();
        assertThat(high.claims.serve(lowClaim, packet, 10)).as("the higher UUID yields").isFalse();
        high.claims.serve(reply, packet, 10);

        high.claims.step(20, packet);
        assertThat(ChainProtocol.boardBit(packet)).isEqualTo(BitBoard.FIRST + 1);
        low.claims.step(ASK, packet);
        high.claims.step(ASK + 20, packet);
        assertThat(low.claims.owned()).isEqualTo(BitBoard.FIRST);
        assertThat(high.claims.owned()).isEqualTo(BitBoard.FIRST + 1);
    }

    @Test
    void aMinedLengthEndsTheOwnershipAndTheNodeClaimsTheNextFreeOne() {
        Node a = new Node(0x01);
        a.own(0);

        a.mine(BitBoard.FIRST);

        assertThat(a.claims.step(ASK + 1, packet)).isTrue();
        assertThat(ChainProtocol.boardBit(packet)).isEqualTo(BitBoard.FIRST + 1);
        assertThat(a.model.board().isMined(BitBoard.FIRST)).isTrue();
        assertThat(a.model.board().cellColor(BitBoard.FIRST)).isEqualTo(a.model.board().ownColor());
    }

    @Test
    void aLateNodeLearnsTheBoardAndTheOwnedLengthsFromHeartbeats() {
        Node a = new Node(0x01);
        Node b = new Node(0x02);
        a.own(0);
        a.mine(BitBoard.FIRST);
        a.own(ASK + 1);
        ChainProtocol.writeHello(packet, a.model.uuidBytes(), 1, 16L, ChainProtocol.MINING);
        a.claims.stampHello(packet);

        b.claims.serveHello(packet, 3 * ASK);

        assertThat(b.model.board().isMined(BitBoard.FIRST)).isTrue();
        assertThat(b.model.board().cellColor(BitBoard.FIRST)).as("in A's color").isEqualTo(a.model.board().ownColor());
        assertThat(b.claims.step(3 * ASK, packet)).isTrue();
        assertThat(ChainProtocol.boardBit(packet)).as("2 is mined and 3 is A's").isEqualTo(BitBoard.FIRST + 2);
    }

    @Test
    void aFullBoardStartsANewRound() {
        Node a = new Node(0x01);
        for (int bit = BitBoard.FIRST; bit <= BitBoard.LAST; bit++) {
            a.model.board().markMined(bit, 7);
        }

        a.claims.step(0, packet);

        assertThat(a.model.board().round()).isEqualTo(2);
        assertThat(a.model.board().count()).isZero();
        assertThat(ChainProtocol.boardBit(packet)).isEqualTo(BitBoard.FIRST);
    }

    @Test
    void ignoresItsOwnClaimEchoedBackAndHeartbeatsFromNodesWithoutABoard() {
        Node a = new Node(0x01);
        a.claims.step(0, packet);

        assertThat(a.claims.serve(packet, reply, 1)).isFalse();
        a.claims.step(ASK, packet);
        assertThat(a.claims.owned()).isEqualTo(BitBoard.FIRST);

        Node old = new Node(0x02);
        ChainProtocol.writeHello(packet, old.model.uuidBytes(), 1, 16L, ChainProtocol.MINING);
        a.claims.serveHello(packet, ASK + 1);
        assertThat(a.claims.owned()).isEqualTo(BitBoard.FIRST);
    }

    @Test
    void ofTwoNodesWithOneColorTheHigherUuidMovesToAFreeColor() {
        Node low = new Node(0x01);
        Node high = new Node(0x02);
        low.model.board().publishOwnColor(3);
        high.model.board().publishOwnColor(3);
        low.claims.step(0, packet);
        high.claims.step(0, reply);

        high.claims.serve(packet, new byte[ChainProtocol.PACKET_SIZE], 10);
        low.claims.serve(reply, new byte[ChainProtocol.PACKET_SIZE], 10);

        assertThat(low.model.board().ownColor()).isEqualTo(3);
        assertThat(low.claims.colorChanged()).isFalse();
        assertThat(high.model.board().ownColor()).as("the lowest color nobody else says").isZero();
        assertThat(high.claims.colorChanged()).as("to be saved").isTrue();
        assertThat(high.claims.colorChanged()).as("once").isFalse();
    }

    /** One simulated node: its model and claim service, on its own chain. */
    private final class Node {
        final Miner2State model;
        final ClaimService claims;

        Node(int first) {
            model = new Miner2State(new BlockPool(4, 8, 30), new UUID((long) first << 56, 1L));
            claims = new ClaimService(model);
        }

        /** Asks at {@code now} and, as nobody answers, owns the lowest free length. */
        void own(int now) {
            claims.step(now, packet);
            claims.step(now + ASK, packet);
        }

        /** Mines a block on the tip with a bounty of {@code bit} bits. */
        void mine(int bit) {
            BlockPool pool = model.pool();
            Miner miner = new Miner();
            miner.prepare(pool, model.nodeId(), pool.time(pool.tip()) + 1, bit);
            while (!miner.mine(64)) {
                // keep hashing
            }
            assertThat(pool.add(miner.block(), 0, 0, 0L, 0)).isEqualTo(BlockPool.NEW_TIP);
        }
    }
}
