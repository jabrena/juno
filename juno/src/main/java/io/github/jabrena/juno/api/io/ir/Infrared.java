package io.github.jabrena.juno.api.io.ir;

/**
 * Serial bytes over modulated infrared light, recognized as compiler intrinsics by Juno and
 * bit-banged on two plain pins, with no Arduino library. It is the transport under protocols such
 * as {@link io.github.jabrena.juno.api.lego.RcxRemote}.
 *
 * <p>Wiring: an IR receiver module with a 38 kHz demodulator (e.g. TSOP38238 or VS1838B), whose
 * output idles high and goes low during a burst, on the receive pin; an IR LED with a series
 * resistor on the transmit pin, driven with a software 38 kHz carrier. A burst is a {@code 0}
 * bit, so the levels match a UART's. Either pin may be {@code -1} to use only the other direction.
 *
 * <p>Each byte is framed as a start bit, eight data bits least significant first, an odd parity
 * bit and a stop bit. Both directions are blocking and busy-wait on the bit timing, so call them
 * from the main loop, not while {@code Delay.millis} is waiting. Works on both boards.
 */
public final class Infrared {
    private Infrared() {
    }

    /** {@link #readByte} result when no start bit arrived before the timeout. */
    public static final int TIMEOUT = -1;
    /** {@link #readByte} result when a byte arrived with a wrong parity bit or stop bit. */
    public static final int FRAMING_ERROR = -2;

    /** Configures the pins and the bit rate, e.g. {@code 2400} for the LEGO RCX. */
    public static native void begin(int receivePin, int transmitPin, int baud);

    /** Whether the receiver currently sees a burst, i.e. a byte may be arriving; never blocks. */
    public static native boolean receiving();

    /**
     * Waits up to {@code timeoutMicros} for a start bit, then reads one byte: {@code 0..255},
     * {@link #TIMEOUT} or {@link #FRAMING_ERROR}.
     */
    public static native int readByte(int timeoutMicros);

    /** Transmits the low 8 bits of {@code value} as one framed byte. */
    public static native void writeByte(int value);

    /**
     * Transmits one framed byte like {@link #writeByte} while reading it back through the receiver
     * module, which hears the board's own IR LED. Each bit is sampled in the middle of its time plus
     * {@code latencyMicros}, to allow for the demodulator's response delay (typically 100 to 400
     * microseconds). Returns the byte received, {@code 0..255}, {@link #TIMEOUT} if nothing was heard or
     * a pin is unset, or {@link #FRAMING_ERROR}. Used to test the wiring and the carrier timing: a
     * working pair returns the byte sent for some latency.
     */
    public static native int echoByte(int value, int latencyMicros);

    /**
     * Transmits a burst of the 38 kHz carrier for {@code micros} microseconds. With {@link #space}, it
     * builds protocols that encode bits in pulse lengths rather than as serial bytes, such as
     * {@link io.github.jabrena.juno.api.lego.PowerFunctionsRemote}; the transmit pin must be set by
     * {@link #begin} (the baud rate is ignored by these two calls).
     */
    public static native void mark(int micros);

    /** Keeps the IR LED dark for {@code micros} microseconds. */
    public static native void space(int micros);
}
