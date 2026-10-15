package io.github.jabrena.juno.usecases.miner2.commons;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Miner2HeaderTest {
    @Test
    void everyCombinationOfDeviceStatesHasADifferentRedrawKey() {
        assertThat(Miner2Header.statusKey(false, false)).isEqualTo(0);
        assertThat(Miner2Header.statusKey(true, false)).isEqualTo(1);
        assertThat(Miner2Header.statusKey(false, true)).isEqualTo(2);
        assertThat(Miner2Header.statusKey(true, true)).isEqualTo(3);
    }
}
