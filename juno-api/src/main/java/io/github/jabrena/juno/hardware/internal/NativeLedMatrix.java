package io.github.jabrena.juno.hardware.internal;

import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.led.LedMatrix;
import io.github.jabrena.juno.hardware.led.LedMatrixDisplay;
import org.jspecify.annotations.Nullable;

/**
 * The board's {@link LedMatrix}: each method is an LED matrix intrinsic the compiler lowers to the
 * {@code Arduino_LED_Matrix} library, so it has no Java body and cannot run, or be mocked, on a desktop JVM. Mock
 * {@link LedMatrix} instead.
 */
public final class NativeLedMatrix implements LedMatrix {
    /** The board's LED matrix: the only instance. */
    public static final NativeLedMatrix INSTANCE;

    /**
     * The board's matrix, which {@link io.github.jabrena.juno.hardware.led.LedCanvas} and {@link #display()} drive, unless a
     * whole-program emulator in a test replaced it.
     */
    private static LedMatrix ledMatrix;

    /** The LED matrix as a {@link Display}, created on first use over {@link #ledMatrix()}; null until then. */
    private static @Nullable LedMatrixDisplay display;

    static {
        INSTANCE = new NativeLedMatrix();
        ledMatrix = INSTANCE;
    }

    private NativeLedMatrix() {
    }

    /** The board's matrix, or the replacement a test installed. */
    public static LedMatrix ledMatrix() {
        return ledMatrix;
    }

    /**
     * Makes {@link #ledMatrix()} return {@code replacement}: for a
     * whole-program emulator or a unit test, never on the board.
     */
    public static void setLedMatrix(LedMatrix replacement) {
        ledMatrix = replacement;
        display = null;
    }

    /**
     * The board's LED matrix as a {@link Display}: what {@link io.github.jabrena.juno.hardware.MCU#getDisplay()} hands
     * out on the UNO R4 WiFi and the UNO Q. Created once, on first use, so the same display keeps its cursor and
     * buffer for the whole program.
     */
    public static Display display() {
        LedMatrixDisplay current = display;
        if (current == null) {
            current = new LedMatrixDisplay(ledMatrix);
            display = current;
        }
        return current;
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
