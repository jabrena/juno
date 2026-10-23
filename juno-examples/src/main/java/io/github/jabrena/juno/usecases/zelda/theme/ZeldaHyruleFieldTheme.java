package io.github.jabrena.juno.usecases.zelda.theme;

/** Monophonic Cardputer arrangement of the Hyrule Field lead phrase and its alternate ending. */
final class ZeldaHyruleFieldTheme {
    private static final int REPEAT_COUNT = 2;
    private static final int[] FREQUENCIES_HZ = {
        196, 147, 196, 392, 349, 330, 294, 262,
        247, 262, 294, 196, 147, 196, 392, 349,
        330, 294, 262, 262, 294, 247
    };
    private static final int[] TONE_MILLIS = {
        150, 300, 150, 300, 800, 300, 300, 200,
        100, 100, 2900, 150, 300, 150, 300, 800,
        300, 300, 200, 100, 100, 2900
    };
    private static final int[] STEP_MILLIS = {
        400, 400, 400, 400, 800, 300, 300, 200,
        100, 100, 3000, 400, 400, 400, 400, 800,
        300, 300, 200, 100, 100, 3000
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
