package io.github.jabrena.juno.games.doom2.pilot;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PilotSelectionViewTest {
    @Test
    void theRowsSelectHumanThenCpu() {
        assertThat(PilotSelectionView.PILOTS.rowAt(160, 100)).isEqualTo(PilotSelectionView.HUMAN);
        assertThat(PilotSelectionView.PILOTS.rowAt(160, 150)).isEqualTo(PilotSelectionView.CPU);
        assertThat(PilotSelectionView.PILOTS.rowAt(160, 138)).isEqualTo(-1);
        assertThat(PilotSelectionView.PILOTS.rowAt(160, 40)).isEqualTo(-1);
    }
}
