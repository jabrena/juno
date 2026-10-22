package io.github.jabrena.juno.usecases.zelda.melody;

/** The six-note ocarina call followed by the complete Sun's Song response. */
final class ZeldaOcarinaSunsSong {
    private static final int OCARINA_NOTE_COUNT = 6;
    private static final int[] FREQUENCIES_HZ = {
        294, 349, 587, 294, 349, 587,
        659, 523, 880, 659, 523, 880, 784, 880, 988, 1047, 1175, 1319, 1397
    };
    private static final int[] TONE_MILLIS = {
        202, 202, 202, 202, 202, 202,
        202, 202, 202, 202, 202, 202, 101, 101, 101, 101, 101, 101, 606
    };
    private static final int[] STEP_MILLIS = {
        231, 231, 692, 231, 231, 1392,
        231, 231, 692, 231, 231, 692, 115, 115, 115, 115, 115, 115, 606
    };

    private ZeldaOcarinaSunsSong() {}

    static int ocarinaNoteCount() { return OCARINA_NOTE_COUNT; }
    static int noteCount() { return FREQUENCIES_HZ.length; }
    static int frequencyHz(int note) { return FREQUENCIES_HZ[note]; }
    static int toneMillis(int note) { return TONE_MILLIS[note]; }
    static int stepMillis(int note) { return STEP_MILLIS[note]; }
}
