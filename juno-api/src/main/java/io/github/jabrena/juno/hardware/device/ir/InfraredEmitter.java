package io.github.jabrena.juno.hardware.device.ir;

/** An infrared emitter module that configures the low-level transport required by its circuit. */
public interface InfraredEmitter {
    /** The Arduino pin driving the emitter module's digital signal. */
    int signalPin();

    /** Configures this emitter with {@code receiver} for framed bytes at {@code baud}. */
    void begin(InfraredReceiver receiver, int baud);
}
