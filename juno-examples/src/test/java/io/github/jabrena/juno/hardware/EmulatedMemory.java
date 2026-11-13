package io.github.jabrena.juno.hardware;

import io.github.jabrena.juno.hardware.internal.NativeBoardMemory;

/**
 * Juno's arena and the program's flash for whole-program tests: a {@link BoardMemory} that
 * {@code MCU.getBoardMemory()} returns once {@link #install()} has run. The JVM has no Juno arena, so it reports an
 * empty one of the default size, or of {@code -Djuno.emulator.arenaBytes=<bytes>} to play a program built with a
 * bigger {@code juno.Xmx}; the flash is the UNO Q's empty sketch partition, the board the emulator models.
 */
public final class EmulatedMemory implements BoardMemory {
    private static final EmulatedMemory INSTANCE = new EmulatedMemory();
    private static final int DEFAULT_ARENA_BYTES = 8192;
    private static final int UNO_Q_SKETCH_PARTITION_BYTES = 786432;

    private EmulatedMemory() {
    }

    /** Makes {@code MCU.getBoardMemory()} from now on report this emulated arena instead of asking the board. */
    public static void install() {
        NativeBoardMemory.setBoardMemory(INSTANCE);
    }

    @Override
    public int arenaUsedBytes() {
        return 0;
    }

    @Override
    public int arenaCapacityBytes() {
        return Integer.getInteger("juno.emulator.arenaBytes", DEFAULT_ARENA_BYTES);
    }

    @Override
    public int flashUsedBytes() {
        return 0;
    }

    @Override
    public int flashCapacityBytes() {
        return UNO_Q_SKETCH_PARTITION_BYTES;
    }
}
