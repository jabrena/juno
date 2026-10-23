package io.github.jabrena.juno.usecases.zelda.theme;

/** Monophonic Cardputer arrangement of the Temple of Time ocarina statement. */
final class ZeldaTempleOfTimeTheme {
    private static final int REPEAT_COUNT = 2;
    private static final int[] FREQUENCIES_HZ = {
        880, 587, 698, 880, 587, 698, 880, 1047,
        988, 784, 698, 784, 880, 587, 523, 659,
        587
    };
    private static final int[] TONE_MILLIS = {
        460, 940, 460, 460, 940, 460, 220, 220,
        460, 460, 220, 220, 460, 460, 220, 220,
        1950
    };
    private static final int[] STEP_MILLIS = {
        500, 1000, 500, 500, 1000, 500, 250, 250,
        500, 500, 250, 250, 500, 500, 250, 250,
        2250
    };

    private ZeldaTempleOfTimeTheme() {
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
