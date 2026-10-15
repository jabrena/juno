package io.github.jabrena.juno.usecases.miner.network;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NetworkViewTest {
    @Test
    void theHashRateIsHashesPerSecondOfTheMeasuredInterval() {
        assertThat(NetworkView.hashesPerSecond(4800, 1000)).isEqualTo(4800);
        assertThat(NetworkView.hashesPerSecond(4800, 1200)).isEqualTo(4000);
        assertThat(NetworkView.hashesPerSecond(3_000_000, 1000)).as("no int overflow").isEqualTo(3_000_000);
    }

    @Test
    void anEmptyIntervalHasNoRate() {
        assertThat(NetworkView.hashesPerSecond(100, 0)).isZero();
    }
}
