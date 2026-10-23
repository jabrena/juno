package io.github.jabrena.juno.usecases.zelda.theme;

/** A monophonic Cardputer arrangement of the Hyrule Field Main Theme. */
final class ZeldaHyruleFieldTheme {
    private static final int REPEAT_COUNT = 2;
    private static final int[] FREQUENCIES_HZ = {
        392, 587, 659, 698, 784, 587, 659, 698,
        784, 880, 784, 698, 659, 587, 523, 587,
        659, 698, 784, 587, 523, 494, 440, 392
    };
    private static final int[] TONE_MILLIS = {
        220, 220, 220, 220, 420, 220, 220, 220,
        420, 220, 220, 220, 420, 220, 220, 220,
        420, 220, 220, 220, 420, 220, 220, 520
    };
    private static final int[] STEP_MILLIS = {
        270, 270, 270, 270, 470, 270, 270, 270,
        470, 270, 270, 270, 470, 270, 270, 270,
        470, 270, 270, 270, 470, 270, 270, 570
    };

    private ZeldaHyruleFieldTheme() {
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
