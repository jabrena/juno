package io.github.jabrena.juno.usecases.poliphonic;

/** A high-register counterpoint track for the Ode to Joy polyphonic RCX orchestra, cycling over its note table. */
public class OdeToJoyCounterpoint {
    private static final int[] FREQUENCIES_HZ = {
        523, 659, 784, 659, 523, 587, 659, 523, 523, 659,
        784, 988, 784, 659, 523, 659, 523, 587, 659, 523
    };

    private OdeToJoyCounterpoint() {
    }

    public static int noteCount() {
        return FREQUENCIES_HZ.length;
    }

    public static int frequencyHz(int note) {
        return FREQUENCIES_HZ[note % FREQUENCIES_HZ.length];
    }
}
