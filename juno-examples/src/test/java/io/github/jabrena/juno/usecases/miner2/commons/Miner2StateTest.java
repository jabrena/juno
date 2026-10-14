package io.github.jabrena.juno.usecases.miner2.commons;

import io.github.jabrena.juno.games.blockchain.Bytes;
import io.github.jabrena.juno.usecases.miner.BlockPool;
import io.github.jabrena.juno.usecases.miner.NodeId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Miner2StateTest {
    private static Miner2State state(int shortId) {
        byte[] uuid = new byte[NodeId.SIZE];
        Bytes.writeInt(uuid, 0, shortId);
        return new Miner2State(new BlockPool(12, 8, 30), uuid);
    }

    @Test
    void theNodeIdIsTheUuidsShortId() {
        assertThat(state(48213377).nodeId()).isEqualTo(48213377);
        assertThat(state(0).nodeId()).as("a block never records miner 0").isEqualTo(1);
    }

    @Test
    void aSendCountsAsSentOrFailedByItsResult() {
        Miner2State model = state(7);

        model.countSend(120);
        model.countSend(0);
        model.countSend(-1);

        assertThat(model.sent()).isEqualTo(2);
        assertThat(model.failed()).isEqualTo(1);
    }

    @Test
    void countersStartAtZeroAndAccumulate() {
        Miner2State model = state(7);
        assertThat(model.received()).isZero();
        assertThat(model.mined()).isZero();
        assertThat(model.syncing()).isFalse();

        model.countReceived();
        model.countReceived();
        model.countIgnored();
        model.countMined();
        model.countRestart();
        model.countHashes(32);
        model.countHashes(5);
        model.publishSyncing(true);

        assertThat(model.received()).isEqualTo(2);
        assertThat(model.ignored()).isEqualTo(1);
        assertThat(model.mined()).isEqualTo(1);
        assertThat(model.restarts()).isEqualTo(1);
        assertThat(model.hashes()).isEqualTo(37);
        assertThat(model.syncing()).isTrue();
    }
}
