package io.github.jabrena.juno.api.io;

import java.io.InputStream;

/**
 * Test double for the SD card, shadowing the {@code juno} artifact's native {@code SdCard} on the test classpath
 * (test classes come first). No card is inserted unless a test sets {@link #present}, so the games that look for
 * files on the card at startup take their no-card path, deterministically.
 */
public final class SdCard {
    /**
     * Whether a card answers {@link #begin()}; every file operation still finds nothing on it. Starts true with
     * {@code -Djuno.emulator.sdCard=true}, so {@code RandomAccessFile} reads files from the working directory.
     */
    public static boolean present = Boolean.getBoolean("juno.emulator.sdCard");

    private SdCard() {
    }

    public static boolean begin() {
        return begin(10);
    }

    public static boolean begin(int chipSelectPin) {
        return present;
    }

    public static boolean exists(String path) {
        return false;
    }

    public static int size(String path) {
        return -1;
    }

    public static InputStream open(String path) {
        return null;
    }

    public static boolean appendLine(String path, String line) {
        return false;
    }

    public static boolean appendText(String path, String text) {
        return false;
    }

    public static boolean remove(String path) {
        return false;
    }
}
