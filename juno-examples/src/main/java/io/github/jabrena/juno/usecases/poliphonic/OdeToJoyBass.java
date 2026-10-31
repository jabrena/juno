package io.github.jabrena.juno.usecases.poliphonic;

/** A low-register bass track for the Ode to Joy polyphonic RCX orchestra, cycling over its note table. */
public class OdeToJoyBass {
    private static final int[] FREQUENCIES_HZ = {
        131, 165, 196, 165, 131, 165, 196, 131, 131, 165,
        196, 262, 196, 165, 131, 165, 131, 147, 165, 131
    };

    private OdeToJoyBass() {
    }

    public static int noteCount() {
        return FREQUENCIES_HZ.length;
    }

    public static int frequencyHz(int note) {
        return FREQUENCIES_HZ[note % FREQUENCIES_HZ.length];
    }
}
