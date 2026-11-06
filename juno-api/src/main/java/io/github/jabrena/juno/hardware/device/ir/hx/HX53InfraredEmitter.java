package io.github.jabrena.juno.hardware.device.ir.hx;

import io.github.jabrena.juno.hardware.device.ir.InfraredDriver;
import io.github.jabrena.juno.hardware.device.ir.InfraredEmitter;

/** An HX-53 infrared emitter using Juno's original software-carrier driver. */
public final class HX53InfraredEmitter implements InfraredEmitter {
    private static final InfraredDriver.Type DRIVER = InfraredDriver.Type.HX;
    private final int signalPin;

    /** Creates an HX-53 emitter connected to {@code signalPin}. */
    public HX53InfraredEmitter(int signalPin) {
        if (signalPin < -1) {
            throw new IllegalArgumentException("HX-53 infrared emitter signal pin must be -1 or non-negative");
        }
        this.signalPin = signalPin;
    }

    @Override
    public int signalPin() {
        return signalPin;
    }

    @Override
    public InfraredDriver.Type driver() {
        return DRIVER;
    }
}
