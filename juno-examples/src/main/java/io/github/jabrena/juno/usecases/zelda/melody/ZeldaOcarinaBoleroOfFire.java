package io.github.jabrena.juno.usecases.zelda.melody;

/** The eight-note ocarina call followed by the complete Bolero of Fire response. */
final class ZeldaOcarinaBoleroOfFire {
    private static final int OCARINA_NOTE_COUNT = 8;
    private static final int[] FREQUENCIES_HZ = {
        349, 440, 349, 440, 294, 349, 294, 349,
        698, 587, 698, 587, 880, 698, 880, 698, 698, 587, 698, 587, 880, 698, 880, 698, 784, 659,
        784, 659, 988, 784, 988, 784, 698, 659, 587
    };
    private static final int[] TONE_MILLIS = {
        328, 328, 328, 328, 328, 328, 328, 1688,
        328, 328, 328, 328, 328, 328, 328, 1688, 328, 328, 328, 328, 328, 328, 328, 1688, 328, 328,
        328, 328, 328, 328, 328, 328, 656, 656, 1313
    };
    private static final int[] STEP_MILLIS = {
        375, 375, 375, 375, 375, 375, 375, 2575,
        375, 375, 375, 375, 375, 375, 375, 1875, 375, 375, 375, 375, 375, 375, 375, 1875, 375, 375,
        375, 375, 375, 375, 375, 375, 750, 750, 1313
    };

    private ZeldaOcarinaBoleroOfFire() {}

    static int ocarinaNoteCount() { return OCARINA_NOTE_COUNT; }
    static int noteCount() { return FREQUENCIES_HZ.length; }
    static int frequencyHz(int note) { return FREQUENCIES_HZ[note]; }
    static int toneMillis(int note) { return TONE_MILLIS[note]; }
    static int stepMillis(int note) { return STEP_MILLIS[note]; }
}
