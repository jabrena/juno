package io.github.jabrena.juno.usecases.poliphonic;

/** An arpeggio accompaniment track for the Ode to Joy polyphonic RCX orchestra, cycling over its note table. */
public class OdeToJoyArpeggios {
    private static final int[] FREQUENCIES_HZ = {
        262, 330, 392, 523, 392, 330, 262, 330, 392, 523,
        659, 523, 392, 330, 262, 330, 392, 523, 392, 330
    };

    private OdeToJoyArpeggios() {
    }

    public static int noteCount() {
        return FREQUENCIES_HZ.length;
    }

    public static int frequencyHz(int note) {
        return FREQUENCIES_HZ[note % FREQUENCIES_HZ.length];
    }
}
