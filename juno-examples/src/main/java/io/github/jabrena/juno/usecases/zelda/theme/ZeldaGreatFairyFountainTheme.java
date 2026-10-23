package io.github.jabrena.juno.usecases.zelda.theme;

/** A monophonic Cardputer arrangement of the Great Fairy's Fountain theme. */
final class ZeldaGreatFairyFountainTheme {
    private static final int REPEAT_COUNT = 2;
    private static final int[] FREQUENCIES_HZ = {
        262, 330, 392, 523, 659, 784, 659, 523,
        392, 330, 262, 330, 392, 523, 659, 880,
        784, 659, 523, 392, 330, 392, 523, 262
    };
    private static final int[] TONE_MILLIS = {
        160, 160, 160, 160, 260, 160, 160, 160,
        260, 160, 160, 160, 160, 160, 160, 160,
        260, 160, 160, 160, 260, 160, 160, 520
    };
    private static final int[] STEP_MILLIS = {
        210, 210, 210, 210, 310, 210, 210, 210,
        310, 210, 210, 210, 210, 210, 210, 210,
        310, 210, 210, 210, 310, 210, 210, 570
    };

    private ZeldaGreatFairyFountainTheme() {
    }

    static int repeatCount() {
        return REPEAT_COUNT;
    }

    static int noteCount() {
        return FREQUENCIES_HZ.length;
    }

    static int frequencyHz(int note) {
        return FREQUENCIES_HZ[note];
    }

    static int toneMillis(int note) {
        return TONE_MILLIS[note];
    }

    static int stepMillis(int note) {
        return STEP_MILLIS[note];
    }
}
