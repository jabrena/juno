package io.github.jabrena.juno.hardware.led;

import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.internal.NativeLedMatrix;

/**
 * The UNO Q's 13x8 LED matrix for whole-program tests: a {@link Display} that {@code MCU.getDisplay()} returns on the
 * UNO Q once {@link #install()} has run. It reports the UNO Q's size, the board the emulator models, and drops every
 * drawing.
 */
public final class EmulatedLedMatrix implements Display {
    private static final EmulatedLedMatrix INSTANCE = new EmulatedLedMatrix();
    private static final int UNO_Q_COLUMNS = 13;
    private static final int HEIGHT = 8;

    private EmulatedLedMatrix() {
    }

    /** Makes {@code MCU.getDisplay()} from now on return this emulated matrix instead of the board's. */
    public static void install() {
        NativeLedMatrix.setDisplay(INSTANCE);
    }

    @Override
    public int width() {
        return UNO_Q_COLUMNS;
    }

    @Override
    public int height() {
        return HEIGHT;
    }

    @Override
    public void fillScreen(int color) {
    }

    @Override
    public void drawPixel(int x, int y, int color) {
    }

    @Override
    public void fillRect(int x, int y, int width, int height, int color) {
    }

    @Override
    public void setCursor(int x, int y) {
    }

    @Override
    public void setTextColor(int foreground, int background) {
    }

    @Override
    public void setTextSize(int size) {
    }

    @Override
    public void print(char character) {
    }

    @Override
    public void print(String text) {
    }

    @Override
    public void println(String text) {
    }
}
