package io.github.jabrena.juno.usecases.zelda.theme;

/** Monophonic Cardputer arrangement of the complete Lost Woods lead phrase. */
final class ZeldaLostWoodsTheme {
    private static final int REPEAT_COUNT = 2;
    private static final int[] FREQUENCIES_HZ = {
        698, 880, 988, 698, 880, 988, 698, 880,
        988, 1319, 1175, 988, 1047, 988, 784, 659,
        587, 659, 784, 659, 698, 880, 988, 698,
        880, 988, 698, 880, 988, 1319, 1175, 988,
        1047, 1319, 988, 784, 988, 784, 587, 659
    };
    private static final int[] TONE_MILLIS = {
        210, 90, 357, 210, 90, 357, 210, 90,
        210, 90, 357, 210, 90, 210, 90, 1068,
        210, 210, 90, 1068, 210, 90, 357, 210,
        90, 357, 210, 90, 210, 90, 357, 210,
        90, 210, 90, 1068, 210, 210, 90, 1068
    };
    private static final int[] STEP_MILLIS = {
        214, 214, 429, 214, 214, 429, 214, 214,
        214, 214, 429, 214, 214, 214, 214, 1072,
        214, 214, 214, 1287, 214, 214, 429, 214,
        214, 429, 214, 214, 214, 214, 429, 214,
        214, 214, 214, 1072, 214, 214, 214, 1287
    };

    private ZeldaLostWoodsTheme() {
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
