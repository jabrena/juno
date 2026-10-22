package io.github.jabrena.juno.usecases.zelda;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ZeldaOcarinaSongsTest {
    @Test
    void putsTheFullThemeFirstAndKeepsTheSongsAfterIt() {
        assertThat(ZeldaOcarinaSongs.TITLES)
                .hasSize(ZeldaOcarinaSongs.COUNT)
                .containsExactly(
                        "Ocarina of Time Theme",
                        "Zelda's Lullaby",
                        "Epona's Song",
                        "Saria's Song",
                        "Sun's Song",
                        "Song of Time",
                        "Song of Storms",
                        "Minuet of Forest",
                        "Bolero of Fire",
                        "Serenade of Water",
                        "Requiem of Spirit",
                        "Nocturne of Shadow",
                        "Prelude of Light",
                        "Scarecrow's Song (Custom)");

        assertThat(ZeldaOcarinaSongs.repeatCount(0)).isEqualTo(2);
        assertThat(ZeldaOcarinaSongs.noteCount(0)).isEqualTo(87);
    }

    @Test
    void opensEveryArrangementWithTheCanonicalFiveNoteOcarinaCall() {
        assertThat(ocarinaFrequencies(1)).containsExactly(494, 587, 294, 494, 587, 294);
        assertThat(ocarinaFrequencies(2)).containsExactly(587, 494, 294, 587, 494, 294);
        assertThat(ocarinaFrequencies(3)).containsExactly(349, 294, 494, 349, 294, 494);
        assertThat(ocarinaFrequencies(4)).containsExactly(294, 349, 587, 294, 349, 587);
        assertThat(ocarinaFrequencies(5)).containsExactly(294, 440, 349, 294, 440, 349);
        assertThat(ocarinaFrequencies(6)).containsExactly(440, 349, 587, 440, 349, 587);
        assertThat(ocarinaFrequencies(7)).containsExactly(440, 587, 494, 294, 494, 294);
        assertThat(ocarinaFrequencies(8)).containsExactly(349, 440, 349, 440, 294, 349, 294, 349);
        assertThat(ocarinaFrequencies(9)).containsExactly(440, 349, 294, 294, 494);
        assertThat(ocarinaFrequencies(10)).containsExactly(440, 349, 440, 294, 349, 440);
        assertThat(ocarinaFrequencies(11)).containsExactly(494, 294, 294, 440, 494, 294, 349);
        assertThat(ocarinaFrequencies(12)).containsExactly(587, 294, 587, 294, 494, 587);
        assertThat(ocarinaFrequencies(13)).containsExactly(440, 587, 349, 294, 494, 349, 294, 587);
    }

    @Test
    void followsEveryOcarinaCallWithACompleteTimedResponse() {
        assertThat(songNoteCounts()).containsExactly(87, 55, 48, 46, 19, 20, 49, 27, 35, 23, 18, 34, 36, 32);

        for (int song = 1; song < ZeldaOcarinaSongs.COUNT; song++) {
            int ocarinaNotes = ZeldaOcarinaSongs.ocarinaNoteCount(song);
            assertThat(ZeldaOcarinaSongs.noteCount(song)).isGreaterThan(ocarinaNotes);
            assertThat(ZeldaOcarinaSongs.stepMillis(song, ocarinaNotes - 1)
                            - ZeldaOcarinaSongs.toneMillis(song, ocarinaNotes - 1))
                    .as("recognition breath for %s", ZeldaOcarinaSongs.title(song))
                    .isGreaterThanOrEqualTo(700);

            for (int note = 0; note < ZeldaOcarinaSongs.noteCount(song); note++) {
                assertThat(ZeldaOcarinaSongs.frequencyHz(song, note)).isBetween(31, 20_000);
                assertThat(ZeldaOcarinaSongs.toneMillis(song, note)).isPositive();
                assertThat(ZeldaOcarinaSongs.stepMillis(song, note))
                        .isGreaterThanOrEqualTo(ZeldaOcarinaSongs.toneMillis(song, note));
            }
        }
    }

    @Test
    void splitsCompleteTitlesIntoFourCharacterScrollFramesAndSelectionWraps() {
        char[] frame = new char[ZeldaOcarinaSongs.FRAME_WIDTH];
        for (int song = 0; song < ZeldaOcarinaSongs.COUNT; song++) {
            assertThat(ZeldaOcarinaSongs.frameCount(song)).isGreaterThan(1);
            for (int frameIndex = 0; frameIndex < ZeldaOcarinaSongs.frameCount(song); frameIndex++) {
                ZeldaOcarinaSongs.split(song, frameIndex, frame);
                assertThat(frame).hasSize(4).doesNotContain('\0');
            }
        }

        ZeldaOcarinaSongs.split(0, 0, frame);
        assertThat(frame).containsExactly('1', ' ', 'O', 'C');
        ZeldaOcarinaSongs.split(0, ZeldaOcarinaSongs.frameCount(0) - 1, frame);
        assertThat(frame).containsExactly('H', 'E', 'M', 'E');
        ZeldaOcarinaSongs.split(9, 0, frame);
        assertThat(frame).containsExactly('1', '0', ' ', 'S');
        ZeldaOcarinaSongs.split(13, 0, frame);
        assertThat(frame).containsExactly('1', '4', ' ', 'S');

        assertThat(ZeldaOcarinaSongs.nextIndex(0)).isEqualTo(1);
        assertThat(ZeldaOcarinaSongs.nextIndex(ZeldaOcarinaSongs.COUNT - 1)).isZero();
    }

    private static int[] ocarinaFrequencies(int song) {
        int[] frequencies = new int[ZeldaOcarinaSongs.ocarinaNoteCount(song)];
        for (int note = 0; note < frequencies.length; note++) {
            frequencies[note] = ZeldaOcarinaSongs.frequencyHz(song, note);
        }
        return frequencies;
    }

    private static int[] songNoteCounts() {
        int[] counts = new int[ZeldaOcarinaSongs.COUNT];
        for (int song = 0; song < counts.length; song++) {
            counts[song] = ZeldaOcarinaSongs.noteCount(song);
        }
        return counts;
    }
}
