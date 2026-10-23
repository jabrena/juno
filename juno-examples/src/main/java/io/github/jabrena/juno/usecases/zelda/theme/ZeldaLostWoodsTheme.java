package io.github.jabrena.juno.usecases.zelda.theme;

/** A monophonic Cardputer arrangement of the Lost Woods theme. */
final class ZeldaLostWoodsTheme {
    private static final int REPEAT_COUNT = 2;
    private static final int[] FREQUENCIES_HZ = {
        523, 440, 494, 392, 440, 349, 392, 330,
        523, 440, 494, 392, 440, 349, 392, 330
    };
    private static final int[] TONE_MILLIS = {
        180, 180, 180, 180, 280, 180, 180, 180,
        180, 180, 180, 180, 280, 180, 180, 420
    };
    private static final int[] STEP_MILLIS = {
        230, 230, 230, 230, 330, 230, 230, 230,
        230, 230, 230, 230, 330, 230, 230, 470
    };

    private ZeldaLostWoodsTheme() {
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
