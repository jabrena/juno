package io.github.jabrena.juno.usecases.zelda.theme;

/** Monophonic Cardputer arrangement of the Gerudo Valley guitar pickup and lead melody. */
final class ZeldaGerudoValleyTheme {
    private static final int REPEAT_COUNT = 2;
    private static final int[] FREQUENCIES_HZ = {
        277, 370, 415, 440, 277, 370, 415, 440,
        294, 370, 415, 440, 294, 370, 415, 440,
        277, 440, 415, 370, 277, 330, 330, 294,
        247, 415, 370, 330, 294, 277, 294, 277,
        277, 440, 415, 370, 277, 330, 330, 294,
        294, 330, 370, 330, 294, 277, 440, 415,
        440, 415, 277, 330, 294, 277, 294
    };
    private static final int[] TONE_MILLIS = {
        110, 110, 110, 245, 110, 110, 110, 750,
        110, 110, 110, 245, 110, 110, 110, 750,
        340, 340, 340, 340, 220, 460, 340, 1125,
        340, 340, 340, 340, 220, 460, 340, 1000,
        340, 340, 340, 340, 220, 460, 340, 700,
        220, 650, 220, 650, 220, 1750, 340, 340,
        340, 340, 220, 560, 95, 95, 1000
    };
    private static final int[] STEP_MILLIS = {
        125, 125, 125, 375, 125, 125, 125, 875,
        125, 125, 125, 375, 125, 125, 125, 875,
        375, 375, 375, 375, 250, 500, 375, 1375,
        375, 375, 375, 375, 250, 500, 375, 1375,
        375, 375, 375, 375, 250, 500, 375, 875,
        250, 750, 250, 750, 250, 2000, 375, 375,
        375, 375, 250, 625, 125, 125, 1125
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
