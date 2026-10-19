package io.github.jabrena.juno.api.hardware.led;

/**
 * The board's built-in LED matrix: 12x8 on the UNO R4 WiFi and 13x8 on the UNO Q.
 * {@link io.github.jabrena.juno.api.hardware.MCU#getLedMatrix()} is the board's own matrix, which the compiler lowers
 * to the {@code Arduino_LED_Matrix} library; a unit test passes {@code mock(LedMatrix.class)} instead. {@link #columns()}
 * tells a program which matrix it was compiled for; frames are packed row by row, MSB first, into 32-bit words
 * ({@code columns() * 8} pixels: three words on the R4, four on the Q). Get the board's matrix once, at startup, and
 * pass it to the classes that need it.
 */
public interface LedMatrix {

    /** Starts the matrix. */
    void begin();

    /** The number of columns of the matrix on the board this program is compiled for: 12 or 13. */
    int columns();

    /**
     * Loads a frame packed MSB-first into four 32-bit words, exactly as the underlying
     * {@code loadFrame(const uint32_t[...])} of the board's {@code Arduino_LED_Matrix} library expects:
     * the UNO R4's 96 pixels use the first three words and ignore {@code word3}; the UNO Q's 104 pixels use
     * all four, the last holding the final 8 pixels in its top bits.
     */
    void loadFrame(int word0, int word1, int word2, int word3);

    /** Loads a frame of at most 96 pixels using {@link #loadFrame(int, int, int, int)} with its fourth word set to zero. */
    default void loadFrame(int word0, int word1, int word2) {
        loadFrame(word0, word1, word2, 0);
    }

    /** Turns every pixel off. */
    void clear();
}
