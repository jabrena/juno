package io.github.jabrena.juno.usecases.miner.services;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SyncGateTest {
    @Test
    void aNodeThatIsNotBehindKeepsMining() {
        SyncGate gate = new SyncGate();

        assertThat(gate.shouldWait(false, 0, 0)).isFalse();
        assertThat(gate.shouldWait(false, 0, 100_000)).isFalse();
    }

    @Test
    void aNodeBehindWaitsButOnlyForALimitedTime() {
        SyncGate gate = new SyncGate();

        assertThat(gate.shouldWait(true, 0, 1000)).as("peers have a heavier chain").isTrue();
        assertThat(gate.shouldWait(true, 0, 1000 + SyncGate.GIVE_UP_MILLIS - 1)).isTrue();
        assertThat(gate.shouldWait(true, 0, 1000 + SyncGate.GIVE_UP_MILLIS)).as("gives up").isFalse();
        assertThat(gate.shouldWait(true, 0, 1000 + 2 * SyncGate.GIVE_UP_MILLIS)).as("and stays given up").isFalse();
    }

    @Test
    void catchingUpEndsTheWaitAndANewGapStartsAFreshOne() {
        SyncGate gate = new SyncGate();
        gate.shouldWait(true, 0, 1000);

        assertThat(gate.shouldWait(false, 1, 2000)).as("caught up").isFalse();
        assertThat(gate.shouldWait(true, 1, 3000)).as("behind again").isTrue();
    }

    @Test
    void progressWhileWaitingRestartsThePatience() {
        SyncGate gate = new SyncGate();
        gate.shouldWait(true, 0, 0);
        assertThat(gate.shouldWait(true, 0, SyncGate.GIVE_UP_MILLIS)).as("gave up").isFalse();

        assertThat(gate.shouldWait(true, 1, SyncGate.GIVE_UP_MILLIS + 1)).as("the tip moved: wait afresh").isTrue();
    }
}
