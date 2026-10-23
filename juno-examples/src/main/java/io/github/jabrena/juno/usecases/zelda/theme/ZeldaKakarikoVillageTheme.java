package io.github.jabrena.juno.usecases.zelda.theme;

/** Monophonic Cardputer arrangement of the Kakariko Village harmonica melody and grace notes. */
final class ZeldaKakarikoVillageTheme {
    private static final int REPEAT_COUNT = 2;
    private static final int[] FREQUENCIES_HZ = {
        349, 466, 349, 440, 494, 587, 466, 587,
        659, 698, 349, 466, 349, 440, 494, 587,
        466, 587, 659, 698, 698, 784, 440, 494,
        587, 784, 698, 587, 587
    };
    private static final int[] TONE_MILLIS = {
        594, 1194, 444, 69, 69, 1194, 444, 69,
        69, 2837, 594, 1194, 444, 69, 69, 1194,
        444, 69, 69, 2837, 594, 1794, 1037, 69,
        69, 594, 1794, 1637, 1706
    };
    private static final int[] STEP_MILLIS = {
        600, 1200, 450, 75, 75, 1200, 450, 75,
        75, 3000, 600, 1200, 450, 75, 75, 1200,
        450, 75, 75, 3000, 600, 1800, 1050, 75,
        75, 600, 1800, 1800, 1800
    };

    private ZeldaKakarikoVillageTheme() {
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
