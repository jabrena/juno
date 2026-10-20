package io.github.jabrena.juno.hardware.led;

import io.github.jabrena.juno.hardware.internal.NativeLedMatrix;

/**
 * The UNO Q's 13x8 LED matrix for whole-program tests: a {@link LedMatrix} that {@code MCU.getLedMatrix()} returns
 * once {@link #install()} has run. It reports the UNO Q's width, the board the emulator models, and drops every frame.
 */
public final class EmulatedLedMatrix implements LedMatrix {
    private static final EmulatedLedMatrix INSTANCE = new EmulatedLedMatrix();
    private static final int UNO_Q_COLUMNS = 13;

    private EmulatedLedMatrix() {
    }

    /** Makes {@code MCU.getLedMatrix()} from now on hand out this emulated matrix instead of the board's. */
    public static void install() {
        NativeLedMatrix.setLedMatrix(INSTANCE);
    }

    @Override
    public void begin() {
    }

    @Override
    public int columns() {
        return UNO_Q_COLUMNS;
    }

    @Override
    public void loadFrame(int word0, int word1, int word2, int word3) {
    }

    @Override
    public void clear() {
    }
}
