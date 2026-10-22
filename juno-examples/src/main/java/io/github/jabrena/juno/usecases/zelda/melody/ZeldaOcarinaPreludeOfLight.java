package io.github.jabrena.juno.usecases.zelda.melody;

/** The six-note ocarina call followed by the complete Prelude of Light response. */
final class ZeldaOcarinaPreludeOfLight {
    private static final int OCARINA_NOTE_COUNT = 6;
    private static final int[] FREQUENCIES_HZ = {
        587, 294, 587, 294, 494, 587,
        1047, 784, 1047, 784, 880, 1047, 1047, 784, 1047, 784, 880, 1047, 1047, 784, 1047, 784,
        880, 988, 880, 784, 880, 988, 880, 784, 1047, 784, 1047, 784, 880, 1047
    };
    private static final int[] TONE_MILLIS = {
        263, 825, 263, 263, 263, 2400,
        263, 825, 263, 263, 263, 2400, 263, 825, 263, 263, 263, 2400, 263, 825, 263, 263, 263, 825,
        263, 263, 263, 263, 263, 263, 263, 825, 263, 263, 263, 2400
    };
    private static final int[] STEP_MILLIS = {
        300, 900, 300, 300, 300, 3400,
        300, 900, 300, 300, 300, 2700, 300, 900, 300, 300, 300, 2700, 300, 900, 300, 300, 300, 900,
        300, 300, 300, 300, 300, 300, 300, 900, 300, 300, 300, 2400
    };

    private ZeldaOcarinaPreludeOfLight() {}

    static int ocarinaNoteCount() { return OCARINA_NOTE_COUNT; }
    static int noteCount() { return FREQUENCIES_HZ.length; }
    static int frequencyHz(int note) { return FREQUENCIES_HZ[note]; }
    static int toneMillis(int note) { return TONE_MILLIS[note]; }
    static int stepMillis(int note) { return STEP_MILLIS[note]; }
}
