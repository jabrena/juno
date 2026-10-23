package io.github.jabrena.juno.remote.lego.rcx;

/** A touch sensor plugged into an RCX or Scout input, e.g. {@code new RcxTouchSensor(remote.getSensor1())}. */
public final class RcxTouchSensor {
    private final RcxSensor port;

    /** A touch sensor on {@code port}. Create it once, at startup, and call {@link #begin} after the remote's {@code begin}. */
    public RcxTouchSensor(RcxSensor port) {
        this.port = port;
    }

    /** Configures the input for a touch sensor. Returns whether the brick acknowledged. */
    public boolean begin() {
        return port.setTouch();
    }

    /** Whether the sensor is pressed; {@code false} also when the brick did not answer. */
    public boolean isPressed() {
        return port.readValue() == 1;
    }
}
