package io.github.jabrena.juno.usecases.poliphonic;

/**
 * Monophonic RCX arrangement of the Tetris theme (Korobeiniki) used by the polyphonic RCX orchestra. The melody is
 * a flash table, so playing allocates nothing.
 */
public class TetrisTheme {
    private static final int[] FREQUENCIES_HZ = {
        659, 494, 523, 587, 523, 494, 440, 440, 523, 659, 587, 523, 494, 523, 587, 659,
        523, 440, 440, 0, 0, 587, 698, 880, 784, 698, 659, 523, 659, 587, 523, 494, 494,
        523, 587, 659, 523, 440, 440, 0, 659, 523, 587, 494, 523, 440, 494, 659, 523, 587,
        494, 523, 659, 880, 831, 659, 494, 523, 587, 523, 494, 440, 440, 523, 659, 587, 523,
        494, 523, 587, 659, 523, 440, 440, 0, 587, 698, 880, 784, 698, 0, 659, 523, 659, 587,
        523, 0, 494, 523, 587, 659, 0, 523, 440, 440, 0
    };

    private TetrisTheme() {
    }

    public static int repeatCount() {
        return 1;
    }

    public static int noteCount() {
        return FREQUENCIES_HZ.length;
    }

    public static int durationMillis() {
        return FREQUENCIES_HZ.length * 250;
    }

    public static int frequencyHz(int note) {
        return FREQUENCIES_HZ[note];
    }

    public static int toneMillis(int note) {
        return 250;
    }

    public static int stepMillis(int note) {
        return 250;
    }
}
