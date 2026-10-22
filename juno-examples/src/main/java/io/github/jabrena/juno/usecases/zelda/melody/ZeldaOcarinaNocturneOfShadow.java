package io.github.jabrena.juno.usecases.zelda.melody;

/** The seven-note ocarina call followed by the complete Nocturne of Shadow response. */
final class ZeldaOcarinaNocturneOfShadow {
    private static final int OCARINA_NOTE_COUNT = 7;
    private static final int[] FREQUENCIES_HZ = {
        494, 294, 294, 440, 494, 294, 349,
        988, 880, 880, 587, 988, 880, 698, 988, 880, 880, 587, 988, 880, 698, 784, 698, 698, 523, 784,
        698, 831, 740, 740, 587, 831, 740, 698
    };
    private static final int[] TONE_MILLIS = {
        656, 656, 328, 328, 328, 328, 2625,
        656, 656, 328, 328, 328, 328, 2625, 656, 656, 328, 328, 328, 328, 2625, 656, 656, 328, 328, 328,
        328, 656, 656, 328, 328, 328, 328, 2625
    };
    private static final int[] STEP_MILLIS = {
        750, 750, 375, 375, 375, 375, 3700,
        750, 750, 375, 375, 375, 375, 3000, 750, 750, 375, 375, 375, 375, 3000, 750, 750, 375, 375, 375,
        375, 750, 750, 375, 375, 375, 375, 2625
    };

    private ZeldaOcarinaNocturneOfShadow() {}

    static int ocarinaNoteCount() { return OCARINA_NOTE_COUNT; }
    static int noteCount() { return FREQUENCIES_HZ.length; }
    static int frequencyHz(int note) { return FREQUENCIES_HZ[note]; }
    static int toneMillis(int note) { return TONE_MILLIS[note]; }
    static int stepMillis(int note) { return STEP_MILLIS[note]; }
}
