package io.github.jabrena.juno.usecases.poliphonic;

/** A mid-register harmony track for the Ode to Joy polyphonic RCX orchestra, cycling over its note table. */
public class OdeToJoyHarmony {
    private static final int[] FREQUENCIES_HZ = {
        262, 294, 330, 349, 330, 294, 262, 247, 196, 196, 247, 262, 262, 247, 247,
        262, 294, 330, 349, 330
    };

    private OdeToJoyHarmony() {
    }

    public static int noteCount() {
        return FREQUENCIES_HZ.length;
    }

    public static int frequencyHz(int note) {
        return FREQUENCIES_HZ[note % FREQUENCIES_HZ.length];
    }
}
