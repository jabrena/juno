package io.github.jabrena.juno.usecases.zelda.melody;

/** The six-note ocarina call followed by the complete Requiem of Spirit response. */
final class ZeldaOcarinaRequiemOfSpirit {
    private static final int OCARINA_NOTE_COUNT = 6;
    private static final int[] FREQUENCIES_HZ = {
        440, 349, 440, 294, 349, 440,
        587, 698, 587, 880, 698, 587, 587, 698, 587, 880, 698, 587
    };
    private static final int[] TONE_MILLIS = {
        656, 328, 328, 656, 656, 2625,
        656, 328, 328, 656, 656, 2625, 656, 328, 328, 656, 656, 2625
    };
    private static final int[] STEP_MILLIS = {
        750, 375, 375, 750, 750, 3700,
        750, 375, 375, 750, 750, 3000, 750, 375, 375, 750, 750, 2625
    };

    private ZeldaOcarinaRequiemOfSpirit() {}

    static int ocarinaNoteCount() { return OCARINA_NOTE_COUNT; }
    static int noteCount() { return FREQUENCIES_HZ.length; }
    static int frequencyHz(int note) { return FREQUENCIES_HZ[note]; }
    static int toneMillis(int note) { return TONE_MILLIS[note]; }
    static int stepMillis(int note) { return STEP_MILLIS[note]; }
}
