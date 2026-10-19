package io.github.jabrena.juno.api.hardware.hid;

/**
 * USB HID mouse control. {@link io.github.jabrena.juno.api.hardware.MCU#getMouse()} is the board's own mouse, which
 * the compiler lowers to the Arduino {@code Mouse} library; a unit test passes {@code mock(Mouse.class)} instead.
 * Requires a board with native USB (UNO R4 WiFi) and the {@code Mouse} library installed
 * ({@code arduino-cli lib install Mouse}). Get the board's mouse once, at startup, and pass it to the classes that
 * need it.
 */
public interface Mouse {

    /** Starts the board acting as a USB mouse. */
    void begin();

    /**
     * Moves the host cursor by ({@code x}, {@code y}) pixels, relative to its current position.
     * Each value is narrowed to the underlying {@code signed char} range (-128..127, low 8 bits,
     * sign-extended) to match {@code Mouse_::move(signed char, signed char, signed char)}; keep
     * both arguments within that range to avoid wraparound.
     */
    void move(int x, int y);
}
