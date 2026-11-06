package io.github.jabrena.juno.remote.lego.poweredup;

/**
 * The sensor ports a program enabled and the latest reports of each: the value of a single-value report, its raw bytes
 * for a multi-value one, and how many reports arrived since the port was enabled. Eight slots, allocated once.
 */
final class LegoSensors {
    static final int SLOTS = 8;
    static final int RAW_BYTES = 16;
    private static final int FREE = -1;

    private final int[] ports = new int[SLOTS];
    private final int[] values = new int[SLOTS];
    private final byte[][] raw = new byte[SLOTS][RAW_BYTES];
    private final int[] rawSize = new int[SLOTS];
    private final int[] reports = new int[SLOTS];

    LegoSensors() {
        clear();
    }

    void clear() {
        for (int i = 0; i < SLOTS; i++) {
            ports[i] = FREE;
            values[i] = 0;
            rawSize[i] = 0;
            reports[i] = 0;
        }
    }

    /** The slot for {@code port}: the one it already has, else a free one; -1 when every slot is taken. */
    int assign(int port) {
        int slot = slotOf(port);
        return slot >= 0 ? slot : slotOf(FREE);
    }

    /** Starts a fresh subscription of {@code port} in {@code slot}, which {@link #assign} chose. */
    void enable(int slot, int port) {
        ports[slot] = port;
        values[slot] = 0;
        rawSize[slot] = 0;
        reports[slot] = 0;
    }

    /** Stores one report of {@code port}: its {@code size} value bytes start at {@code offset} in {@code message}. */
    void report(int port, byte[] message, int offset, int size) {
        int slot = slotOf(port);
        if (slot < 0) {
            return;
        }
        int kept = size < RAW_BYTES ? size : RAW_BYTES;
        for (int i = 0; i < kept; i++) {
            raw[slot][i] = message[offset + i];
        }
        rawSize[slot] = kept;
        reports[slot] = reports[slot] + 1;
        values[slot] = size >= 1 ? LegoBytes.valueAt(message, offset, size == 2 || size == 4 ? size : 1) : 0;
    }

    /** The latest single value of {@code port}; 0 when it is not enabled. */
    int value(int port) {
        int slot = slotOf(port);
        return slot < 0 ? 0 : values[slot];
    }

    int reportCount(int port) {
        int slot = slotOf(port);
        return slot < 0 ? 0 : reports[slot];
    }

    int reportSize(int port) {
        int slot = slotOf(port);
        return slot < 0 ? 0 : rawSize[slot];
    }

    /** The index-th value of the latest report, {@code width} bytes wide; 0 when the report does not have it. */
    int readValue(int port, int index, int width) {
        int slot = slotOf(port);
        if (slot < 0 || index < 0 || (width != 1 && width != 2 && width != 4)
                || rawSize[slot] < (index + 1) * width) {
            return 0;
        }
        return LegoBytes.valueAt(raw[slot], index * width, width);
    }

    private int slotOf(int port) {
        for (int i = 0; i < SLOTS; i++) {
            if (ports[i] == port) {
                return i;
            }
        }
        return -1;
    }
}
