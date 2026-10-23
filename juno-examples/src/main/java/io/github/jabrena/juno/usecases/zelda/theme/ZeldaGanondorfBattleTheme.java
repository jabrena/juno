package io.github.jabrena.juno.usecases.zelda.theme;

/** A monophonic Cardputer arrangement of the Ganondorf Battle theme. */
final class ZeldaGanondorfBattleTheme {
    private static final int REPEAT_COUNT = 2;
    private static final int[] FREQUENCIES_HZ = {
        147, 147, 220, 147, 277, 147, 220, 147,
        330, 277, 220, 147, 147, 147, 220, 147,
        311, 147, 220, 147, 370, 311, 277, 220,
        147, 220, 277, 147
    };
    private static final int[] TONE_MILLIS = {
        160, 160, 220, 160, 220, 160, 160, 160,
        300, 220, 160, 160, 160, 160, 220, 160,
        220, 160, 160, 160, 300, 220, 160, 160,
        160, 160, 220, 420
    };
    private static final int[] STEP_MILLIS = {
        210, 210, 270, 210, 270, 210, 210, 210,
        350, 270, 210, 210, 210, 210, 270, 210,
        270, 210, 210, 210, 350, 270, 210, 210,
        210, 210, 270, 470
    };

    private ZeldaGanondorfBattleTheme() {
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
