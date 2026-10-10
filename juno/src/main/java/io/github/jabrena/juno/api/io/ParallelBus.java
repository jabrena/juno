package io.github.jabrena.juno.api.io;

/**
 * An 8-bit parallel output bus, as display shields wire it: eight data pins written together, latched by a write
 * strobe pulsed low then high. Recognized as compiler intrinsics by Juno: the runtime resolves each pin to its GPIO
 * port once in {@link #begin}, so a byte costs a few port register writes instead of one {@link Gpio#digitalWrite}
 * per changed pin and two per strobe.
 *
 * <p>There is one bus. Its pins must already be outputs ({@link Gpio#pinMode}); the bus drives every data pin on
 * each write, so their levels may be changed in between (to share them with a touch panel, say).
 */
public final class ParallelBus {
    /** How many data pins the bus has. */
    public static final int WIDTH = 8;

    private ParallelBus() {
    }

    /** Uses {@code dataPins} ({@link #WIDTH} of them, bit 0 first) and {@code strobePin}, which is left high. */
    public static native void begin(int[] dataPins, int strobePin);

    /** Drives the low byte of {@code value} onto the data pins and pulses the strobe. */
    public static native void write(int value);

    /** Writes the 16-bit {@code value} high byte first, {@code count} times over: a run of one RGB565 color. */
    public static native void repeat16(int value, int count);
}
