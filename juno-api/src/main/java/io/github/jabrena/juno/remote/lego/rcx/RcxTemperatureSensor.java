package io.github.jabrena.juno.remote.lego.rcx;

/** A temperature sensor plugged into an RCX or Scout input, e.g. {@code new RcxTemperatureSensor(remote.getSensor1())}. */
public final class RcxTemperatureSensor {
    private final RcxSensor port;

    /** A temperature sensor on {@code port}. Create it once, at startup, and call {@link #begin} after the remote's {@code begin}. */
    public RcxTemperatureSensor(RcxSensor port) {
        this.port = port;
    }

    /** Configures the input for a temperature sensor reading Celsius. Returns whether the brick acknowledged. */
    public boolean begin() {
        return port.setTemperature();
    }

    /** The temperature in tenths of a degree Celsius (235 is 23.5 degrees), or {@code 0} if the brick did not answer. */
    public int temperatureTenths() {
        return port.temperatureTenths();
    }
}
