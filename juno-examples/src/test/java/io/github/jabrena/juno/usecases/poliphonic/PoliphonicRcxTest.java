package io.github.jabrena.juno.usecases.poliphonic;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PoliphonicRcxTest {
    @Test
    void shouldKeepTheHarmonyTrackInTheAudibleRangeAndRepeatIt() {
        assertThat(OdeToJoyTheme.noteCount()).isEqualTo(62);
        assertThat(OdeToJoyTheme.frequencyHz(0)).isEqualTo(330);
        assertThat(OdeToJoyHarmony.noteCount()).isEqualTo(20);
        assertThat(OdeToJoyHarmony.frequencyHz(0)).isEqualTo(262);
        assertThat(OdeToJoyHarmony.frequencyHz(20)).isEqualTo(262);
        assertThat(OdeToJoyCounterpoint.noteCount()).isEqualTo(20);
        assertThat(OdeToJoyCounterpoint.frequencyHz(0)).isEqualTo(523);
        assertThat(OdeToJoyCounterpoint.frequencyHz(20)).isEqualTo(523);
    }

    @Test
    void shouldUseTheSameReferenceToneForAllOrchestraChannels() {
        assertThat(OdeToJoyPoliphonicRcx.REFERENCE_FREQUENCY_HZ).isEqualTo(440);
        assertThat(OdeToJoyPoliphonicRcx.REFERENCE_DURATION_CENTISECONDS).isEqualTo(50);
        assertThat(OdeToJoyPoliphonicRcx.ORCHESTRA_CHANNELS).containsExactly(1, 2, 3);
        assertThat(OdeToJoyPoliphonicRcxNano.REFERENCE_FREQUENCY_HZ).isEqualTo(440);
        assertThat(OdeToJoyPoliphonicRcxNano.REFERENCE_DURATION_CENTISECONDS).isEqualTo(50);
        assertThat(OdeToJoyPoliphonicRcxNano.ORCHESTRA_CHANNELS).containsExactly(1, 2, 3, 4);
    }

    @Test
    void shouldFitRcxToneDurationsInOneByte() {
        assertThat(OdeToJoyPoliphonicRcx.durationCentiseconds(0)).isEqualTo(1);
        assertThat(OdeToJoyPoliphonicRcx.durationCentiseconds(274)).isEqualTo(27);
        assertThat(OdeToJoyPoliphonicRcx.durationCentiseconds(3356)).isEqualTo(255);
        assertThat(OdeToJoyPoliphonicRcxNano.durationCentiseconds(0)).isEqualTo(1);
        assertThat(OdeToJoyPoliphonicRcxNano.durationCentiseconds(274)).isEqualTo(27);
        assertThat(OdeToJoyPoliphonicRcxNano.durationCentiseconds(3356)).isEqualTo(255);
    }
}
