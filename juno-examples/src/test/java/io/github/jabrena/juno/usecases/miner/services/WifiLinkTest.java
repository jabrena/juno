package io.github.jabrena.juno.usecases.miner.services;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WifiLinkTest {
    @Test
    void doesNothingUntilStarted() {
        WifiLink link = new WifiLink();

        link.step(10_000);

        assertThat(link.state()).isEqualTo(WifiLink.IDLE);
        assertThat(link.ready()).isFalse();
        assertThat(link.connected()).isFalse();
    }

    @Test
    void joinsThenListensOneStepAtATime() {
        WifiLink link = new WifiLink();

        link.start(0);
        assertThat(link.state()).isEqualTo(WifiLink.JOINING);
        assertThat(link.connected()).as("not polled yet").isFalse();

        link.step(0);
        assertThat(link.state()).isEqualTo(WifiLink.LISTENING);
        assertThat(link.connected()).isTrue();
        assertThat(link.ready()).as("the socket is not open yet").isFalse();

        link.step(1);
        assertThat(link.ready()).isTrue();
    }

    @Test
    void restartingAReadyLinkJoinsAgain() {
        WifiLink link = new WifiLink();
        link.start(0);
        link.step(0);
        link.step(0);

        link.start(5000);

        assertThat(link.ready()).isFalse();
        assertThat(link.connected()).isFalse();
        link.step(5000);
        link.step(5000);
        assertThat(link.ready()).isTrue();
    }

    @Test
    void aReadyLinkIsPolledOnlyEveryPollPeriod() {
        WifiLink link = new WifiLink();
        link.start(0);
        link.step(0);
        link.step(0);

        link.step(WifiLink.POLL_MILLIS - 1);

        assertThat(link.ready()).isTrue();
        assertThat(link.connected()).isTrue();
    }

    @Test
    void disconnectReportsTheLinkDownUntilItJoinsAgain() {
        WifiLink link = new WifiLink();
        link.start(0);
        link.step(0);
        link.step(0);

        link.disconnect();

        assertThat(link.ready()).isFalse();
        assertThat(link.connected()).isFalse();
        link.step(10_000);
        assertThat(link.state()).as("a disconnected link does not poll").isEqualTo(WifiLink.IDLE);

        link.start(20_000);
        link.step(20_000);
        link.step(20_000);
        assertThat(link.ready()).isTrue();
    }
}
