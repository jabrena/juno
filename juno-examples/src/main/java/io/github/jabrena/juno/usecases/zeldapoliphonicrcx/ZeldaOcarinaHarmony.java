package io.github.jabrena.juno.usecases.zeldapoliphonicrcx;

/** The low-register accompaniment for the melody brick, kept as a compact repeating RCX track. */
final class ZeldaOcarinaHarmony {
    private static final int[] FREQUENCIES_HZ = {
        87, 175, 220, 294, 330, 65, 196, 247, 294, 330,
        87, 131, 175, 220, 294, 330, 65, 98, 165, 247
    };

    private ZeldaOcarinaHarmony() {
    }

    static int frequencyHz(int note) {
        return FREQUENCIES_HZ[note % FREQUENCIES_HZ.length];
    }

    static int noteCount() {
        return FREQUENCIES_HZ.length;
    }
}
