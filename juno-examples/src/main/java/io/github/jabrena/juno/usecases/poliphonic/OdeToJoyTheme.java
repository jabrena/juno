package io.github.jabrena.juno.usecases.poliphonic;

/**
 * Monophonic RCX arrangement of the "Ode to Joy" melody (Beethoven, Symphony No. 9) used by the polyphonic RCX
 * orchestra. The melody is a flash table, so playing allocates nothing. Frequencies follow the simplified piano
 * arrangement used by common Arduino "Ode to Joy" sketches.
 */
public class OdeToJoyTheme {
    private static final int[] FREQUENCIES_HZ = {
        330, 330, 349, 392, 392, 349, 330, 294, 262, 262, 294, 330, 330, 294, 294,
        330, 330, 349, 392, 392, 349, 330, 294, 262, 262, 294, 330, 294, 262, 262,
        294, 294, 330, 262, 294, 330, 349, 330, 262, 294, 330, 349, 330, 294, 262, 294, 196,
        330, 330, 349, 392, 392, 349, 330, 294, 262, 262, 294, 330, 294, 262, 262
    };

    private OdeToJoyTheme() {
    }

    public static int repeatCount() {
        return 2;
    }

    public static int noteCount() {
        return FREQUENCIES_HZ.length;
    }

    public static int durationMillis() {
        return FREQUENCIES_HZ.length * 400;
    }

    public static int frequencyHz(int note) {
        return FREQUENCIES_HZ[note];
    }

    public static int toneMillis(int note) {
        return 400;
    }

    public static int stepMillis(int note) {
        return 400;
    }
}
