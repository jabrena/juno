package io.github.jabrena.juno.hardware.internal;

import io.github.jabrena.juno.hardware.led.LedMatrix;

/**
 * The board's {@link LedMatrix}: each method is an LED matrix intrinsic the compiler lowers to the
 * {@code Arduino_LED_Matrix} library, so it has no Java body and cannot run, or be mocked, on a desktop JVM. Mock
 * {@link LedMatrix} instead.
 */
public final class NativeLedMatrix implements LedMatrix {
    /** The board's LED matrix: the only instance. */
    public static final NativeLedMatrix INSTANCE;

    /**
     * What {@link io.github.jabrena.juno.hardware.ArduinoUno#getLedMatrix()} returns: the board's matrix, unless a
     * whole-program emulator in a test replaced it.
     */
    private static LedMatrix ledMatrix;

    static {
        INSTANCE = new NativeLedMatrix();
        ledMatrix = INSTANCE;
    }

    private NativeLedMatrix() {
    }

    /** The matrix {@link io.github.jabrena.juno.hardware.ArduinoUno#getLedMatrix()} hands out. */
    public static LedMatrix ledMatrix() {
        return ledMatrix;
    }

    /**
     * Makes {@link io.github.jabrena.juno.hardware.ArduinoUno#getLedMatrix()} hand out {@code replacement}: for a
     * whole-program emulator or a unit test, never on the board.
     */
    public static void setLedMatrix(LedMatrix replacement) {
        ledMatrix = replacement;
    }

    @Override
    public native void begin();

    @Override
    public native int columns();

    @Override
    public native void loadFrame(int word0, int word1, int word2, int word3);

    /** Declared here, not inherited, because Juno dispatches an interface call only to a method its class declares. */
    @Override
    public void loadFrame(int word0, int word1, int word2) {
        loadFrame(word0, word1, word2, 0);
    }

    @Override
    public native void clear();
}
