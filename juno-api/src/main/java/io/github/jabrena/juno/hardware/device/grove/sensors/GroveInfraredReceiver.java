package io.github.jabrena.juno.hardware.device.grove.sensors;

import io.github.jabrena.juno.hardware.device.ir.InfraredReceiver;

/**
 * A Grove infrared receiver module's signal connection.
 *
 * <p>The receiver is configured together with a {@link GroveInfraredEmitter} by
 * {@link io.github.jabrena.juno.remote.lego.rcx.RcxLink#begin}; it does not allocate or configure anything while a
 * frame is being received.
 */
public final class GroveInfraredReceiver implements InfraredReceiver {
    private final int signalPin;

    /** Creates a receiver connected to a Grove socket's signal pin. */
    public GroveInfraredReceiver(int signalPin) {
        if (signalPin < 0) {
            throw new IllegalArgumentException("Grove infrared receiver signal pin must be non-negative");
        }
        this.signalPin = signalPin;
    }

    /** The Nano or Arduino pin carrying the receiver's signal. */
    @Override
    public int signalPin() {
        return signalPin;
    }
}
