package io.github.jabrena.juno.hardware.device.ir.hx;

import io.github.jabrena.juno.hardware.device.ir.InfraredReceiver;

/** An HX-M121 38 kHz demodulating infrared receiver. */
public final class HXM121InfraredReceiver implements InfraredReceiver {
    private final int signalPin;

    /** Creates an HX-M121 receiver connected to {@code signalPin}. */
    public HXM121InfraredReceiver(int signalPin) {
        if (signalPin < -1) {
            throw new IllegalArgumentException("HX-M121 infrared receiver signal pin must be -1 or non-negative");
        }
        this.signalPin = signalPin;
    }

    @Override
    public int signalPin() {
        return signalPin;
    }
}
