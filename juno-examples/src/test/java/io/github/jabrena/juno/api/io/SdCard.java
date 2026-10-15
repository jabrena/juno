package io.github.jabrena.juno.api.io;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Test double for the SD card, shadowing the {@code juno} artifact's native {@code SdCard} on the test classpath
 * (test classes come first). No card is inserted unless a test sets {@link #present}, so the games that look for
 * files on the card at startup take their no-card path, deterministically. Tests that need persistence can populate
 * the in-memory card through the same public API used on the board.
 */
public final class SdCard {
    private static final String INITIAL_MINER2_ID = System.getProperty("juno.emulator.miner2Identity");
    private static final Map<String, String> FILES = new HashMap<>();

    /**
     * Whether a card answers {@link #begin()}. Starts true for an explicitly enabled card or a seeded Miner2
     * identity; all files live in this test double's in-memory map.
     */
    public static boolean present = Boolean.getBoolean("juno.emulator.sdCard") || INITIAL_MINER2_ID != null;

    static {
        if (INITIAL_MINER2_ID != null) {
            FILES.put("MINER2.ID", INITIAL_MINER2_ID + "\n");
        }
    }

    private SdCard() {
    }

    public static boolean begin() {
        return begin(10);
    }

    public static boolean begin(int chipSelectPin) {
        return present;
    }

    public static boolean exists(String path) {
        return present && FILES.containsKey(path);
    }

    public static int size(String path) {
        return exists(path) ? FILES.get(path).getBytes(StandardCharsets.UTF_8).length : -1;
    }

    public static InputStream open(String path) {
        return exists(path)
                ? new ByteArrayInputStream(FILES.get(path).getBytes(StandardCharsets.UTF_8))
                : null;
    }

    public static boolean appendLine(String path, String line) {
        return appendText(path, line + "\n");
    }

    public static boolean appendText(String path, String text) {
        if (!present) {
            return false;
        }
        FILES.merge(path, text, String::concat);
        return true;
    }

    public static boolean remove(String path) {
        return present && FILES.remove(path) != null;
    }

    /** Restores the deterministic no-card state between tests. */
    public static void reset() {
        present = false;
        FILES.clear();
    }
}
