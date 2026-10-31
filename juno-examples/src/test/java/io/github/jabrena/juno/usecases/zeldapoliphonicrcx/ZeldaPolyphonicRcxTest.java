package io.github.jabrena.juno.usecases.zeldapoliphonicrcx;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ZeldaPolyphonicRcxTest {
    @Test
    void shouldKeepTheHarmonyTrackInTheAudibleRangeAndRepeatIt() {
        assertThat(ZeldaOcarinaTheme.noteCount()).isEqualTo(87);
        assertThat(ZeldaOcarinaTheme.frequencyHz(0)).isEqualTo(87);
        assertThat(ZeldaOcarinaHarmony.noteCount()).isEqualTo(20);
        assertThat(ZeldaOcarinaHarmony.frequencyHz(0)).isEqualTo(87);
        assertThat(ZeldaOcarinaHarmony.frequencyHz(20)).isEqualTo(87);
        assertThat(ZeldaOcarinaHarmony.frequencyHz(19)).isGreaterThan(0);
        assertThat(ZeldaOcarinaCounterpoint.noteCount()).isEqualTo(20);
        assertThat(ZeldaOcarinaCounterpoint.frequencyHz(0)).isEqualTo(523);
        assertThat(ZeldaOcarinaCounterpoint.frequencyHz(20)).isEqualTo(523);
    }

    @Test
    void shouldUseTheSameReferenceToneForAllOrchestraChannels() {
        assertThat(ZeldaPolyphonicRcx.REFERENCE_FREQUENCY_HZ).isEqualTo(440);
        assertThat(ZeldaPolyphonicRcx.REFERENCE_DURATION_CENTISECONDS).isEqualTo(50);
        assertThat(ZeldaPolyphonicRcx.ORCHESTRA_CHANNELS).containsExactly(1, 2, 3);
        assertThat(ZeldaPolyphonicRcxNano.REFERENCE_FREQUENCY_HZ).isEqualTo(440);
        assertThat(ZeldaPolyphonicRcxNano.REFERENCE_DURATION_CENTISECONDS).isEqualTo(50);
        assertThat(ZeldaPolyphonicRcxNano.ORCHESTRA_CHANNELS).containsExactly(1, 2, 3, 4);
    }

    @Test
    void shouldFitRcxToneDurationsInOneByte() {
        assertThat(ZeldaPolyphonicRcx.durationCentiseconds(0)).isEqualTo(1);
        assertThat(ZeldaPolyphonicRcx.durationCentiseconds(274)).isEqualTo(27);
        assertThat(ZeldaPolyphonicRcx.durationCentiseconds(3356)).isEqualTo(255);
        assertThat(ZeldaPolyphonicRcxNano.durationCentiseconds(0)).isEqualTo(1);
        assertThat(ZeldaPolyphonicRcxNano.durationCentiseconds(274)).isEqualTo(27);
        assertThat(ZeldaPolyphonicRcxNano.durationCentiseconds(3356)).isEqualTo(255);
    }
}
