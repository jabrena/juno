package io.github.jabrena.juno.remote.lego.rcx;

/** A touch sensor plugged into an RCX input, e.g. {@code new RcxTouchSensor(rcx.getSensor1())}. */
public final class RcxTouchSensor {
    private final RcxSensor port;

    /** A touch sensor on {@code port}. Create it once, at startup; construction configures the port. */
    public RcxTouchSensor(RcxSensor port) {
        this.port = port;
        if (!begin()) {
            throw new IllegalStateException("RCX touch sensor did not acknowledge initialization");
        }
    }

    /** Configures the input for a touch sensor. Returns whether the brick acknowledged. */
    private boolean begin() {
        return port.setTouch();
    }

    /** Whether the sensor is pressed; {@code false} also when the brick did not answer. */
    public boolean isPressed() {
        return port.readValue() == 1;
    }
}
