package io.github.jabrena.juno.usecases.poliphonic;

public class TetrisCounterpoint {
    private static final int[] FREQUENCIES_HZ = {
        784, 880, 988, 880, 784, 659, 784, 659, 587, 659, 784, 880, 988, 880, 784, 659, 587, 523, 659, 784
    };

    private TetrisCounterpoint() {
    }

    public static int noteCount() {
        return FREQUENCIES_HZ.length;
    }

    public static int frequencyHz(int note) {
        return FREQUENCIES_HZ[note % FREQUENCIES_HZ.length];
    }
}
