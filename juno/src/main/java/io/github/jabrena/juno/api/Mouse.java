package io.github.jabrena.juno.api;

/**
 * USB HID mouse control recognized as compiler intrinsics by Juno, backed by the Arduino
 * {@code Mouse} library. Requires a board with native USB (UNO R4 WiFi/Minima) and the
 * {@code Mouse} library installed ({@code arduino-cli lib install Mouse}).
 */
public final class Mouse {
    private Mouse() {
    }

    public static native void begin();

    /**
     * Moves the host cursor by ({@code x}, {@code y}) pixels, relative to its current position.
     * Each value is narrowed to the underlying {@code signed char} range (-128..127, low 8 bits,
     * sign-extended) to match {@code Mouse_::move(signed char, signed char, signed char)}; keep
     * both arguments within that range to avoid wraparound.
     */
    public static native void move(int x, int y);
}
