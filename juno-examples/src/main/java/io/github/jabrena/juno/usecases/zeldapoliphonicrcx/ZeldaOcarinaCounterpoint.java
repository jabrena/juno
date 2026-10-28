package io.github.jabrena.juno.usecases.zeldapoliphonicrcx;

/** A high-register counterpoint track, completing the three-instrument Ocarina arrangement. */
final class ZeldaOcarinaCounterpoint {
    private static final int[] FREQUENCIES_HZ = {
        523, 659, 784, 659, 440, 523, 659, 523, 523, 659,
        784, 988, 784, 659, 523, 659, 440, 523, 659, 523
    };

    private ZeldaOcarinaCounterpoint() {
    }

    static int frequencyHz(int note) {
        return FREQUENCIES_HZ[note % FREQUENCIES_HZ.length];
    }

    static int noteCount() {
        return FREQUENCIES_HZ.length;
    }
}
