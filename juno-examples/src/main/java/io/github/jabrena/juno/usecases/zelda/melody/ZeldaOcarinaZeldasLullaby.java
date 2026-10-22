package io.github.jabrena.juno.usecases.zelda.melody;

/** The six-note ocarina call followed by the complete Zelda's Lullaby response. */
final class ZeldaOcarinaZeldasLullaby {
    private static final int OCARINA_NOTE_COUNT = 6;
    private static final int[] FREQUENCIES_HZ = {
        494, 587, 294, 494, 587, 294,
        659, 784, 587, 523, 587, 659, 784, 587, 659, 784, 1175, 1047, 784, 698, 659, 587,
        659, 784, 587, 523, 587, 659, 784, 587, 659, 784, 1175, 1047, 784, 784, 698, 659,
        698, 659, 523, 784, 698, 659, 698, 659, 880, 784, 698, 659, 698, 659, 523, 698, 1047
    };
    private static final int[] TONE_MILLIS = {
        1010, 505, 1514, 1010, 505, 1514,
        1010, 505, 1010, 252, 252, 1010, 505, 1514, 1010, 505, 1010, 505, 1010, 252, 252, 1514,
        1010, 505, 1010, 252, 252, 1010, 505, 1514, 1010, 505, 1010, 505, 1514, 1010, 252, 252,
        252, 252, 1010, 1010, 252, 252, 252, 252, 1010, 1010, 252, 252, 252, 252, 505, 505, 1514
    };
    private static final int[] STEP_MILLIS = {
        1154, 577, 1731, 1154, 577, 2431,
        1154, 577, 1154, 288, 288, 1154, 577, 1731, 1154, 577, 1154, 577, 1154, 288, 288, 1731,
        1154, 577, 1154, 288, 288, 1154, 577, 1731, 1154, 577, 1154, 577, 1731, 1154, 288, 288,
        288, 288, 1154, 1154, 288, 288, 288, 288, 1154, 1154, 288, 288, 288, 288, 577, 577, 1514
    };

    private ZeldaOcarinaZeldasLullaby() {}

    static int ocarinaNoteCount() { return OCARINA_NOTE_COUNT; }
    static int noteCount() { return FREQUENCIES_HZ.length; }
    static int frequencyHz(int note) { return FREQUENCIES_HZ[note]; }
    static int toneMillis(int note) { return TONE_MILLIS[note]; }
    static int stepMillis(int note) { return STEP_MILLIS[note]; }
}
