package io.github.jabrena.juno.usecases.zelda.melody;

/** The six-note ocarina call followed by the complete Minuet of Forest response. */
final class ZeldaOcarinaMinuetOfForest {
    private static final int OCARINA_NOTE_COUNT = 6;
    private static final int[] FREQUENCIES_HZ = {
        440, 587, 494, 294, 494, 294,
        587, 1175, 988, 880, 988, 880, 587, 1175, 988, 880, 988, 880, 659, 880, 784, 880, 784, 880,
        784, 740, 659
    };
    private static final int[] TONE_MILLIS = {
        263, 263, 1050, 263, 263, 1050,
        263, 263, 1050, 263, 263, 1050, 263, 263, 1050, 263, 263, 1050, 263, 263, 1050, 263, 263,
        1050, 263, 263, 1050
    };
    private static final int[] STEP_MILLIS = {
        300, 300, 1200, 300, 300, 1900,
        300, 300, 1200, 300, 300, 1200, 300, 300, 1200, 300, 300, 1200, 300, 300, 1200, 300, 300,
        1200, 300, 300, 1050
    };

    private ZeldaOcarinaMinuetOfForest() {}

    static int ocarinaNoteCount() { return OCARINA_NOTE_COUNT; }
    static int noteCount() { return FREQUENCIES_HZ.length; }
    static int frequencyHz(int note) { return FREQUENCIES_HZ[note]; }
    static int toneMillis(int note) { return TONE_MILLIS[note]; }
    static int stepMillis(int note) { return STEP_MILLIS[note]; }
}
