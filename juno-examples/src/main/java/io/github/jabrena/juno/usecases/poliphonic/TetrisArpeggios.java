package io.github.jabrena.juno.usecases.poliphonic;

public class TetrisArpeggios {
    private static final int[] FREQUENCIES_HZ = {
        330, 415, 494, 659, 494, 415, 330, 415, 494, 659, 784, 659, 494, 415, 330, 415, 494, 659, 494, 415
    };

    private TetrisArpeggios() {
    }

    public static int noteCount() {
        return FREQUENCIES_HZ.length;
    }

    public static int frequencyHz(int note) {
        return FREQUENCIES_HZ[note % FREQUENCIES_HZ.length];
    }
}
