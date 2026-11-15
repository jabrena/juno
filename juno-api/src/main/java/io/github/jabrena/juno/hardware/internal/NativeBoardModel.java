package io.github.jabrena.juno.hardware.internal;

import io.github.jabrena.juno.hardware.BoardModel;

/**
 * The board's own answer to {@link io.github.jabrena.juno.hardware.MCU#getBoardModel()}. On the board the call is an
 * intrinsic the compiler folds to the target's constant, so this body never runs there; on a desktop JVM it hands out
 * whatever {@link #setBoardModel} last set, which is how a whole-program emulator in a test says which board it is
 * pretending to be.
 */
public final class NativeBoardModel {

    /** What {@link io.github.jabrena.juno.hardware.MCU#getBoardModel()} returns off the board. */
    private static BoardModel boardModel = BoardModel.UNO_R4_WIFI;

    private NativeBoardModel() {
    }

    /** The board model {@link io.github.jabrena.juno.hardware.MCU#getBoardModel()} hands out. */
    public static BoardModel boardModel() {
        return boardModel;
    }

    /**
     * Makes {@link io.github.jabrena.juno.hardware.MCU#getBoardModel()} hand out {@code replacement}: for a
     * whole-program emulator in a test, never on the board, where the call is folded to a constant instead.
     */
    public static void setBoardModel(BoardModel replacement) {
        boardModel = replacement;
    }

    /** The selected board's onboard SD chip-select pin; only called after capability validation. */
    public static native int onboardSdChipSelect();
}
