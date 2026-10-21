package io.github.jabrena.juno.hardware;

import io.github.jabrena.juno.hardware.internal.NativeBoardModel;

/**
 * The board the screenshot emulator models, for whole-program tests: a {@link BoardModel} that
 * {@code Arduino.getBoardModel()} returns once {@link #install()} has run. The emulator models an UNO Q, the board with
 * room for every TFT game.
 */
public final class EmulatedUnoQ implements BoardModel {
    private static final EmulatedUnoQ INSTANCE = new EmulatedUnoQ();

    private EmulatedUnoQ() {
    }

    /** Makes {@code Arduino.getBoardModel()} from now on answer as an UNO Q instead of asking the board. */
    public static void install() {
        NativeBoardModel.setBoardModel(INSTANCE);
    }

    @Override
    public boolean isQ() {
        return true;
    }

    @Override
    public boolean isR4() {
        return false;
    }

    @Override
    public boolean isR3() {
        return false;
    }
}
