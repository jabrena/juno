package io.github.jabrena.juno.usecases.zelda.melody;

/** A customizable eight-note Scarecrow call followed by an RCX response built from the same motif. */
final class ZeldaOcarinaScarecrowsSong {
    private static final int OCARINA_NOTE_COUNT = 8;
    private static final int[] FREQUENCIES_HZ = {
        440, 587, 349, 294, 494, 349, 294, 587,
        440, 587, 349, 294, 494, 349, 294, 587,
        440, 587, 349, 440, 494, 587, 494, 440,
        349, 440, 494, 587, 494, 440, 349, 294
    };
    private static final int[] TONE_MILLIS = {
        219, 219, 438, 219, 219, 438, 219, 875,
        219, 219, 438, 219, 219, 438, 219, 875,
        219, 219, 438, 219, 219, 438, 219, 875,
        219, 219, 219, 219, 438, 438, 438, 875
    };
    private static final int[] STEP_MILLIS = {
        250, 250, 500, 250, 250, 500, 250, 1700,
        250, 250, 500, 250, 250, 500, 250, 1000,
        250, 250, 500, 250, 250, 500, 250, 1000,
        250, 250, 250, 250, 500, 500, 500, 875
    };

    private ZeldaOcarinaScarecrowsSong() {}

    static int ocarinaNoteCount() { return OCARINA_NOTE_COUNT; }
    static int noteCount() { return FREQUENCIES_HZ.length; }
    static int frequencyHz(int note) { return FREQUENCIES_HZ[note]; }
    static int toneMillis(int note) { return TONE_MILLIS[note]; }
    static int stepMillis(int note) { return STEP_MILLIS[note]; }
}
