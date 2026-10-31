package io.github.jabrena.juno.usecases.zeldapoliphonicrcx;

/** A low-register bass ostinato on the fourth RCX brick, completing the four-instrument Ocarina arrangement. */
final class ZeldaOcarinaBass {
    private static final int[] FREQUENCIES_HZ = {
        65, 87, 110, 131, 65, 87, 110, 131, 65, 87,
        110, 131, 65, 87, 110, 131, 65, 87, 110, 131
    };

    private ZeldaOcarinaBass() {
    }

    static int frequencyHz(int note) {
        return FREQUENCIES_HZ[note % FREQUENCIES_HZ.length];
    }

    static int noteCount() {
        return FREQUENCIES_HZ.length;
    }
}
