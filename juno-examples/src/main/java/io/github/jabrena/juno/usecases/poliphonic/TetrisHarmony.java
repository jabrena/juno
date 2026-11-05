package io.github.jabrena.juno.usecases.poliphonic;

public class TetrisHarmony {
    private static final int[] FREQUENCIES_HZ = {
        330, 349, 392, 440, 392, 349, 330, 294, 262, 294, 330, 349, 392, 440, 392, 349, 330, 294, 262, 247
    };

    private TetrisHarmony() {
    }

    public static int noteCount() {
        return FREQUENCIES_HZ.length;
    }

    public static int frequencyHz(int note) {
        return FREQUENCIES_HZ[note % FREQUENCIES_HZ.length];
    }
}
