package io.github.jabrena.juno.games.doom2.services;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Finds which of Doom1's episodes DOOM1.WAD holds from its directory markers. The full WAD's directory has thousands
 * of entries, so it is read a block at a time, one block per {@link #step()}, keeping the frame loop responsive.
 */
final class DoomWadCatalog {
    private static final int HEADER_BYTES = 12;
    private static final int ENTRY_BYTES = 16;
    private static final int ENTRIES_PER_STEP = 32;

    private final byte[] block = new byte[ENTRIES_PER_STEP * ENTRY_BYTES];
    private int lumps = -1;
    private int directory;
    private int index;
    private int found;
    private boolean complete;

    /** Whether the whole directory has been read, or the file was found missing or invalid. */
    boolean complete() {
        return complete;
    }

    /** A bit for each E?M1 marker found, or zero when the file is missing or invalid. */
    int episodes() {
        return found;
    }

    /** Reads the next block of the directory; any failure completes the scan with no episodes. */
    void step() {
        if (complete) {
            return;
        }
        try (RandomAccessFile wad = new RandomAccessFile("DOOM1.WAD", "r")) {
            step(wad);
        } catch (IOException missingOrBroken) {
            found = 0;
            complete = true;
        }
    }

    void step(RandomAccessFile wad) throws IOException {
        if (lumps < 0 && !readHeader(wad)) {
            found = 0;
            complete = true;
            return;
        }
        int count = Math.min(ENTRIES_PER_STEP, lumps - index);
        if (count > 0) {
            wad.seek((long) directory + (long) index * ENTRY_BYTES);
            wad.readFully(block, 0, count * ENTRY_BYTES);
            for (int entry = 0; entry < count; entry++) {
                mark(entry * ENTRY_BYTES);
            }
            index = index + count;
        }
        complete = index >= lumps;
    }

    /** Scans a whole open WAD at once: what the JVM tests check. */
    static int episodes(RandomAccessFile wad) throws IOException {
        DoomWadCatalog catalog = new DoomWadCatalog();
        while (!catalog.complete()) {
            catalog.step(wad);
        }
        return catalog.episodes();
    }

    private boolean readHeader(RandomAccessFile wad) throws IOException {
        wad.seek(0);
        wad.readFully(block, 0, HEADER_BYTES);
        if (block[1] != 'W' || block[2] != 'A' || block[3] != 'D') {
            return false;
        }
        lumps = int32(block, 4);
        directory = int32(block, 8);
        return lumps >= 0 && directory >= HEADER_BYTES;
    }

    private void mark(int offset) {
        int episode = block[offset + 9] - '0';
        if (block[offset + 8] == 'E' && episode >= 1 && episode <= 4 && block[offset + 10] == 'M'
                && block[offset + 11] == '1' && block[offset + 12] == 0) {
            found = found | 1 << episode;
        }
    }

    private static int int32(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) | (bytes[offset + 1] & 0xFF) << 8 | (bytes[offset + 2] & 0xFF) << 16
                | (bytes[offset + 3] & 0xFF) << 24;
    }
}
