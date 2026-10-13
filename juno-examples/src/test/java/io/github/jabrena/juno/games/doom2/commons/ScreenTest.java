package io.github.jabrena.juno.games.doom2.commons;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScreenTest {
    @Test
    void onlyTheBackButtonAtTheBottomLeftNavigatesBack() {
        assertThat(Screen.isBack(8, 204)).isTrue();
        assertThat(Screen.isBack(71, 231)).isTrue();
        assertThat(Screen.isBack(72, 231)).isFalse();
        assertThat(Screen.isBack(8, 203)).isFalse();
        assertThat(Screen.isBack(20, 104)).isFalse();
    }
}
