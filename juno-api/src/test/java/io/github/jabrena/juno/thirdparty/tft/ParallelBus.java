package io.github.jabrena.juno.thirdparty.tft;

import java.util.ArrayList;
import java.util.List;

/**
 * Test-only stand-in for the parallel display bus. Single bytes go to {@link #bytes}, and runs of repeated 16-bit pixels
 * go to {@link #runs} as {@code {color, count}} pairs, so a test can check what a driver sent without the megabytes a
 * full screen would produce.
 */
public final class ParallelBus {
    public static final int WIDTH = 8;

    /** Each byte written with {@link #write}, in order. */
    public static final List<Integer> bytes = new ArrayList<>();
    /** Each {@link #repeat16} call as {color, count}, in order. */
    public static final List<int[]> runs = new ArrayList<>();
    public static int[] dataPins;
    public static int strobePin = -1;

    private ParallelBus() {
    }

    public static void reset() {
        bytes.clear();
        runs.clear();
        dataPins = null;
        strobePin = -1;
    }

    public static void begin(int[] dataPins, int strobePin) {
        ParallelBus.dataPins = dataPins.clone();
        ParallelBus.strobePin = strobePin;
    }

    public static void write(int value) {
        bytes.add(value & 0xFF);
    }

    public static void repeat16(int value, int count) {
        runs.add(new int[] {value, count});
    }
}
