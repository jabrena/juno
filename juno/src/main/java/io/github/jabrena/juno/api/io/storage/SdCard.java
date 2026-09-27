package io.github.jabrena.juno.api.io.storage;

import java.io.InputStream;

/**
 * Read-only access to an SPI SD card, backed by the Arduino {@code SdFat} library with long-file-name
 * support enabled.
 *
 * <p>Call {@link #begin()} for the standard D10 chip-select pin, or {@link #begin(int)} for custom
 * wiring, before opening files. Paths must currently be compile-time string literals and use the
 * SD card's root as their base.
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
}
