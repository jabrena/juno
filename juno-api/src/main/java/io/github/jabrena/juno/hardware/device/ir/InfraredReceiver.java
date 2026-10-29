package io.github.jabrena.juno.hardware.device.ir;

/** A demodulating infrared receiver module connected to one signal pin. */
public interface InfraredReceiver {
    /** The Arduino pin carrying the receiver module's digital signal. */
    int signalPin();
}
