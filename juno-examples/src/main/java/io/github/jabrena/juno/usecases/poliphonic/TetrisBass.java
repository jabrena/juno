package io.github.jabrena.juno.usecases.poliphonic;

public class TetrisBass {
    private static final int[] FREQUENCIES_HZ = {
        165, 196, 220, 196, 165, 131, 165, 196, 220, 196, 165, 131, 110, 131, 165, 196, 220, 196, 165, 131
    };

    private TetrisBass() {
    }

    public static int noteCount() {
        return FREQUENCIES_HZ.length;
    }

    public static int frequencyHz(int note) {
        return FREQUENCIES_HZ[note % FREQUENCIES_HZ.length];
    }
}
