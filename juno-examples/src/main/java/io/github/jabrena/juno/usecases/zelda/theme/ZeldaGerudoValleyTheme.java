package io.github.jabrena.juno.usecases.zelda.theme;

/** A monophonic Cardputer arrangement of the Gerudo Valley theme. */
final class ZeldaGerudoValleyTheme {
    private static final int REPEAT_COUNT = 2;
    private static final int[] FREQUENCIES_HZ = {
        330, 392, 440, 494, 440, 392, 330, 294,
        330, 392, 440, 494, 587, 523, 494, 440,
        392, 330, 392, 440, 494, 440, 392, 330
    };
    private static final int[] TONE_MILLIS = {
        180, 180, 180, 180, 300, 180, 180, 180,
        300, 180, 180, 180, 300, 180, 180, 180,
        300, 180, 180, 180, 300, 180, 180, 420
    };
    private static final int[] STEP_MILLIS = {
        230, 230, 230, 230, 350, 230, 230, 230,
        350, 230, 230, 230, 350, 230, 230, 230,
        350, 230, 230, 230, 350, 230, 230, 470
    };

    private ZeldaGerudoValleyTheme() {
    }

    static int repeatCount() {
        return REPEAT_COUNT;
    }

    static int noteCount() {
        return FREQUENCIES_HZ.length;
    }

    static int frequencyHz(int note) {
        return FREQUENCIES_HZ[note];
    }

    static int toneMillis(int note) {
        return TONE_MILLIS[note];
    }

    static int stepMillis(int note) {
        return STEP_MILLIS[note];
    }
}
