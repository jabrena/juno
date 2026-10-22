package io.github.jabrena.juno.usecases.zelda.melody;

/** The six-note ocarina call followed by the complete Epona's Song response. */
final class ZeldaOcarinaEponasSong {
    private static final int OCARINA_NOTE_COUNT = 6;
    private static final int[] FREQUENCIES_HZ = {
        587, 494, 294, 587, 494, 294,
        1175, 988, 880, 1175, 988, 880, 1175, 988, 880, 988, 880, 740, 698, 740, 1109, 1175, 988,
        1175, 1175, 1109, 988, 880, 1175, 1109, 988, 1175, 1109, 988, 1175, 988, 880, 988, 880,
        740, 698, 740, 988, 1109, 1175, 1175, 1319, 1175
    };
    private static final int[] TONE_MILLIS = {
        300, 300, 1050, 300, 300, 1050,
        300, 300, 1050, 300, 300, 1050, 300, 300, 600, 600, 1575, 600, 600, 600, 300, 300, 1050,
        1200, 600, 300, 300, 1050, 300, 300, 1050, 300, 300, 1050, 300, 300, 600, 600, 1575, 600,
        600, 600, 300, 300, 1050, 1200, 600, 1050
    };
    private static final int[] STEP_MILLIS = {
        300, 300, 1200, 300, 300, 1900,
        300, 300, 1200, 300, 300, 1200, 300, 300, 600, 600, 1800, 600, 600, 600, 300, 300, 1200,
        1200, 600, 300, 300, 1200, 300, 300, 1200, 300, 300, 1200, 300, 300, 600, 600, 1800, 600,
        600, 600, 300, 300, 1200, 1200, 600, 1050
    };

    private ZeldaOcarinaEponasSong() {}

    static int ocarinaNoteCount() { return OCARINA_NOTE_COUNT; }
    static int noteCount() { return FREQUENCIES_HZ.length; }
    static int frequencyHz(int note) { return FREQUENCIES_HZ[note]; }
    static int toneMillis(int note) { return TONE_MILLIS[note]; }
    static int stepMillis(int note) { return STEP_MILLIS[note]; }
}
