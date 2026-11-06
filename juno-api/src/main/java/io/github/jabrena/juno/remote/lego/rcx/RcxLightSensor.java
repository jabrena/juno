package io.github.jabrena.juno.remote.lego.rcx;

/** A light sensor plugged into an RCX input, e.g. {@code new RcxLightSensor(rcx.getSensor3())}. */
public final class RcxLightSensor {
    private final RcxSensor port;

    /** A light sensor on {@code port}. Create it once, at startup; construction configures the port. */
    public RcxLightSensor(RcxSensor port) {
        this.port = port;
        if (!begin()) {
            throw new IllegalStateException("RCX light sensor did not acknowledge initialization");
        }
    }

    /** Configures the input for a light sensor reading percent. Returns whether the brick acknowledged. */
    private boolean begin() {
        return port.setLight();
    }

    /** The light level in percent, {@code 0..100}, or {@code -1} if the brick did not answer. */
    public int lightPercent() {
        return port.readValue();
    }
}
