package io.github.jabrena.juno.usecases.miner2.peers;

import io.github.jabrena.juno.usecases.miner.PeerTable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PeersViewTest {
    @Test
    void anEmptySlotHasAKeyNoUsedSlotCanHave() {
        assertThat(PeersView.rowKey(PeerTable.EMPTY, 9, true, 42)).isEqualTo(-2);
        assertThat(PeersView.rowKey(PeerTable.ALIVE, 0, false, 0)).isNotNegative();
        assertThat(PeersView.rowKey(PeerTable.DEAD, Integer.MAX_VALUE, true, -1)).isNotNegative();
    }

    @Test
    void theKeyChangesWithEverythingTheRowShows() {
        int key = PeersView.rowKey(PeerTable.ALIVE, 12, true, 42);

        assertThat(PeersView.rowKey(PeerTable.SUSPECT, 12, true, 42)).isNotEqualTo(key);
        assertThat(PeersView.rowKey(PeerTable.ALIVE, 13, true, 42)).isNotEqualTo(key);
        assertThat(PeersView.rowKey(PeerTable.ALIVE, 12, false, 42)).isNotEqualTo(key);
        assertThat(PeersView.rowKey(PeerTable.ALIVE, 12, true, 43)).as("another peer took the slot")
                .isNotEqualTo(key);
    }

    @Test
    void eachStateHasItsLabel() {
        assertThat(PeersView.stateLabel(PeerTable.ALIVE)).isEqualTo("alive");
        assertThat(PeersView.stateLabel(PeerTable.SUSPECT)).isEqualTo("suspect");
        assertThat(PeersView.stateLabel(PeerTable.DEAD)).isEqualTo("lost");
    }
}
