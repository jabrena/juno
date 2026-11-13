package io.github.jabrena.juno.remote.lego.poweredup;

/**
 * A port of a Powered Up hub, as one value for both external ports and built-in sensors.
 *
 * @deprecated Kept for one compatibility release. Use {@link PoweredUpPort} for the external ports A to D, and
 *     {@link BuiltInSensor} for the Technic Hub's built-in sensors; the {@code RemotePoweredUpHub} overloads that take
 *     a {@code HubPort} delegate to them.
 */
@Deprecated(since = "0.1.0")
public enum HubPort {
    /** External port A, the first motor or sensor port. */
    A(0),
    /** External port B. */
    B(1),
    /** External port C (Technic and Move hubs). */
    C(2),
    /** External port D (Technic and Move hubs). */
    D(3),
    /** The Technic Hub's built-in accelerometer, gyroscope and tilt sensor, which report like a sensor on a port. */
    TECHNIC_ACCELEROMETER(0x61),
    /** The Technic Hub's built-in gyroscope. */
    TECHNIC_GYRO(0x62),
    /** The Technic Hub's built-in tilt sensor. */
    TECHNIC_TILT(0x63),
    /** The Technic Hub's built-in current sensor. */
    TECHNIC_CURRENT(0x3B),
    /** The Technic Hub's built-in voltage sensor. */
    TECHNIC_VOLTAGE(0x3C);

    private final int value;

    HubPort(int value) {
        this.value = value;
    }

    /** The port number the LEGO Wireless Protocol uses for this port. */
    int value() {
        return value;
    }
}
