package io.github.jabrena.juno.usecases.zelda.theme;

/** A monophonic Cardputer arrangement of the Temple of Time theme. */
final class ZeldaTempleOfTimeTheme {
    private static final int REPEAT_COUNT = 2;
    private static final int[] FREQUENCIES_HZ = {
        294, 440, 587, 698, 659, 587, 523, 440,
        294, 440, 587, 784, 698, 659, 587, 440,
        294, 330, 392, 294
    };
    private static final int[] TONE_MILLIS = {
        360, 360, 360, 360, 520, 360, 360, 360,
        520, 360, 360, 360, 520, 360, 360, 360,
        360, 360, 360, 560
    };
    private static final int[] STEP_MILLIS = {
        410, 410, 410, 410, 570, 410, 410, 410,
        570, 410, 410, 410, 570, 410, 410, 410,
        410, 410, 410, 610
    };

    private ZeldaTempleOfTimeTheme() {
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
