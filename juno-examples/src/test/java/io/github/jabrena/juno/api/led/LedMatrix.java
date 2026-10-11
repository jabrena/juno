package io.github.jabrena.juno.api.led;

/** Test double for the board-specific LED-matrix intrinsic; screenshot tests emulate an UNO Q. */
public final class LedMatrix {
    private LedMatrix() {
    }

    public static void begin() {
    }

    public static int columns() {
        return 13;
    }

    public static void loadFrame(int word0, int word1, int word2, int word3) {
    }

    public static void loadFrame(int word0, int word1, int word2) {
    }

    public static void clear() {
    }
}
