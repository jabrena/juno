package io.github.jabrena.juno.remote.lego.rcx;

/** A light sensor plugged into an RCX or Scout input, e.g. {@code new RcxLightSensor(remote.getSensor1())}. */
public final class RcxLightSensor {
    private final RcxSensor port;

    /** A light sensor on {@code port}. Create it once, at startup, and call {@link #begin} after the remote's {@code begin}. */
    public RcxLightSensor(RcxSensor port) {
        this.port = port;
    }

    /** Configures the input for a light sensor reading percent. Returns whether the brick acknowledged. */
    public boolean begin() {
        return port.setLight();
    }

    /** The light level in percent, {@code 0..100}, or {@code -1} if the brick did not answer. */
    public int lightPercent() {
        return port.readValue();
    }
}
