package io.github.jabrena.juno.api.io.storage;

import java.io.InputStream;

/**
 * Access to an SPI SD card, backed by the Arduino {@code SdFat} library with long-file-name support
 * enabled.
 *
 * <p>Call {@link #begin()} for the standard D10 chip-select pin, or {@link #begin(int)} for custom
 * wiring, before opening or appending to files. Every {@code path} must currently be a compile-time
 * string literal and use the SD card's root as its base. {@link #open(String)} is read-only;
 * {@link #append(String, String)} is the only way to write, and always opens, writes one line, and
 * closes the file again rather than keeping a writable handle open.
 */
public final class SdCard {
    private static final int DEFAULT_CHIP_SELECT_PIN = 10;

    private SdCard() {
    }

    /** Initializes the card using the standard Arduino shield chip-select pin, D10. */
    public static boolean begin() {
        return begin(DEFAULT_CHIP_SELECT_PIN);
    }

    /** Initializes the card and returns whether it was detected successfully. */
    public static native boolean begin(int chipSelectPin);

    /** Returns whether {@code path} exists on the card. */
    public static native boolean exists(String path);

    /** Opens {@code path} for reading, or returns {@code null} when it cannot be opened. */
    public static native InputStream open(String path);

    /**
     * Appends {@code line} plus a newline to {@code path}, creating the file first if it does not
     * exist yet. Unlike {@code open()}, {@code line} may be a runtime string. Returns whether the
     * write succeeded.
     */
    public static native boolean append(String path, String line);

    /** Deletes {@code path} from the card. Returns whether it was removed. */
    public static native boolean remove(String path);
}
