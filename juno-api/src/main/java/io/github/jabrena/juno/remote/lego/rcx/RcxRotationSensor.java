package io.github.jabrena.juno.remote.lego.rcx;

/** A rotation sensor plugged into an RCX or Scout input, e.g. {@code new RcxRotationSensor(remote.getSensor1())}. */
public final class RcxRotationSensor {
    private final RcxSensor port;

    /** A rotation sensor on {@code port}. Create it once, at startup, and call {@link #begin} after the remote's {@code begin}. */
    public RcxRotationSensor(RcxSensor port) {
        this.port = port;
    }

    /** Configures the input for a rotation sensor counting 16 steps per turn. Returns whether the brick acknowledged. */
    public boolean begin() {
        return port.setRotation();
    }

    /** The signed count in steps, 16 per turn, or {@code 0} if the brick did not answer. */
    public int steps() {
        return port.rotationSteps();
    }

    /** The angle in degrees, 22.5 per step rounded down. */
    public int degrees() {
        return port.rotationDegrees();
    }

    /** Sets the count back to zero. */
    public void reset() {
        port.clear();
    }
}
