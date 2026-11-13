package io.github.jabrena.juno.hardware.internal;

import io.github.jabrena.juno.hardware.spi.Spi;

/**
 * The board's {@link Spi} bus: each method is an SPI intrinsic the compiler lowers to the core-bundled {@code SPI}
 * library, so it has no Java body and cannot run, or be mocked, on a desktop JVM. Mock {@link Spi} instead.
 */
public final class NativeSpi implements Spi {
    /** The board's primary SPI bus: the only instance. */
    public static final NativeSpi INSTANCE;

    /**
     * What {@link io.github.jabrena.juno.hardware.MCU#getSpi()} returns: the board's bus, unless a whole-program
     * emulator in a test replaced it.
     */
    private static Spi spi;

    static {
        INSTANCE = new NativeSpi();
        spi = INSTANCE;
    }

    private NativeSpi() {
    }

    /** The bus {@link io.github.jabrena.juno.hardware.MCU#getSpi()} hands out. */
    public static Spi spi() {
        return spi;
    }

    /**
     * Makes {@link io.github.jabrena.juno.hardware.MCU#getSpi()} hand out {@code replacement}: for a
     * whole-program emulator in a test, never on the board.
     */
    public static void setSpi(Spi replacement) {
        spi = replacement;
    }

    @Override
    public native void begin();

    @Override
    public native int transfer(int value);
}
