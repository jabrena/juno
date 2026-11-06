package io.github.jabrena.juno.remote.lego.rcx;

/** A rotation sensor plugged into an RCX input, e.g. {@code new RcxRotationSensor(rcx.getSensor2())}. */
public final class RcxRotationSensor {
    private final RcxSensor port;

    /** A rotation sensor on {@code port}. Create it once, at startup; construction configures the port. */
    public RcxRotationSensor(RcxSensor port) {
        this.port = port;
        if (!begin()) {
            throw new IllegalStateException("RCX rotation sensor did not acknowledge initialization");
        }
    }

    /** Configures the input for a rotation sensor counting 16 steps per turn. Returns whether the brick acknowledged. */
    private boolean begin() {
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
