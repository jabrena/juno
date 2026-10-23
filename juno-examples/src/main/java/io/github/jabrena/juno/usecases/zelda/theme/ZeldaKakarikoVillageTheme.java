package io.github.jabrena.juno.usecases.zelda.theme;

/** A monophonic Cardputer arrangement of the Kakariko Village theme. */
final class ZeldaKakarikoVillageTheme {
    private static final int REPEAT_COUNT = 2;
    private static final int[] FREQUENCIES_HZ = {
        294, 392, 440, 494, 440, 392, 330, 294,
        294, 392, 440, 523, 494, 440, 392, 330,
        294, 330, 392, 294
    };
    private static final int[] TONE_MILLIS = {
        320, 320, 320, 320, 480, 320, 320, 320,
        320, 320, 320, 320, 480, 320, 320, 320,
        320, 320, 320, 520
    };
    private static final int[] STEP_MILLIS = {
        370, 370, 370, 370, 530, 370, 370, 370,
        370, 370, 370, 370, 530, 370, 370, 370,
        370, 370, 370, 570
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
