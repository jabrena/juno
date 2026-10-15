package io.github.jabrena.juno.usecases.miner.cover;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CoverViewTest {
    @Test
    void thePlayButtonIsTheTouchTarget() {
        assertThat(CoverView.isPlay(50, 200)).isTrue();
        assertThat(CoverView.isPlay(189, 249)).isTrue();
        assertThat(CoverView.isPlay(120, 120)).as("the title").isFalse();
        assertThat(CoverView.isPlay(190, 225)).isFalse();
    }
}
