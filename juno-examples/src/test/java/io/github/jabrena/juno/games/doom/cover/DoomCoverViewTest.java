package io.github.jabrena.juno.games.doom.cover;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DoomCoverViewTest {
    @Test
    void thePlayPromptIsTheTouchTarget() {
        assertThat(DoomCoverView.isPlay(80, 194)).isTrue();
        assertThat(DoomCoverView.isPlay(239, 221)).isTrue();
        assertThat(DoomCoverView.isPlay(268, 208)).isFalse();
    }
}
