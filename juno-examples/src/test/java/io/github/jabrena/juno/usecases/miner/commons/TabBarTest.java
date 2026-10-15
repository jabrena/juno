package io.github.jabrena.juno.usecases.miner.commons;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TabBarTest {
    @Test
    void eachTabIsAsWideAsItsLabelAndBlocksComesFirst() {
        assertThat(TabBar.tabAt(0, TabBar.TOP)).isEqualTo(Miner2Views.BLOCKS);
        assertThat(TabBar.tabAt(73, 319)).isEqualTo(Miner2Views.BLOCKS);
        assertThat(TabBar.tabAt(74, 300)).isEqualTo(Miner2Views.CHAIN);
        assertThat(TabBar.tabAt(136, 300)).isEqualTo(Miner2Views.PEERS);
        assertThat(TabBar.tabAt(198, 300)).isEqualTo(Miner2Views.NETWORK);
        assertThat(TabBar.tabAt(239, 300)).isEqualTo(Miner2Views.NETWORK);
    }

    @Test
    void touchesAboveTheBarOrOffTheScreenSelectNoTab() {
        assertThat(TabBar.tabAt(120, TabBar.TOP - 1)).isEqualTo(-1);
        assertThat(TabBar.tabAt(240, 300)).isEqualTo(-1);
        assertThat(TabBar.tabAt(-1, 300)).isEqualTo(-1);
    }
}
