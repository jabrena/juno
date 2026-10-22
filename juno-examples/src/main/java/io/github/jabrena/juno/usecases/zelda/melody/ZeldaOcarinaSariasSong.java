package io.github.jabrena.juno.usecases.zelda.melody;

/** The six-note ocarina call followed by the complete Saria's Song response. */
final class ZeldaOcarinaSariasSong {
    private static final int OCARINA_NOTE_COUNT = 6;
    private static final int[] FREQUENCIES_HZ = {
        349, 294, 494, 349, 294, 494,
        698, 880, 988, 698, 880, 988, 698, 880, 988, 1319, 1175, 988, 1047, 988, 784, 659, 587, 659,
        784, 659, 698, 880, 988, 698, 880, 988, 698, 880, 988, 1319, 1175, 988, 1047, 1175, 1047,
        784, 988, 784, 587, 659
    };
    private static final int[] TONE_MILLIS = {
        219, 219, 438, 219, 219, 438,
        219, 219, 438, 219, 219, 438, 219, 219, 219, 219, 438, 219, 219, 219, 219, 938, 219, 219, 219,
        1375, 219, 219, 438, 219, 219, 438, 219, 219, 219, 219, 438, 219, 219, 219, 219, 938, 219, 219,
        219, 1375
    };
    private static final int[] STEP_MILLIS = {
        250, 250, 500, 250, 250, 1200,
        250, 250, 500, 250, 250, 500, 250, 250, 250, 250, 500, 250, 250, 250, 250, 1250, 250, 250, 250,
        1500, 250, 250, 500, 250, 250, 500, 250, 250, 250, 250, 500, 250, 250, 250, 250, 1250, 250,
        250, 250, 1375
    };

    private ZeldaOcarinaSariasSong() {}

    static int ocarinaNoteCount() { return OCARINA_NOTE_COUNT; }
    static int noteCount() { return FREQUENCIES_HZ.length; }
    static int frequencyHz(int note) { return FREQUENCIES_HZ[note]; }
    static int toneMillis(int note) { return TONE_MILLIS[note]; }
    static int stepMillis(int note) { return STEP_MILLIS[note]; }
}
