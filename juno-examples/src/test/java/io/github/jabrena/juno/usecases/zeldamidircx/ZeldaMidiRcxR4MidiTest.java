package io.github.jabrena.juno.usecases.zeldamidircx;

import io.github.jabrena.juno.hardware.device.sd.SdCard;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ZeldaMidiRcxR4MidiTest {
    private static final String MIDI_FILE = ZeldaMidiRcxR4Midi.MIDI_FILE;

    @Test
    void shouldKeepPianoStringsOcarinaAndViolinWithoutTheEchoTracks() throws IOException {
        ZeldaMidiRcxR4Midi score = new ZeldaMidiRcxR4Midi();

        assertThat(score.load(cardHolding(titleTheme()))).isTrue();

        assertThat(score.voiceCount(0)).as("piano").isEqualTo(168);
        assertThat(score.voiceCount(1)).as("strings").isEqualTo(136);
        assertThat(score.voiceCount(2)).as("ocarina").isEqualTo(96);
        assertThat(score.voiceCount(3)).as("violin").isEqualTo(32);
        assertThat(score.noteCount()).isEqualTo(432).isLessThanOrEqualTo(ZeldaMidiRcxR4Midi.MAX_NOTES);
        assertThat(score.noteNumber(2, 0)).as("the ocarina opens on D5").isEqualTo(74);
        assertThat(score.noteNumber(2, 2)).as("then leaps to D6").isEqualTo(86);
    }

    @Test
    void shouldStartEveryVoiceInOrderAndMatchTheNanoParser() throws IOException {
        byte[] file = titleTheme();
        ZeldaMidiRcxR4Midi score = new ZeldaMidiRcxR4Midi();
        score.load(cardHolding(file));
        ZeldaMidiRcxNanoMidi.midiData = file;
        ZeldaMidiRcxNanoMidi.parseMidi();

        for (int voice = 0; voice < ZeldaMidiRcxR4Midi.VOICES; voice++) {
            for (int i = 1; i < score.voiceCount(voice); i++) {
                assertThat(score.startMillis(voice, i)).isGreaterThanOrEqualTo(score.startMillis(voice, i - 1));
            }
        }
        // The Nano parser rounds down once per tempo change; this one converts each start exactly once.
        int tempoChanges = ZeldaMidiRcxNanoMidi.tempoCount;
        for (int i = 0; i < score.voiceCount(1); i++) {
            assertThat(score.noteNumber(1, i)).isEqualTo(ZeldaMidiRcxNanoMidi.noteNum2[i]);
            assertThat(score.startMillis(1, i))
                    .isCloseTo(ZeldaMidiRcxNanoMidi.ticksToMillis(ZeldaMidiRcxNanoMidi.noteTick2[i]), offset(tempoChanges));
        }
    }

    @Test
    void shouldRejectATruncatedFile() throws IOException {
        byte[] half = Arrays.copyOf(titleTheme(), titleTheme().length / 2);

        assertThat(new ZeldaMidiRcxR4Midi().load(cardHolding(half))).isFalse();
    }

    @Test
    void shouldRejectAMissingFile() throws IOException {
        assertThat(new ZeldaMidiRcxR4Midi().load(mock(SdCard.class))).isFalse();
    }

    @Test
    void shouldPlayTheViolinAnOctaveUpAndTheOtherVoicesAnOctaveDown() {
        assertThat(ZeldaMidiRcxR4.toneHz(3, 41)).as("violin F2 as F3").isEqualTo(175);
        assertThat(ZeldaMidiRcxR4.toneHz(3, 51)).as("violin D#3 as D#4").isEqualTo(311);
        assertThat(ZeldaMidiRcxR4.toneHz(2, 74)).as("ocarina D5 as D4").isEqualTo(294);
        assertThat(ZeldaMidiRcxR4.toneHz(0, 36)).as("piano C2 as C1").isEqualTo(33);
        assertThat(ZeldaMidiRcxR4.toneHz(0, 5)).as("below the table").isZero();
        assertThat(ZeldaMidiRcxR4.toneHz(3, 120)).as("above the table").isZero();
    }

    @Test
    void shouldFitRcxToneDurationsInOneByte() {
        assertThat(ZeldaMidiRcxR4.ORCHESTRA_CHANNELS).containsExactly(1, 2, 3, 4);
        assertThat(ZeldaMidiRcxR4.durationCentiseconds(0)).isEqualTo(1);
        assertThat(ZeldaMidiRcxR4.durationCentiseconds(274)).isEqualTo(27);
        assertThat(ZeldaMidiRcxR4.durationCentiseconds(3356)).isEqualTo(255);
    }

    private static SdCard cardHolding(byte[] file) {
        SdCard card = mock(SdCard.class);
        when(card.open(MIDI_FILE)).thenReturn(new ByteArrayInputStream(file));
        return card;
    }

    private static byte[] titleTheme() throws IOException {
        try (InputStream in = ZeldaMidiRcxR4MidiTest.class.getResourceAsStream("/midi/" + MIDI_FILE)) {
            return in.readAllBytes();
        }
    }
}
