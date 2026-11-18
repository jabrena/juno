package io.github.jabrena.juno.hardware;

import io.github.jabrena.juno.hardware.internal.NativeBoardModel;

/**
 * The board the screenshot emulator models, for whole-program tests: the model {@code MCU.getBoardModel()} answers
 * with once {@link #install()} has run. The emulator models an UNO R4 WiFi, the board the TFT games run on.
 */
public final class EmulatedUnoR4Wifi {

    private EmulatedUnoR4Wifi() {
    }

    /** Makes {@code MCU.getBoardModel()} from now on answer as an UNO R4 WiFi instead of asking the board. */
    public static void install() {
        NativeBoardModel.setBoardModel(BoardModel.UNO_R4_WIFI);
    }
}
