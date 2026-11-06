package io.github.jabrena.juno.hardware.device.grove.sensors;

import io.github.jabrena.juno.hardware.device.ir.InfraredDriver;
import io.github.jabrena.juno.hardware.device.ir.InfraredEmitter;

/**
 * A Grove infrared emitter module's signal connection.
 *
 * <p>The emitter is configured together with a {@link GroveInfraredReceiver} by
 * {@link io.github.jabrena.juno.remote.lego.rcx.RcxLink} constructs the link; it does not allocate or configure anything while a
 * frame is being transmitted.
 */
public final class GroveInfraredEmitter implements InfraredEmitter {
    private static final InfraredDriver.Type DRIVER = InfraredDriver.Type.GROVE;
    private final int signalPin;

    /** Creates an emitter connected to a Grove socket's signal pin. */
    public GroveInfraredEmitter(int signalPin) {
        if (signalPin < 0) {
            throw new IllegalArgumentException("Grove infrared emitter signal pin must be non-negative");
        }
        this.signalPin = signalPin;
    }

    /** The Nano or Arduino pin carrying the emitter's signal. */
    @Override
    public int signalPin() {
        return signalPin;
    }

    @Override
    public InfraredDriver.Type driver() {
        return DRIVER;
    }
}
