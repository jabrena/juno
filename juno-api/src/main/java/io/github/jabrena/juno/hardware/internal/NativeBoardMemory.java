package io.github.jabrena.juno.hardware.internal;

import io.github.jabrena.juno.hardware.BoardMemory;

/**
 * The board's {@link BoardMemory}: each method is an intrinsic the compiler lowers to its runtime shim, so it has no
 * Java body and cannot run, or be mocked, on a desktop JVM. Mock {@link BoardMemory} instead.
 */
public final class NativeBoardMemory implements BoardMemory {
    /** The board's own arena and flash figures: the only instance. */
    public static final NativeBoardMemory INSTANCE;

    /**
     * What {@link io.github.jabrena.juno.hardware.ArduinoOne#getBoardMemory()} returns: the board's figures, unless a
     * whole-program emulator in a test replaced them.
     */
    private static BoardMemory boardMemory;

    static {
        INSTANCE = new NativeBoardMemory();
        boardMemory = INSTANCE;
    }

    private NativeBoardMemory() {
    }

    /** The board memory {@link io.github.jabrena.juno.hardware.ArduinoOne#getBoardMemory()} hands out. */
    public static BoardMemory boardMemory() {
        return boardMemory;
    }

    /**
     * Makes {@link io.github.jabrena.juno.hardware.ArduinoOne#getBoardMemory()} hand out {@code replacement}: for a
     * whole-program emulator in a test, never on the board.
     */
    public static void setBoardMemory(BoardMemory replacement) {
        boardMemory = replacement;
    }

    @Override
    public native int arenaUsedBytes();

    @Override
    public native int arenaCapacityBytes();

    @Override
    public native int flashUsedBytes();

    @Override
    public native int flashCapacityBytes();
}
