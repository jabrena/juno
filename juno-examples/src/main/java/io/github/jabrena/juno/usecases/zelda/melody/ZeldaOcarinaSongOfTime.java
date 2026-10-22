package io.github.jabrena.juno.usecases.zelda.melody;

/** The six-note ocarina call followed by the complete Song of Time response. */
final class ZeldaOcarinaSongOfTime {
    private static final int OCARINA_NOTE_COUNT = 6;
    private static final int[] FREQUENCIES_HZ = {
        294, 440, 349, 294, 440, 349,
        880, 587, 698, 880, 1047, 988, 784, 698, 784, 880, 587, 523, 659, 587
    };
    private static final int[] TONE_MILLIS = {
        536, 1071, 536, 536, 1071, 536,
        536, 1071, 536, 268, 268, 536, 536, 268, 268, 536, 536, 268, 268, 536
    };
    private static final int[] STEP_MILLIS = {
        612, 1224, 612, 612, 1224, 1312,
        612, 1224, 612, 306, 306, 612, 612, 306, 306, 612, 612, 306, 306, 536
    };

    private ZeldaOcarinaSongOfTime() {}

    static int ocarinaNoteCount() { return OCARINA_NOTE_COUNT; }
    static int noteCount() { return FREQUENCIES_HZ.length; }
    static int frequencyHz(int note) { return FREQUENCIES_HZ[note]; }
    static int toneMillis(int note) { return TONE_MILLIS[note]; }
    static int stepMillis(int note) { return STEP_MILLIS[note]; }
}
