package io.github.jabrena.juno.hardware.internal;

import io.github.jabrena.juno.hardware.BoardModel;

/**
 * The board's {@link BoardModel}: each method is an intrinsic the compiler folds to a constant for the target board,
 * so it has no Java body and cannot run, or be mocked, on a desktop JVM. Mock {@link BoardModel} instead.
 */
public final class NativeBoardModel implements BoardModel {
    /** The board's own answer: the only instance. */
    public static final NativeBoardModel INSTANCE;

    /**
     * What {@link io.github.jabrena.juno.hardware.Arduino#getBoardModel()} returns: the board's answer, unless a
     * whole-program emulator in a test replaced it.
     */
    private static BoardModel boardModel;

    static {
        INSTANCE = new NativeBoardModel();
        boardModel = INSTANCE;
    }

    private NativeBoardModel() {
    }

    /** The board model {@link io.github.jabrena.juno.hardware.Arduino#getBoardModel()} hands out. */
    public static BoardModel boardModel() {
        return boardModel;
    }

    /**
     * Makes {@link io.github.jabrena.juno.hardware.Arduino#getBoardModel()} hand out {@code replacement}: for a
     * whole-program emulator in a test, never on the board.
     */
    public static void setBoardModel(BoardModel replacement) {
        boardModel = replacement;
    }

    @Override
    public native boolean isQ();

    @Override
    public native boolean isR4();

    @Override
    public native boolean isR3();
}
