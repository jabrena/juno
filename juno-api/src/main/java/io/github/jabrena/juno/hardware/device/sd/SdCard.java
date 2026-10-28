package io.github.jabrena.juno.hardware.device.sd;

import java.io.InputStream;

/**
 * Access to an SPI SD card, backed by the Arduino {@code SdFat} library with long-file-name support
 * enabled.
 *
 * <p>Take the card from the shield or carrier that holds its socket, once, at startup:
 * {@link DataLoggerShield#getSdCard()}, {@link io.github.jabrena.juno.hardware.device.tft.TftTouchShield#getSdCard()},
 * {@link io.github.jabrena.juno.hardware.device.lcd.LcdKeypadShield#getSdCard()},
 * {@link io.github.jabrena.juno.hardware.device.wifi.ArduinoWifiShield#getSdCard()} or
 * {@link io.github.jabrena.juno.hardware.device.carrier.NanoConnectorCarrier#getSdCard()}, or from
 * {@link io.github.jabrena.juno.hardware.M5StackCardputer#getSd()} for the Cardputer's built-in socket. A unit test passes
 * {@code mock(SdCard.class)} instead.
 *
 * <p>Call {@link #begin()} for the chip-select pin of the socket the card came from, or {@link #begin(int)} for
 * custom wiring, before opening or writing files. A root-level {@code path} uses the SD card's root as its base and
 * must be a compile-time string literal. The folder-aware overloads accept runtime directory and file-stem strings;
 * they are intended for bounded dynamic layouts such as {@code /mailbox/YYYYMMDD/<uuid>.txt}. {@link #open(String)}
 * is read-only; the writers, {@link #appendLine(String, String)} (adds a line) and
 * {@link #appendText(String, String)} (adds raw text), each open the file, write, and close it again rather than
 * keeping a writable handle open. To replace a file, {@link #remove(String)} it first.
 *
 * <p>Limits:
 * <ul>
   *   <li>Root-level paths are compile-time string literals, so their length is not limited by the runtime. Folder-aware
   *       directory and file-stem strings each fit in a runtime string slot; the native SD layer combines them.</li>
 *   <li>A literal {@code line} passed to {@link #appendLine(String, String)} has no length limit either, but a
 *       {@code line} built while the program runs ({@code String.valueOf}, concatenation,
 *       {@code StringBuilder.toString()}) lives in a runtime-string slot of
 *       {@code io.github.jabrena.juno.RuntimeLimits#STRING_SLOT_CAPACITY_BYTES} bytes including the NUL
 *       terminator: 31 characters at most, and a longer value panics rather than truncating. Split longer
 *       records over several calls; each call ends its line with a newline.</li>
 * </ul>
 *
 * @see <a href="https://www.az-delivery.de/en/products/datenlogger-modul">AZ-Delivery data logger module</a>
 * @see <a href="https://fr.elegoo.com/products/elegoo-uno-r3-2-8-inch-touch-screen-tft-touch-screen-320x240-with-sd-card-socket-with-technical-data">ELEGOO 2.8 inch TFT touch screen with SD card socket</a>
 */
public interface SdCard {

    /**
     * Initializes the card on the chip-select pin of the socket it came from (D10 on the data logger and TFT shields,
     * D4 on the Arduino WiFi Shield and the Nano Connector Carrier, or GPIO12 on the Cardputer) and returns whether it
     * was detected successfully.
     */
    boolean begin();

    /** Initializes the card on {@code chipSelectPin} and returns whether it was detected successfully. */
    boolean begin(int chipSelectPin);

    /** Returns whether {@code path} exists on the card. */
    boolean exists(String path);

    /** Creates {@code path} and its missing parent directories. Returns whether the operation succeeded. */
    boolean createDirectory(String path);

    /**
     * Returns the size of {@code path} in bytes, or {@code -1} when it does not exist or cannot be opened. A
     * file over {@link Integer#MAX_VALUE} bytes reports {@code Integer.MAX_VALUE}. Use it to tell whether a log
     * has grown too large or a file is empty; {@link java.io.InputStream#available()} only says whether bytes
     * remain.
     */
    int size(String path);

    /** Opens {@code path} for reading, or returns {@code null} when it cannot be opened. */
    InputStream open(String path);

    /** Opens {@code directory}/{@code fileStem}.txt for reading, or returns {@code null} when it cannot be opened. */
    InputStream open(String directory, String fileStem);

    /**
     * Copies the name of the direct child at {@code index} in {@code directory} into {@code name}. Returns the number
     * of bytes copied, zero when there are no more children, or a negative value when the directory cannot be read or
     * the buffer is too small.
     */
    int list(String directory, int index, byte[] name, int capacity);

    /**
     * Appends {@code line} plus a newline to {@code path}, creating the file first if it does not
     * exist yet. Unlike {@code open()}, {@code line} may be a runtime string. Returns whether the
     * write succeeded.
     */
    boolean appendLine(String path, String line);

    /**
     * Appends {@code text} to {@code path} exactly as given, creating the file first if it does not exist
     * yet: unlike {@link #appendLine(String, String)} no newline is added, so a record can be written in pieces
     * (each piece within the runtime-string limit above) or be raw text such as CSV fields. Returns
     * whether the write succeeded.
     */
    boolean appendText(String path, String text);

    /** Appends {@code text} to {@code directory}/{@code fileStem}.txt, creating the file when needed. */
    boolean appendText(String directory, String fileStem, String text);

    /** Deletes {@code path} from the card. Returns whether it was removed. */
    boolean remove(String path);

    /** Deletes the empty directory at {@code path}. Returns false when it is missing, non-empty, or cannot be removed. */
    boolean removeDirectory(String path);

    /** Deletes {@code directory}/{@code fileStem}.txt from the card. Returns whether it was removed. */
    boolean remove(String directory, String fileStem);

    /**
     * The card volume's total capacity in KiB, or {@code -1} before a successful {@link #begin()}. Reads only the
     * volume header, so it returns at once.
     */
    int totalKiB();

    /**
     * The card volume's free space in KiB, or {@code -1} before a successful {@link #begin()}. A FAT32 card's own
     * free-space count (its FSInfo sector, kept by the computer that last wrote the card) answers at once, though it
     * may lag a few clusters behind writes the board made since. Without one, the whole allocation table is counted,
     * which can take a minute on a large card; the count pauses between slices so other threads keep running, but
     * the calling thread waits for all of it, so call this from a background thread and keep the result.
     */
    int freeKiB();
}
