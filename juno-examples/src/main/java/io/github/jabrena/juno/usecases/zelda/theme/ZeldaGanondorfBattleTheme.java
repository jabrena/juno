package io.github.jabrena.juno.usecases.zelda.theme;

/** Monophonic Cardputer arrangement of the Ganondorf Battle ostinato and brass descent. */
final class ZeldaGanondorfBattleTheme {
    private static final int REPEAT_COUNT = 2;
    private static final int[] FREQUENCIES_HZ = {
        196, 262, 294, 196, 262, 294, 196, 262,
        294, 196, 349, 311, 294, 196, 262, 294,
        196, 262, 294, 196, 349, 311, 294, 208,
        277, 311, 208, 277, 311, 208, 277, 311,
        208, 370, 330, 311, 208, 277, 311, 208,
        466, 415, 392, 415, 392, 349, 392, 349,
        311, 294, 311, 294, 262, 466, 415, 392,
        415, 392, 349, 392, 349, 311, 262, 523,
        466, 440, 466, 440, 392, 440, 392, 349,
        311, 349, 311, 294, 523, 466, 440
    };
    private static final int[] TONE_MILLIS = {
        125, 125, 125, 125, 125, 125, 125, 125,
        125, 125, 125, 125, 125, 125, 125, 125,
        125, 125, 125, 125, 125, 125, 125, 125,
        125, 125, 125, 125, 125, 125, 125, 125,
        125, 125, 125, 125, 125, 125, 125, 125,
        125, 125, 125, 125, 125, 125, 125, 125,
        125, 125, 125, 125, 125, 125, 125, 125,
        125, 125, 125, 125, 125, 125, 125, 125,
        125, 125, 125, 125, 125, 125, 125, 125,
        125, 125, 125, 125, 125, 125, 125
    };
    private static final int[] STEP_MILLIS = {
        143, 143, 143, 143, 143, 143, 143, 143,
        143, 143, 143, 143, 143, 143, 143, 143,
        143, 143, 143, 143, 143, 143, 143, 143,
        143, 143, 143, 143, 143, 143, 143, 143,
        143, 143, 143, 143, 143, 143, 143, 500,
        143, 143, 143, 143, 143, 143, 143, 143,
        143, 143, 143, 143, 143, 143, 143, 143,
        143, 143, 143, 143, 143, 143, 143, 143,
        143, 143, 143, 143, 143, 143, 143, 143,
        143, 143, 143, 143, 143, 143, 700
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
