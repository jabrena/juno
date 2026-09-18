package io.github.jabrena.juno.api.led;

/** UNO R4 WiFi 12x8 built-in LED matrix operations recognized as compiler intrinsics by Juno. */
public final class LedMatrix {
    private LedMatrix() {
    }

    public static native void begin();

    /**
     * Loads a 96-pixel frame packed MSB-first into three 32-bit words, exactly as the underlying
     * {@code Arduino_LED_Matrix::loadFrame(const uint32_t[3])} expects.
     */
    public static native void loadFrame(int word0, int word1, int word2);

    public static native void clear();
}
