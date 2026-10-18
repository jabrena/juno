package io.github.jabrena.juno.api.led;

/**
 * Test-only stand-in for the board's LED matrix intrinsic. It shadows the production class of the same name on the
 * test classpath, so {@link LedCanvas} runs on the JVM: it reports the matrix width a test sets and records the last
 * frame the program loaded instead of driving hardware.
 */
public final class LedMatrix {
    static int columns = 12;
    static int[] lastFrame = new int[4];
    static int loads;

    private LedMatrix() {
    }

    public static void begin() {
    }

    public static int columns() {
        return columns;
    }

    public static void loadFrame(int word0, int word1, int word2, int word3) {
        lastFrame = new int[] {word0, word1, word2, word3};
        loads++;
    }

    public static void loadFrame(int word0, int word1, int word2) {
        loadFrame(word0, word1, word2, 0);
    }

    public static void clear() {
    }
}
