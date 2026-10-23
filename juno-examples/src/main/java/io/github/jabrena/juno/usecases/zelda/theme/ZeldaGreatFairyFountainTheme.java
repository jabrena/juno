package io.github.jabrena.juno.usecases.zelda.theme;

/** Monophonic Cardputer arrangement of the Great Fairy's Fountain broken chords and lead melody. */
final class ZeldaGreatFairyFountainTheme {
    private static final int REPEAT_COUNT = 2;
    private static final int[] FREQUENCIES_HZ = {
        880, 587, 466, 392, 784, 587, 466, 392,
        740, 587, 466, 392, 784, 587, 466, 392,
        784, 523, 440, 349, 698, 523, 440, 349,
        659, 523, 440, 349, 698, 523, 440, 349,
        880, 784, 740, 784, 932, 880, 831, 880,
        1047, 932, 880, 932, 880, 784, 698, 659
    };
    private static final int[] TONE_MILLIS = {
        165, 165, 165, 165, 165, 165, 165, 165,
        165, 165, 165, 165, 165, 165, 165, 165,
        165, 165, 165, 165, 165, 165, 165, 165,
        165, 165, 165, 165, 165, 165, 165, 165,
        690, 690, 690, 690, 690, 690, 690, 690,
        690, 690, 690, 690, 690, 690, 690, 690
    };
    private static final int[] STEP_MILLIS = {
        188, 188, 188, 188, 188, 188, 188, 188,
        188, 188, 188, 188, 188, 188, 188, 188,
        188, 188, 188, 188, 188, 188, 188, 188,
        188, 188, 188, 188, 188, 188, 188, 400,
        750, 750, 750, 750, 750, 750, 750, 750,
        750, 750, 750, 750, 750, 750, 750, 1000
    };

    private ZeldaGreatFairyFountainTheme() {
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
