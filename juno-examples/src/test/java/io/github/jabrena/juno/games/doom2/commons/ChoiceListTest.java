package io.github.jabrena.juno.games.doom2.commons;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChoiceListTest {
    @Test
    void episodeRowsExcludeTheirGapsAndTheBackButton() {
        ChoiceList episodes = new ChoiceList(46, 32, 4);

        assertThat(episodes.rowAt(160, 46)).isZero();
        assertThat(episodes.rowAt(160, 78)).isEqualTo(-1);
        assertThat(episodes.rowAt(160, 82)).isEqualTo(1);
        assertThat(episodes.rowAt(160, 185)).isEqualTo(3);
        assertThat(episodes.rowAt(8, 204)).isEqualTo(-1);
    }

    @Test
    void theFiveSkillRowsEndAboveTheBackButton() {
        ChoiceList skills = new ChoiceList(42, 28, 5);

        assertThat(skills.rowAt(160, 42)).isZero();
        assertThat(skills.rowAt(160, 70)).isEqualTo(-1);
        assertThat(skills.rowAt(160, 170)).isEqualTo(4);
        assertThat(skills.rowAt(160, 198)).isEqualTo(-1);
        assertThat(skills.rowAt(160, 210)).isEqualTo(-1);
    }
}
