package io.github.jabrena.juno.usecases.miner.commons;

import io.github.jabrena.juno.api.UuidCodec;
import io.github.jabrena.juno.usecases.miner.chain.BlockPool;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class Miner2StateTest {
    private static Miner2State state(int shortId) {
        UUID uuid = new UUID((long) shortId << 32, 0L);
        return new Miner2State(new BlockPool(12, 8, 30), uuid);
    }

    @Test
    void theNodeIdIsTheUuidsShortId() {
        assertThat(state(48213377).nodeId()).isEqualTo(48213377);
        assertThat(state(0).nodeId()).as("a block never records miner 0").isEqualTo(1);
    }

    @Test
    void retainsTheUuidAndEncodesItOnceForTheNetwork() {
        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        Miner2State model = new Miner2State(new BlockPool(12, 8, 30), uuid);

        assertThat(model.uuid()).isSameAs(uuid);
        assertThat(UuidCodec.fromBytes(model.uuidBytes(), 0)).isEqualTo(uuid);
        assertThat(model.uuidBytes()).isSameAs(model.uuidBytes());
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
        assertThat(model.wifiConnected()).isFalse();
        assertThat(model.sdCardReady()).isFalse();

        model.countReceived();
        model.countReceived();
        model.countIgnored();
        model.countMined();
        model.countRestart();
        model.countHashes(32);
        model.countHashes(5);
        model.publishSyncing(true);
        model.publishWifiConnected(true);
        model.publishSdCardReady(true);

        assertThat(model.received()).isEqualTo(2);
        assertThat(model.ignored()).isEqualTo(1);
        assertThat(model.mined()).isEqualTo(1);
        assertThat(model.restarts()).isEqualTo(1);
        assertThat(model.hashes()).isEqualTo(37);
        assertThat(model.syncing()).isTrue();
        assertThat(model.wifiConnected()).isTrue();
        assertThat(model.sdCardReady()).isTrue();
    }
}
