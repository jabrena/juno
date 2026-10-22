package io.github.jabrena.juno.usecases.zelda.theme;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ZeldaOcarinaThemeTest {
    @Test
    void preservesThePianoArrangementsVoicingTimingAndRepeat() {
        assertThat(ZeldaOcarinaTheme.repeatCount()).isEqualTo(2);
        assertThat(ZeldaOcarinaTheme.noteCount()).isEqualTo(87);
        assertThat(ZeldaOcarinaTheme.durationMillis()).isEqualTo(77_186);

        assertThat(frequencies()).startsWith(87, 175, 220, 294, 330, 65, 196, 247, 294, 330);
        assertThat(toneMillis()).startsWith(274, 77, 60, 86, 2_791, 283, 77, 85, 86, 3_356);
        assertThat(stepMillis()).startsWith(274, 77, 60, 86, 3_758, 283, 77, 85, 86, 3_356);
    }

    @Test
    void keepsEveryToneInsideItsStep() {
        int duration = 0;
        for (int note = 0; note < ZeldaOcarinaTheme.noteCount(); note++) {
            assertThat(ZeldaOcarinaTheme.frequencyHz(note)).isBetween(31, 20_000);
            assertThat(ZeldaOcarinaTheme.toneMillis(note)).isPositive();
            assertThat(ZeldaOcarinaTheme.stepMillis(note)).isGreaterThanOrEqualTo(ZeldaOcarinaTheme.toneMillis(note));
            duration = duration + ZeldaOcarinaTheme.stepMillis(note);
        }
        assertThat(duration).isEqualTo(ZeldaOcarinaTheme.durationMillis());
    }

    @Test
    void includesPhraseBreathsAndTheTempoShapedClosingLine() {
        assertThat(ZeldaOcarinaTheme.stepMillis(28) - ZeldaOcarinaTheme.toneMillis(28)).isEqualTo(300);
        assertThat(ZeldaOcarinaTheme.stepMillis(36) - ZeldaOcarinaTheme.toneMillis(36)).isEqualTo(274);
        assertThat(ZeldaOcarinaTheme.stepMillis(52) - ZeldaOcarinaTheme.toneMillis(52)).isEqualTo(377);
        assertThat(frequencies()).endsWith(87, 131, 175, 220, 294, 220);
    }

    private static int[] frequencies() {
        int[] frequencies = new int[ZeldaOcarinaTheme.noteCount()];
        for (int note = 0; note < frequencies.length; note++) {
            frequencies[note] = ZeldaOcarinaTheme.frequencyHz(note);
        }
        return frequencies;
    }

    private static int[] toneMillis() {
        int[] durations = new int[ZeldaOcarinaTheme.noteCount()];
        for (int note = 0; note < durations.length; note++) {
            durations[note] = ZeldaOcarinaTheme.toneMillis(note);
        }
        return durations;
    }

    private static int[] stepMillis() {
        int[] durations = new int[ZeldaOcarinaTheme.noteCount()];
        for (int note = 0; note < durations.length; note++) {
            durations[note] = ZeldaOcarinaTheme.stepMillis(note);
        }
        return durations;
    }
}
