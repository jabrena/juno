package io.github.jabrena.juno.usecases.zelda.melody;

/** The five-note ocarina call followed by the complete Serenade of Water response. */
final class ZeldaOcarinaSerenadeOfWater {
    private static final int OCARINA_NOTE_COUNT = 5;
    private static final int[] FREQUENCIES_HZ = {
        440, 349, 294, 294, 494,
        587, 698, 880, 880, 988, 587, 698, 880, 880, 988, 880, 587, 698, 784, 698, 659, 784, 740
    };
    private static final int[] TONE_MILLIS = {
        656, 656, 656, 656, 1313,
        656, 656, 656, 656, 1313, 656, 656, 656, 656, 1313, 656, 656, 656, 328, 328, 656, 656, 1969
    };
    private static final int[] STEP_MILLIS = {
        750, 750, 750, 750, 2200,
        750, 750, 750, 750, 1500, 750, 750, 750, 750, 1500, 750, 750, 750, 375, 375, 750, 750, 1969
    };

    private ZeldaOcarinaSerenadeOfWater() {}

    static int ocarinaNoteCount() { return OCARINA_NOTE_COUNT; }
    static int noteCount() { return FREQUENCIES_HZ.length; }
    static int frequencyHz(int note) { return FREQUENCIES_HZ[note]; }
    static int toneMillis(int note) { return TONE_MILLIS[note]; }
    static int stepMillis(int note) { return STEP_MILLIS[note]; }
}
