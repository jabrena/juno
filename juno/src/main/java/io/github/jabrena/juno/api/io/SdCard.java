package io.github.jabrena.juno.api.io;

import java.io.InputStream;

/**
 * Access to an SPI SD card, backed by the Arduino {@code SdFat} library with long-file-name support
 * enabled.
 *
 * <p>Call {@link #begin()} for the standard D10 chip-select pin, or {@link #begin(int)} for custom
 * wiring, before opening or writing files. Every {@code path} must currently be a compile-time
 * string literal and use the SD card's root as its base. {@link #open(String)} is read-only; the three writers,
 * {@link #appendLine(String, String)} (adds a line) and {@link #appendText(String, String)} (adds raw text),
 * each open the file, write, and close it again rather than keeping a writable handle open. To replace a file,
 * {@link #remove(String)} it first.
 *
 * <p>Limits:
 * <ul>
 *   <li>Paths are compile-time string literals, so their length is not limited by the runtime.</li>
 *   <li>A literal {@code line} passed to {@link #appendLine(String, String)} has no length limit either, but a
 *       {@code line} built while the program runs ({@code String.valueOf}, concatenation,
 *       {@code StringBuilder.toString()}) lives in a runtime-string slot of
 *       {@link io.github.jabrena.juno.RuntimeLimits#STRING_SLOT_CAPACITY_BYTES} bytes including the NUL
 *       terminator: 31 characters at most, and a longer value panics rather than truncating. Split longer
 *       records over several calls; each call ends its line with a newline.</li>
 * </ul>
 *
 * <p>Hardware: any SPI SD socket wired to the board's SPI pins and a chip-select pin works, for example the
 * SD socket of the ELEGOO 2.8" TFT touch shield (see {@link io.github.jabrena.juno.api.tft.TftTouchShield}) or
 * a data logger module such as the AZ-Delivery one, which pairs the card with a real-time clock. Pass its
 * chip-select pin to {@link #begin(int)} when it is not D10.
 *
 * @see <a href="https://www.az-delivery.de/en/products/datenlogger-modul">AZ-Delivery data logger module</a>
 * @see <a href="https://fr.elegoo.com/products/elegoo-uno-r3-2-8-inch-touch-screen-tft-touch-screen-320x240-with-sd-card-socket-with-technical-data">ELEGOO 2.8 inch TFT touch screen with SD card socket</a>
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

    /**
     * Returns the size of {@code path} in bytes, or {@code -1} when it does not exist or cannot be opened. A
     * file over {@link Integer#MAX_VALUE} bytes reports {@code Integer.MAX_VALUE}. Use it to tell whether a log
     * has grown too large or a file is empty; {@link java.io.InputStream#available()} only says whether bytes
     * remain.
     */
    public static native int size(String path);

    /** Opens {@code path} for reading, or returns {@code null} when it cannot be opened. */
    public static native InputStream open(String path);

    /**
     * Appends {@code line} plus a newline to {@code path}, creating the file first if it does not
     * exist yet. Unlike {@code open()}, {@code line} may be a runtime string. Returns whether the
     * write succeeded.
     */
    public static native boolean appendLine(String path, String line);

    /**
     * Appends {@code text} to {@code path} exactly as given, creating the file first if it does not exist
     * yet: unlike {@link #appendLine(String, String)} no newline is added, so a record can be written in pieces
     * (each piece within the runtime-string limit above) or be raw text such as CSV fields. Returns
     * whether the write succeeded.
     */
    public static native boolean appendText(String path, String text);

    /** Deletes {@code path} from the card. Returns whether it was removed. */
    public static native boolean remove(String path);
}
