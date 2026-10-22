package io.github.jabrena.juno.usecases.zelda.melody;

/** The six-note ocarina call followed by the complete Song of Storms response. */
final class ZeldaOcarinaSongOfStorms {
    private static final int OCARINA_NOTE_COUNT = 6;
    private static final int[] FREQUENCIES_HZ = {
        440, 349, 587, 440, 349, 587,
        587, 698, 1175, 587, 698, 1175, 1319, 1397, 1319, 1397, 1319, 1047, 880, 880, 587, 698,
        784, 880, 880, 587, 698, 784, 659, 587, 698, 1175, 587, 698, 1175, 1319, 1397, 1319, 1397,
        1319, 1047, 880, 880, 587, 698, 784, 880, 880, 587
    };
    private static final int[] TONE_MILLIS = {
        164, 164, 656, 164, 164, 656,
        164, 164, 656, 164, 164, 656, 492, 164, 164, 164, 164, 164, 656, 328, 328, 164, 164, 984,
        328, 328, 164, 164, 984, 164, 164, 656, 164, 164, 656, 492, 164, 164, 164, 164, 164, 656,
        328, 328, 164, 164, 656, 328, 328
    };
    private static final int[] STEP_MILLIS = {
        188, 188, 750, 188, 188, 1450,
        188, 188, 750, 188, 188, 750, 563, 188, 188, 188, 188, 188, 750, 375, 375, 188, 188, 1125,
        375, 375, 188, 188, 1125, 188, 188, 750, 188, 188, 750, 563, 188, 188, 188, 188, 188, 750,
        375, 375, 188, 188, 750, 375, 328
    };

    private ZeldaOcarinaSongOfStorms() {}

    static int ocarinaNoteCount() { return OCARINA_NOTE_COUNT; }
    static int noteCount() { return FREQUENCIES_HZ.length; }
    static int frequencyHz(int note) { return FREQUENCIES_HZ[note]; }
    static int toneMillis(int note) { return TONE_MILLIS[note]; }
    static int stepMillis(int note) { return STEP_MILLIS[note]; }
}
