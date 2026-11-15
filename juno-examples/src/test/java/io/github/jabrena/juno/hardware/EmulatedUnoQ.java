package io.github.jabrena.juno.hardware;

import io.github.jabrena.juno.hardware.internal.NativeBoardModel;

/**
 * The board the screenshot emulator models, for whole-program tests: the model {@code MCU.getBoardModel()} answers
 * with once {@link #install()} has run. The emulator models an UNO Q, the board with room for every TFT game.
 */
public final class EmulatedUnoQ {

    private EmulatedUnoQ() {
    }

    /** Makes {@code MCU.getBoardModel()} from now on answer as an UNO Q instead of asking the board. */
    public static void install() {
        NativeBoardModel.setBoardModel(BoardModel.UNO_Q);
    }
}
